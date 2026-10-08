package com.muazdev.hijricalendar.widget.glance

import android.content.Context
import androidx.compose.runtime.saveable.Saver
import androidx.core.content.edit
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.muazdev.hijricalendar.widgetdata.HijriYearMonth
import com.muazdev.hijricalendar.widgetdata.NumeralStyle
import com.muazdev.hijricalendar.widgetdata.WeekStart
import com.muazdev.hijricalendar.widgetdata.WidgetLanguage
import com.muazdev.hijricalendar.widgetdata.WidgetLocalization
import com.muazdev.hijricalendar.widgetdata.WidgetOptions
import com.muazdev.hijricalendar.widgetdata.WidgetOptionsJson
import com.muazdev.hijricalendar.widgetdata.WidgetSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

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
public object HijriWidgetConfig {

    private const val LEGACY_PREFS = "hijri_widget_config"
    private const val RUNTIME_PREFS = "hijri_widget_runtime"

    // Keys inside the per-widget Glance preferences store.
    private const val KEY_OPTIONS = "options"
    private const val KEY_VIEWED = "viewed"
    private const val KEY_SELECTED_DAY = "selected_day"
    private const val KEY_LOADING = "loading"

    private val OPTIONS_KEY = stringPreferencesKey(KEY_OPTIONS)

    /**
     * `internal` rather than private so the local unit tests can round-trip the viewed month
     * without a device: the encoding used to be `org.json`, which is a stubbed `android.jar` class
     * in unit tests, so this pair was previously only verifiable on real hardware.
     */
    internal val VIEWED_KEY = stringPreferencesKey(KEY_VIEWED)

    /**
     * The day the user tapped on the grid (FD-09). `internal` for the same reason as [VIEWED_KEY]:
     * the encoding is JSON, and `org.json` is a stubbed `android.jar` class in local unit tests, so an
     * unencodable store is only verifiable off-device.
     */
    internal val SELECTED_DAY_KEY = stringPreferencesKey(KEY_SELECTED_DAY)

    /**
     * The month-navigation in-flight flag. A **boolean**, not the viewed month it is heading for:
     * the widget cannot paint a month it has not computed yet, so what it needs to know is only
     * "something is being computed" — the value it will land on is written to [VIEWED_KEY] by the
     * same step that clears this, and the two are never both absent from a settled widget.
     */
    internal val LOADING_KEY = booleanPreferencesKey(KEY_LOADING)

    private const val KEY_LOADING_STARTED = "loading_started"

    /**
     * Wall-clock time [LOADING_KEY] was set, written in the same edit as the flag itself.
     *
     * The flag alone cannot answer "is a step actually running?" — a process killed mid-step never
     * reaches the `finally` that clears it, and the flag outlives the step in persistent state. The
     * stamp is what lets the reader below tell an in-flight step from an orphan; see
     * [decodeLoadingIfFresh]. `internal` rather than private for the same reason as [VIEWED_KEY]:
     * the pairing is only verifiable off-device unless the keys are visible to the unit tests.
     */
    internal val LOADING_STARTED_KEY = longPreferencesKey(KEY_LOADING_STARTED)

    /**
     * How long a loading flag may be set before it is assumed orphaned.
     *
     * A real step is the Pakistan warm-up (seconds, cold) plus two Glance compositions — well
     * under this. The bound exists for the failure it cannot see directly: a process killed
     * between [setLoading] and its `finally`, after which nothing in any future process will
     * clear the flag. Five minutes keeps that window small while leaving an order of magnitude of
     * headroom over the longest legitimate step.
     */
    internal const val LOADING_STALE_MS: Long = 5 * 60_000L

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

    // Marks the last local day the generated (Android 15+) picker previews were published, so
    // [HijriWidgetPreviewPublisher] regenerates them at most once per day — the system rate-limits
    // widget-preview updates (~2/hour), so every app launch / refresh must not re-publish.
    private const val KEY_PREVIEW_PUBLISHED_EPOCH_DAY = "previews_published_epoch_day"

