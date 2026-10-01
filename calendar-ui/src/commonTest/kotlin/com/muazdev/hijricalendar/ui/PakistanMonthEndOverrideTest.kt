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
import com.muazdev.hijricalendar.core.HijriMonthLengths
import com.muazdev.hijricalendar.core.PakistanHijriCalendar
import com.muazdev.hijricalendar.core.WeekDay
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Forcing a month end must change the **painted cells** in Pakistan (Ruet-e-Hilal) mode, not just
 * the state's own arithmetic.
 *
 * `calendar-core` is correct here and is deliberately not re-tested: with a forced 1445-01 the
 * Pakistan builder emits 30 cells, both through the process-global table and a scoped one. The
 * defect was in what the grid did with that month.
 *
 * The month is chosen by searching for one where forcing actually changes the length, rather than
 * hardcoded. A fixed month is a trap: 1448-04 is naturally 30 days, so "force it to 30" is a no-op
 * and the test passes while proving nothing. 1445-01 is naturally 29.
 */
@OptIn(ExperimentalTestApi::class)
class PakistanMonthEndOverrideTest {

    private companion object {
        const val DAY = "day"
    }

    private fun desc(day: CalendarDay): String {
        val label = day.pakistanDate?.let { "pk${it.year}-${it.month}-${it.day}" }
            ?: day.hijrahDate?.toString()
            ?: "none"
        return "$DAY $label"
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

    /** A month whose natural Pakistan length differs from [forced]. */
    private fun discriminatingMonth(forced: Int): HijrahYearMonth {
        for (year in 1445..1450) {
            for (month in 1..12) {
                val natural = PakistanHijriCalendar.lengthOfMonth(year, month, HijriMonthLengths())
                if (natural != forced) return HijrahYearMonth(year, month)
            }
        }
        error("no Pakistan month in 1445..1450 has a length other than $forced")
    }

    private val cellLabel = Regex("day pk(\\d+)-(\\d+)-(\\d+)")

    /**
     * The day numbers of [year]-[month] actually painted on screen.
     *
     * Read from the semantics tree rather than from the state, because the whole point is that the
     * two can disagree. The grid is a fixed 42 cells, so a forced length never changes the cell
     * *count* — it changes which days occupy those cells. Counting nodes would have passed while
     * painting the wrong month.
     */
    private fun ComposeUiTest.paintedDays(year: Int, month: Int): List<Int> =
        onAllNodes(hasContentDescription("$DAY pk", substring = true))
            .fetchSemanticsNodes()
            .mapNotNull { node ->
                cellLabel.find(node.config.toString())
                    ?.takeIf { it.groupValues[1] == "$year" && it.groupValues[2] == "$month" }
                    ?.groupValues?.get(3)
                    ?.toInt()
            }

    @Test
    fun forcingAMonthEndChangesTheRenderedPakistanGrid() = runComposeUiTest {
        val ym = discriminatingMonth(forced = 30)
        val state = HijriCalendarState(
            initialMonth = ym,
            firstDayOfWeek = WeekDay.SATURDAY,
            pakistanDates = true,
        )

        val natural = PakistanHijriCalendar.lengthOfMonth(ym.year, ym.month.number, HijriMonthLengths())
        setContent { host(state)() }

        assertEquals(
            natural,
            paintedDays(ym.year, ym.month.number).size,
            "sanity: the grid paints the natural month to begin with",
        )

        state.setMonthLength(ym.year, ym.month.number, 30)
        waitForIdle()

        // The state already knows; assert that first so the test cannot pass for the wrong reason.
        assertEquals(
            30,
            state.calendarMonth.days.count {
                it.pakistanDate?.year == ym.year && it.pakistanDate?.month == ym.month.number
            },
            "sanity: the state must hold 30 cells in the forced month",
        )

        // The screen must agree. This is the assertion that failed before the fix: the state held
        // 30 cells while the painted grid was byte-identical to the pre-change month.
        assertEquals(
            (1..30).toList(),
            paintedDays(ym.year, ym.month.number),
            "forcing 30 on a naturally-$natural month must repaint the cells with a 30th",
        )
        onNodeWithContentDescription("$DAY pk${ym.year}-${ym.month.number}-30").assertExists()
    }

    @Test
    fun forcingAMonthEndThroughAScopedTableAlsoChangesTheGrid() = runComposeUiTest {
        val ym = discriminatingMonth(forced = 30)
        val scoped = HijriMonthLengths().apply {
            setMonthLength(ym.year, ym.month.number, 30)
        }
        val state = HijriCalendarState(
            initialMonth = ym,
            firstDayOfWeek = WeekDay.SATURDAY,
            pakistanDates = true,
            monthLengths = scoped,
        )

        setContent { host(state)() }

        // A scoped table that disagrees with the process global is the case AGENTS.md warns about;
        // rendering it correctly is what proves the builder reads *this* table.
        assertEquals(
            (1..30).toList(),
            paintedDays(ym.year, ym.month.number),
            "a scoped table must drive the painted cells without touching the process global",
        )
        onNodeWithContentDescription("$DAY pk${ym.year}-${ym.month.number}-30").assertExists()
    }
}
