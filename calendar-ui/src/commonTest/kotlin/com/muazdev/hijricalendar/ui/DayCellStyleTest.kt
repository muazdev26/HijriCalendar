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
        dayContentColor = Color.Black,
        dayBackgroundColor = Color.Gray,
        headerContentColor = Color.White,
        navigationIconColor = Color.Cyan,
        dayOfWeekLabelColor = Color.DarkGray,
        outsideMonthDayContentColor = Color.LightGray,
        gregorianDayContentColor = Color.DarkGray,
        gregorianHeaderColor = Color.Gray,
    )

    private fun day(
        currentMonth: Boolean = true,
        today: Boolean = false,
        selected: Boolean = false,
        disabled: Boolean = false,
        weekend: Boolean = false,
    ) = CalendarDay(
        hijrahDate = HijrahDate(1447, 9, 15),
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