    // Family-wide options mirror (SharedPreferences), consumed by widgets that have no settings
    // screen of their own (the Today strip): every [save] re-writes it, so those widgets follow
    // the most recent configure-screen save or on-widget source toggle.
    private const val KEY_FAMILY_OPTIONS = "family_options"

    /**
     * The widget is shown unless the app explicitly disables it; -1 is the "not pinned"
     * sentinel for both year and month.
     */
    private const val NOT_PINNED = -1

    /**
     * Fields the pre-shared format wrote as integer ordinals. At least one of these being an integer
     * is what identifies a blob as legacy — see [decodeOptionsJson].
     *
     * **A field added since that format is deliberately absent**, and `weekendPattern` is the first
     * one. The discriminator's contract is "the pre-shared format wrote enum fields as integers", and
     * that format is frozen: it wrote exactly four enum fields, all of them above. Listing a fifth
     * would widen the gate to blobs whose *other* enum fields are already named — and
     * [decodeLegacyOrdinalJson] reads those as ordinals, so `intOrNull` answers `null` for `"URDU"`
     * and the blob would silently lose its language, numerals, source and month-name language. A new
     * field's *wrong* value is already handled where it belongs: `coerceInputValues` in
     * [WidgetOptionsJson] folds an unreadable enum to its default, which for [weekendPattern] is the
     * behaviour every widget rendered before the field existed.
     */
    private val LEGACY_ENUM_KEYS = listOf("language", "numeralStyle", "source", "monthNameLanguage")

    /**
     * Whether this object was written by the pre-shared format, i.e. carries at least one enum field
     * as an integer.
     *
     * `intOrNull` is the whole test and is deliberately the *only* one: it answers `null` for a
     * string like `"URDU"`, so a blob in the current named format cannot match, and a JSON `null`
     * cannot match either. There is no shape for which both formats are plausible, which is what
     * makes this a discriminator rather than a heuristic.
     */
    private fun JsonObject.hasLegacyOrdinalEnum(): Boolean = LEGACY_ENUM_KEYS.any { key ->
        (this[key] as? JsonPrimitive)?.intOrNull != null
    }

    /**
     * Fresh widgets default to Urdu names, Eastern Arabic-Indic digits and the Calculation
     * source, matching the app's Urdu labels. Existing widgets keep whatever they stored.
     */
    public val DEFAULTS: WidgetOptions = WidgetOptions.DEFAULTS

    /**
     * A Bundle-safe [Saver] for [WidgetOptions], for host apps building their own settings screen:
     * pass it to `rememberSaveable(stateSaver = ...)` so in-progress edits survive
     * rotation/process death like the library's own screens did.
     *
     * Stores the options as one [WidgetOptionsJson] string rather than a positional list, and that
     * is the whole design (WG-06). The previous version wrote enums as **ordinals** into a
     * `listSaver`, then read them back with an unchecked `entries[ordinal]`. Two problems, one of
     * them a crash:
     *
     *  - **Ordinals are not stable across a version boundary.** A Bundle outlives the process and
     *    the app update. A saved list written by a build whose `NumeralStyle` had three entries,
     *    read by one whose has two, indexes off the end. `listSaver.restore` runs inside
     *    `rememberSaveable` on the main thread during composition, so the `IndexOutOfBoundsException`
     *    escapes into composition and takes down the settings screen — the screen the user opened in
     *    order to *fix* the widget.
     *  - **Names survive all of it.** The shared codec already exists precisely because enums must
     *    be stored by name; it is the module's own storage format and the one every other native
     *    renderer writes. Delegating to it also removes the failure mode where the saver and the
     *    JSON store disagree about the field set — this saver used to have its own copy, and
     *    `WidgetOptions.monthLengthOverrides` had to be added to *both* (WG-01).
     *
     * **One-time cost:** saved state in a live Bundle across an in-place app update was written in
     * the old positional format and will not decode, so in-progress settings edits are lost once.
     * `decodeOrNull` returning `null` means that degrades to `DEFAULTS` rather than throwing, which
     * is already a strict improvement over the crash.
     */
    public fun widgetOptionsSaver(): Saver<WidgetOptions, Any> = Saver(
        save = { WidgetOptionsJson.encode(it) },
        // A `null` here would be a saved value that is not a String; fall back the same way an
        // absent value does, so a Bundle written by some other saver cannot crash composition either.
        restore = { WidgetOptionsJson.decodeOrNull(it as? String) },
    )

