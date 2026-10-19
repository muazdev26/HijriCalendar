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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import com.muazdev.hijricalendar.core.HijriCalendarState
import com.muazdev.hijricalendar.core.WeekDay
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class GridDividerThicknessTest {

    private val border = Color(0xFF00FF00)

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

    private fun ComposeUiTest.pixels(density: Float, widthDp: Int): PixelMap {
        val state = HijriCalendarState(
            initialMonth = HijrahYearMonth(1447, 9),
            firstDayOfWeek = WeekDay.SATURDAY,
            showCellBorders = true,
        )
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(density)) {
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

    /** Column is "rule-bearing" when at least half the grid height is rule ink. */
    private fun PixelMap.ruleColumnHeights(): IntArray =
        IntArray(width) { x ->
            var hits = 0
            for (y in 0 until height) if (isRuleInk(x, y)) hits++
            hits
        }

    /**
     * Thickness in device pixels of each of the six interior vertical rules, measured as the run of
     * rule-bearing columns around each seventh-width boundary.
     */
    private fun PixelMap.verticalRuleThicknesses(): List<Int> {
        val heights = ruleColumnHeights()
        val floor = height / 2
        val boundary = width / 7
        return (1..6).map { k ->
            val centre = (k * boundary).toInt()
            var left = centre
            while (left > 0 && heights[left - 1] > floor) left--
            var right = centre
            while (right < width - 1 && heights[right + 1] > floor) right++
            right - left + 1
        }
    }

    @Test
    fun verticalRulesAreOneDpNotOneDevicePixel() = runComposeUiTest {
        // The defect was a `strokeWidth = 1f` — one *device pixel* — while every other rule in the
        // family is `1.dp`. A device-pixel rule has the same thickness in pixels at every density;
        // a dp rule grows with the display's density. So the discriminating assertion is that each
        // vertical rule is measurably thicker at 2.75x than at 1x. The old form returns the same
        // numbers at both densities and fails here.
        val thin = pixels(density = 1f, widthDp = 312).verticalRuleThicknesses()
        val thick = pixels(density = 2.75f, widthDp = 312).verticalRuleThicknesses()
        println("thickness @1x=$thin @2.75x=$thick")
        thin.zip(thick).forEachIndexed { index, (t1, t2) ->
            check(t2 > t1) {
                "vertical rule ${index + 1} did not grow with density: ${t1}px @1x vs ${t2}px @2.75x"
            }
        }
    }
}
