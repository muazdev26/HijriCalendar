package com.muazdev.hijricalendar.ui

import com.muazdev.hijricalendar.core.CalendarDay
import com.muazdev.hijricalendar.core.HijriEvent

/**
 * Every observance the displayed month holds, in calendar order.
 *
 * This is the banner's content **while nothing has been tapped**: the report's second round asked for
 * "all events in that line for that month, and skip attaching the days with them". So the banner lists
 * names and nothing else — the filled cells already answer *when*, and a day number beside a name in
 * one permanently-present line read as a footnote on a date nobody had asked about.
 *
 * An earlier version resolved a **single** observance by precedence (selected day, then today, then
 * the nearest upcoming one), which made the bar answer a tap. That precedence is gone on purpose: it
 * could only ever show one of a month's observances, so a month with three of them was silently
 * described by one.
 *
 * Taking the month's **cells** rather than `HijriEvents.forMonth(month)` is what keeps the bar and the
 * filled cells from naming different days in Pakistan or observed mode, where a month's day numbers
 * differ from Umm al-Qura's: [CalendarDay.event] resolves through the same router a tap takes, and
 * [CalendarDay.isCurrentMonth] keeps a neighbouring month's padded cell out of the answer even though
 * [days] contains it.
 *
 * Distinct by value, because two cells could carry the same observance (a duplicate in a host's event
 * table) and printing it twice in one line is never what a reader wants.
 *
 * Not the whole rule — see [eventBannerEvents], which narrows this to one name once a day is selected.
 */
internal fun monthEvents(days: List<CalendarDay>): List<HijriEvent> = days
    .asSequence()
    .filter { it.isCurrentMonth }
    .mapNotNull { it.event }
    .distinct()
    .toList()

/**
 * What the banner below the grid shows: the month's whole observance list, or the **one** observance of
 * the tapped day.
 *
 * The two answers are the report's two states, and they are deliberately different questions:
 *
 * - **Nothing tapped** — the user is paging through months, so the bar describes the month on screen:
 *   every observance it holds ([monthEvents]). That is the only place a calendar-wide strip can say
 *   what a month *is*.
 * - **A day tapped, and it carries an observance** — the user just answered "which day?", so the bar
 *   answers the same day back: that one name. Leaving the whole month listed after a tap made the tap
 *   look like it had not landed, since the bar is where a selected observance is read.
 *
 * A tap that lands on a day with **no** observance keeps the month's list. There is nothing to narrow
 * to, and a bar that emptied itself on most taps (`HijriEvents` names one day per month at most) would
 * flicker between content and [HijriCalendarLabels.noEventBanner] while the grid stayed unchanged.
 *
 * A selection made in a **different** month than the one displayed is ignored, exactly as the widget's
 * footer ignores it: paging away from a tapped day must not keep the bar describing a date the grid no
 * longer shows. `isSelected` is resolved per cell across all three calendar spaces, so this works in
 * Pakistan and observed modes without a second lookup.
 */
internal fun eventBannerEvents(days: List<CalendarDay>): List<HijriEvent> {
    val tapped = days.firstOrNull { it.isSelected && it.isCurrentMonth }?.event
    return if (tapped != null) listOf(tapped) else monthEvents(days)
}