    // ── Per-widget state (Glance preferences store) ─────────────────────────

    /**
     * Reads the stored options, porting any legacy SharedPreferences config for this widget on the
     * first access after an upgrade. A widget that never stored options follows [DEFAULTS].
     */
    public suspend fun load(context: Context, glanceId: GlanceId): WidgetOptions {
        migrateLegacyIfNeeded(context, glanceId)
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        return decodeOptions(prefs) ?: DEFAULTS
    }

    public suspend fun save(context: Context, glanceId: GlanceId, options: WidgetOptions) {
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
    public fun saveFamily(context: Context, options: WidgetOptions) {
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
    public fun loadFamily(context: Context): WidgetOptions {
        val raw = context.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_FAMILY_OPTIONS, null)
            ?: return DEFAULTS
        return decodeOptionsJson(raw) ?: DEFAULTS
    }

    /** True once a family mirror exists (used to seed it exactly once from legacy config). */
    public fun hasFamily(context: Context): Boolean =
        context.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE)
            .contains(KEY_FAMILY_OPTIONS)

    // ── On-widget navigation state ───────────────────────────────────────────

    /**
     * The month the user navigated to via the widget arrows, or `null` while the widget is
     * "following today". Unlike the config [pinnedYear]/[pinnedMonth], this is runtime state the
     * user edits from the widget itself: it is intentionally kept out of [WidgetOptions] so a
     * configuration-screen save never clobbers it.
     */
    public suspend fun loadViewedMonth(context: Context, glanceId: GlanceId): HijriYearMonth? {
        migrateLegacyIfNeeded(context, glanceId)
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        return decodeViewed(prefs)
    }

    public suspend fun setViewedMonth(
        context: Context,
        glanceId: GlanceId,
        year: Int,
        month: Int,
    ) {
        updateAppWidgetState(context, glanceId) { mutable ->
            mutable[VIEWED_KEY] = encodeViewed(year, month)
        }
    }

    /** Returns the widget to "following today" (clears navigation state). */
    public suspend fun clearViewedMonth(context: Context, glanceId: GlanceId) {
        updateAppWidgetState(context, glanceId) { mutable ->
            mutable.remove(VIEWED_KEY)
        }
    }

    /**
     * Moves the viewed month **and drops the tapped day, in one store transaction**.
     *
     * One transaction because the two are a single state change, not two: a day tapped inside the
     * month the user was looking at says nothing about the month they moved to. Writing them
     * separately is what let the footer keep naming an observance for a day in a month the widget
     * had already left — the reader gets "آج" for a day that is not today, attached to a grid that
     * no longer contains it. It is the same disagreement [HijriWidgetTodayResetCallback] resolves by
     * clearing both, so the arrows now go through one function rather than reproducing that pair of
     * calls at every navigation site.
     *
     * A **day** selection is cleared, but a month *pin* is not: the pin is configuration the user
     * chose in settings, and it is the fallback this navigation is temporarily overriding.
     */
    public suspend fun moveViewedMonth(
        context: Context,
        glanceId: GlanceId,
        year: Int,
        month: Int,
    ) {
        updateAppWidgetState(context, glanceId) { mutable ->
            mutable[VIEWED_KEY] = encodeViewed(year, month)
            mutable.remove(SELECTED_DAY_KEY)
        }
    }

    /**
     * Whether the widget is currently rendering the month a navigation tap asked for.
     *
     * Runtime state, not [WidgetOptions], for the reason the viewed month and the tapped day are
     * also runtime state: it describes the widget's *current position*, and a flag in the published
     * schema would put a transient value into every consumer's saved configuration and settings
     * screen. It is written by the navigation callback before it starts the expensive part and
     * cleared in a `finally`, so an interrupted step cannot leave a spinner on the widget forever.
     */
    public suspend fun setLoading(context: Context, glanceId: GlanceId, loading: Boolean) {
        updateAppWidgetState(context, glanceId) { mutable ->
            if (loading) {
                mutable[LOADING_KEY] = true
                // Stamped in the same edit: the reader must never see one without the other from
                // a write this function performed, or "fresh" would mean half a step.
                mutable[LOADING_STARTED_KEY] = System.currentTimeMillis()
            } else {
                mutable.remove(LOADING_KEY)
                mutable.remove(LOADING_STARTED_KEY)
            }
        }
    }

