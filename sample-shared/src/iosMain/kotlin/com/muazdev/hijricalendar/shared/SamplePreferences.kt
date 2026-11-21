package com.muazdev.hijricalendar.shared

import com.muazdev.hijricalendar.core.DesiLeapRule
import com.muazdev.hijricalendar.core.DesiRules
import com.muazdev.hijricalendar.core.DesiVersion
import com.muazdev.hijricalendar.ui.DateDisplayMode
import platform.Foundation.NSUserDefaults

/**
 * `NSUserDefaults` mirror of the Android sample's `SavedStateHandle` persistence.
 *
 * The Android app keeps the display mode, the moon-sighting adjustment, the Pakistan toggle and
 * the month-length overrides in a `SavedStateHandle` so they survive process death. iOS has no
 * equivalent, so the same values are written to the standard user defaults instead. The
 * month-length overrides use the same compact `"year-month:length"` CSV the Android app persists,
 * because plain maps are not reliably round-tripped through either store.
 */
@Suppress("TooManyFunctions")
internal object SamplePreferences {

    private const val KEY_DATE_DISPLAY_MODE = "hijri_sample_date_display_mode"
    private const val KEY_ADJUSTMENT_DAYS = "hijri_sample_adjustment_days"
    private const val KEY_PAKISTAN_DATES = "hijri_sample_pakistan_dates"
    private const val KEY_NANAKSHAHI_ENABLED = "hijri_sample_desi_enabled"
    private const val KEY_NANAKSHAHI_LEAP_RULE = "hijri_sample_desi_leap_rule"
    private const val KEY_NANAKSHAHI_NAME_LANGUAGE = "hijri_sample_desi_name_language"
    private const val KEY_NANAKSHAHI_CELL_PLACEMENT = "hijri_sample_desi_cell_placement"

    private const val DEFAULT_ADJUSTMENT_DAYS = 0
    private val DEFAULT_DATE_DISPLAY_MODE = DateDisplayMode.BOTH

    private val defaults: NSUserDefaults
        get() = NSUserDefaults.standardUserDefaults

    fun dateDisplayMode(): DateDisplayMode {
        val saved = defaults.stringForKey(KEY_DATE_DISPLAY_MODE) ?: return DEFAULT_DATE_DISPLAY_MODE
        return runCatching { DateDisplayMode.valueOf(saved) }.getOrDefault(DEFAULT_DATE_DISPLAY_MODE)
    }

    fun setDateDisplayMode(mode: DateDisplayMode) {
        defaults.setObject(mode.name, KEY_DATE_DISPLAY_MODE)
    }

    fun adjustmentDays(): Int =
        if (defaults.objectForKey(KEY_ADJUSTMENT_DAYS) == null) {
            DEFAULT_ADJUSTMENT_DAYS
        } else {
            defaults.integerForKey(KEY_ADJUSTMENT_DAYS).toInt()
        }

    fun setAdjustmentDays(days: Int) {
        defaults.setInteger(days.toLong(), KEY_ADJUSTMENT_DAYS)
    }

    fun pakistanDates(): Boolean =
        if (defaults.objectForKey(KEY_PAKISTAN_DATES) == null) {
            false
        } else {
            defaults.boolForKey(KEY_PAKISTAN_DATES)
        }

    fun setPakistanDates(enabled: Boolean) {
        defaults.setBool(enabled, KEY_PAKISTAN_DATES)
    }

    /**
     * Whether the sample should show the Desi date. Off by default, matching the Android store.
     */
    fun desiEnabled(): Boolean =
        if (defaults.objectForKey(KEY_NANAKSHAHI_ENABLED) == null) {
            false
        } else {
            defaults.boolForKey(KEY_NANAKSHAHI_ENABLED)
        }

    fun setDesiEnabled(enabled: Boolean) {
        defaults.setBool(enabled, KEY_NANAKSHAHI_ENABLED)
    }

    /**
     * The stored Desi rules. An unknown value falls back to [DesiRules.DEFAULT], which is
     * the Android store's rule too.
     */
    fun desiRules(): DesiRules {
        val saved = defaults.stringForKey(KEY_NANAKSHAHI_LEAP_RULE) ?: return DesiRules.DEFAULT
        val rule = runCatching { DesiLeapRule.valueOf(saved) }
            .getOrDefault(DesiLeapRule.FEB29_IN_PHAGUN)
        return DesiRules(DesiVersion.MOOL_2003, rule)
    }

    fun setDesiRules(rules: DesiRules) {
        defaults.setObject(rules.leapRule.name, KEY_NANAKSHAHI_LEAP_RULE)
    }

    /**
     * The stored month-name script. Defaults to English, matching the Android store.
     */
    fun desiNameLanguage(): DesiNameLanguage {
        val saved = defaults.stringForKey(KEY_NANAKSHAHI_NAME_LANGUAGE)
            ?: return DesiNameLanguage.ENGLISH
        return runCatching { DesiNameLanguage.valueOf(saved) }
            .getOrDefault(DesiNameLanguage.ENGLISH)
    }

    fun setDesiNameLanguage(language: DesiNameLanguage) {
        defaults.setObject(language.name, KEY_NANAKSHAHI_NAME_LANGUAGE)
    }

    fun desiCellPlacement(): DesiCellPlacement {
        val saved = defaults.stringForKey(KEY_NANAKSHAHI_CELL_PLACEMENT)
            ?: return DesiCellPlacement.UNDER_DATE
        return runCatching { DesiCellPlacement.valueOf(saved) }
            .getOrDefault(DesiCellPlacement.UNDER_DATE)
    }

    fun setDesiCellPlacement(placement: DesiCellPlacement) {
        defaults.setObject(placement.name, KEY_NANAKSHAHI_CELL_PLACEMENT)
    }

    /**
     * Restores the persisted month-length overrides. No-op: iOS does not use the process-global
     * override table; the calendar state holds its own explicit table.
     */
    fun restoreMonthLengthOverrides() {
        // iOS no longer seeds the global table.
    }

    /** Persists month-length overrides. No-op: iOS does not use the process-global table. */
    fun persistMonthLengthOverrides() {
        // iOS no longer reads the global table.
    }
}
