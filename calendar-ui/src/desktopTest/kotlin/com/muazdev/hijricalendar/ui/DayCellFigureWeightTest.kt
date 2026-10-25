package com.muazdev.hijricalendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.abdulrahman_b.hijrahdatetime.HijrahDate
import com.muazdev.hijricalendar.core.CalendarDay
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A single-date cell's figure is **painted** heavier than its type's own weight.
 *
 * `DayCellStyleTest` pins colours and `PrimaryDateCellOrderTest` pins which date is on top, but neither
 * can see a `fontWeight`: Compose's semantics expose a `Text`'s string, not its style, so a cell that
 * quietly dropped the weight would pass every existing test. The report that asked for this ("if
 * single date formats are there, the dates should be bold") is therefore pinned the only way a weight
 * can be: by reading the rendered pixels, which is a Skiko/Android API and so lives in `desktopTest`
 * next to `DayCellTodayRingRenderTest`.
 *
 * The assertion is an **agreement between paired values**, not a magic ink count. One composition
 * holds three boxes of the cell's exact size: the real cell, the same figure as a bare `Text` at
 * `bodyMedium`'s own weight, and the same figure at [CellFigureWeight]. Bold strokes cover strictly
 * more pixels than regular ones at the same size and position, so the cell's ink is expected to sit on
 * the `CellFigureWeight` value and *away* from the regular one. The gap between the two references is
 * asserted as well, so a renderer that stopped distinguishing weights fails as "cannot measure" rather
 * than passing by accident.
 *
 * `BOTH` is deliberately not measured: its two lines differ in size and colour as well as weight, so a
 * whole-cell ink count could not attribute a difference to the weight. Its hierarchy is pinned by
 * `PrimaryDateCellOrderTest` (which line is on top) and by the device pass (how it looks).
 */
@OptIn(ExperimentalTestApi::class)
class DayCellFigureWeightTest {

    private val colors = HijriCalendarColors(
        selectedDayContainerColor = Color.Red,
        selectedDayContentColor = Color.Blue,
        todayBorderColor = Color.Green,
        todayBorderWidth = 3.dp,
        disabledDayContentColor = Color.Yellow,
        weekendDayContentColor = Color.Magenta,
        cellBorderColor = Color(0xFF112233),
        dayContentColor = Color.Black,
        dayBackgroundColor = Color.White,
        headerContentColor = Color.White,
        navigationIconColor = Color.Cyan,
        dayOfWeekLabelColor = Color.DarkGray,
        outsideMonthDayContentColor = Color.LightGray,
        gregorianDayContentColor = Color.DarkGray,
        gregorianHeaderColor = Color.Gray,
        eventDayContainerColor = Color(0xFF445566),
        eventDayContentColor = Color(0xFF778899),
    )

    /** 15 is two digits — the widest figure a cell draws. */
    private val day = CalendarDay(
        hijrahDate = HijrahDate(1447, 9, 15),
        isCurrentMonth = true,
        isToday = false,
        isSelected = false,
        isDisabled = false,
        isWeekend = false,
    )

    @Test
    fun hijriOnlyDrawsTheFigureBold() = runComposeUiTest {
        assertFigureIsEmphasized(DateDisplayMode.HIJRI_ONLY)
    }

    @Test
    fun gregorianOnlyDrawsTheFigureBold() = runComposeUiTest {
        assertFigureIsEmphasized(DateDisplayMode.GREGORIAN_ONLY)
    }

    private fun ComposeUiTest.assertFigureIsEmphasized(mode: DateDisplayMode) {
        val inks = render(mode)
        val cell = inks.getValue(CELL_TAG)
        val regular = inks.getValue(REGULAR_TAG)
        val bold = inks.getValue(BOLD_TAG)

        // The control: if the two references carried the same ink this test could not tell a bold
        // figure from a regular one, and the comparison below would be meaningless.
        assertTrue(
            bold > regular,
            "$mode: the reference figures must differ in ink for the measurement to mean anything " +
                "(regular=$regular, bold=$bold)",
        )
        assertTrue(
            abs(cell - bold) < abs(cell - regular),
            "$mode: the cell's painted figure ($cell) must sit on the $CellFigureWeight reference " +
                "($bold), not the regular one ($regular)",
        )
    }

    /**
     * The real cell and both references, side by side in one composition.
     *
     * One `setContent` because the harness allows only one per test; the three boxes are the cell's own
     * size and draw the same glyph at integer pixel offsets, so their ink counts are comparable.
     */
    private fun ComposeUiTest.render(mode: DateDisplayMode): Map<String, Int> {
        val text = if (mode == DateDisplayMode.HIJRI_ONLY) {
            day.dayOfMonth.toString()
        } else {
            day.localDate.day.toString()
        }
        setContent {
            MaterialTheme {
                Row(Modifier.background(Color.White)) {
                    HijriCalendarDayCell(
                        day = day,
                        onClick = {},
                        colors = colors,
                        dateDisplayMode = mode,
                        modifier = Modifier.testTag(CELL_TAG),
                    )
                    ReferenceFigure(text = text, weight = FontWeight.Normal, modifier = Modifier.testTag(REGULAR_TAG))
                    ReferenceFigure(text = text, weight = CellFigureWeight, modifier = Modifier.testTag(BOLD_TAG))
                }
            }
        }
        return listOf(CELL_TAG, REGULAR_TAG, BOLD_TAG).associateWith { tag ->
            onNodeWithTag(tag).captureToImage().toPixelMap().ink()
        }
    }

    // ── pixel helpers ──

    /** Pixels dark enough to be ink rather than the white background or an anti-aliasing fringe. */
    private fun PixelMap.ink(): Int {
        var count = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (this[x, y].luminance() < 0.5f) count++
            }
        }
        return count
    }

    private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

    private companion object {
        const val CELL_TAG = "figure-cell"
        const val REGULAR_TAG = "figure-regular"
        const val BOLD_TAG = "figure-bold"
    }
}

/**
 * The reference the cell is compared against: the same text, in the same 48dp box, at [weight].
 *
 * Applies the cell's own `Text` settings — centred, one line, no wrapping — so position and
 * anti-aliasing are comparable; anything differing here would show up as an ink difference that is not
 * the weight.
 */
@Composable
private fun ReferenceFigure(text: String, weight: FontWeight, modifier: Modifier = Modifier) {
    val style: TextStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = weight)
    Box(
        modifier = modifier
            .size(HijriCalendarDefaults.SingleLineCellSize)
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = style,
            color = Color.Black,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
        )
    }
}