    /**
     * [decodeLoading] that also refuses an **orphaned** flag: set, but written longer than
     * [LOADING_STALE_MS] ago, or written before the stamp existed.
     *
     * The `finally` in `stepViewedMonth` clears the flag for every in-process outcome — an
     * exception, a dropped step at a range edge, a cancelled callback (`NonCancellable`). What it
     * cannot clear is the state left by a process killed outright mid-step, and [LOADING_KEY]
     * lives in persistent storage, so without this check that one kill renders the widget inert
     * forever: the flag nulls every action (`WidgetActions.whileLoading`), so no tap can even
     * reach the code that would clear it. Reading staleness here — at composition, in whatever
     * process renders next — is the only place that can break that circle; the next render after
     * the threshold (host sweep, midnight alarm, clock change, app open) shows the widget
     * interactive again.
     *
     * A flag with no stamp can only have been written by a build older than this one, whose step
     * is definitionally long over, so it answers `false` rather than trusting an unbounded age.
     */
    internal fun decodeLoadingIfFresh(prefs: Preferences, nowEpochMillis: Long): Boolean {
        val started = prefs[LOADING_STARTED_KEY]
        return prefs[LOADING_KEY] == true &&
            started != null &&
            nowEpochMillis - started < LOADING_STALE_MS
    }

    /**
     * Decodes the loading flag, or `false` when absent.
     *
     * Absent-means-false is what makes a half-finished step harmless: the flag is removed rather
     * than written as `false`, so the store's common case — a widget nobody is navigating — carries
     * no key at all and this is one map lookup that finds nothing.
     */
    public fun decodeLoading(prefs: Preferences): Boolean = prefs[LOADING_KEY] == true

    internal val PREFS: PreferencesGlanceStateDefinition = PreferencesGlanceStateDefinition

    // ── Pure decode helpers, shared with the composable read ─────────────────

    /** Decodes stored options, or `null` when the widget has never stored any. */
    public fun decodeOptions(prefs: Preferences): WidgetOptions? {
        val raw = prefs[OPTIONS_KEY] ?: return null
        return decodeOptionsJson(raw)
    }

    /**
     * Decodes an encoded options JSON blob, or `null` when it is absent/malformed.
     *
     * The current format is `calendar-widget-data`'s `WidgetOptionsJson` (enums by name, unknown
     * *keys* ignored and unknown *values* coerced), shared with every other native renderer.
     * [decodeLegacyOrdinalJson] is tried second for blobs the current format cannot read.
     *
     * **The two readers are told apart by shape, not by "the first one failed".** The legacy format
     * wrote enum fields as integers; the current one writes them as names. The legacy reader requires
     * at least one integer enum field before it will claim a blob, so a blob the current reader
     * rejects for some *other* reason is not silently reinterpreted as legacy — see
     * [decodeLegacyOrdinalJson] for what that cost when the distinction was not made.
     */
    public fun decodeOptionsJson(raw: String): WidgetOptions? {
        val element = raw.parseJsonObjectOrNull() ?: return null

        // The discriminator, and it is a discriminator rather than "try one, then the other" because
        // the two formats are *distinguishable*: the legacy one wrote enum fields as integers, the
        // current one writes them as names.
        //
        // Routing on shape rather than on a failed parse matters in both directions now. "Try the
        // shared decoder, then the legacy one" was the original design and it was wrong twice over:
        // the shared decoder's failure used to send a *modern* blob into the legacy reader, which
        // read every field as "not an integer" and returned all-defaults — a silent, permanent
        // revert (WG-09). And once the shared decoder learned `coerceInputValues` (WD-06), it
        // started *succeeding* on a legacy blob instead, coercing each integer enum to a default —
        // quietly wrong values instead of a clean fall-through. Neither is reachable once the format
        // is decided up front.
        return if (element.hasLegacyOrdinalEnum()) {
            decodeLegacyOrdinalJson(element)?.also {
                // The legacy path is invisible by nature — it looks exactly like a successful read —
                // so it says so. This is a render path, and a store stuck on the old format would
                // otherwise be indistinguishable from a settled one.
                HijriWidgetRefreshLog.d(
                    "config",
                    "read a pre-shared ordinal options blob; it will be rewritten on next save",
                )
            }
        } else {
            val decoded = WidgetOptionsJson.decodeOrReport(raw)
            if (decoded != null && decoded.wasRepaired) {
                // An impossible stored value — a month of 13, a half-set pin — used to decode
                // cleanly and then render as a compact today card in a grid-sized frame, with no
                // error anywhere (WD-09). The repair is the evidence, so it gets logged.
                HijriWidgetRefreshLog.e(
                    "config",
                    "repaired impossible stored option(s): ${decoded.repairedFields.joinToString()}",
                )
            }
            decoded?.options
        }
    }

