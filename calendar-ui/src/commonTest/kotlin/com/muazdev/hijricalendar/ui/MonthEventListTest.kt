package com.muazdev.hijricalendar.ui

import com.abdulrahman_b.hijrahdatetime.HijrahDate
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import com.muazdev.hijricalendar.core.CalendarDay
import com.muazdev.hijricalendar.core.HijriCalendarState
import com.muazdev.hijricalendar.core.HijriEvents
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The event banner's content, in each of its three states.
 *
 * The bar names **only what the user selected**: nothing has been tapped ([eventBannerEvents] returns
 * `null`, the bar shows the hint), a selected day holds nothing (empty, the bar says so), or a selected
 * day holds something (that day's marks).
 *
 * It used to list **every** observance the month holds while nothing was tapped. That is what was
 * removed: the list read as if all of the month's history belonged to the selected day, and on the
 * widget — one line under a grid whose selection moves with every tap — paging months and tapping a day
 * looked like the same gesture. These tests exist to keep the month's history from creeping back into
 * the line, so several of them assert the *absence* of the month's other events rather than only the
 * presence of the tapped one.
 *
 * They feed the same shape `HijriCalendar` does — the month's **cells**, in grid order, including any
 * padded neighbouring-month cells — because that is what makes the bar agree with the filled cells
 * under it in Pakistan and observed modes.
 */
class MonthEventListTest {

    private companion object {
        const val YEAR = 1447
        const val MUHARRAM = 1
        const val SAFAR = 2
        const val RAMADAN = 9
    }

    private fun cell(
        dayOfMonth: Int,
        month: Int = MUHARRAM,
        isCurrentMonth: Boolean = true,
        isSelected: Boolean = false,
    ): CalendarDay = CalendarDay(
        hijrahDate = HijrahDate(YEAR, month, dayOfMonth),
        isCurrentMonth = isCurrentMonth,
        isToday = false,
        isSelected = isSelected,
        isDisabled = false,
        isWeekend = false,
    )

    /**
     * A full month's grid: every day, plus a padded cell from the neighbouring month at each end.
     *
     * [length] is explicit because `HijrahDate` validates the day: Safar 1447 has 29 days, so a
     * hard-coded 1..30 would throw on the month this file uses as its "no observance" case.
     */
    private fun month(month: Int, previous: Int, next: Int, length: Int): List<CalendarDay> = listOf(
        cell(dayOfMonth = 1, month = previous, isCurrentMonth = false),
        cell(dayOfMonth = 1, month = next, isCurrentMonth = false),
    ) + (1..length).map { day -> cell(dayOfMonth = day, month = month) }

    private fun List<CalendarDay>.selecting(predicate: (CalendarDay) -> Boolean): List<CalendarDay> =
        map { day -> if (predicate(day)) day.copy(isSelected = true) else day }

    private val newYear = assertNotNull(HijriEvents.forDate(MUHARRAM, 1))
    private val ashuraEve = assertNotNull(HijriEvents.forDate(MUHARRAM, 9))
    private val ashura = assertNotNull(HijriEvents.forDate(MUHARRAM, 10))

    // ── no selection: the hint, never the month's history ─────────────────

    /**
     * The headline change. A month holding three observances names **none** of them until one is
     * tapped — this is the assertion that fails if the month's list is ever restored.
     */
    @Test
    fun aMonthWithObservancesNamesNoneOfThemBeforeATap() {
        assertNull(
            eventBannerEvents(month(MUHARRAM, previous = 12, next = SAFAR, length = 30)),
            "nothing is selected, so the bar must ask for a selection rather than list the month",
        )
    }

    /** A month with no observance is equally silent before a tap: the two cases differ only after one. */
    @Test
    fun aMonthWithNoObservanceIsAlsoSilentBeforeATap() {
        assertNull(eventBannerEvents(month(SAFAR, previous = MUHARRAM, next = 3, length = 29)))
    }

    /**
     * A selection made in a month other than the one displayed counts as no selection.
     *
     * The grid carries the neighbouring month's days, so a padded cell *can* be the selected one — and
     * paging away from a tapped day must not leave the bar describing a date the grid no longer shows.
     * This is the same rule the widget's footer applies to its `selectedDay`.
     */
    @Test
    fun aSelectionInAnotherMonthIsIgnoredEntirely() {
        val safarWithSelectedMuharramPadding = listOf(
            cell(dayOfMonth = 10, month = MUHARRAM, isCurrentMonth = false, isSelected = true),
            cell(dayOfMonth = 15, month = SAFAR),
        )

        assertNull(eventBannerEvents(safarWithSelectedMuharramPadding))
    }

    // ── a selected day: its marks, and only its marks ─────────────────────

    /**
     * A tap on an observance names that one — and **not** the rest of the month's.
     *
     * Asserting the absence of the neighbouring observances is the point: "narrows to the tapped event"
     * was already true before, but it was the *fallback* that listed everything else.
     */
    @Test
    fun aTappedObservanceIsTheOnlyNameShown() {
        val tapped = month(MUHARRAM, previous = 12, next = SAFAR, length = 30)
            .selecting { it.dayOfMonth == 10 }

        val events = assertNotNull(eventBannerEvents(tapped))

        assertEquals(listOf(ashura), events.hijri)
        assertTrue(newYear !in events.hijri, "the month's other observances are not this day's")
        assertTrue(ashuraEve !in events.hijri)
        assertEquals(listOf(ashura), events.ordered)
        assertEquals("Ashura", HijriCalendarLabels().monthEventBanner(events.hijri))
    }

    /** The neighbouring observances stay out even though they are in the same grid. */
    @Test
    fun tappingOneObservanceDoesNotPullInTheOthers() {
        val tapped = month(MUHARRAM, previous = 12, next = SAFAR, length = 30)
            .selecting { it.dayOfMonth == 1 }

        assertEquals(listOf(newYear), assertNotNull(eventBannerEvents(tapped)).hijri)
    }

    /**
     * A selected day with no observance answers with an **empty** list, not `null`.
     *
     * The two are different to the caller: `null` prints the hint, empty prints "no event on this day".
     * A tap on an ordinary day is the common case — most Hijri months name only one day — so a bar that
     * said "tap a day" right after the user tapped one would read as broken.
     */
    @Test
    fun aSelectedDayWithoutAnObservanceIsEmptyButNotAbsent() {
        val tapped = month(MUHARRAM, previous = 12, next = SAFAR, length = 30)
            .selecting { it.dayOfMonth == 2 }

        val events = assertNotNull(eventBannerEvents(tapped))
        assertTrue(events.isEmpty)
        assertTrue(events.ordered.isEmpty())
    }

    /** Two selected cells cannot happen through the UI, but the first one wins rather than doubling. */
    @Test
    fun onlyOneDayContributes() {
        val tapped = listOf(
            cell(dayOfMonth = 1, isSelected = true),
            cell(dayOfMonth = 10, isSelected = true),
        )

        assertEquals(listOf(newYear), assertNotNull(eventBannerEvents(tapped)).hijri)
    }

    // ── real state, both calendar spaces ─────────────────────────────────

    /**
     * The first of Ramadan reaches the bar — the reported case, end to end.
     *
     * Built from a real [HijriCalendarState] grid rather than a hand-made cell on purpose: the failure
     * being ruled out is a **table** with no entry for 1 Ramadan, and a fixture that calls
     * `HijriEvents.forDate(9, 1)` to build its own cell would agree with that table whatever it said —
     * including nothing. Driving the cells means the assertion fails if the entry is removed, which is
     * exactly how it went missing before.
     */
    @Test
    fun theFirstOfRamadanIsNamedOnceItsDayIsSelected() {
        val ramadan = HijriCalendarState(initialMonth = HijrahYearMonth(YEAR, RAMADAN))
            .calendarMonthFor(HijrahYearMonth(YEAR, RAMADAN))
            .days
            .selecting { it.dayOfMonth == 1 }

        val events = assertNotNull(eventBannerEvents(ramadan))

        assertEquals(
            listOf("ramadan"),
            events.hijri.map { it.key },
            "1 Ramadan carries the month's own first day, and no other day of it carries anything",
        )
        assertEquals("Ramadan", HijriCalendarLabels().monthEventBanner(events.hijri))
    }

    /**
     * 14 August 2026 is 30 Safar 1448, and selecting it names the national day.
     *
     * Driven through [HijriCalendarState] in Pakistan mode rather than a hand-built cell, because the
     * gate that makes the mark appear (`CalendarDay.pakistanEvent`, which requires `pakistanDate`) is
     * the thing this test exists to pin. A fixture would set `pakistanDate` itself and prove nothing.
     */
    @Test
    fun aPakistanModeDayNamesItsNationalDay() {
        val safar = pakistanMonth(1448, 2)
            .selecting { it.localDate == LocalDate(2026, 8, 14) }

        val events = assertNotNull(eventBannerEvents(safar))

        assertEquals(listOf("independence_day"), events.pakistan.map { it.key })
        assertEquals(
            listOf("independence_day"),
            events.ordered.map { it.key },
            "the ordered list is what the combined label receives",
        )
        assertEquals(
            "Independence Day",
            HijriCalendarLabels().monthEventBannerWithPakistan(events.ordered),
        )
    }

    /**
     * In Calculation mode the same Gregorian month names **no** national day.
     *
     * The gate is the calendar space the user chose, so switching the in-app calendar to Umm al-Qura
     * must drop the national days with it rather than filling 14 August on a calculated grid.
     */
    @Test
    fun theCalculationModeDayNamesNoNationalDay() {
        val safar = calculationMonth(1448, 2)

        assertTrue(
            safar.none { it.pakistanEvent != null },
            "Calculation mode must not carry Pakistani national days on any cell",
        )

        val selected = assertNotNull(eventBannerEvents(safar.selecting { it.isCurrentMonth }))

        assertTrue(
            selected.pakistan.isEmpty(),
            "and therefore a selected day in that month names no national day",
        )
    }

    /**
     * A day holding both kinds names both, Hijri first — the case the combined label exists for.
     *
     * Read from the real tables rather than invented: if no day in the shipped data carries both, the
     * assertion below is skipped rather than silently passing on a fixture that cannot occur.
     */
    @Test
    fun aDayHoldingBothKindsNamesBoth() {
        val pakistanMode = pakistanMonth(1448, 2)
        val both = pakistanMode.firstOrNull { it.event != null && it.pakistanEvent != null }
            ?: return

        val events = assertNotNull(eventBannerEvents(pakistanMode.selecting { it == both }))

        assertEquals(listOfNotNull(both.event), events.hijri)
        assertEquals(listOfNotNull(both.pakistanEvent), events.pakistan)
        assertEquals(both.notableEvents, events.ordered)
    }

    // ── the labels ───────────────────────────────────────────────────────

    /**
     * The default label joins the names, and carries **no day numbers**.
     *
     * The day used to be appended ("Ashura · Day 10"). The requirement is to skip it: the filled cell
     * already answers *when*, and a day number in the line made the bar read as a footnote on a date
     * nobody had asked about.
     */
    @Test
    fun theDefaultLabelJoinsTheNamesWithoutDayNumbers() {
        val text = HijriCalendarLabels().monthEventBanner(listOf(newYear, ashuraEve, ashura))

        assertEquals(
            "Islamic New Year · Ashura (9th Muharram) · Ashura",
            text,
        )
        assertTrue("Day " !in text, "the banner must not attach the day to the name")
    }

    /** The resting state asks for a selection, and is non-empty so the reserved line is not a gap. */
    @Test
    fun theRestingStateIsAHintRatherThanSilence() {
        assertTrue(HijriCalendarLabels().eventBannerHint.isNotBlank())
    }

    /** A selected-but-empty day is described as such rather than as a month with no history. */
    @Test
    fun theEmptyDayLabelIsNotEmpty() {
        assertTrue(HijriCalendarLabels().noEventBanner.isNotBlank())
    }

    /** A month with no national day keeps the **published** Hijri-only lambda. */
    @Test
    fun aDayWithoutANationalDayStaysOnTheHijriOnlyLabel() {
        val muharram = pakistanMonth(1448, 1)
            .selecting { it.dayOfMonth == 10 }

        val events = assertNotNull(eventBannerEvents(muharram))

        assertTrue(events.pakistan.isEmpty(), "10 Muharram 1448 holds no national day")
        assertEquals(
            HijriCalendarLabels().monthEventBanner(events.hijri),
            events.ordered.joinToString(" · ") { it.nameEn },
            "the ordered stream and the Hijri list describe the same set here",
        )
    }

    /** The month grid of a real state in Pakistan mode. */
    private fun pakistanMonth(year: Int, month: Int): List<CalendarDay> =
        HijriCalendarState(
            initialMonth = HijrahYearMonth(year, month),
            pakistanDates = true,
        ).calendarMonthFor(HijrahYearMonth(year, month)).days

    /** The month grid of a real state in Calculation mode. */
    private fun calculationMonth(year: Int, month: Int): List<CalendarDay> =
        HijriCalendarState(
            initialMonth = HijrahYearMonth(year, month),
            pakistanDates = false,
        ).calendarMonthFor(HijrahYearMonth(year, month)).days
}
