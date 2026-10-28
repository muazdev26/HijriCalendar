package com.muazdev.hijricalendar.ui

import com.muazdev.hijricalendar.core.CalendarDay
import com.muazdev.hijricalendar.core.CalendarEvent
import com.muazdev.hijricalendar.core.HijriEvent
import com.muazdev.hijricalendar.core.PakistanEvent

/**
 * The notable dates the displayed month holds, in calendar order and split by coordinate.
 *
 * Two typed lists because [HijriCalendarLabels] has a published lambda for a Hijri list
 * ([HijriCalendarLabels.monthEventBanner]) that a host may already override; a month that holds a
 * Pakistani national day goes through the additive
 * [HijriCalendarLabels.monthEventBannerWithPakistan] instead, so an existing label set keeps rendering
 * exactly as it did on every non-Pakistan month.
 *
 * [ordered] is the same set as **one stream in cell order**, which is what the combined label receives.
 * It cannot be reconstructed by concatenating [hijri] and [pakistan]: a Gregorian-fixed national day
 * and a Hijri observance interleave by day, and concatenation would print August's national day before
 * July's observance in a month that straddles both.
 */
internal data class MonthEvents(
    val hijri: List<HijriEvent>,
    val pakistan: List<PakistanEvent>,
    val ordered: List<CalendarEvent>,
) {
    val isEmpty: Boolean get() = ordered.isEmpty()
}

/**
 * Every notable date the displayed month holds, in calendar order.
 *
 * This is the banner's content **while nothing has been tapped**: the report's second round asked for
 * "all events in that line for that month, and skip attaching the days with them". So the banner lists
 * names and nothing else — the filled cells already answer *when*, and a day number beside a name in
 * one permanently-present line read as a footnote on a date nobody had asked about.
 *
 * Taking the month's **cells** rather than `HijriEvents.forMonth(month)` is what keeps the bar and the
 * filled cells from naming different days in Pakistan or observed mode, where a month's day numbers
 * differ from Umm al-Qura's: [CalendarDay.event] and [CalendarDay.pakistanEvent] resolve through the
 * same cell the grid paints, and [CalendarDay.isCurrentMonth] keeps a neighbouring month's padded cell
 * out of the answer even though [days] contains it.
 *
 * One pass collects all three lists, so the typed lists and [MonthEvents.ordered] can never disagree.
 * Distinct by value, because two cells could carry the same observance (a duplicate in a host's event
 * table) and printing it twice in one line is never what a reader wants.
 *
 * Not the whole rule — see [eventBannerEvents], which narrows this to one day's marks once a day is
 * selected.
 */
internal fun monthEvents(days: List<CalendarDay>): MonthEvents {
    val ordered = days.asSequence()
        .filter { it.isCurrentMonth }
        .flatMap { it.notableEvents.asSequence() }
        .distinct()
        .toList()
    return MonthEvents(
        hijri = ordered.filterIsInstance<HijriEvent>(),
        pakistan = ordered.filterIsInstance<PakistanEvent>(),
        ordered = ordered,
    )
}

/**
 * What the banner below the grid shows: the month's whole notable-date list, or the **one day's** marks
 * once a day carrying one has been tapped.
 *
 * The two answers are the report's two states, and they are deliberately different questions:
 *
 * - **Nothing tapped** — the user is paging through months, so the bar describes the month on screen:
 *   every notable date it holds ([monthEvents]). That is the only place a calendar-wide strip can say
 *   what a month *is*.
 * - **A day tapped, and it carries at least one mark** — the user just answered "which day?", so the
 *   bar answers the same day back: that day's list, which in Pakistan mode may be a national day and a
 *   Hijri observance at once. Leaving the whole month listed after a tap made the tap look like it had
 *   not landed, since the bar is where a selected observance is read.
 *
 * A tap that lands on a day with **no** mark keeps the month's list. There is nothing to narrow to, and
 * a bar that emptied itself on most taps would flicker between content and
 * [HijriCalendarLabels.noEventBanner] while the grid stayed unchanged.
 *
 * A selection made in a **different** month than the one displayed is ignored, exactly as the widget's
 * footer ignores it: paging away from a tapped day must not keep the bar describing a date the grid no
 * longer shows. `isSelected` is resolved per cell across all three calendar spaces, so this works in
 * Pakistan and observed modes without a second lookup.
 */
internal fun eventBannerEvents(days: List<CalendarDay>): MonthEvents {
    val tapped = days.firstOrNull { it.isSelected && it.isCurrentMonth && it.hasNotableEvent }
        ?: return monthEvents(days)
    return MonthEvents(
        hijri = listOfNotNull(tapped.event),
        pakistan = listOfNotNull(tapped.pakistanEvent),
        ordered = tapped.notableEvents,
    )
}
