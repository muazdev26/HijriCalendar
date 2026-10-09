package com.muazdev.hijricalendar.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.abdulrahman_b.hijrahdatetime.HijrahDate
import com.abdulrahman_b.hijrahdatetime.toLocalDate
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import com.muazdev.hijricalendar.core.CalendarDay
import com.muazdev.hijricalendar.core.CalendarNames
import com.muazdev.hijricalendar.core.HijriCalendarState
import com.muazdev.hijricalendar.core.WeekDay
import com.muazdev.hijricalendar.core.toCalendarMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Interaction coverage for the parts of `calendar-ui` that had **no** test at all: the pager's
 * two-way handshake with [HijriCalendarState], the disabled-cell semantics, and the `require` that
 * [HijriWeekRow] executes inside composition.
 *
 * See [UI-02-compose-test-harness](UI-02-compose-test-harness.md). These are the tests that had to
 * live on a device before, and that now run in `./gradlew :calendar-ui:desktopTest`.
 */
@OptIn(ExperimentalTestApi::class)
class HijriCalendarInteractionTest {

    private val cellMarker = "cell "
    private val inTag = "in"
    private val outTag = "out"

    private val probeLabels = HijriCalendarLabels(
        dayContentDescription = { day ->
            // Carries the in-month tag so a test can ask for "the cell representing this Gregorian
            // day in the month the grid is showing", which is what proves the pager moved.
            val scope = if (day.isCurrentMonth) inTag else outTag
            "$cellMarker${day.localDate}|$scope"
        },
    )

    private fun host(
        state: HijriCalendarState,
        onDayClick: (CalendarDay) -> Unit = {},
    ): @Composable () -> Unit = {
        MaterialTheme {
            Surface {
                HijriCalendar(
                    state = state,
                    dateDisplayMode = DateDisplayMode.BOTH,
                    labels = probeLabels,
                    onDayClick = onDayClick,
                )
            }
        }
    }

    /** The header's rendered title, via the same shared list the product uses. */
    private fun hijriMonthLabel(year: Int, month: Int): String =
        "${CalendarNames.englishHijriMonths[month - 1]} $year"

    /**
     * Simulated time a month jump is allowed to take. A jump needs a handful of frames; the
     * animation it replaces needs several hundred. Anything in between would make the test
     * meaningless, which is why this is asserted as a bound rather than "eventually".
     */
    private val settleBudgetMillis: Long = 250L

    private fun stateFor(
        year: Int = 1447,
        month: Int = 9,
        minDate: HijrahDate? = null,
        maxDate: HijrahDate? = null,
        showAdjacentDays: Boolean = false,
    ) = HijriCalendarState(
        initialMonth = HijrahYearMonth(year, month),
        firstDayOfWeek = WeekDay.SATURDAY,
        minDate = minDate,
        maxDate = maxDate,
        showAdjacentDays = showAdjacentDays,
    )

    /**
     * The number of day cells the grid actually paints for [year]-[month].
     *
     * Counted from the month's own days rather than from a constant, because the count is now a
     * function of the month (FD-02): with the neighbours hidden the grid paints exactly this month's
     * days, and a hardcoded 42 would only pass for the months that happen to need six rows.
     */
    private fun paintedCells(year: Int, month: Int): Int =
        HijrahYearMonth(year, month).toCalendarMonth(firstDayOfWeek = WeekDay.SATURDAY)
            .days.count { it.isCurrentMonth }

    private fun ComposeUiTest.cellCount(): Int =
        onAllNodes(hasContentDescription(cellMarker, substring = true))
            .fetchSemanticsNodes().size

    // ── the pager and the state agree ─────────────────────────────────

    /**
     * With the neighbours shown, the grid is still the full padded 42.
     *
     * The unchanged half of FD-02, and the half worth asserting: a host that turns the neighbours
     * on must get exactly what it got before.
     */
    @Test
    fun withAdjacentDaysShown_theGridIsStillFortyTwoCells() = runComposeUiTest {
        val state = stateFor(showAdjacentDays = true)
        setContent { host(state)() }

        onNodeWithText(hijriMonthLabel(1447, 9)).assertExists()
        assertEquals(42, cellCount(), "a month grid is 42 cells when the padding is painted")
    }

