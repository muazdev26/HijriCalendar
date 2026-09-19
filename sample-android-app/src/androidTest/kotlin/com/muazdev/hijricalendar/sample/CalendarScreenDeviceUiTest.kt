package com.muazdev.hijricalendar.sample

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.abdulrahman_b.hijrahdatetime.toLocalDate
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import com.muazdev.hijricalendar.core.DateDisplayMode
import com.muazdev.hijricalendar.core.HijriMonthOverrides
import com.muazdev.hijricalendar.core.ObservedHijriCalendar
import com.muazdev.hijricalendar.core.PakistanHijriCalendar
import com.muazdev.hijricalendar.core.todayHijriDate
import com.muazdev.hijricalendar.shared.CalendarScreen
import com.muazdev.hijricalendar.shared.UrduCalendarLabels
import com.muazdev.hijricalendar.ui.HijriCalendarLabels
import com.muazdev.hijricalendar.ui.rememberHijriCalendarState
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private val englishHijriMonths = listOf(
    "Muharram", "Safar", "Rabi al-Awwal", "Rabi al-Thani",
    "Jumada al-Ula", "Jumada al-Akhirah", "Rajab", "Shaban",
    "Ramadan", "Shawwal", "Dhu al-Qadah", "Dhu al-Hijjah",
)

private val englishGregorianMonths = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

