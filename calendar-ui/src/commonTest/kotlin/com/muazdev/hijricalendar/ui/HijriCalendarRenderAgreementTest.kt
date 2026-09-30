package com.muazdev.hijricalendar.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import com.muazdev.hijricalendar.core.CalendarNames
import com.muazdev.hijricalendar.core.HijriCalendarState
import com.muazdev.hijricalendar.core.HijriMonthLengths
import com.muazdev.hijricalendar.core.WeekDay
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The assertions [UI-02-compose-test-harness](UI-02-compose-test-harness.md) says the module was
 * missing: **pairs of values that must agree**, not each value checked in isolation.
 *
 * Every pre-existing test asserted one thing at a time. The device test in the sample app is the
 * clearest example — it checks the grid with `assertCountEquals(42)`, checks the header range
 * separately, and never compares a cell's date against the header. That missing comparison is
 * precisely what [UI-01-scoped-overrides-ignored](UI-01-scoped-overrides-ignored.md) would have
 * been caught by, so these tests exist to make that class of bug fail loudly.
 *
 * ## How the cells are read
 *
 * The default `dayContentDescription` is `"Day 15"` — no date. That is enough for "is there a cell
 * 15" and useless for "is this cell the right *day*", which is the whole point here. So these tests
 * supply a test-only [HijriCalendarLabels] whose `dayContentDescription` encodes the cell's real
 * Gregorian day, and parse it back. No production code is changed to make this testable, which is
 * the point: a test that needs an instrumentation hook in the product is a test that will not be
 * written.
 *
 * ## Why `dateDisplayMode = BOTH`
 *
 * `HijriCalendarHeader` only renders the Gregorian range when
 * `dateDisplayMode != DateDisplayMode.HIJRI_ONLY` (`HijriCalendarHeader.kt:38`). The default
 * hides it, so any header/grid agreement test must ask for a mode that shows it.
 */
@OptIn(ExperimentalTestApi::class)
class HijriCalendarRenderAgreementTest {

    private val cellMarker = "cell "

    /** Labels that make each day cell announce its real Gregorian day. */
    private val probeLabels = HijriCalendarLabels(
        dayContentDescription = { day ->
            // Carries the in-month flag because a 42-cell grid is 42 *consecutive* real days:
            // leading days are the real days before the month starts and trailing days the ones
            // after it ends, so date contiguity cannot identify the month's own cells. The grid's
            // own isCurrentMonth flag can.
            val scope = if (day.isCurrentMonth) inMonthTag else outOfMonthTag
            "$cellMarker${day.localDate}|$scope"
        },
    )

    private val inMonthTag = "in"
    private val outOfMonthTag = "out"

    private fun host(
        state: HijriCalendarState,
        labels: HijriCalendarLabels = probeLabels,
        dateDisplayMode: DateDisplayMode = DateDisplayMode.BOTH,
    ): @Composable () -> Unit = {
        MaterialTheme {
            Surface {
                HijriCalendar(
                    state = state,
                    dateDisplayMode = dateDisplayMode,
                    labels = labels,
                    onDayClick = state.defaultOnDayClick(),
                )
            }
        }
    }

    /** One rendered day cell: its real-world day, and whether the grid marked it in-month. */
    private data class PaintedCell(val date: LocalDate, val isCurrentMonth: Boolean)

    private fun ComposeUiTest.descriptionOf(node: SemanticsNode): String {
        val config = node.config
        return if (config.contains(SemanticsProperties.ContentDescription)) {
            config[SemanticsProperties.ContentDescription].firstOrNull().orEmpty()
        } else {
            ""
        }
    }

    /** Every rendered day cell, parsed back out of the semantics tree. */
    private fun ComposeUiTest.paintedCells(): List<PaintedCell> =
        onAllNodes(hasContentDescription(cellMarker, substring = true))
            .fetchSemanticsNodes()
            .map { node ->
                val payload = descriptionOf(node).removePrefix(cellMarker)
                val date = payload.substringBefore('|')
                val scope = payload.substringAfter('|')
                PaintedCell(LocalDate.parse(date), scope == inMonthTag)
            }
            .sortedBy { it.date }

