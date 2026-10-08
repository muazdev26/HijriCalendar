package com.muazdev.hijricalendar.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.abdulrahman_b.hijrahdatetime.HijrahDate
import com.muazdev.hijricalendar.core.CalendarDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the day cell's colour and border precedence, which is **design** and was previously asserted
 * by nothing.
 *
 * Six parallel `when` blocks sat inline in the composable body, so the rules they encoded were
 * invisible to review and to CI. Extracting them into [CalendarDay.cellStyle] is what made them
 * testable; this file is why the extraction was worth doing. See UI-12.
 *
 * The precedence is a *design decision*, so these assert the decision, not just self-consistency:
 * "selected wins" is a choice about what a user should see when a cell is both selected and
 * something else.
 */
class DayCellStyleTest {

    // Distinct values per role, so a mis-wired branch cannot pass by accident.
    private val colors = HijriCalendarColors(
        selectedDayContainerColor = Color.Red,
        selectedDayContentColor = Color.Blue,
        todayBorderColor = Color.Green,
        todayBorderWidth = 3.dp,
        disabledDayContentColor = Color.Yellow,
        weekendDayContentColor = Color.Magenta,
        cellBorderColor = Color(0xFF112233),
        dayContentColor = Color.Black,
        dayBackgroundColor = Color.Gray,
        headerContentColor = Color.White,
        navigationIconColor = Color.Cyan,
        dayOfWeekLabelColor = Color.DarkGray,
        outsideMonthDayContentColor = Color.LightGray,
        gregorianDayContentColor = Color.DarkGray,
        gregorianHeaderColor = Color.Gray,
        eventDayContainerColor = Color(0xFF445566),
        eventDayContentColor = Color(0xFF778899),
    )

    private fun day(
        currentMonth: Boolean = true,
        today: Boolean = false,
        selected: Boolean = false,
        disabled: Boolean = false,
        weekend: Boolean = false,
        event: Boolean = false,
    ) = CalendarDay(
        // 10 Muharram is Ashura and 9 Shawwal is not, so `event` is expressed through the *coordinate*
        // rather than passed in — `CalendarDay.event` is derived, and a test that could set it directly
        // would be testing a shape the class does not have.
        hijrahDate = if (event) HijrahDate(1447, 1, 10) else HijrahDate(1447, 9, 15),
        isCurrentMonth = currentMonth,
        isToday = today,
        isSelected = selected,
        isDisabled = disabled,
        isWeekend = weekend,
    )

    // ── content colour: selected > disabled > outside-month > weekend > normal ──

    @Test
    fun selected_beatsEveryOtherState() {
        val style = day(selected = true, disabled = true, currentMonth = false, weekend = true)
            .cellStyle(colors)
        assertEquals(colors.selectedDayContentColor, style.contentColor)
        assertEquals(colors.selectedDayContainerColor, style.backgroundColor)
    }

    @Test
    fun disabled_beatsOutsideMonthAndWeekend() {
        assertEquals(
            colors.disabledDayContentColor,
            day(disabled = true, currentMonth = false, weekend = true).cellStyle(colors).contentColor,
        )
    }

    @Test
    fun outsideMonth_beatsWeekend() {
        assertEquals(
            colors.outsideMonthDayContentColor,
            day(currentMonth = false, weekend = true).cellStyle(colors).contentColor,
        )
    }

    @Test
    fun weekend_isUsedOnlyWhenNothingElseApplies() {
        assertEquals(colors.weekendDayContentColor, day(weekend = true).cellStyle(colors).contentColor)
    }

    @Test
    fun ordinaryDay_usesTheDefaultContentColour() {
        assertEquals(colors.dayContentColor, day().cellStyle(colors).contentColor)
    }

    /**
     * The consequence worth naming: a cell that is both selected and disabled is drawn as fully
     * enabled. Reachable, because `isDisabled` is resolved against the mutable `adjustmentDays` while
     * the selection is not cleared. It is a deliberate choice — a selection should not change
     * appearance under the user — but it means appearance cannot report interactivity, which is why
     * the cell sets `disabled()` in its semantics block instead.
     */
    @Test
    fun selectedAndDisabled_looksEnabledButIsNotInteractive() {
        val style = day(selected = true, disabled = true).cellStyle(colors)
        assertEquals(colors.selectedDayContentColor, style.contentColor, "selected should still look selected")
        assertFalse(style.enabled, "but it must not be interactive")
    }

    // ── observance fill (FD-08) ──

    /**
     * The fill itself, and the reason it exists.
     *
     * This was a 4dp dot above the day figure. A dot says "something is here" without saying it is
     * legible at all, and the grid is exactly where a user looks to find out which days matter — so
     * the cell is filled instead.
     */
    @Test
    fun observanceDay_isFilled() {
        val style = day(event = true).cellStyle(colors)
        assertEquals(colors.eventDayContainerColor, style.backgroundColor)
        assertEquals(colors.eventDayContentColor, style.contentColor)
    }