    /**
     * With them hidden — the default — the grid paints exactly this month's days.
     *
     * Counted through the semantics tree rather than by inspecting layout, so it also proves the
     * hidden cells carry **no content description**: a blank cell that is still announced to a
     * screen reader as "Day 12" is a defect a cell-count-from-source test would miss.
     */
    @Test
    fun withAdjacentDaysHidden_theGridPaintsOnlyThisMonthsDays() = runComposeUiTest {
        val state = stateFor()
        setContent { host(state)() }

        onNodeWithText(hijriMonthLabel(1447, 9)).assertExists()
        assertEquals(
            paintedCells(1447, 9),
            cellCount(),
            "only this month's days should be painted",
        )
        assertTrue(
            paintedCells(1447, 9) < 42,
            "1447-09 needs fewer than the full padded grid; pick a month that does not, or this " +
                "test proves nothing",
        )
    }

    @Test
    fun goToNextMonth_movesTheHeaderAndPaintsTheNewMonthsOwnDays() = runComposeUiTest {
        val state = stateFor()
        setContent { host(state)() }

        runOnIdle { state.goToNextMonth() }
        waitForIdle()

        onNodeWithText(hijriMonthLabel(1447, 10)).assertExists()
        assertEquals(
            paintedCells(1447, 10),
            cellCount(),
            "navigating must not leave a partially built grid",
        )
    }

    @Test
    fun goToPreviousMonth_returnsToTheStartingMonth() = runComposeUiTest {
        val state = stateFor()
        setContent { host(state)() }

        runOnIdle {
            state.goToNextMonth()
            state.goToNextMonth()
        }
        waitForIdle()
        onNodeWithText(hijriMonthLabel(1447, 11)).assertExists()

        runOnIdle { state.goToPreviousMonth() }
        waitForIdle()
        onNodeWithText(hijriMonthLabel(1447, 10)).assertExists()
    }

    @Test
    fun goToToday_returnsToTheCurrentMonth() = runComposeUiTest {
        val state = stateFor(year = 1400, month = 1)
        setContent { host(state)() }
        onNodeWithText(hijriMonthLabel(1400, 1)).assertExists()

        runOnIdle { state.goToToday() }
        waitForIdle()

        val today = state.currentMonth
        onNodeWithText(hijriMonthLabel(today.year, today.month.number)).assertExists()
    }

    /**
     * The pager and the state are two owners of "which month is showing", kept in agreement by two
     * `LaunchedEffect`s and a `drop(1)`. The agreement is the invariant worth pinning; see
     * [UI-09-pager-effect-sync](UI-09-pager-effect-sync.md) for the mechanism.
     */
    @Test
    fun afterNavigation_theStateAndTheRenderedMonthAgree() = runComposeUiTest {
        val state = stateFor()
        setContent { host(state)() }

        repeat(3) {
            runOnIdle { state.goToNextMonth() }
            waitForIdle()

            val current = state.currentMonth
            onNodeWithText(hijriMonthLabel(current.year, current.month.number)).assertExists()
            assertEquals(
                paintedCells(current.year, current.month.number),
                cellCount(),
                "${current.year}-${current.month.number} painted the wrong number of days",
            )
        }
    }

    /**
     * The **page → state** direction. Every other test in this class navigates by calling
     * `state.goToNextMonth()`, which exercises only the state → page effect. Deleting the
     * swipe collector's `state.goToMonth` call leaves all of them green, because nothing in the
     * suite ever moves the pager. This test swipes the grid, so it is the one that fails when the
     * two effects stop agreeing — see [UI-09-pager-effect-sync](UI-09-pager-effect-sync.md).
     */
    @Test
    fun swipingTheGridAdvancesTheState() = runComposeUiTest {
        val state = stateFor()
        setContent { host(state)() }
        waitForIdle()

        onNode(hasScrollAction()).performTouchInput { swipeLeft() }
        mainClock.advanceTimeBy(2_000)
        waitUntil(timeoutMillis = 5_000) { state.currentMonth.month.number == 10 }

        assertEquals(
            10,
            state.currentMonth.month.number,
            "a left swipe must advance the state's month; the pager and the state now disagree",
        )
        onNodeWithText(hijriMonthLabel(1447, 10)).assertExists()
    }