/**
 * On-device (instrumented) UI verification of the full calendar surface the app ships:
 * grid rendering, month navigation, day selection, display modes, adjustment, Pakistan
 * mode, month-end length overrides, jump-to-today and the Urdu localization bundle.
 *
 * Runs against the real Android runtime via
 * `./gradlew :sample-android-app:connectedDebugAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class CalendarScreenDeviceUiTest {

    @get:Rule
    val compose = createComposeRule()

    @After
    fun tearDown() {
        HijriMonthOverrides.clearAll()
    }

    // ── Shared state helpers ────────────────────────────────────────────

    private fun contentDescriptions(node: SemanticsNode): List<String> =
        if (node.config.contains(SemanticsProperties.ContentDescription))
            node.config[SemanticsProperties.ContentDescription]
        else emptyList()

    /** A semantics matcher for day cells: any node whose content description is "Day N". */
    private val dayCellMatcher = SemanticsMatcher("a Hijri day-cell") { node ->
        contentDescriptions(node).any { it.startsWith("Day ") }
    }

    private fun dayCell(day: Int) = SemanticsMatcher("day cell $day") { node ->
        contentDescriptions(node).any { it == "Day $day" }
    }

    /**
     * Looks up the Gregorian month range shown inside the merged calendar header. The range
     * text is a descendant of the header Row, which sets `mergeDescendants = true`, so it is
     * hidden from the merged semantics tree and must be queried with the unmerged tree.
     * `performScrollTo` cannot operate on unmerged nodes, so the merged header node (which is
     * at the top of the screen) is scrolled into view first, then the unmerged range text is
     * asserted to be displayed.
     */
    private fun assertHeaderRangeShown(range: String) {
        compose.onNodeWithContentDescription("Ramadan 1447")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText(range, useUnmergedTree = true).assertIsDisplayed()
    }

    /** Recreates the internal `sameMonthRangeLabel` text with English month names. */
    private fun rangeText(first: LocalDate, last: LocalDate): String = when {
        first.month == last.month && first.year == last.year ->
            "${englishGregorianMonths[first.month.ordinal]} ${first.year}"
        first.year == last.year ->
            "${englishGregorianMonths[first.month.ordinal]} - " +
                "${englishGregorianMonths[last.month.ordinal]} ${first.year}"
        else ->
            "${englishGregorianMonths[first.month.ordinal]} ${first.year} - " +
                "${englishGregorianMonths[last.month.ordinal]} ${last.year}"
    }

    private fun host(labels: HijriCalendarLabels = HijriCalendarLabels()) {
        compose.setContent {
            CalendarApp(labels)
        }
    }

    @Composable
    private fun CalendarApp(labels: HijriCalendarLabels) {
        MaterialTheme {
            val state = rememberHijriCalendarState(HijrahYearMonth(1447, 9))
            var displayMode by remember { mutableStateOf(DateDisplayMode.HIJRI_ONLY) }
            CalendarScreen(
                state = state,
                dateDisplayMode = displayMode,
                onDateDisplayModeChange = { displayMode = it },
                labels = labels,
                showAdjustmentSelector = true,
                showPakistanToggle = true,
                showMonthLengthSettings = true,
                onJumpToToday = { state.goToToday() },
            )
        }
    }

    // ── Grid rendering ────────────────────────────────────────────────

    @Test
    fun gridRendersCompleteMonthWithHeaderAndDayCells() {
        host()

        // Sticky header: month name + year.
        compose.onNodeWithContentDescription("Ramadan 1447").assertIsDisplayed()
        // Full 42-cell grid (6 weeks x 7 days), always rendered regardless of month shape.
        compose.onAllNodes(dayCellMatcher).assertCountEquals(42)
        // A mid-month seed day and the first day of the month are both present. The first
        // day of the month appears twice in a 42-cell grid (once in the month, once as the
        // leading cell of the next month when the month doesn't end on a week boundary), so
        // match any instance rather than exactly one.
        compose.onAllNodes(dayCell(1))[0].assertIsDisplayed()
        compose.onNode(dayCell(15)).assertIsDisplayed()
        // Nothing is selectable yet.
        compose.onNodeWithText("No date selected").performScrollTo().assertIsDisplayed()
    }

    // ── Month navigation ──────────────────────────────────────────────

    @Test
    fun navigationMovesHeaderForwardAndBack() {
        host()

        compose.onNodeWithContentDescription("Next month").performClick()
        compose.onNodeWithContentDescription("Shawwal 1447").assertIsDisplayed()

        compose.onNodeWithContentDescription("Previous month").performClick()
        compose.onNodeWithContentDescription("Ramadan 1447").assertIsDisplayed()
    }

    // ── Day selection + summary ───────────────────────────────────────

    @Test
    fun selectingADayUpdatesTheSummary() {
        host()

        compose.onNode(dayCell(15)).performClick()
        compose.onNodeWithText("15 RAMADAN 1447").performScrollTo().assertIsDisplayed()
    }

    // ── Date display modes ────────────────────────────────────────────

    @Test
    fun displayModesSwitchHijriGregorianAndBoth() {
        host()
        compose.onNode(dayCell(15)).performClick()

        // Hijri-only (default): summary shows only the Hijri date.
        compose.onNodeWithText("15 RAMADAN 1447").performScrollTo().assertIsDisplayed()

        val gregorian = ObservedHijriCalendar.observedToGregorian(1447, 9, 15)
        val gregorianText = "${gregorian.day} ${gregorian.month.name} ${gregorian.year}"

        compose.onNodeWithText("Gregorian").performScrollTo().performClick()
        compose.onNodeWithText(gregorianText).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("15 RAMADAN 1447").assertDoesNotExist()

        compose.onNodeWithText("Both").performScrollTo().performClick()
        compose.onNode(hasText("15 RAMADAN 1447", substring = true)).performScrollTo().assertIsDisplayed()
        compose.onNode(hasText(gregorianText, substring = true)).performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("Hijri").performScrollTo().performClick()
        compose.onNodeWithText("15 RAMADAN 1447").performScrollTo().assertIsDisplayed()
    }

    // ── Moon-sighting adjustment ──────────────────────────────────────

    @Test
    fun adjustmentSelectorShiftsTheGridRange() {
        host()

        // Gregorian header range is only rendered in a non-Hijri-only display mode.
        compose.onNodeWithText("Both").performScrollTo().performClick()

        val first = HijrahYearMonth(1447, 9).firstDay.toLocalDate()
        val last = HijrahYearMonth(1447, 9).lastDay.toLocalDate()

        assertHeaderRangeShown(rangeText(first, last))

        compose.onNodeWithText("+1").performScrollTo().performClick()
        compose.onNodeWithText("Show Hijri 1 day(s) ahead").performScrollTo().assertIsDisplayed()

        // The whole grid range slides one Gregorian day earlier.
        val shiftedFirst = first.minus(1, DateTimeUnit.DAY)
        val shiftedLast = last.minus(1, DateTimeUnit.DAY)
        assertHeaderRangeShown(rangeText(shiftedFirst, shiftedLast))
    }

    // ── Pakistan (Ruet-e-Hilal) mode ──────────────────────────────────

    @Test
    fun pakistanModeRecomputesTheGrid() {
        host()
        compose.onNodeWithText("Both").performScrollTo().performClick()

        compose.onNodeWithText("Pakistan").performScrollTo().performClick()
        compose.onNodeWithText("Pakistan (Ruet-e-Hilal moon sighting)").performScrollTo().assertIsDisplayed()

        // Header range is now driven by the Pakistan calendar for the same month.
        val first = PakistanHijriCalendar.hijriToGregorian(1447, 9, 1)
        val last = first.plus(
            PakistanHijriCalendar.lengthOfMonth(1447, 9) - 1,
            DateTimeUnit.DAY,
        )
        assertHeaderRangeShown(rangeText(first, last))

        // Grid still renders a complete month.
        compose.onAllNodes(dayCellMatcher).assertCountEquals(42)
    }

    // ── Month-end length overrides ────────────────────────────────────

    @Test
    fun monthLengthOverrideTakesEffectImmediately() {
        host()
        compose.onNodeWithText("Both").performScrollTo().performClick()

        compose.onNodeWithText("No overrides set for this month").performScrollTo().assertIsDisplayed()

        // Force the OPPOSITE length vs the calculation for the current month (1447-09)
        // using the middle row of the three-month panel.
        val defaultLength = HijrahYearMonth(1447, 9).numberOfDays
        val targetLength = if (defaultLength == 30) 29 else 30
        compose.onAllNodesWithText("$targetLength days")[1].performScrollTo().performClick()

        compose.onNodeWithText("Monthly override set \u2014 resets take effect immediately").performScrollTo().assertIsDisplayed()

        // Header range shifts to the observed (override-shifted) calendar.
        val first = ObservedHijriCalendar.observedToGregorian(1447, 9, 1)
        val last = ObservedHijriCalendar.observedToGregorian(
            1447, 9, ObservedHijriCalendar.observedLength(1447, 9),
        )
        assertHeaderRangeShown(rangeText(first, last))

        // Clearing re-anchors the grid onto the calculation: clicking the now-effective
        // (filled) length button for the current month clears the override.
        compose.onAllNodesWithText("$targetLength days")[1].performScrollTo().performClick()
        compose.onNodeWithText("No overrides set for this month").performScrollTo().assertIsDisplayed()
    }

    // ── Jump to today ─────────────────────────────────────────────────

    @Test
    fun jumpToTodayAnchorsTheHeaderOnTodaysMonth() {
        host()

        val today = todayHijriDate(0) ?: error("today must resolve")
        val expectedHeader = "${englishHijriMonths[today.month.number - 1]} ${today.year}"

        compose.onNodeWithText("Jump to Today").performScrollTo().performClick()
        compose.onNodeWithContentDescription(expectedHeader).performScrollTo().assertIsDisplayed()
    }

    // ── Urdu localization bundle ──────────────────────────────────────

    @Test
    fun urduLabelsLocalizeHeaderNavigationAndWeekdays() {
        host(UrduCalendarLabels)

        compose.onNodeWithContentDescription("\u0631\u0645\u0636\u0627\u0646 1447").assertIsDisplayed()
        compose.onNodeWithContentDescription("\u067e\u0686\u06be\u0644\u0627 \u0645\u06c1\u06cc\u0646\u06c1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("\u0627\u06af\u0644\u0627 \u0645\u06c1\u06cc\u0646\u06c1").performScrollTo().assertIsDisplayed()

        // First column of the week header = Saturday under the default first-day-of-week.
        compose.onNodeWithText("\u06c1\u0641\u062a\u06c1").assertIsDisplayed()

        // Accessibility day-cell descriptions stay intact under localization.
        compose.onNode(dayCell(15)).assertIsDisplayed()
    }
}