    /**
     * Reads the pre-1.0 storage format, which wrote the option fields as enum ordinals. Kept because
     * ordinals are exactly what the shared named format fixed: reordering an enum entry used to
     * silently reinterpret every stored widget.
     *
     * **Only reachable for a blob [JsonObject.hasLegacyOrdinalEnum] vouches for.** Every field below
     * is `?: DEFAULTS.x`, so without that gate *any* JSON object decoded to a valid all-defaults
     * `WidgetOptions` — answering "here are the defaults" for input it did not understand, which is
     * a guess dressed as a result.
     *
     * The worked example, and the reason the gate lives in the dispatcher rather than here: a widget
     * written by a newer app and read by an older library stored
     * `{"language":"PERSIAN","adjustmentDays":-2}`. The shared decoder failed on the unknown enum
     * name, the then-unfenced fallback read `"PERSIAN"` as "not an integer" so every field fell back,
     * and the widget silently reverted to Urdu defaults, Western numerals and Umm al-Qura — no log,
     * no error, no way for a user to tell (WG-09).
     *
     * Retire this once no installed build predates the named format: the shape test is then the only
     * thing still routing blobs here, and deleting the reader is a strict improvement rather than a
     * migration risk. AGENTS.md records the same lifecycle for the iOS `migrateLegacySwiftFormat`.
     */
    private fun decodeLegacyOrdinalJson(json: JsonObject): WidgetOptions? {
        val language = json.intOrNull("language")?.let { WidgetLanguage.entries.getOrNull(it) }
            ?: DEFAULTS.language
        // A widget that never stored a numeral style follows its language's default (Eastern for
        // Urdu, Western for English) so a fresh Urdu widget shows Eastern digits.
        val numeralDefault = WidgetLocalization.defaultNumeralStyle(language)
        return WidgetOptions(
            adjustmentDays = json.intOrNull("adjustmentDays") ?: DEFAULTS.adjustmentDays,
            numeralStyle = json.intOrNull("numeralStyle")?.let { NumeralStyle.entries.getOrNull(it) }
                ?: numeralDefault,
            // This whole reader handles the ordinal format, so its weekday field is an index too;
            // mapped into the name-backed enum the current schema uses (WD-05).
            weekStart = WeekStart.fromIndex(
                (json.intOrNull("firstDayOfWeekIndex") ?: DEFAULTS.firstDayOfWeekIndexValue)
                    .coerceIn(0, 6),
            ),
            pinnedYear = json.intOrNull("pinnedYear"),
            pinnedMonth = json.intOrNull("pinnedMonth"),
            source = json.intOrNull("source")?.let { WidgetSource.entries.getOrNull(it) }
                ?: DEFAULTS.source,
            language = language,
            // Widgets stored before this option keep following their language's month names.
            monthNameLanguage = json.intOrNull("monthNameLanguage")
                ?.let { WidgetLanguage.entries.getOrNull(it) } ?: language,
        )
    }

