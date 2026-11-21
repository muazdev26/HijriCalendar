package com.muazdev.hijricalendar.sample

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.muazdev.hijricalendar.core.DesiLeapRule
import com.muazdev.hijricalendar.core.DesiRules
import com.muazdev.hijricalendar.core.DesiVersion
import com.muazdev.hijricalendar.shared.DesiCellPlacement
import com.muazdev.hijricalendar.shared.DesiNameLanguage

/**
 * The screen's view of [DesiSettingsStore]: Compose state that writes through on every change.
 *
 * The setters are named `update*` rather than `set*` because a `set` prefix collides with the JVM
 * setter the `var` itself generates.
 *
 * A holder rather than a `remember { mutableStateOf(...) }` pair at the call site, for two reasons. The
 * store has to be written in the same expression that changes the state — with two separate callbacks
 * that each remember to persist, one of them eventually does not — and `CalendarApp` is at detekt's
 * method-length limit, so the four lines this saves matter.
 */
@Stable
internal class DesiSampleState(private val context: Context) {

    var enabled by mutableStateOf(DesiSettingsStore.isEnabled(context))
        private set

    var rules by mutableStateOf(DesiSettingsStore.read(context))
        private set

    var nameLanguage by mutableStateOf(DesiSettingsStore.nameLanguage(context))
        private set

    var cellPlacement by mutableStateOf(DesiSettingsStore.cellPlacement(context))
        private set

    fun updateEnabled(value: Boolean) {
        enabled = value
        DesiSettingsStore.setEnabled(context, value)
    }

    fun updateRules(value: DesiRules) {
        rules = value
        DesiSettingsStore.write(context, value)
    }

    fun updateNameLanguage(value: DesiNameLanguage) {
        nameLanguage = value
        DesiSettingsStore.setNameLanguage(context, value)
    }

    fun updateCellPlacement(value: DesiCellPlacement) {
        cellPlacement = value
        DesiSettingsStore.setCellPlacement(context, value)
    }
}

/**
 * Sample-local persistence for the Desi calendar settings.
 *
 * Stores the on/off flag, the version and the leap rule in SharedPreferences so the sample's calendar
 * screen can read them. Falls back to [DesiRules.DEFAULT] when a stored value is unknown.
 *
 * This is sample-only state and deliberately not a `WidgetOptions` field: the widgets render the Hijri
 * date, and `calendar-core`'s Desi calendar is a standalone calculator with no renderer of its
 * own.
 */
internal object DesiSettingsStore {

    private const val PREFS = "hijri_sample"
    private const val KEY_ENABLED = "desi_enabled"
    private const val KEY_VERSION = "desi_version"
    private const val KEY_LEAP_RULE = "desi_leap_rule"
    private const val KEY_NAME_LANGUAGE = "desi_name_language"
    private const val KEY_CELL_PLACEMENT = "desi_cell_placement"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Whether the sample should show the Desi date. Off by default: the calendar is not wired
     * into the grid, so this is the switch that puts it on screen.
     */
    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    /**
     * Persists the on/off flag.
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /**
     * Reads the stored Desi rules. Unknown values fall back to the default.
     */
    fun read(context: Context): DesiRules {
        val prefs = prefs(context)
        val versionName = prefs.getString(KEY_VERSION, null)
        val leapRuleName = prefs.getString(KEY_LEAP_RULE, null)

        val version = runCatching {
            DesiVersion.valueOf(versionName ?: DesiVersion.MOOL_2003.name)
        }.getOrDefault(DesiVersion.MOOL_2003)

        val leapRule = runCatching {
            DesiLeapRule.valueOf(leapRuleName ?: DesiLeapRule.FEB29_IN_PHAGUN.name)
        }.getOrDefault(DesiLeapRule.FEB29_IN_PHAGUN)

        return DesiRules(version, leapRule)
    }

    /**
     * The script the month name is written in. Defaults to English, so nothing a reader who never opens
     * the control sees a script they did not choose.
     */
    fun nameLanguage(context: Context): DesiNameLanguage {
        val saved = prefs(context).getString(KEY_NAME_LANGUAGE, null)
            ?: return DesiNameLanguage.ENGLISH
        return runCatching { DesiNameLanguage.valueOf(saved) }
            .getOrDefault(DesiNameLanguage.ENGLISH)
    }

    /**
     * Persists the chosen script.
     */
    fun setNameLanguage(context: Context, language: DesiNameLanguage) {
        prefs(context).edit().putString(KEY_NAME_LANGUAGE, language.name).apply()
    }

    /**
     * Where the Desi date sits inside a cell. Defaults to under the date the mode would have drawn.
     */
    fun cellPlacement(context: Context): DesiCellPlacement {
        val saved = prefs(context).getString(KEY_CELL_PLACEMENT, null)
            ?: return DesiCellPlacement.UNDER_DATE
        return runCatching { DesiCellPlacement.valueOf(saved) }
            .getOrDefault(DesiCellPlacement.UNDER_DATE)
    }

    /**
     * Persists the chosen placement.
     */
    fun setCellPlacement(context: Context, placement: DesiCellPlacement) {
        prefs(context).edit().putString(KEY_CELL_PLACEMENT, placement.name).apply()
    }

    /**
     * Persists the chosen rules.
     */
    fun write(context: Context, rules: DesiRules) {
        prefs(context).edit()
            .putString(KEY_VERSION, rules.version.name)
            .putString(KEY_LEAP_RULE, rules.leapRule.name)
            .apply()
    }
}
