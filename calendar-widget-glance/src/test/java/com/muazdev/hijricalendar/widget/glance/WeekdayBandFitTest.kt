package com.muazdev.hijricalendar.widget.glance

import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule that decides how large a weekday name is drawn — the answer to "the day name when it is
 * `بدھ` is showing very very small, unable to read".
 *
 * Two claims, and each is asserted against the *widgets* rather than only against the constants, because
 * both are the kind of thing that passes on paper and fails on a placed cell:
 *
 * 1. **The size is a share of the figure**, the same share on every widget in the family. The defect
 *    was not a single number being too small — it was the dual and compact tiles sizing the weekday
 *    from a *caption* while the three tiles sized it from the figure, so the same word was a different
 *    size on five widgets that sit side by side on one home screen.
 * 2. **The band costs its ink, not its line box**, so growing the word does not take height from the
 *    body. On the Today strip that distinction is a regeneration of a defect the family already fixed
 *    once: a taller weekday band was what starved the month captions in round 3.
 *
 * The placed-cell numbers are computed here from each typography's own `sizesFor`, so a change to any
 * widget's reference sizes moves this test with it.
 */
class WeekdayBandFitTest {

    @Test
    fun theSizeIsHalfTheFigureAndNoWidgetDisagrees() {
        // Every fitted typography, at its own reference figure.
        val pairs = listOf(
            "the 1x1 tiles" to (
                DateTileTypography.weekdaySizeAtReference.value to
                    DateTileTypography.daySize.value
                ),
            "the Today strip" to (
                TodayStripTypography.weekdaySize.value to
                    TodayStripTypography.daySize.value
                ),
            "the dual tile" to (
                DualDateTileTypography.weekdaySizeAtReference.value to
                    DualDateTileTypography.daySize.value
                ),
            "the compact tile" to (
                CompactDateTileTypography.weekdaySizeAtReference.value to
                    CompactDateTileTypography.daySize.value
                ),
        )
        for ((name, pair) in pairs) {
            val (weekday, figure) = pair
            assertEquals(
                "$name must draw its weekday at ${WeekdayBandFit.SHARE_OF_FIGURE} of its figure",
                (figure * WeekdayBandFit.SHARE_OF_FIGURE).toDouble(),
                weekday.toDouble(),
                0.01,
            )
            assertTrue(
                "$name's weekday ($weekday) must stay under its figure ($figure)",
                weekday < figure,
            )
        }
    }

    /**
     * The rule read backwards: the compact tile is the cell the report is about, so its placed size is
     * pinned in sp.
     *
     * A Pixel 6a grants a 1×1 a 62×88dp content cell, so the tile draws its 62dp square there. Before
     * this rule its weekday rendered at ~6.9sp in an 18.6dp band; the assertions below are the size *and*
     * the fact that the band got shorter while the word got bigger.
     */
    @Test
    fun theCompactTilesPlacedWeekdayIsBiggerAndItsBandShorter() {
        val edge = 62f
        val sizes = CompactDateTileTypography.sizesFor(edge, edge)

        assertTrue(
            "the placed compact weekday is ${sizes.weekdaySize}sp; it was 6.93sp, so anything at or " +
                "below 7sp is the defect returning",
            sizes.weekdaySize.value > 7f,
        )
        val band = WeekdayBandFit.bandHeightDp(sizes.weekdaySize, SYSTEM_WEEKDAY_INK_EM)
        val oldBand = 6.93f * TEXT_LINE_RATIO + WidgetBand.TOP_PADDING_DP + WidgetBand.BOTTOM_PADDING_DP
        assertTrue(
            "the band must be shorter than the ${oldBand}dp the line box reserved, not taller: got " +
                "${band}dp",
            band < oldBand,
        )
        assertTrue(
            "and it must still fit the 62dp square with the three body lines",
            sizes.requestedHeightDp() <= edge + 0.01f,
        )
    }

    @Test
    fun theInkReserveFollowsTheFaceRatherThanTheLineBox() {
        assertEquals(
            "the system face's ink is the system reserve",
            SYSTEM_WEEKDAY_INK_EM.toDouble(),
            WeekdayBandFit.inkEmFor(TEXT_LINE_RATIO).toDouble(),
            0.0001,
        )
        assertEquals(
            "Nastaliq's ink band is the Nastaliq reserve, whichever of its two ratios arrives",
            NASTALIQ_INK_BAND_RATIO.toDouble(),
            WeekdayBandFit.inkEmFor(NASTALIQ_INK_BAND_RATIO).toDouble(),
            0.0001,
        )
        assertEquals(
            "and the same for the declared box, which is what the fitted typographies thread",
            NASTALIQ_INK_BAND_RATIO.toDouble(),
            WeekdayBandFit.inkEmFor(NASTALIQ_LINE_RATIO).toDouble(),
            0.0001,
        )
        // The whole reason the band can be ink-tall: the box is the wrong number for a single word.
        assertTrue(
            "the ink reserve must be strictly smaller than the box it replaces, or nothing was gained",
            WeekdayBandFit.inkEmFor(TEXT_LINE_RATIO) < TEXT_LINE_RATIO,
        )
    }

    @Test
    fun theBandHeightIsItsInkPlusItsPaddings() {
        val size = 12.sp
        val inkEm = WeekdayBandFit.inkEmFor(TEXT_LINE_RATIO)
        assertEquals(
            "the reserve is the ink plus the two paddings and nothing else",
            (size.value * inkEm + WidgetBand.TOP_PADDING_DP + WidgetBand.BOTTOM_PADDING_DP).toDouble(),
            WeekdayBandFit.bandHeightDp(size, inkEm).toDouble(),
            0.0001,
        )
        assertEquals(
            "and the ink is a plain multiple of the size",
            (size.value * inkEm).toDouble(),
            WeekdayBandFit.inkHeightDp(size, inkEm).toDouble(),
            0.0001,
        )
    }

    @Test
    fun theSizeNeverExceedsItsCeilingAndNeverVanishes() {
        assertEquals(
            "a figure large enough to push the share past the ceiling is clamped to it",
            WeekdayBandFit.MAX_SIZE.value.toDouble(),
            WeekdayBandFit.sizeFor(400.sp).value.toDouble(),
            0.01,
        )
        assertTrue(
            "a zero figure yields a zero size rather than a negative one",
            WeekdayBandFit.sizeFor(0.sp).value >= 0f,
        )
        assertEquals(
            "and the share itself is the family's half",
            0.5f,
            WeekdayBandFit.SHARE_OF_FIGURE,
            0.0001f,
        )
    }
}
