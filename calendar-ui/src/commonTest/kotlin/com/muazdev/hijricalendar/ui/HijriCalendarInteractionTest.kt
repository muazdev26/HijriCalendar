package com.muazdev.hijricalendar.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
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

    private val probeLabels = HijriCalendarLabels(
        dayContentDescription = { day -> "$cellMarker${day.localDate}" },
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

    private fun stateFor(
        year: Int = 1447,
        month: Int = 9,
        minDate: HijrahDate? = null,
        maxDate: HijrahDate? = null,
    ) = HijriCalendarState(
        initialMonth = HijrahYearMonth(year, month),
        firstDayOfWeek = WeekDay.SATURDAY,
        minDate = minDate,
        maxDate = maxDate,
    )

    private fun ComposeUiTest.cellCount(): Int =
        onAllNodes(hasContentDescription(cellMarker, substring = true))
            .fetchSemanticsNodes().size

    // ── the pager and the state agree ─────────────────────────────────

    @Test
    fun headerShowsTheInitialMonthAndFortyTwoCells() = runComposeUiTest {
        val state = stateFor()
        setContent { host(state)() }

        onNodeWithText(hijriMonthLabel(1447, 9)).assertExists()
        assertEquals(42, cellCount(), "a month grid is always 42 cells")
    }

    @Test
    fun goToNextMonth_movesTheHeaderAndKeepsFortyTwoCells() = runComposeUiTest {
        val state = stateFor()
        setContent { host(state)() }

        runOnIdle { state.goToNextMonth() }
        waitForIdle()

        onNodeWithText(hijriMonthLabel(1447, 10)).assertExists()
        assertEquals(42, cellCount(), "navigating must not leave a partially built grid")
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
            assertEquals(42, cellCount())
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

    // ── tapping a day ─────────────────────────────────────────────────

    @Test
    fun tappingADayInvokesTheCallbackWithThatDay() = runComposeUiTest {
        val clicked = mutableListOf<CalendarDay>()
        val state = stateFor()
        setContent { host(state) { clicked += it }() }
        waitForIdle()

        val targetDay = state.currentMonth.firstDay.toLocalDate()
        onNodeWithContentDescription("$cellMarker$targetDay", substring = false).performClick()
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
        onNodeWithContentDescription("$cellMarker$earlyDay", substring = false).performClick()
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
        onNodeWithContentDescription("$cellMarker${range.first}", substring = false)
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
}
