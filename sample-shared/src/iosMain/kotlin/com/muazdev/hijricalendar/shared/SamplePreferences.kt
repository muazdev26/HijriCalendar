package com.muazdev.hijricalendar.shared

import com.muazdev.hijricalendar.core.DateDisplayMode
import com.muazdev.hijricalendar.core.HijriMonthOverrides
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
internal object SamplePreferences {

    private const val KEY_DATE_DISPLAY_MODE = "hijri_sample_date_display_mode"
    private const val KEY_ADJUSTMENT_DAYS = "hijri_sample_adjustment_days"
    private const val KEY_PAKISTAN_DATES = "hijri_sample_pakistan_dates"
    private const val KEY_MONTH_LENGTH_OVERRIDES = "hijri_sample_month_length_overrides"

    private const val DEFAULT_ADJUSTMENT_DAYS = 0
    private val DEFAULT_DATE_DISPLAY_MODE = DateDisplayMode.HIJRI_ONLY

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
     * Restores the persisted month-length overrides into the process-global
     * [HijriMonthOverrides] singleton. Must run before the calendar state is constructed so its
     * observed grid starts from the restored calendar rather than snapping to it on first read.
     */
    fun restoreMonthLengthOverrides() {
        val encoded = defaults.stringForKey(KEY_MONTH_LENGTH_OVERRIDES)?.takeIf { it.isNotBlank() } ?: return
        val restored = runCatching {
            encoded.split(",").mapNotNull { token ->
                val period = token.substringBefore(":")
                val length = token.substringAfter(":").toIntOrNull() ?: return@mapNotNull null
                val year = period.substringBefore("-").toIntOrNull() ?: return@mapNotNull null
                val month = period.substringAfter("-").toIntOrNull() ?: return@mapNotNull null
                (year to month) to length
            }.toMap()
        }.getOrNull() ?: return
        HijriMonthOverrides.replaceAll(restored)
    }

    /** Persists the current global month-length overrides, clearing the key when there are none. */
    fun persistMonthLengthOverrides() {
        val all = HijriMonthOverrides.all()
        if (all.isEmpty()) {
            defaults.removeObjectForKey(KEY_MONTH_LENGTH_OVERRIDES)
            return
        }
        val encoded = all.entries.joinToString(",") { (period, length) ->
            "${period.first}-${period.second}:$length"
        }
        defaults.setObject(encoded, KEY_MONTH_LENGTH_OVERRIDES)
    }
}
