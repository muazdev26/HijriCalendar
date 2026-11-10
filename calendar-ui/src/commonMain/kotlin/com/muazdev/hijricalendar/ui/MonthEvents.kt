package com.muazdev.hijricalendar.ui

import com.muazdev.hijricalendar.core.CalendarDay
import com.muazdev.hijricalendar.core.CalendarEvent
import com.muazdev.hijricalendar.core.HijriEvent
import com.muazdev.hijricalendar.core.PakistanEvent

/**
 * The notable dates the banner below the grid names, split by coordinate.
 *
 * Two typed lists because [HijriCalendarLabels] has a published lambda for a Hijri list
 * ([HijriCalendarLabels.monthEventBanner]) that a host may already override; a day that also holds a
 * Pakistani national day goes through the additive
 * [HijriCalendarLabels.monthEventBannerWithPakistan] instead, so an existing label set keeps rendering
 * exactly as it did for every date with no national day.
 *
 * [ordered] is the same set as **one stream**, which is what the combined label receives. It cannot be
 * reconstructed by concatenating [hijri] and [pakistan]: a Gregorian-fixed national day and a Hijri
 * observance can fall on the same day, and a separator would then always print one before the other.
 */
internal data class MonthEvents(
    val hijri: List<HijriEvent>,
    val pakistan: List<PakistanEvent>,
    val ordered: List<CalendarEvent>,
) {
    val isEmpty: Boolean get() = ordered.isEmpty()
}

/**
 * What the banner below the grid shows, which is now **only** the selected day's notable dates.
 *
 * Three answers, and the `null` is not the same as the empty one:
 *
 * - **`null` — no day in the displayed month is selected.** Nothing has been asked, so the bar does not
 *   answer: the caller prints [HijriCalendarLabels.eventBannerHint] ("Tap a day to see its event").
 * - **Empty — a day is selected and it carries nothing.** The user asked "what happened on this day?",
 *   and the honest answer is that nothing did, so the caller prints
 *   [HijriCalendarLabels.noEventBanner].
 * - **Non-empty — the selected day's marks**, which in Pakistan mode may be a national day and a Hijri
 *   observance at once.
 *
 * **It used to describe the whole month while nothing was tapped**, and that is what this replaced. A
 * bar that listed every observance in the month read as if all of them belonged to the selected day, and
 * on the widget — where the footer has one line under a grid whose selection moves with every tap — it
 * made paging and tapping look like the same action. The month's history is still on screen: the filled
 * cells are the month's calendar. What the bar adds is the name of the thing you touched.
 *
 * Reading the month's **cells** rather than `HijriEvents.forMonth(month)` is what keeps the bar and the
 * filled cells from naming different things in Pakistan or observed mode, where a month's day numbers
 * differ from Umm al-Qura's: [CalendarDay.event] and [CalendarDay.pakistanEvent] resolve through the
 * same cell the grid paints, and [CalendarDay.isCurrentMonth] keeps a neighbouring month's padded cell
 * out of the answer even though [days] contains it — so a selection left behind by paging away is
 * ignored here exactly as the widget's footer ignores it.
 *
 * Distinct by value, because two cells could carry the same observance (a duplicate in a host's event
 * table) and printing it twice in one line is never what a reader wants.
 */
internal fun eventBannerEvents(days: List<CalendarDay>): MonthEvents? {
    val selected = days.firstOrNull { it.isSelected && it.isCurrentMonth }
        ?: return null
    return if (selected.hasNotableEvent) {
        MonthEvents(
            hijri = listOfNotNull(selected.event),
            pakistan = listOfNotNull(selected.pakistanEvent),
            ordered = selected.notableEvents.distinct(),
        )
    } else {
        MonthEvents(emptyList(), emptyList(), emptyList())
    }
}