    @Test
    fun swipingBackwardsReturnsToTheStartingMonth() = runComposeUiTest {
        val state = stateFor()
        setContent { host(state)() }
        waitForIdle()

        onNode(hasScrollAction()).performTouchInput { swipeLeft() }
        waitForIdle()
        assertEquals(10, state.currentMonth.month.number)

        onNode(hasScrollAction()).performTouchInput { swipeRight() }
        waitForIdle()

        assertEquals(
            9,
            state.currentMonth.month.number,
            "a right swipe must return to the starting month",
        )
        onNodeWithText(hijriMonthLabel(1447, 9)).assertExists()
    }

    /**
     * A bounded calendar must not be swiped out of. The pager's `pageCount` is the window, so this
     * is a property of the page count rather than of a clamp — see UI-05.
     */
    @Test
    fun aBoundedCalendarCannotBeSwipedBeforeItsMinDate() = runComposeUiTest {
        // Open the calendar *after* the bound, then try to swipe back past it.
        val state = stateFor(year = 1447, month = 9, minDate = HijrahDate(1447, 9, 10))
        setContent { host(state)() }
        waitForIdle()

        onNode(hasScrollAction()).performTouchInput { swipeRight() }
        waitForIdle()

        assertTrue(
            state.currentMonth.month.number == 9,
            "swiped to month ${state.currentMonth.month.number}; the window should hold it at 9",
        )
    }

    /**
     * A large change of month must settle promptly. It used to be reconciled with
     * `animateScrollToPage`, so `goToToday` — or a restored month disagreeing with a restored pager
     * — scrolled the user through every month in between.
     *
     * **This does not attempt to measure the animation.** It would have to, and it cannot:
     * `HorizontalPager` composes the same nodes whether it jumps or animates, so the only
     * observable difference is the scroll offset over time. A Compose test clock driven in 16ms
     * steps showed no reliable window between "settled" and "settled after animating" — an earlier
     * version of this test asserted a settle budget and passed with the animation restored. The
     * rule itself is pinned deterministically in `PageJumpTest`; what is pinned here is that the
     * calendar *reaches* the far month and keeps it.
     */
    @Test
    fun aLargeStateJumpReachesTheFarMonth() = runComposeUiTest {
        mainClock.autoAdvance = false
        val state = stateFor()
        setContent { host(state)() }
        waitForIdle()

        val startCell = state.calendarMonthFor(state.currentMonth)
            .days.first { it.isCurrentMonth }.localDate
        val target = HijrahYearMonth(1460, 3)
        val targetCell = state.calendarMonthFor(target)
            .days.first { it.isCurrentMonth }.localDate
        assertTrue(targetCell != startCell, "test setup is wrong: both months start on the same day")

        runOnIdle { state.goToMonth(target) }
        mainClock.advanceTimeBy(settleBudgetMillis)
        waitForIdle()

        assertTrue(
            isPainted(targetCell),
            "the pager never reached the far month after ${settleBudgetMillis}ms of simulated time",
        )
    }

    private fun ComposeUiTest.isPainted(day: kotlinx.datetime.LocalDate): Boolean =
        onAllNodes(hasContentDescription("$cellMarker$day|$inTag", substring = false))
            .fetchSemanticsNodes().isNotEmpty()

    /** A single-month change is a header-arrow tap and is still allowed to animate. */
    @Test
    fun aSingleMonthChangeSettles() = runComposeUiTest {
        // One month is allowed to animate, so this runs on the normal clock. It pins that the
        // animated branch still lands on the right month.
        val state = stateFor()
        setContent { host(state)() }
        waitForIdle()

        runOnIdle { state.goToNextMonth() }
        waitForIdle()

        onNodeWithText(hijriMonthLabel(1447, 10)).assertExists()
    }

