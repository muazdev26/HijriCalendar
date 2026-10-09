package com.muazdev.hijricalendar.widget.glance

import com.muazdev.hijricalendar.widgetdata.HijriYearMonth

/**
 * The Hijri day the user tapped on the grid widget (FD-09).
 *
 * A value type rather than three loose integers, because the three always travel together and a
 * half-set triple is meaningless — the same reason `WidgetOptions` exposes `pinned` as a pair rather
 * than `pinnedYear` and `pinnedMonth` separately (WD-03).
 *
 * @property day 1-30. Hijri months are 29 or 30 days, so 30 is the widest a day can be; a value
 *   outside 1..30 is a corrupt store rather than a real date and is dropped at decode.
 * @property month 1-12.
 */
public data class HijriDaySelection(
    public val year: Int,
    public val month: Int,
    public val day: Int,
) {
    /** The Hijri month this day belongs to. */
    public val yearMonth: HijriYearMonth get() = HijriYearMonth(year = year, month = month)

    /** Whether this selection is in [month]'s month — the test every renderer needs. */
    public fun isInMonth(year: Int, month: Int): Boolean = this.year == year && this.month == month

    override fun toString(): String = "$year-$month-$day"
}
