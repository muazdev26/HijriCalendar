package com.muazdev.hijricalendar.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.runComposeUiTest
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import com.muazdev.hijricalendar.core.CalendarDay
import com.muazdev.hijricalendar.core.HijriCalendarState
import com.muazdev.hijricalendar.core.WeekDay
import com.muazdev.hijricalendar.core.todayHijriDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * A settings change made *after* the grid is on screen must reach the **painted cells**.
 *
 * Every other suite here asserts the header, the pager's page, or a callback payload. None changes
 * a setting while the month is visible and then inspects a cell — which is why the page-level
 * `remember` could freeze every setting except navigation without anything going red. The sample's
 * instrumented `monthLengthOverrideTakesEffectImmediately` asserts the *header* range (keyed
 * correctly) and a cell *count* (always 42).
 *
 * The probe labels encode each cell's flags and Hijri date into its content description, so a test
 * asks what is genuinely on screen rather than what the state says should be.
 *
 * **On what "today moved" means.** `CalendarDay.isToday` marks one real-world day, and that day is
 * *invariant* under `adjustmentDays`: the adjustment shifts which Hijri date that day carries, not
 * which day it is. So the assertions below check the Hijri label on the today cell, which is what a
 * sighting adjustment actually changes. Asserting that `localDate` moves would be wrong, and core
 * is provably right here — see `MonthLengthOverridesTest` and `CalendarMonthExt`'s `isToday`.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsReflectIntoGridTest {

    private companion object {
        const val DAY = "day"
        const val TODAY = "today"
        const val SELECTED = "selected"
    }

    private fun desc(day: CalendarDay): String = buildString {
        append(DAY).append(' ').append(day.localDate).append(' ').append(day.hijrahDate)
        if (day.isToday) append('|').append(TODAY)
        if (day.isSelected) append('|').append(SELECTED)
    }

    private val probeLabels = HijriCalendarLabels(dayContentDescription = ::desc)

    private fun host(state: HijriCalendarState): @Composable () -> Unit = {
        MaterialTheme {
            Surface {
                HijriCalendar(
                    state = state,
                    dateDisplayMode = DateDisplayMode.HIJRI_ONLY,
                    labels = probeLabels,
                    onDayClick = {},
                )
            }
        }
    }

    /**
     * Starts on the month containing today, since these tests are about the today highlight. The
     * month is derived rather than hardcoded so the suite cannot rot into testing a month with no
     * today cell in it -- which is exactly the mistake a fixed 1447-9 made while writing this.
     */
    private fun stateForToday(): HijriCalendarState {
        val today = requireNotNull(todayHijriDate(0)) { "today must be representable" }
        return HijriCalendarState(
            initialMonth = HijrahYearMonth(today.year, today.month.number),
            firstDayOfWeek = WeekDay.SATURDAY,
        )
    }

    private fun ComposeUiTest.countPainted(flag: String): Int =
        onAllNodes(hasContentDescription(flag, substring = true)).fetchSemanticsNodes().size

    /** Asserts a cell is painted carrying [flag], identified by real-world day and Hijri label. */
    private fun ComposeUiTest.assertPaints(flag: String, day: CalendarDay) {
        onNodeWithContentDescription("$DAY ${day.localDate} ${day.hijrahDate}|$flag").assertExists()
    }

    @Test
    fun changingAdjustmentDays_movesTodaysHijriLabelInTheGrid() = runComposeUiTest {
        val state = stateForToday()
        setContent { host(state)() }

        val before = state.calendarMonth.days.first { it.isToday }
        assertPaints(TODAY, before)
        assertEquals(1, countPainted(TODAY), "exactly one cell is painted as today")

        state.setAdjustmentDays(1)
        waitForIdle()

        // Sanity on the state, so the test cannot pass for the wrong reason.
        val after = state.calendarMonth.days.first { it.isToday }
        assertEquals(before.localDate, after.localDate, "the marked real-world day is invariant")
        assertNotEquals(before.hijrahDate, after.hijrahDate, "the Hijri label must shift")

        // The screen must agree. This is the assertion that fails when the page's `remember(month)`
        // serves a stale CalendarMonth: the state says `after`, the cells still paint `before`, and
        // the header — keyed correctly — disagrees with the grid.
        assertPaints(TODAY, after)
        assertEquals(1, countPainted(TODAY), "still exactly one painted today cell")
        onNodeWithContentDescription("$DAY ${before.localDate} ${before.hijrahDate}|$TODAY")
            .assertDoesNotExist()
    }

    @Test
    fun selectingADay_highlightsThatCellInTheGrid() = runComposeUiTest {
        val state = stateForToday()
        setContent { host(state)() }

        // Neither today nor already selected, so "it became highlighted" is a real observation.
        val target = state.calendarMonth.days.first {
            !it.isToday && !it.isSelected && it.isCurrentMonth
        }

        state.selectDay(target)
        waitForIdle()

        assertPaints(SELECTED, target)
        assertEquals(
            1,
            state.calendarMonth.days.count { it.isSelected },
            "sanity: the state holds exactly one selection",
        )
    }

    @Test
    fun togglingPakistanMode_repaintsTheGridCells() = runComposeUiTest {
        val state = stateForToday()
        setContent { host(state)() }

        val before = state.calendarMonth.days.first { it.isToday }
        assertPaints(TODAY, before)

        state.setPakistanDates(true)
        waitForIdle()

        // In Pakistan space `hijrahDate` is null and `pakistanDate` carries the label, so the probe
        // description changes wholesale. If the page cached its CalendarMonth, the painted
        // descriptions would be byte-identical to the ones above.
        val painted = onAllNodes(hasContentDescription("$DAY ", substring = true))
            .fetchSemanticsNodes()
        assertTrue(painted.isNotEmpty(), "cells are painted")
        val stale = before.hijrahDate.toString()
        val paintedLabels = painted.map { node -> node.config.toString() }
        assertTrue(
            paintedLabels.none { stale in it },
            "no cell may still paint the Umm al-Qura label '$stale' after switching to Pakistan " +
                "space; if one does, the grid is frozen. Painted: $paintedLabels",
        )
    }
}