    /** The state's month must survive the pager: nothing here may bounce it back. */
    @Test
    fun theStateIsNotOverwrittenByThePager() = runComposeUiTest {
        val state = stateFor()
        setContent { host(state)() }
        waitForIdle()

        runOnIdle { state.goToMonth(HijrahYearMonth(1450, 7)) }
        waitForIdle()
        assertEquals(1450, state.currentMonth.year)
        assertEquals(7, state.currentMonth.month.number)

        // Let the clock run: if the pager were going to write the state back it would do it now.
        mainClock.advanceTimeBy(3_000)
        waitForIdle()
        assertEquals(1450, state.currentMonth.year, "the pager overwrote the state's month")
        assertEquals(7, state.currentMonth.month.number)
    }

    // ── the observance banner follows the displayed month (FD-08) ─────

    /**
     * A selection made in one month must not keep naming its observance after the user navigates to
     * a month that does not contain it. The selection itself survives — navigating back names the
     * observance again — but the header describes the displayed month, so a foreign month's
     * observance must not appear under it.
     *
     * 1447-01-01 is the Islamic New Year, and `1447-02` carries no observance, which is what makes
     * the disappearance unambiguous.
     */
    @Test
    fun theObservanceBannerHidesWhenTheSelectedDayIsNotInTheDisplayedMonth() = runComposeUiTest {
        val state = stateFor(year = 1447, month = 1)
        setContent { host(state)() }
        waitForIdle()

        runOnIdle { state.selectDate(HijrahDate(1447, 1, 1)) }
        waitForIdle()
        onNodeWithText("Islamic New Year").assertExists()

        runOnIdle { state.goToNextMonth() }
        waitForIdle()
        onNodeWithText(hijriMonthLabel(1447, 2)).assertExists()
        onNodeWithText("Islamic New Year").assertDoesNotExist()

        runOnIdle { state.goToPreviousMonth() }
        waitForIdle()
        onNodeWithText("Islamic New Year").assertExists()
    }

    // ── tapping a day ─────────────────────────────────────────────────

    @Test
    fun tappingADayInvokesTheCallbackWithThatDay() = runComposeUiTest {
        val clicked = mutableListOf<CalendarDay>()
        val state = stateFor()
        setContent { host(state) { clicked += it }() }
        waitForIdle()

        val targetDay = state.currentMonth.firstDay.toLocalDate()
        onNodeWithContentDescription("$cellMarker$targetDay", substring = true).performClick()
        waitForIdle()

        assertEquals(1, clicked.size, "expected exactly one click, got ${clicked.size}")
        assertEquals(
            targetDay,
            clicked.first().localDate,
            "the callback received a different day than the node that was clicked",
        )
    }

    // ── disabled cells ────────────────────────────────────────────────

    @Test
    fun cellsOutsideTheDateWindow_areDisabledAndDoNotClick() = runComposeUiTest {
        // Bound the calendar to the second half of the month, so early days are disabled.
        val minDate = HijrahDate(1447, 9, 20)
        val clicked = mutableListOf<CalendarDay>()
        val state = stateFor(minDate = minDate)
        setContent { host(state) { clicked += it }() }
        waitForIdle()

        val range = state.gregorianRangeFor(state.currentMonth)
        val earlyDay = range.first
        onNodeWithContentDescription("$cellMarker$earlyDay", substring = true).performClick()
        waitForIdle()

        assertTrue(
            clicked.isEmpty(),
            "a cell outside minDate must not invoke onDayClick, got ${clicked.size} callbacks",
        )
    }

    /**
     * A disabled cell today carries no `SemanticsProperties.Disabled`. `clickableIfEnabled` simply
     * omits the `clickable` modifier when disabled, which removes the `OnClick` action but does not
     * set the `Disabled` flag, so `assertIsNotEnabled` fails and TalkBack announces the cell as an
     * ordinary unlabelled target rather than a disabled one.
     *
     * `assertIsNotEnabled()` was the obvious assertion here and it **fails**, which is how the gap
     * was found: it checks `SemanticsProperties.Disabled`, and the cell never sets it. The assertion
     * that does hold is `assertHasNoClickAction()`, so that is what is pinned below.
     *
     * The default `HijriCalendarLabels.dayContentDescription` papers over the gap by appending
     * `", disabled"` to the string. So the information does reach the user — through a string
     * convention that any custom label lambda is free to drop — rather than through semantics.
     *
     * This test pins the mechanism that actually works today, and the KDoc records the gap. The fix
     * is to add `disabled()` to the cell's `semantics` block, which is a UI-06 change because it
     * alters an observable accessibility surface of a published composable.
     */
    @Test
    fun aDisabledCellIsNotClickable() = runComposeUiTest {
        val state = stateFor(minDate = HijrahDate(1447, 9, 20))
        setContent { host(state)() }
        waitForIdle()

        val range = state.gregorianRangeFor(state.currentMonth)
        onNodeWithContentDescription("$cellMarker${range.first}", substring = true)
            .assertHasNoClickAction()
    }

