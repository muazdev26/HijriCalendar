package com.muazdev.hijricalendar.core

/**
 * A date in the Nanakshahi (solar) calendar.
 *
 * @property year Nanakshahi year (e.g. 555 for 2023 CE).
 * @property month Month of year, `1` (Chet) to `12` (Phagun).
 * @property day Day of month, `1` to the month's length (30 or 31).
 */
public data class NanakshahiDate(
    public val year: Int,
    public val month: Int,
    public val day: Int,
) {
    init {
        require(month in 1..12) { "month must be 1..12, was $month" }
        // Full day validation (leap-aware) is done in NanakshahiCalendar; here we only check the
        // absolute upper bound so the data class itself cannot hold an obviously broken value.
        require(day in 1..31) { "day must be 1..31, was $day" }
    }

    override fun toString(): String = "$year-$month-$day"
}