    /** Decodes the viewed month, or `null` when the widget is following today. */
    public fun decodeViewed(prefs: Preferences): HijriYearMonth? {
        val json = prefs[VIEWED_KEY]?.parseJsonObjectOrNull() ?: return null
        val year = json.intOrNull("year")
        val month = json.intOrNull("month")?.takeIf { it in 1..12 }
        return if (year == null || month == null) null else HijriYearMonth(year = year, month = month)
    }

    /**
     * The Hijri day the user tapped on the grid, or `null` when nothing is selected.
     *
     * **Not** part of [WidgetOptions], and that is the whole design point. Options are the user's
     * *configuration* — language, numerals, the pin — and they are mirrored to the family and shown in
     * every settings screen. A tap is the widget's *current position*, the same kind of thing as the
     * viewed month beside it, and putting it in the schema would put a transient value into a
     * user's saved configuration and into every settings screen.
     *
     * [year] is stored alongside the month and day because a day number alone cannot say which month
     * it belongs to, and the grid resolves its month as viewed > pinned > today — a stored day from a
     * month the user has since navigated away from would otherwise attach itself to whatever month
     * happens to be showing.
     */
    public suspend fun loadSelectedDay(
        context: Context,
        glanceId: GlanceId,
    ): HijriDaySelection? {
        migrateLegacyIfNeeded(context, glanceId)
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        return decodeSelectedDay(prefs)
    }

    /** Records the tapped day. Persisted before the render, so the render sees it — see the caller. */
    public suspend fun setSelectedDay(
        context: Context,
        glanceId: GlanceId,
        year: Int,
        month: Int,
        day: Int,
    ) {
        updateAppWidgetState(context, glanceId) { mutable ->
            mutable[SELECTED_DAY_KEY] = encodeSelectedDay(year, month, day)
        }
    }

    /** Forgets the selection — the month reset and widget removal both call this. */
    public suspend fun clearSelectedDay(context: Context, glanceId: GlanceId) {
        updateAppWidgetState(context, glanceId) { mutable -> mutable.remove(SELECTED_DAY_KEY) }
    }

    /**
     * Decodes the selected day from the store, or `null` when absent or unreadable.
     *
     * Every field is range-checked rather than trusted. A corrupt value here would otherwise become a
     * marked day that does not exist, and the mark is drawn from this.
     */
    public fun decodeSelectedDay(prefs: Preferences): HijriDaySelection? {
        val json = prefs[SELECTED_DAY_KEY]?.parseJsonObjectOrNull() ?: return null
        val year = json.intOrNull("year")
        val month = json.intOrNull("month")?.takeIf { it in 1..12 }
        val day = json.intOrNull("day")?.takeIf { it in 1..30 }
        return if (year == null || month == null || day == null) {
            null
        } else {
            HijriDaySelection(year = year, month = month, day = day)
        }
    }

    /** Encodes a selection. `internal` like [encodeViewed], and for the same testability reason. */
    internal fun encodeSelectedDay(year: Int, month: Int, day: Int): String =
        buildJsonObject {
            put("year", year)
            put("month", month)
            put("day", day)
        }.toString()

    // ── Runtime markers (global SharedPreferences, not per-widget view state) ─
    //
    // All `internal` (WG-05). These are the switches that decide whether the widget updates, and a
    // consumer has no legitimate reason to write them: `markUpdatedNow(today)` from outside
    // suppresses every non-bypassing refresh for the rest of the day, permanently, with a log line
    // reading "already refreshed". Every call site is in this module. They were public only because
    // Kotlin demands explicit visibility and each one was written that way; see the public-surface
    // list in the module KDoc.