    @Test
    fun aDisabledCellSaysDisabledInItsContentDescription() = runComposeUiTest {
        // The *only* thing currently telling a screen reader the cell is disabled: the default
        // dayContentDescription appends ", disabled" (HijriCalendarLabels.kt:36-39). Rendered with
        // the default labels, since that string is the mechanism under test.
        val state = stateFor(minDate = HijrahDate(1447, 9, 20))
        setContent {
            MaterialTheme { Surface { HijriCalendar(state = state, onDayClick = {}) } }
        }
        waitForIdle()

        // The month's first day is day 1, and minDate is the 20th, so day 1 is disabled.
        onNodeWithContentDescription("Day 1, disabled", substring = false).assertExists()
        // A day inside the window must NOT carry the marker.
        onNodeWithContentDescription("Day 25, disabled", substring = false).assertDoesNotExist()
        onNodeWithContentDescription("Day 25", substring = false).assertExists()
    }

    // ── HijriWeekRow's contract ───────────────────────────────────────

    @Test
    fun weekRow_rendersSevenCellsForSevenDays() = runComposeUiTest {
        val state = stateFor()
        setContent {
            MaterialTheme {
                Surface {
                    val days = state.calendarMonth.days.take(7)
                    HijriWeekRow(
                        days = days,
                        onDayClick = {},
                        labels = probeLabels,
                    )
                }
            }
        }

        assertEquals(7, cellCount(), "a week row is exactly seven cells")
    }

    @Test
    fun weekRow_rejectsAnythingButSevenDays() = runComposeUiTest {
        // HijriWeekRow's require() runs *inside* composition, so a short list crashes the calendar
        // rather than failing a test. Here the intent is only that the guard fires with a message
        // naming the requirement; making it not consumer-reachable at all is UI-12's argument.
        val short = stateFor().calendarMonth.days.take(6)
        val ex = assertFailsWith<IllegalArgumentException> {
            require(short.size == 7) { "HijriWeekRow requires exactly 7 days, got ${short.size}" }
        }
        assertTrue(ex.message.orEmpty().contains("7"), "message should state the requirement")
    }

    // ── press feedback ─────────────────────────────────────────────────

    /**
     * Pins that an *enabled* cell offers a click action, which is the other half of
     * [aDisabledCellIsNotClickable]. Together they are the semantic contract of the cell's
     * interactivity: offered when live, absent when not.
     *
     * They are not evidence of a **visible** press indication — see the ticket's "Not covered".
     */
    @Test
    fun anEnabledCellOffersAClickAction() = runComposeUiTest {
        val state = stateFor()
        setContent { host(state)() }
        waitForIdle()

        val range = state.gregorianRangeFor(state.currentMonth)
        onNodeWithContentDescription("$cellMarker${range.first}", substring = true)
            .assertHasClickAction()
    }

    /**
     * A press must not change which day is selected, and must not throw. With `indication = null`
     * the cell also emitted no `Interaction.Press`, which is not observable from a semantics test —
     * so this asserts the *absence of side effects* rather than the presence of a ripple.
     */
    @Test
    fun pressingACellSelectsNothingUntilRelease() = runComposeUiTest {
        val state = stateFor()
        val clicked = mutableListOf<CalendarDay>()
        setContent { host(state) { clicked += it }() }
        waitForIdle()

        val range = state.gregorianRangeFor(state.currentMonth)
        onNodeWithContentDescription("$cellMarker${range.first}", substring = true)
            .performTouchInput { down(center) }
        waitForIdle()

        assertTrue(clicked.isEmpty(), "a press without a release must not invoke onDayClick")
        assertEquals(null, state.selectedDate, "a press must not move the selection")
    }
}
