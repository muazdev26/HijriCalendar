package com.muazdev.hijricalendar.ui

import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import com.muazdev.hijricalendar.core.CalendarMonth
import com.muazdev.hijricalendar.core.HijriCalendarState
import com.muazdev.hijricalendar.core.HijriMonthLengths
import com.muazdev.hijricalendar.core.ObservedHijriCalendar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression coverage for [UI-01-scoped-overrides-ignored].
 *
 * `HijriCalendarState.monthLengths` is the scoped override table CORE-02 introduced, and it used
 * to be unread outside `calendar-core`: every argument list in `calendar-ui` omitted `overrides`,
 * so the render path fell back to the process-global `HijriMonthOverrides.current` while the state
 * reported the scoped table's answer. A calendar with a scoped table rendered a month its own data
 * model said did not exist.
 *
 * These tests go through [renderMonthFor] and [renderGregorianRangeFor] — the render path's own
 * seam — rather than re-deriving expected values from the same builder the composables call. A
 * test that mirrored the argument list would drift the moment the bug was reintroduced, which is
 * exactly what happened the first time.
 */
class RenderMonthOverridesTest {

    /**
     * The month under override. Its length is *read* from the calendar at test time rather than
     * hardcoded, and the forced value is always the opposite of the calculated one, so these
     * assertions cannot silently decay into asserting nothing if the underlying data changes.
     */
    private val overrideYear = 1448
    private val overrideMonth = 9

    private val calculatedLength: Int
        get() = ObservedHijriCalendar.defaultLength(overrideYear, overrideMonth)

    private val forcedLength: Int
        get() = if (calculatedLength == 30) 29 else 30

    private val overrideKey: Pair<Int, Int> get() = overrideYear to overrideMonth

    private fun scopedState(
        lengths: HijriMonthLengths = HijriMonthLengths(),
        year: Int = overrideYear,
        month: Int = overrideMonth,
    ): HijriCalendarState = HijriCalendarState(
        initialMonth = HijrahYearMonth(year, month),
        monthLengths = lengths,
    )

    private fun tableForcingOpposite(): HijriMonthLengths =
        HijriMonthLengths(mapOf(overrideKey to forcedLength))

    private val currentYearMonth: HijrahYearMonth
        get() = HijrahYearMonth(overrideYear, overrideMonth)

    private fun CalendarMonth.daysInMonth(): List<Int> =
        days.filter { it.isCurrentMonth }.map { it.dayOfMonth }

    // ── test setup is itself asserted ──────────────────────────────────

    @Test
    fun setupForcesTheOppositeOfTheCalculatedLength() {
        // Guards the guard. If the calendar ever makes this month 30 days, every assertion below
        // would be testing a no-op override and would pass without proving anything.
        assertTrue(
            forcedLength != calculatedLength,
            "forced $forcedLength equals calculated $calculatedLength for $overrideYear-$overrideMonth",
        )
    }

    // ── the override table is actually consulted ────────────────────────

    @Test
    fun renderMonthFor_honoursAScopedOverrideTable() {
        val state = scopedState(tableForcingOpposite())

        val rendered = state.renderMonthFor(currentYearMonth).daysInMonth()

        assertEquals(
            forcedLength,
            rendered.size,
            "render path painted ${rendered.size} days; the scoped table forces $forcedLength and " +
                "the calculated length is $calculatedLength. The render path is reading the " +
                "process-global table instead of the scoped one.",
        )
        assertEquals((1..forcedLength).toList(), rendered)
    }

    @Test
    fun renderMonthFor_agreesWithTheStatesOwnDerivedMonth() {
        val state = scopedState(tableForcingOpposite())

        assertEquals(
            state.calendarMonth.daysInMonth(),
            state.renderMonthFor(state.currentMonth).daysInMonth(),
            "state.calendarMonth and the grid's month disagree — one of the two is using a " +
                "different override table",
        )
    }

