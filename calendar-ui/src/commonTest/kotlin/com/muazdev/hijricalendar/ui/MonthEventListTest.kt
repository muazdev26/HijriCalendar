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
import kotlin.test.assertTrue

/**
 * The always-present event banner's content, in both of its states.
 *
 * While the user pages through months the line lists **every** observance the month holds, in day
 * order ([monthEvents]); once a day that carries one is tapped it names **only** that one
 * ([eventBannerEvents]). The banner used to resolve a *single* observance by precedence (selected day,
 * today, nearest upcoming), which could only ever name one of a month's events — and Muharram has
 * three. The report asked for "all events in that line for that month, and skip attaching the days
 * with them", then for a tap to narrow the line to the tapped event.
 *
 * These tests feed the same shape `HijriCalendar` does — the month's **cells**, in grid order,
 * including any padded neighbouring-month cells — because that is what makes the bar agree with the
 * filled cells under it in Pakistan and observed modes.
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

    private val newYear = assertNotNull(HijriEvents.forDate(MUHARRAM, 1))
    private val ashuraEve = assertNotNull(HijriEvents.forDate(MUHARRAM, 9))
    private val ashura = assertNotNull(HijriEvents.forDate(MUHARRAM, 10))

    /**
     * The headline case: a month with more than one observance lists **all** of them, in day order.
     * Asserting the order as well as the set is deliberate — "all of them" was the request, but a
     * list rendered in `HijriEvents.all` order or in map order would read as arbitrary history rather
     * than as the month's progression.
     */
    @Test
    fun everyObservanceOfTheMonthIsListedInDayOrder() {
        assertEquals(
            listOf(newYear, ashuraEve, ashura),
            monthEvents(month(MUHARRAM, previous = 12, next = SAFAR, length = 30)).hijri,
        )
    }

    /** A month with no observance at all answers with an empty list, which the caller words itself. */
    @Test
    fun aMonthWithNoObservance_hasAnEmptyList() {
        assertTrue(monthEvents(month(SAFAR, previous = MUHARRAM, next = 3, length = 29)).isEmpty)
    }

    /**
     * The first of Ramadan reaches the banner — the report's case, end to end.
     *
     * Built from a real [HijriCalendarState] grid rather than a hand-made cell on purpose: the failure
     * being ruled out is a **table** with no entry for 1 Ramadan, and a fixture that calls
     * `HijriEvents.forDate(9, 1)` to build its own cell would agree with that table whatever it said —
     * including nothing. Driving the cells means the assertion fails if the entry is removed, which is
     * exactly how it went missing before.
     */
    @Test
    fun theFirstOfRamadanIsNamedInTheBanner() {
        val ramadan = HijriCalendarState(initialMonth = HijrahYearMonth(YEAR, RAMADAN))
            .calendarMonthFor(HijrahYearMonth(YEAR, RAMADAN))
            .days

        val events = monthEvents(ramadan)

        assertEquals(
            listOf("ramadan"),
            events.hijri.map { it.key },
            "1 Ramadan carries the month's own first day, and no other day of it carries anything",
        )
        assertEquals("Ramadan", HijriCalendarLabels().monthEventBanner(events.hijri))
    }

    /**
     * A padded cell from the neighbouring month is excluded.
     *
     * This is the case that would otherwise put Ashura in a Safar banner: the grid carries 10 Muharram
     * in its padding, and `HijriEvents.forMonth(SAFAR)` would not — but reading the *cells* is what
     * keeps the bar and the fills in agreement, so the filter has to be explicit.
     */
    @Test
    fun paddedNeighbouringMonthCellsAreIgnored() {
        val safarWithMuharramPadding = listOf(
            cell(dayOfMonth = 10, month = MUHARRAM, isCurrentMonth = false),
            cell(dayOfMonth = 15, month = SAFAR),
        )

        assertTrue(monthEvents(safarWithMuharramPadding).isEmpty)
    }

    /**
     * The same observance carried by two cells appears once.
     *
     * Reachable only through a malformed grid, which is exactly why the de-duplication is stated: the
     * line is one string, and printing "Ashura · Ashura" would be a rendering bug with no clue where
     * it came from.
     */
    @Test
    fun aRepeatedObservanceAppearsOnce() {
        assertEquals(
            listOf(ashura),
            monthEvents(listOf(cell(dayOfMonth = 10), cell(dayOfMonth = 10))).hijri,
        )
    }

    // ── the banner narrows to a tapped observance ─────────────────────

    /**
     * A tap on an observance narrows the line to that one name — the report's second state.
     *
     * The same grid answers both states, so the assertions differ only by which cell is selected: that
     * is the whole rule, and stating it as a pair is what rules out "narrowing" that quietly also
     * dropped the month's other events when nothing was tapped.
     */
    @Test
    fun aTappedObservanceIsTheOnlyNameShown() {
        val grid = month(MUHARRAM, previous = 12, next = SAFAR, length = 30)
        assertEquals(
            listOf(newYear, ashuraEve, ashura),
            eventBannerEvents(grid).hijri,
            "paging through the month: the bar describes the month",
        )

        val tapped = grid.map { day -> if (day.dayOfMonth == 10) day.copy(isSelected = true) else day }

        assertEquals(listOf(ashura), eventBannerEvents(tapped).hijri)
        assertEquals("Ashura", HijriCalendarLabels().monthEventBanner(eventBannerEvents(tapped).hijri))
    }

    /**
     * A tap on a day with no observance keeps the month's list.
     *
     * There is nothing to narrow to, and emptying the line would make a tap on an ordinary day look
     * like the month had lost its history — most Hijri months name only one day, so this is the common
     * case, not an edge one.
     */
    @Test
    fun aTapOnADayWithoutAnObservanceKeepsTheMonthList() {
        val tapped = month(MUHARRAM, previous = 12, next = SAFAR, length = 30)
            .map { day -> if (day.dayOfMonth == 2) day.copy(isSelected = true) else day }

        assertEquals(listOf(newYear, ashuraEve, ashura), eventBannerEvents(tapped).hijri)
    }

    /**
     * A selection made in a month other than the one displayed does not narrow anything.
     *
     * The grid carries the neighbouring month's days, so a padded cell *can* be the selected one —
     * and paging away from a tapped day must not leave the bar describing a date the grid no longer
     * shows. This is the same rule the widget's footer applies to its `selectedDay`.
     */
    @Test
    fun aSelectionInAnotherMonthDoesNotNarrowTheBanner() {
        val safarWithSelectedMuharramPadding = listOf(
            cell(dayOfMonth = 10, month = MUHARRAM, isCurrentMonth = false, isSelected = true),
            cell(dayOfMonth = 15, month = SAFAR),
        )

        assertTrue(eventBannerEvents(safarWithSelectedMuharramPadding).isEmpty)
    }

    /**
     * The default label joins the names, and carries **no day numbers**.
     *
     * The day used to be appended ("Ashura · Day 10"). The report asked to skip it: the filled cells
     * already answer *when*, and a day number in a permanently-present line made the bar read as a
     * footnote on a date nobody had asked about.
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

    // ── Pakistan mode: national days join the same line ───────────────────

    /**
     * A Pakistan-mode month lists its national day, end to end from a real state.
     *
     * 14 August 2026 is 30 Safar 1448, so the banner for that month must name Independence Day — driven
     * through [HijriCalendarState] in Pakistan mode rather than a hand-built cell, because the gate that
     * makes it appear (`CalendarDay.pakistanEvent`, which requires `pakistanDate`) is the thing this test
     * exists to pin. A fixture would set `pakistanDate` itself and prove nothing.
     */
    @Test
    fun aPakistanModeMonthNamesItsNationalDay() {
        val safar = pakistanMonth(1448, 2)

        val events = monthEvents(safar)

        assertEquals(
            listOf("independence_day"),
            events.pakistan.map { it.key },
            "Safar 1448 holds 14 August, the only national day in it",
        )
        assertEquals(
            listOf("independence_day"),
            events.ordered.map { it.key },
            "and the ordered list is what the combined label receives",
        )
        assertEquals(
            "Independence Day",
            HijriCalendarLabels().monthEventBannerWithPakistan(events.ordered),
        )
    }

    /**
     * A month with no national day keeps the **published** Hijri-only lambda.
     *
     * That is why the combined label is an additional field rather than a changed signature: a host that
     * customised `monthEventBanner` must keep rendering through it on every month that has no Pakistan
     * national day, which is most of them.
     */
    @Test
    fun aMonthWithoutANationalDayStaysOnTheHijriOnlyLabel() {
        val muharram = pakistanMonth(1448, 1)

        val events = monthEvents(muharram)

        assertTrue(events.pakistan.isEmpty(), "Muharram 1448 holds no national day")
        assertEquals(
            listOf("islamic_new_year", "ashura_ninth", "ashura"),
            events.hijri.map { it.key },
            "the Hijri list is unchanged, so the Hijri-only label still renders it",
        )
        assertEquals(
            HijriCalendarLabels().monthEventBanner(events.hijri),
            events.ordered.joinToString(" · ") { it.nameEn },
            "the ordered stream and the Hijri list describe the same set here",
        )
    }

    /**
     * Tapping the national day narrows the line to that one name, and the *national* name at that.
     *
     * The tap rule did not change for Pakistan mode — it narrows to the tapped day's marks — but the
     * mark here comes from the Gregorian table, so a banner that had kept resolving through `event`
     * alone would have shown the month's Hijri list instead of the day the user tapped.
     */
    @Test
    fun tappingTheNationalDayNarrowsTheBannerToIt() {
        val safar = pakistanMonth(1448, 2)
        val tapped = safar.map { day ->
            if (day.localDate == LocalDate(2026, 8, 14)) day.copy(isSelected = true) else day
        }

        val events = eventBannerEvents(tapped)

        assertEquals(listOf("independence_day"), events.ordered.map { it.key })
        assertEquals(
            "Independence Day",
            HijriCalendarLabels().monthEventBannerWithPakistan(events.ordered),
        )
    }

    /**
     * In Calculation mode the same Gregorian month names **no** national day.
     *
     * The gate is the calendar space the user chose, so switching the in-app calendar to Umm al-Qura
     * must drop the national days with it rather than painting 14 August's fill on a calculated grid.
     */
    @Test
    fun theCalculationModeMonthNamesNoNationalDay() {
        val safar = calculationMonth(1448, 2)

        assertTrue(
            monthEvents(safar).pakistan.isEmpty(),
            "Calculation mode must not carry Pakistani national days",
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