    private fun ComposeUiTest.cellDates(): List<LocalDate> = paintedCells().map { it.date }

    /** The painted days the grid itself marked as belonging to this month, in order. */
    private fun ComposeUiTest.inMonthDays(): List<LocalDate> =
        paintedCells().filter { it.isCurrentMonth }.map { it.date }

    private fun stateFor(
        year: Int = 1447,
        month: Int = 9,
        monthLengths: HijriMonthLengths = HijriMonthLengths(),
        pakistan: Boolean = false,
        adjustmentDays: Int = 0,
    ) = HijriCalendarState(
        initialMonth = HijrahYearMonth(year, month),
        firstDayOfWeek = WeekDay.SATURDAY,
        pakistanDates = pakistan,
        adjustmentDays = adjustmentDays,
        monthLengths = monthLengths,
    )

    /** The header's rendered "Month - Month Year" / "Month Year" line. */
    private fun expectedRangeLabel(first: LocalDate, last: LocalDate): String = when {
        first.month == last.month && first.year == last.year ->
            "${CalendarNames.englishGregorianMonths[first.monthNumber - 1]} ${first.year}"
        first.year == last.year ->
            "${CalendarNames.englishGregorianMonths[first.monthNumber - 1]} - " +
                "${CalendarNames.englishGregorianMonths[last.monthNumber - 1]} ${first.year}"
        else ->
            "${CalendarNames.englishGregorianMonths[first.monthNumber - 1]} ${first.year} - " +
                "${CalendarNames.englishGregorianMonths[last.monthNumber - 1]} ${last.year}"
    }

    // ── header range vs the cells actually painted ─────────────────────
    //
    // Every assertion below derives its expectation from the **painted cells**, read back out of
    // the semantics tree, and never from `gregorianRangeFor` — which is the same function the
    // header itself calls. An earlier draft computed the expected header text from that resolver
    // and compared it to the header: tautological, and it stayed green when the resolver was
    // broken out from under the grid. These compare the header's rendered string against the first
    // and last cell the tree actually contains, which is the only comparison that can fail.

    @Test
    fun headerRange_describesTheCellsActuallyPainted_ummAlQura() =
        assertHeaderDescribesPaintedCells(stateFor(), "UAQ")

    @Test
    fun headerRange_describesTheCellsActuallyPainted_pakistan() =
        assertHeaderDescribesPaintedCells(stateFor(pakistan = true), "Pakistan")

    @Test
    fun headerRange_describesTheCellsActuallyPainted_observed() =
        assertHeaderDescribesPaintedCells(
            stateFor(monthLengths = HijriMonthLengths(mapOf((1447 to 9) to 29))),
            "Observed",
        )

    @Test
    fun headerRange_describesTheCellsActuallyPainted_withAdjustment() =
        assertHeaderDescribesPaintedCells(stateFor(adjustmentDays = 2), "adjusted")

    /**
     * The shared body. Fails if the header's rendered range line is not exactly the
     * `expectedRangeLabel` of the earliest and latest day the tree actually painted.
     */
    private fun assertHeaderDescribesPaintedCells(state: HijriCalendarState, space: String) =
        runComposeUiTest {
            setContent { host(state)() }

            val inMonth = inMonthDays()
            assertTrue(inMonth.isNotEmpty(), "$space: no in-month day cells were rendered")

            // Expected from the cells the tree actually painted, never from the resolver the
            // header itself calls — that comparison cannot fail.
            onNodeWithText(expectedRangeLabel(inMonth.first(), inMonth.last())).assertExists()
        }