    @Test
    fun renderMonthFor_prefersTheScopedTableOverTheProcessGlobal() {
        val state = scopedState(tableForcingOpposite())

        assertEquals(
            forcedLength,
            state.renderMonthFor(currentYearMonth).daysInMonth().size,
            "render path followed the process-global length ($calculatedLength) over the " +
                "scoped one ($forcedLength)",
        )
    }

    @Test
    fun twoStatesWithDifferentTablesRenderDifferentMonths() {
        val tableA = HijriMonthLengths(mapOf(overrideKey to forcedLength))
        val tableB = HijriMonthLengths(mapOf(overrideKey to calculatedLength))
        val stateA = scopedState(tableA)
        val stateB = scopedState(tableB)

        val monthA = stateA.renderMonthFor(currentYearMonth)
        val monthB = stateB.renderMonthFor(currentYearMonth)

        assertEquals(forcedLength, monthA.daysInMonth().size)
        assertEquals(calculatedLength, monthB.daysInMonth().size)
        assertTrue(
            monthA.days != monthB.days,
            "two states with different override tables rendered the same month; at least one of " +
                "them is reading a table it was not given",
        )
    }

    // ── the header must not describe a different month than the grid ──

    @Test
    fun renderGregorianRangeFor_matchesTheGridsFirstAndLastDay() {
        val state = scopedState(tableForcingOpposite())
        val month = state.renderMonthFor(currentYearMonth)
        val inMonth = month.days.filter { it.isCurrentMonth }

        val range = state.renderGregorianRangeFor(currentYearMonth)

        assertTrue(inMonth.isNotEmpty(), "no in-month cells rendered")
        assertEquals(
            inMonth.first().localDate,
            range.first,
            "header range starts on a different day than the grid's first cell",
        )
        assertEquals(
            inMonth.last().localDate,
            range.last,
            "header range ends on a different day than the grid's last cell — the header names a " +
                "different month than the grid below it",
        )
    }

    @Test
    fun renderGregorianRangeFor_reflectsAScopedOverride() {
        val withOverride = scopedState(tableForcingOpposite()).renderGregorianRangeFor(currentYearMonth)
        val without = scopedState(HijriMonthLengths()).renderGregorianRangeFor(currentYearMonth)

        assertTrue(
            withOverride != without,
            "the header's Gregorian range is identical with and without the scoped override; " +
                "resolveGregorianMonthRange is being called without overrides",
        )
    }

    // ── cells carry the override-effective length ──────────────────────

    @Test
    fun observedCellsCarryTheOverrideEffectiveLength() {
        val state = scopedState(tableForcingOpposite())

        val observedLengths = state.renderMonthFor(currentYearMonth)
            .days
            .filter { it.isCurrentMonth }
            .map { it.observedDate?.monthLength }
            .distinct()

        assertEquals(
            listOf(forcedLength),
            observedLengths,
            "observed cells should all report the override-effective length $forcedLength",
        )
    }

    // ── mutation ───────────────────────────────────────────────────────

    @Test
    fun theStatesOwnMutatorChangesWhatIsRendered() {
        val table = HijriMonthLengths()
        val state = scopedState(table)

        state.setMonthLength(overrideYear, overrideMonth, forcedLength)

        assertEquals(forcedLength, state.renderMonthFor(currentYearMonth).daysInMonth().size)
    }

    @Test
    fun theStatesOwnMutatorAdvancesTheCacheRevision() {
        val state = scopedState(HijriMonthLengths())
        val before = state.overridesRevision

        state.setMonthLength(overrideYear, overrideMonth, forcedLength)

        assertTrue(
            state.overridesRevision != before,
            "overridesRevision did not advance; the grid's remember key cannot invalidate on a " +
                "user override and would keep painting the old month",
        )
    }

    @Test
    fun clearingAnOverrideRestoresTheCalculatedLength() {
        val state = scopedState(tableForcingOpposite())
        assertEquals(forcedLength, state.renderMonthFor(currentYearMonth).daysInMonth().size)

        state.clearAllMonthLengths()

        assertEquals(
            calculatedLength,
            state.renderMonthFor(currentYearMonth).daysInMonth().size,
            "clearing the override did not restore the calculated length",
        )
    }
}
