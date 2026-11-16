package com.muazdev.hijricalendar.sample

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.muazdev.hijricalendar.core.NanakshahiLeapRule
import com.muazdev.hijricalendar.core.NanakshahiRules
import com.muazdev.hijricalendar.core.NanakshahiVersion
import com.muazdev.hijricalendar.shared.NanakshahiNameLanguage

/**
 * The screen's view of [NanakshahiSettingsStore]: Compose state that writes through on every change.
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
internal class NanakshahiSampleState(private val context: Context) {

    var enabled by mutableStateOf(NanakshahiSettingsStore.isEnabled(context))
        private set

    var rules by mutableStateOf(NanakshahiSettingsStore.read(context))
        private set

    var nameLanguage by mutableStateOf(NanakshahiSettingsStore.nameLanguage(context))
        private set

    fun updateEnabled(value: Boolean) {
        enabled = value
        NanakshahiSettingsStore.setEnabled(context, value)
    }

    fun updateRules(value: NanakshahiRules) {
        rules = value
        NanakshahiSettingsStore.write(context, value)
    }

    fun updateNameLanguage(value: NanakshahiNameLanguage) {
        nameLanguage = value
        NanakshahiSettingsStore.setNameLanguage(context, value)
    }
}

/**
 * Sample-local persistence for the Nanakshahi calendar settings.
 *
 * Stores the on/off flag, the version and the leap rule in SharedPreferences so the sample's calendar
 * screen can read them. Falls back to [NanakshahiRules.DEFAULT] when a stored value is unknown.
 *
 * This is sample-only state and deliberately not a `WidgetOptions` field: the widgets render the Hijri
 * date, and `calendar-core`'s Nanakshahi calendar is a standalone calculator with no renderer of its
 * own.
 */
internal object NanakshahiSettingsStore {

    private const val PREFS = "hijri_sample"
    private const val KEY_ENABLED = "nanakshahi_enabled"
    private const val KEY_VERSION = "nanakshahi_version"
    private const val KEY_LEAP_RULE = "nanakshahi_leap_rule"
    private const val KEY_NAME_LANGUAGE = "nanakshahi_name_language"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Whether the sample should show the Nanakshahi date. Off by default: the calendar is not wired
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
     * Reads the stored Nanakshahi rules. Unknown values fall back to the default.
     */
    fun read(context: Context): NanakshahiRules {
        val prefs = prefs(context)
        val versionName = prefs.getString(KEY_VERSION, null)
        val leapRuleName = prefs.getString(KEY_LEAP_RULE, null)

        val version = runCatching {
            NanakshahiVersion.valueOf(versionName ?: NanakshahiVersion.MOOL_2003.name)
        }.getOrDefault(NanakshahiVersion.MOOL_2003)

        val leapRule = runCatching {
            NanakshahiLeapRule.valueOf(leapRuleName ?: NanakshahiLeapRule.FEB29_IN_PHAGUN.name)
        }.getOrDefault(NanakshahiLeapRule.FEB29_IN_PHAGUN)

        return NanakshahiRules(version, leapRule)
    }

    /**
     * The script the month name is written in. Defaults to English, so nothing a reader who never opens
     * the control sees a script they did not choose.
     */
    fun nameLanguage(context: Context): NanakshahiNameLanguage {
        val saved = prefs(context).getString(KEY_NAME_LANGUAGE, null)
            ?: return NanakshahiNameLanguage.ENGLISH
        return runCatching { NanakshahiNameLanguage.valueOf(saved) }
            .getOrDefault(NanakshahiNameLanguage.ENGLISH)
    }

    /**
     * Persists the chosen script.
     */
    fun setNameLanguage(context: Context, language: NanakshahiNameLanguage) {
        prefs(context).edit().putString(KEY_NAME_LANGUAGE, language.name).apply()
    }

    /**
     * Persists the chosen rules.
     */
    fun write(context: Context, rules: NanakshahiRules) {
        prefs(context).edit()
            .putString(KEY_VERSION, rules.version.name)
            .putString(KEY_LEAP_RULE, rules.leapRule.name)
            .apply()
    }
}
