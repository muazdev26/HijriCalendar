package com.muazdev.hijricalendar.sample

import android.content.Context
import com.muazdev.hijricalendar.core.NanakshahiLeapRule
import com.muazdev.hijricalendar.core.NanakshahiRules
import com.muazdev.hijricalendar.core.NanakshahiVersion

/**
 * Sample-local persistence for the Nanakshahi calendar settings.
 *
 * Stores the version and leap rule in SharedPreferences so the sample's calendar screen
 * can read them. Falls back to [NanakshahiRules.DEFAULT] when a stored value is unknown.
 */
internal object NanakshahiSettingsStore {

    private const val PREFS = "hijri_sample"
    private const val KEY_VERSION = "nanakshahi_version"
    private const val KEY_LEAP_RULE = "nanakshahi_leap_rule"

    /**
     * Reads the stored Nanakshahi rules. Unknown values fall back to the default.
     */
    fun read(context: Context): NanakshahiRules {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
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
     * Persists the chosen rules.
     */
    fun write(context: Context, rules: NanakshahiRules) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_VERSION, rules.version.name)
            .putString(KEY_LEAP_RULE, rules.leapRule.name)
            .apply()
    }
}