    /** Marks the widget family as freshly updated for the current local calendar day. */
    internal fun markUpdatedNow(context: Context, epochDay: Long) {
        context.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE).edit {
            putLong(KEY_LAST_UPDATE_EPOCH_DAY, epochDay)
        }
    }

    /** True when the widget family already reflects the given local calendar day. */
    internal fun isFreshFor(context: Context, epochDay: Long): Boolean {
        return context.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_UPDATE_EPOCH_DAY, Long.MIN_VALUE) >= epochDay
    }

    /**
     * Records that a refresh was skipped because the app was in the foreground. The flag is
     * persisted (not just in-memory) so a catch-up still happens if the process dies before the
     * app leaves the foreground; [HijriWidgetRefresher] clears it once the render lands.
     */
    internal fun markRefreshPending(context: Context, pending: Boolean) {
        context.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_REFRESH_PENDING, pending)
        }
    }

    /** True when an earlier refresh was skipped and has not yet been applied. */
    internal fun isRefreshPending(context: Context): Boolean {
        return context.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_REFRESH_PENDING, false)
    }

    /** The epoch-day marker of the last successful family refresh, or [Long.MIN_VALUE]. */
    internal fun lastUpdatedEpochDay(context: Context): Long {
        return context.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_UPDATE_EPOCH_DAY, Long.MIN_VALUE)
    }

    // ── Generated-preview marker ────────────────────────────────────────────

    /** The epoch-day the generated picker previews were last published, or [Long.MIN_VALUE]. */
    internal fun lastPreviewPublishedEpochDay(context: Context): Long {
        return context.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_PREVIEW_PUBLISHED_EPOCH_DAY, Long.MIN_VALUE)
    }

    /** Records that the generated picker previews now reflect the given local calendar day. */
    internal fun markPreviewsPublishedNow(context: Context, epochDay: Long) {
        context.getSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE).edit {
            putLong(KEY_PREVIEW_PUBLISHED_EPOCH_DAY, epochDay)
        }
    }

    // ── Legacy SharedPreferences migration ───────────────────────────────────

    /**
     * Ports a widget's pre-Glance-state config (SharedPreferences `hijri_widget_config`, keyed by
     * the app-widget id digits) into its Glance store, once. No-op after the first write, so it is
     * safe to call from every render/navigation path.
     */
    public suspend fun migrateLegacyIfNeeded(context: Context, glanceId: GlanceId) {
        val suffix = legacySuffix(glanceId)
        val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        if (!legacy.contains("$KEY_ADJUSTMENT_DAYS$suffix")) return
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        if (prefs.contains(OPTIONS_KEY)) return
        val options = readLegacyOptions(legacy, suffix) ?: return
        val viewed = readLegacyViewed(legacy, suffix)
        updateAppWidgetState(context, glanceId) { mutable ->
            mutable[OPTIONS_KEY] = encodeOptions(options)
            val viewedJson = viewed?.let { encodeViewed(it.year, it.month) }
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
            // As above: the SharedPreferences store predates `weekStart` entirely.
            weekStart = WeekStart.fromIndex(
                legacy.getInt("$KEY_FIRST_DAY$key", DEFAULTS.firstDayOfWeekIndexValue).coerceIn(0, 6),
            ),
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

    private fun readLegacyViewed(
        legacy: android.content.SharedPreferences,
        key: String,
    ): HijriYearMonth? {
        val year = legacy.getInt("$KEY_VIEWED_YEAR$key", NOT_PINNED)
        val month = legacy.getInt("$KEY_VIEWED_MONTH$key", NOT_PINNED)
        if (year == NOT_PINNED || month == NOT_PINNED || month !in 1..12) return null
        return HijriYearMonth(year = year, month = month)
    }

    private fun encodeOptions(options: WidgetOptions): String = WidgetOptionsJson.encode(options)

    internal fun encodeViewed(year: Int, month: Int): String =
        buildJsonObject { put("year", year); put("month", month) }.toString()

    /**
     * Parses [raw] as a JSON object, or `null` when it is absent or malformed.
     *
     * This module parses with kotlinx rather than `org.json` for two reasons: `org.json` is a
     * stubbed `android.jar` class in local unit tests (every call throws or answers 0, which
     * silently turns a decode into a bogus value), and the option/viewed formats are already
     * kotlinx-serialized, so one codec covers the whole store.
     */
    private fun String.parseJsonObjectOrNull(): JsonObject? = runCatching {
        Json.parseToJsonElement(this) as? JsonObject
    }.getOrNull()

    private fun JsonObject.intOrNull(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull
}
