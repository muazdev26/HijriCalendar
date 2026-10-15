package com.muazdev.hijricalendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.abdulrahman_b.hijrahdatetime.HijrahDate
import com.muazdev.hijricalendar.core.CalendarDay
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The today ring is **painted**, not merely resolved, and it survives cell dividers.
 *
 * [DayCellStyleTest] pins the border a today cell *declares* (colour and width). That is not the same
 * claim as the border being drawn: the composable once skipped the border whenever the grid had
 * dividers on, so selecting another day moved the fill and left today with no marker at all while the
 * style still said `todayBorderColor`. Only reading the rendered pixels can tell those two apart,
 * which is why this test lives in `desktopTest` — `captureToImage` is a Skiko/Android API, not a
 * common one.
 *
 * The second claim is the shape swap the widget's `CellDecoration.TODAY_RING` also makes: a rectangle
 * with dividers (a cell of a ruled grid), the day token's circle without them. The corner pixel tells
 * the two apart — a square corner is covered by the border, an inscribed circle's is not.
 */
@OptIn(ExperimentalTestApi::class)
class DayCellTodayRingRenderTest {

    private val accent = Color(0xFF00FF00)

    // Distinct per role, as in DayCellStyleTest, so a mis-wired branch cannot pass by accident. The
    // cell is drawn on white, and the accent is the only strongly-green colour anywhere.
    private val colors = HijriCalendarColors(
        selectedDayContainerColor = Color.Red,
        selectedDayContentColor = Color.Blue,
        todayBorderColor = accent,
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

    @Test
    fun withDividersTheTodayRingIsStillPaintedAndIsRectangular() = runComposeUiTest {
        val pixels = render(day = day(today = true), cellHasDividers = true)

        assertTrue(
            pixels.hasAccent(),
            "today's ring must still be painted when the grid draws dividers",
        )
        assertTrue(
            pixels.cornerIsAccent(),
            "with dividers the ring must be the cell's rectangle, not an inscribed circle",
        )
    }

    @Test
    fun withoutDividersTheTodayRingIsPaintedAndCircular() = runComposeUiTest {
        val pixels = render(day = day(today = true), cellHasDividers = false)

        assertTrue(pixels.hasAccent(), "today's ring must be painted")
        assertFalse(
            pixels.cornerIsAccent(),
            "without dividers the ring is the day token's circle, so the corner is outside it",
        )
    }

    /** The control: a day that is not today draws no ring in either mode. */
    @Test
    fun anOrdinaryDayHasNoRingInEitherMode() = runComposeUiTest {
        assertFalse(render(day = day(today = false), cellHasDividers = true).hasAccent())
        assertFalse(render(day = day(today = false), cellHasDividers = false).hasAccent())
    }

    private fun day(today: Boolean): CalendarDay = CalendarDay(
        hijrahDate = HijrahDate(1447, 9, 15),
        isCurrentMonth = true,
        isToday = today,
        isSelected = false,
        isDisabled = false,
        isWeekend = false,
    )

    private fun ComposeUiTest.render(day: CalendarDay, cellHasDividers: Boolean): PixelMap {
        setContent {
            MaterialTheme {
                Box(Modifier.background(Color.White)) {
                    HijriCalendarDayCell(
                        day = day,
                        onClick = {},
                        colors = colors,
                        cellHasDividers = cellHasDividers,
                        modifier = Modifier.testTag(CELL_TAG),
                    )
                }
            }
        }
        return onNodeWithTag(CELL_TAG).captureToImage().toPixelMap()
    }

    // ── pixel helpers ──

    /** The accent is the only strongly-green colour in the scene; the threshold tolerates edges. */
    private fun PixelMap.hasAccent(): Boolean {
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (this[x, y].isAccent()) return true
            }
        }
        return false
    }

    private fun PixelMap.cornerIsAccent(): Boolean = this[0, 0].isAccent()

    private fun Color.isAccent(): Boolean =
        alpha > 0.5f && green > 0.8f && red < 0.3f && blue < 0.3f

    private companion object {
        const val CELL_TAG = "day-cell"
    }
}
