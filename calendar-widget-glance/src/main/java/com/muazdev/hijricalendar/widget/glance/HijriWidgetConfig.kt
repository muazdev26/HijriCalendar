package com.muazdev.hijricalendar.widget.glance

import android.content.Context
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.core.content.edit
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.muazdev.hijricalendar.core.WeekDay
import com.muazdev.hijricalendar.widgetdata.NumeralStyle
import com.muazdev.hijricalendar.widgetdata.WidgetLanguage
import com.muazdev.hijricalendar.widgetdata.WidgetLocalization
import com.muazdev.hijricalendar.widgetdata.WidgetSource
import org.json.JSONObject

/**
 * Per-widget configuration persisted in Glance's preferences state store (one DataStore
 * preferences file per widget id, deleted automatically when the widget is removed), keyed by the
 * Glance id. The on-widget viewed month lives in the same store under a separate key so a
 * configuration-screen save can never clobber navigation state. Also stores the global "last day we
 * refreshed" marker used to catch up after boot/time changes (that marker is process-global, not
 * per-widget view state, so it stays in SharedPreferences).
 *
 * Two things live in that global SharedPreferences file beyond the markers: the family options
 * mirror (see [saveFamily]/[loadFamily]) which lets widgets without a settings screen — the
 * Today strip and the 1x1 date widgets — follow the family's latest configuration.
 *
 * This object is the public configuration API for host apps that build their own settings screen:
 * read the current options with [load], persist edits with [save] (which also writes the family
 * mirror), and trigger a re-render of the edited instance through
 * `HijriWidgetRefresher.refreshInstanceAsync`. See [widgetOptionsSaver] for a Bundle-safe saver
 * to back `rememberSaveable` in the settings UI.
 */
object HijriWidgetConfig {

    private const val LEGACY_PREFS = "hijri_widget_config"
    private const val RUNTIME_PREFS = "hijri_widget_runtime"

    // Keys inside the per-widget Glance preferences store.
    private const val KEY_OPTIONS = "options"
    private const val KEY_VIEWED = "viewed"

    private val OPTIONS_KEY = stringPreferencesKey(KEY_OPTIONS)
    private val VIEWED_KEY = stringPreferencesKey(KEY_VIEWED)

    // Legacy SharedPreferences keys (migration only).
    private const val KEY_ADJUSTMENT_DAYS = "adjustment_days"
    private const val KEY_NUMERAL_STYLE = "numeral_style"
    private const val KEY_FIRST_DAY = "first_day_of_week"
    private const val KEY_PINNED_YEAR = "pinned_year"
    private const val KEY_PINNED_MONTH = "pinned_month"
    private const val KEY_SOURCE = "source"
    private const val KEY_LANGUAGE = "language"
    private const val KEY_VIEWED_YEAR = "viewed_year"
    private const val KEY_VIEWED_MONTH = "viewed_month"

    private const val KEY_LAST_UPDATE_EPOCH_DAY = "last_update_epoch_day"
    private const val KEY_REFRESH_PENDING = "refresh_pending"

    // Family-wide options mirror (SharedPreferences), consumed by widgets that have no settings
    // screen of their own (the Today strip): every [save] re-writes it, so those widgets follow
    // the most recent configure-screen save or on-widget source toggle.
    private const val KEY_FAMILY_OPTIONS = "family_options"

    data class WidgetOptions(
        val adjustmentDays: Int,
        val numeralStyle: NumeralStyle,
        val firstDayOfWeekIndex: Int,
        val pinnedYear: Int?,
        val pinnedMonth: Int?,
        val source: WidgetSource,
        val language: WidgetLanguage,
        /**
         * Which language the Hijri/Gregorian month names render in, independently of [language]
         * (the latter also drives numerals, RTL and weekday names). Lets a widget show Eastern
         * digits with English month names, or Western digits with Urdu month names. Defaults to
         * [language] for widgets stored before this option existed, so old configs keep whatever
         * month-name script they already had.
         */
        val monthNameLanguage: WidgetLanguage = language,
    )

    /**
     * The widget is shown unless the app explicitly disables it; -1 is the "not pinned"
     * sentinel for both year and month.
     */
    private const val NOT_PINNED = -1