    @Test
    fun observance_fillBeatsTheOrdinaryBackgroundAndWeekend() {
        assertEquals(
            colors.eventDayContainerColor,
            day(event = true, weekend = true).cellStyle(colors).backgroundColor,
        )
        assertEquals(
            colors.eventDayContentColor,
            day(event = true, weekend = true).cellStyle(colors).contentColor,
        )
    }

    /**
     * The distinction the whole change exists to make: an observance is a fact about the **date**, the
     * selection a fact about the **session**. A day that is both must read as selected, or the user
     * cannot tell which day they picked.
     */
    @Test
    fun selected_beatsAnObservance() {
        val style = day(event = true, selected = true).cellStyle(colors)
        assertEquals(colors.selectedDayContainerColor, style.backgroundColor)
        assertEquals(colors.selectedDayContentColor, style.contentColor)
    }

    /**
     * No fill outside the month, or on a disabled day.
     *
     * Both are already dimmed figures, and a filled cell there reads as a second selection — which is
     * worse than the dot this replaces. The observance is still named in the header once the day is
     * selected.
     */
    @Test
    fun observance_isNotFilledOutsideTheMonthOrWhenDisabled() {
        assertEquals(
            colors.dayBackgroundColor,
            day(event = true, currentMonth = false).cellStyle(colors).backgroundColor,
        )
        assertEquals(
            colors.outsideMonthDayContentColor,
            day(event = true, currentMonth = false).cellStyle(colors).contentColor,
        )
        assertEquals(
            colors.dayBackgroundColor,
            day(event = true, disabled = true).cellStyle(colors).backgroundColor,
        )
        assertEquals(
            colors.disabledDayContentColor,
            day(event = true, disabled = true).cellStyle(colors).contentColor,
        )
    }

    /** The sub-label dims with its figure rather than staying the ordinary grey. */
    @Test
    fun observance_gregorianSubLabelIsDimmedToo() {
        assertEquals(
            colors.eventDayContentColor.copy(alpha = 0.7f),
            day(event = true).cellStyle(colors).gregorianColor,
        )
    }

    // ── today border ──

    @Test
    fun today_drawsItsBorder() {
        val style = day(today = true).cellStyle(colors)
        assertEquals(colors.todayBorderColor, style.borderColor)
        assertEquals(colors.todayBorderWidth, style.borderWidth)
    }

    @Test
    fun selected_todayBorderIsSuppressedBecauseTheFillWouldHideIt() {
        val style = day(today = true, selected = true).cellStyle(colors)
        assertEquals(colors.selectedDayContainerColor, style.borderColor)
        assertEquals(HijriCalendarDefaults.TodayBorderWidth, style.borderWidth)
    }

    /**
     * Today's border is suppressed on an observance day for the same reason it is on a selected one:
     * the fill would hide it. Asserted because the suppression is now driven by two conditions, and a
     * change to either could leave today unmarked on an Eid.
     */
    @Test
    fun todayOnAnObservance_keepsNoBorderBecauseTheFillWouldHideIt() {
        val style = day(today = true, event = true).cellStyle(colors)
        assertEquals(colors.eventDayContainerColor, style.backgroundColor)
        assertEquals(Color.Transparent, style.borderColor)
        assertEquals(0.dp, style.borderWidth)
    }

    @Test
    fun ordinaryDay_hasNoBorder() {
        val style = day().cellStyle(colors)
        assertEquals(Color.Transparent, style.borderColor)
        assertEquals(0.dp, style.borderWidth)
    }

    // ── gregorian sub-label ──

    @Test
    fun gregorianSubLabel_isDimmedForSelectedAndOutsideMonth() {
        assertEquals(
            colors.selectedDayContentColor.copy(alpha = 0.7f),
            day(selected = true).cellStyle(colors).gregorianColor,
        )
        assertEquals(
            colors.outsideMonthDayContentColor.copy(alpha = 0.7f),
            day(currentMonth = false).cellStyle(colors).gregorianColor,
        )
        assertEquals(colors.gregorianDayContentColor, day().cellStyle(colors).gregorianColor)
    }

    // ── interactivity ──

    @Test
    fun onlyDisabledCellsAreInert() {
        assertTrue(day().cellStyle(colors).enabled)
        assertTrue(day(selected = true).cellStyle(colors).enabled)
        assertTrue(day(weekend = true).cellStyle(colors).enabled)
        assertFalse(day(disabled = true).cellStyle(colors).enabled)
        // An outside-month cell is NOT inert. It is dimmed, but tapping it selects that date and
        // navigates to its month, which is the usual calendar behaviour. `isDisabled` comes only
        // from minDate/maxDate, not from being outside the month. (Asserted the other way first; the
        // code was right and the expectation was not.)
        assertTrue(
            day(currentMonth = false).cellStyle(colors).enabled,
            "an outside-month cell is dimmed but still selectable",
        )
    }
}
