package com.muazdev.hijricalendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import com.muazdev.hijricalendar.core.HijriCalendarState
import com.muazdev.hijricalendar.core.WeekDay
import kotlin.test.Test

/**
 * The vertical rules must divide **every** pair of neighbours, in both reading directions.
 *
 * This is the regression test for "the lines are missing between Thursday and Friday". The rule is
 * drawn on a cell's *physical* trailing edge, and the last column is skipped so the grid has no outer
 * border. That is right in LTR, but `Row` places the first cell on the right under RTL — so the
 * skipped "last" cell sits on the **left**, and the edge that goes unpainted is an *interior* one
 * (Thursday|Friday with a Saturday-first week). The two cells then read as one double-width cell.
 *
 * A pixel assertion, not a layout one: the cell's own transparent `Box` is laid out correctly either
 * way; only the ink is missing.
 */
@OptIn(ExperimentalTestApi::class)
class GridDividerDirectionTest {

    private val border = Color(0xFF00FF00)
    private val columnPx = 45
    private val widthDp = columnPx * 7

    private fun colors() = HijriCalendarColors(
        selectedDayContainerColor = Color(0xFF00CCFF),
        selectedDayContentColor = Color.Black,
        todayBorderColor = Color(0xFF0000FF),
        todayBorderWidth = 2.dp,
        disabledDayContentColor = Color.Gray,
        weekendDayContentColor = Color(0xFF990099),
        cellBorderColor = border,
        dayContentColor = Color.Black,
        dayBackgroundColor = Color.Transparent,
        headerContentColor = Color.Black,
        navigationIconColor = Color.Black,
        dayOfWeekLabelColor = Color.Black,
        outsideMonthDayContentColor = Color.Gray,
        gregorianDayContentColor = Color.DarkGray,
        gregorianHeaderColor = Color.Gray,
        eventDayContainerColor = Color(0xFF445566),
        eventDayContentColor = Color(0xFF778899),
    )

    private fun ComposeUiTest.pixels(direction: LayoutDirection): PixelMap {
        val state = HijriCalendarState(
            initialMonth = HijrahYearMonth(1447, 9),
            firstDayOfWeek = WeekDay.SATURDAY,
            showCellBorders = true,
        )
        setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f),
                LocalLayoutDirection provides direction,
            ) {
                MaterialTheme {
                    Box(Modifier.background(Color.White).width(widthDp.dp)) {
                        HijriCalendarGrid(
                            state = state,
                            onDayClick = {},
                            colors = colors(),
                            modifier = Modifier.testTag("grid"),
                        )
                    }
                }
            }
        }
        return onNodeWithTag("grid").captureToImage().toPixelMap()
    }

    /** A pixel the vertical rule painted at least half of: green-dominant. */
    private fun PixelMap.isRuleInk(x: Int, y: Int): Boolean {
        val p = this[x, y]
        return p.green > 0.5f && p.red < 0.5f && p.blue < 0.5f
    }

    /** How many rows of column [x] carry vertical-rule ink. */
    private fun PixelMap.ruleHeight(x: Int): Int {
        var hits = 0
        for (y in 0 until height) if (isRuleInk(x, y)) hits++
        return hits
    }

    /** The tallest rule-bearing column within [slack] pixels of [x]. */
    private fun PixelMap.ruleNear(x: Int, slack: Int = 2): Int =
        (maxOf(0, x - slack)..minOf(width - 1, x + slack)).maxOf { ruleHeight(it) }

    /** A column is "rule-bearing" when most of the grid height is rule ink. */
    private fun PixelMap.isRuleColumn(x: Int): Boolean = ruleHeight(x) > height / 2

    private fun assertAllInteriorRulesDrawn(direction: LayoutDirection) = runComposeUiTest {
        val pixels = pixels(direction)
        val floor = pixels.height / 2
        assert(pixels.height > 0)

        for (k in 1..6) {
            val boundary = k * columnPx
            val strongest = pixels.ruleNear(boundary)
            check(strongest > floor) {
                "$direction: no vertical rule at the boundary between columns ${k - 1} and $k " +
                    "(x≈$boundary, rule height $strongest of ${pixels.height})"
            }
        }

        // The grid must not grow an outer border: the rule belongs *between* cells only.
        check(!pixels.isRuleColumn(0)) { "$direction: an outer rule was painted on the left edge" }
        check(!pixels.isRuleColumn(pixels.width - 1)) {
            "$direction: an outer rule was painted on the right edge"
        }
    }

    @Test
    fun everyBoundaryIsDividedInLtr() {
        assertAllInteriorRulesDrawn(LayoutDirection.Ltr)
    }

    /**
     * The failing case from the report. With a Saturday-first week the last column is Friday, so the
     * missing rule lands between Thursday and Friday — the two leftmost cells on an RTL device.
     */
    @Test
    fun everyBoundaryIsDividedInRtl() {
        assertAllInteriorRulesDrawn(LayoutDirection.Rtl)
    }
}