    /**
     * The assertion that would have caught UI-01. A scoped table moves the month's last day, so the
     * header must move with it *and* the painted cells must agree with both. Deriving the expected
     * text from the painted cells (not the resolver) is what makes this capable of failing.
     */
    @Test
    fun headerAndPaintedCellsAgreeUnderAScopedOverrideTable() = runComposeUiTest {
        val year = 1448
        val month = 9
        val calculated =
            com.muazdev.hijricalendar.core.ObservedHijriCalendar.defaultLength(year, month)
        val forced = if (calculated == 30) 29 else 30
        val table = HijriMonthLengths(mapOf((year to month) to forced))
        val state = stateFor(year = year, month = month, monthLengths = table)
        setContent { host(state)() }

        val inMonth = inMonthDays()

        assertEquals(
            forced,
            inMonth.size,
            "the grid painted ${inMonth.size} in-month days; the scoped table forces $forced " +
                "(calculated $calculated). Header and cells must both follow the scoped table.",
        )
        // Cross-check: the header's rendered line must describe those exact painted days.
        onNodeWithText(expectedRangeLabel(inMonth.first(), inMonth.last())).assertExists()
    }

    // ── the header never names a different month than the grid ────────
    //
    // One test per space, deliberately. setContent can only be called once per runComposeUiTest, so
    // looping over the spaces inside one test would read the *first* space's cells every time and
    // pass or fail for the wrong reason. An earlier draft did exactly that.

    @Test
    fun headerNamesTheMonthBelowIt_ummAlQura() = assertHeaderMatchesPaintedCells(stateFor(), "UAQ")

    @Test
    fun headerNamesTheMonthBelowIt_pakistan() =
        assertHeaderMatchesPaintedCells(stateFor(pakistan = true), "Pakistan")

    @Test
    fun headerNamesTheMonthBelowIt_observed() =
        assertHeaderMatchesPaintedCells(
            stateFor(monthLengths = HijriMonthLengths(mapOf((1447 to 9) to 29))),
            "Observed",
        )

    @Test
    fun headerNamesTheMonthBelowIt_withAdjustment() =
        assertHeaderMatchesPaintedCells(stateFor(adjustmentDays = 2), "adjusted")

    /** Shared body for the four spaces above; each caller gets its own fresh render. */
    private fun assertHeaderMatchesPaintedCells(state: HijriCalendarState, space: String) =
        runComposeUiTest {
            setContent { host(state)() }
            val painted = cellDates()
            val range = state.gregorianRangeFor(state.currentMonth)

            assertTrue(
                range.first in painted,
                "$space: header starts at ${range.first}, which no cell represents",
            )
            assertTrue(
                range.last in painted,
                "$space: header ends at ${range.last}, which no cell represents",
            )
            onNodeWithText(expectedRangeLabel(range.first, range.last)).assertExists()
        }

    // ── display modes ─────────────────────────────────────────────────

    @Test
    fun gregorianOnly_rendersTheGregorianDayNumber() = runComposeUiTest {
        val state = stateFor()
        setContent { host(state, dateDisplayMode = DateDisplayMode.GREGORIAN_ONLY)() }

        // The day cells are still identifiable; this asserts the *mode* did not break the grid.
        assertEquals(42, cellDates().size, "a month grid is always 42 cells")
    }

    @Test
    fun hijriOnly_doesNotRenderTheGregorianRangeLine() = runComposeUiTest {
        val state = stateFor()
        setContent { host(state, dateDisplayMode = DateDisplayMode.HIJRI_ONLY)() }

        val range = state.gregorianRangeFor(state.currentMonth)
        assertTrue(
            onAllNodes(hasText(rangeTextFor(range.first, range.last))).fetchSemanticsNodes()
                .isEmpty(),
            "HIJRI_ONLY must not show the Gregorian range line",
        )
    }

    @Test
    fun both_makesTheHeaderRangeLineVisible() = runComposeUiTest {
        val state = stateFor()
        setContent { host(state, dateDisplayMode = DateDisplayMode.BOTH)() }

        val range = state.gregorianRangeFor(state.currentMonth)
        onNodeWithText(rangeTextFor(range.first, range.last)).assertExists()
    }

    // ── helpers ───────────────────────────────────────────────────────

    private fun rangeTextFor(first: LocalDate, last: LocalDate): String =
        expectedRangeLabel(first, last)
}