    /**
     * Fresh widgets default to Urdu names, Eastern Arabic-Indic digits and the Calculation
     * source, matching the app's Urdu labels. Existing widgets keep whatever they stored.
     */
    val DEFAULTS = WidgetOptions(
        adjustmentDays = 0,
        numeralStyle = WidgetLocalization.defaultNumeralStyle(WidgetLanguage.URDU),
        firstDayOfWeekIndex = WeekDay.DEFAULT_FIRST_DAY.index,
        pinnedYear = null,
        pinnedMonth = null,
        source = WidgetSource.CALCULATION,
        language = WidgetLanguage.URDU,
    )

    /**
     * A Bundle-safe [Saver] for [WidgetOptions] (enums as ordinals, nulls preserved), for host
     * apps building their own settings screen: pass it to `rememberSaveable(stateSaver = ...)`
     * so in-progress edits survive rotation/process death like the library's own screens did.
     */
    fun widgetOptionsSaver(): Saver<WidgetOptions, Any> = listSaver(
        save = {
            listOf(
                it.adjustmentDays,
                it.numeralStyle.ordinal,
                it.firstDayOfWeekIndex,
                it.pinnedYear,
                it.pinnedMonth,
                it.source.ordinal,
                it.language.ordinal,
                it.monthNameLanguage.ordinal,
            )
        },
        restore = {
            val language = WidgetLanguage.entries[it[6] as Int]
            WidgetOptions(
                adjustmentDays = it[0] as Int,
                numeralStyle = NumeralStyle.entries[it[1] as Int],
                firstDayOfWeekIndex = it[2] as Int,
                pinnedYear = it[3],
                pinnedMonth = it[4],
                source = WidgetSource.entries[it[5] as Int],
                language = language,
                // Lists saved before the month-name option existed have 7 entries; keep their
                // month names tied to the widget language instead of crashing on index 7.
                monthNameLanguage = it.getOrNull(7)?.let { ordinal -> WidgetLanguage.entries[ordinal as Int] }
                    ?: language,
            )
        },
    )

    // ── Per-widget state (Glance preferences store) ─────────────────────────

    /**
     * Reads the stored options, porting any legacy SharedPreferences config for this widget on the
     * first access after an upgrade. A widget that never stored options follows [DEFAULTS].
     */
    suspend fun load(context: Context, glanceId: GlanceId): WidgetOptions {
        migrateLegacyIfNeeded(context, glanceId)
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        return decodeOptions(prefs) ?: DEFAULTS
    }

    suspend fun save(context: Context, glanceId: GlanceId, options: WidgetOptions) {
        updateAppWidgetState(context, glanceId) { mutable ->
            mutable[OPTIONS_KEY] = encodeOptions(options)
        }
        saveFamily(context, options)
    }

    // ── Family options mirror (SharedPreferences) ───────────────────────────

    /**
     * Persists the family-wide options mirror. Written by every [save] (configure-screen apply),
     * so widgets without a settings screen — the Today strip and the 1x1 tiles — always render
     * with the family's most recently chosen language, numerals and source.
     */
    fun saveFamily(context: Context, options: WidgetOptions) {
        context.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_FAMILY_OPTIONS, encodeOptions(options))
        }
    }

    /**
     * The options widgets without their own settings screen render with: the family mirror when
     * one has been written, else [DEFAULTS]. Synchronous (SharedPreferences), so it can be read
     * both in a [provideGlance]-style peek and inside the composition, where every
     * `UpdateGlanceState` sweep re-evaluates it after a mirror write.
     */
    fun loadFamily(context: Context): WidgetOptions {
        val raw = context.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_FAMILY_OPTIONS, null)
            ?: return DEFAULTS
        return decodeOptionsJson(raw) ?: DEFAULTS
    }

    /** True once a family mirror exists (used to seed it exactly once from legacy config). */
    fun hasFamily(context: Context): Boolean =
        context.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE)
            .contains(KEY_FAMILY_OPTIONS)

    // ── On-widget navigation state ───────────────────────────────────────────

    /**
     * The month the user navigated to via the widget arrows, or `null` while the widget is
     * "following today". Unlike the config [pinnedYear]/[pinnedMonth], this is runtime state the
     * user edits from the widget itself: it is intentionally kept out of [WidgetOptions] so a
     * configuration-screen save never clobbers it.
     */
    suspend fun loadViewedMonth(context: Context, glanceId: GlanceId): Pair<Int, Int>? {
        migrateLegacyIfNeeded(context, glanceId)
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        return decodeViewed(prefs)
    }

    suspend fun setViewedMonth(context: Context, glanceId: GlanceId, year: Int, month: Int) {
        updateAppWidgetState(context, glanceId) { mutable ->
            mutable[VIEWED_KEY] = JSONObject()
                .put("year", year)
                .put("month", month)
                .toString()
        }
    }

    /** Returns the widget to "following today" (clears navigation state). */
    suspend fun clearViewedMonth(context: Context, glanceId: GlanceId) {
        updateAppWidgetState(context, glanceId) { mutable ->
            mutable.remove(VIEWED_KEY)
        }
    }

    val PREFS = PreferencesGlanceStateDefinition

    // ── Pure decode helpers, shared with the composable read ─────────────────

    /** Decodes stored options, or `null` when the widget has never stored any. */
    fun decodeOptions(prefs: Preferences): WidgetOptions? {
        val raw = prefs[OPTIONS_KEY] ?: return null
        return decodeOptionsJson(raw)
    }

    /** Decodes an encoded options JSON blob, or `null` when it is absent/malformed. */
    fun decodeOptionsJson(raw: String): WidgetOptions? {
        return runCatching {
            val json = JSONObject(raw)
            val language = WidgetLanguage.entries.getOrElse(
                json.optInt("language", DEFAULTS.language.ordinal),
            ) { DEFAULTS.language }
            // A widget that never stored a numeral style follows its language's default (Eastern
            // for Urdu, Western for English) so a fresh Urdu widget shows Eastern digits.
            val numeralDefault = WidgetLocalization.defaultNumeralStyle(language)
            WidgetOptions(
                adjustmentDays = json.optInt("adjustmentDays", DEFAULTS.adjustmentDays),
                numeralStyle = NumeralStyle.entries.getOrElse(
                    json.optInt("numeralStyle", numeralDefault.ordinal),
                ) { numeralDefault },
                firstDayOfWeekIndex = json
                    .optInt("firstDayOfWeekIndex", DEFAULTS.firstDayOfWeekIndex)
                    .coerceIn(0, 6),
                pinnedYear = json.optIntOrNull("pinnedYear"),
                pinnedMonth = json.optIntOrNull("pinnedMonth"),
                source = WidgetSource.entries.getOrElse(
                    json.optInt("source", DEFAULTS.source.ordinal),
                ) { DEFAULTS.source },
                language = language,
                // Widgets stored before this option keep following their language's month names.
                monthNameLanguage = WidgetLanguage.entries.getOrElse(
                    json.optInt("monthNameLanguage", language.ordinal),
                ) { language },
            )
        }.getOrNull()
    }

    /** Decodes the viewed month, or `null` when the widget is following today. */
    fun decodeViewed(prefs: Preferences): Pair<Int, Int>? {
        val raw = prefs[VIEWED_KEY] ?: return null
        return runCatching {
            val json = JSONObject(raw)
            val year = json.optInt("year", NOT_PINNED)
            val month = json.optInt("month", NOT_PINNED)
            if (year == NOT_PINNED || month == NOT_PINNED) null else year to month
        }.getOrNull()
    }

    // ── Runtime markers (global SharedPreferences, not per-widget view state) ─

    /** Marks the widget family as freshly updated for the current local calendar day. */
    fun markUpdatedNow(context: Context, epochDay: Long) {
        context.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE).edit {
            putLong(KEY_LAST_UPDATE_EPOCH_DAY, epochDay)
        }
    }

    /** True when the widget family already reflects the given local calendar day. */
    fun isFreshFor(context: Context, epochDay: Long): Boolean {
        return context.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_UPDATE_EPOCH_DAY, Long.MIN_VALUE) >= epochDay
    }

    /**
     * Records that a refresh was skipped because the app was in the foreground. The flag is
     * persisted (not just in-memory) so a catch-up still happens if the process dies before the
     * app leaves the foreground; [HijriWidgetRefresher] clears it once the render lands.
     */
    fun markRefreshPending(context: Context, pending: Boolean) {
        context.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_REFRESH_PENDING, pending)
        }
    }

    /** True when an earlier refresh was skipped and has not yet been applied. */
    fun isRefreshPending(context: Context): Boolean {
        return context.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_REFRESH_PENDING, false)
    }

    /** The epoch-day marker of the last successful family refresh, or [Long.MIN_VALUE]. */
    fun lastUpdatedEpochDay(context: Context): Long {
        return context.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_UPDATE_EPOCH_DAY, Long.MIN_VALUE)
    }

    // ── Legacy SharedPreferences migration ───────────────────────────────────

    /**
     * Ports a widget's pre-Glance-state config (SharedPreferences `hijri_widget_config`, keyed by
     * the app-widget id digits) into its Glance store, once. No-op after the first write, so it is
     * safe to call from every render/navigation path.
     */
    suspend fun migrateLegacyIfNeeded(context: Context, glanceId: GlanceId) {
        val suffix = legacySuffix(glanceId)
        val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        if (!legacy.contains("$KEY_ADJUSTMENT_DAYS$suffix")) return
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        if (prefs.contains(OPTIONS_KEY)) return
        val options = readLegacyOptions(legacy, suffix) ?: return
        val viewed = readLegacyViewed(legacy, suffix)
        updateAppWidgetState(context, glanceId) { mutable ->
            mutable[OPTIONS_KEY] = encodeOptions(options)
            val viewedJson = viewed?.let {
                JSONObject().put("year", it.first).put("month", it.second).toString()
            }
            if (viewedJson == null) {
                mutable.remove(VIEWED_KEY)
            } else {
                mutable[VIEWED_KEY] = viewedJson
            }
        }
        // Seed the family mirror (once) so the Today strip inherits this widget's ported config
        // even if the user never opens the configure screen after the upgrade.
        if (!hasFamily(context)) {
            saveFamily(context, options)
        }
    }

    /** Digits-only suffix used to key legacy SharedPreferences entries. */
    internal fun legacySuffix(glanceId: GlanceId): String =
        glanceId.toString().filter { it.isDigit() }.ifEmpty { glanceId.toString() }

    private fun readLegacyOptions(legacy: android.content.SharedPreferences, key: String): WidgetOptions? {
        val language = WidgetLanguage.entries.getOrElse(
            legacy.getInt("$KEY_LANGUAGE$key", DEFAULTS.language.ordinal),
        ) { DEFAULTS.language }
        val numeralDefault = WidgetLocalization.defaultNumeralStyle(language)
        return WidgetOptions(
            adjustmentDays = legacy.getInt("$KEY_ADJUSTMENT_DAYS$key", DEFAULTS.adjustmentDays),
            numeralStyle = NumeralStyle.entries.getOrElse(
                legacy.getInt("$KEY_NUMERAL_STYLE$key", numeralDefault.ordinal),
            ) { numeralDefault },
            firstDayOfWeekIndex = legacy
                .getInt("$KEY_FIRST_DAY$key", DEFAULTS.firstDayOfWeekIndex)
                .coerceIn(0, 6),
            pinnedYear = legacy.getInt("$KEY_PINNED_YEAR$key", NOT_PINNED).let { if (it == NOT_PINNED) null else it },
            pinnedMonth = legacy.getInt("$KEY_PINNED_MONTH$key", NOT_PINNED).let { if (it == NOT_PINNED) null else it },
            source = WidgetSource.entries.getOrElse(
                legacy.getInt("$KEY_SOURCE$key", DEFAULTS.source.ordinal),
            ) { DEFAULTS.source },
            language = language,
            // Legacy config had no separate month-name language; it followed `language`.
            monthNameLanguage = language,
        )
    }

    private fun readLegacyViewed(legacy: android.content.SharedPreferences, key: String): Pair<Int, Int>? {
        val year = legacy.getInt("$KEY_VIEWED_YEAR$key", NOT_PINNED)
        val month = legacy.getInt("$KEY_VIEWED_MONTH$key", NOT_PINNED)
        if (year == NOT_PINNED || month == NOT_PINNED) return null
        return year to month
    }

    private fun encodeOptions(options: WidgetOptions): String =
        JSONObject()
            .put("adjustmentDays", options.adjustmentDays)
            .put("numeralStyle", options.numeralStyle.ordinal)
            .put("firstDayOfWeekIndex", options.firstDayOfWeekIndex)
            .put("pinnedYear", options.pinnedYear ?: JSONObject.NULL)
            .put("pinnedMonth", options.pinnedMonth ?: JSONObject.NULL)
            .put("source", options.source.ordinal)
            .put("language", options.language.ordinal)
            .put("monthNameLanguage", options.monthNameLanguage.ordinal)
            .toString()

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (isNull(key)) null else optInt(key)
}
