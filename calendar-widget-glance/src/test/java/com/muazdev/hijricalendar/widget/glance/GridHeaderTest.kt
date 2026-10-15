package com.muazdev.hijricalendar.widget.glance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The grid header's two size tiers, from [GridHeaderTypography].
 *
 * The header is one `Row`: two nav arrows, the centred two-line title, then the today and refresh
 * icons. The arrows and icons are fixed-size and the title takes what is left, so on a phone-width
 * widget the chrome has to shrink *and* the title has to shrink, or the month name is ellipsized — the
 * exact failure the redesign's two-line header exists to avoid.
 *
 * The tier is a function of the widget's own width rather than a constant at the call site because a
 * `@Composable` cannot be invoked from a JVM unit test (the same argument [GridCellWeightsTest] makes).
 * These pin the thresholds and the sizes, so a future edit that quietly widens the "narrow" band or
 * re-inflates a narrow size fails here rather than on a device.
 */
class GridHeaderTest {

    /**
     * The common grid size is in the narrow tier.
     *
     * `HijriCalendarWidget.sizeMode`'s large tier is `260×280`, which is the size nearly every placed
     * grid widget is granted, so the narrow treatment is the *default* one and the wide tier is the
     * tablet exception. If the threshold moved above 260 the shipped phone experience would silently
     * regress to the full-size chrome.
     */
    @Test
    fun theCommonGridSizeIsNarrow() {
        assertTrue(
            "a 260dp grid widget (the large tier) must be in the narrow tier",
            GridHeaderTypography.isNarrow(260f),
        )
        assertTrue(
            "the medium tier (200dp) must be narrow too",
            GridHeaderTypography.isNarrow(200f),
        )
    }

    /** The threshold itself: at or above it is wide, below it is narrow. */
    @Test
    fun theThresholdIsInclusiveAtTheWideEnd() {
        val threshold = GridHeaderTypography.NARROW_WIDTH_DP
        assertFalse(
            "a widget exactly at the threshold should use the full-size chrome",
            GridHeaderTypography.isNarrow(threshold),
        )
        assertTrue(
            "a hair below the threshold should be narrow",
            GridHeaderTypography.isNarrow(threshold - 0.1f),
        )
    }

    /**
     * Every used size is larger in the wide tier.
     *
     * Stated as a relationship rather than four separate equalities so a size that was made *smaller*
     * when wide — a copy/paste of the narrow value — cannot pass.
     */
    @Test
    fun theWideTierIsNeverSmallerThanTheNarrowOne() {
        val narrow = GridHeaderTypography.NARROW_WIDTH_DP - 1f
        val wide = GridHeaderTypography.NARROW_WIDTH_DP + 1f

        assertTrue(
            "the nav arrow must grow when wide",
            GridHeaderTypography.arrowIconDp(wide) > GridHeaderTypography.arrowIconDp(narrow),
        )
        assertTrue(
            "the action icons must grow when wide",
            GridHeaderTypography.actionIconDp(wide) > GridHeaderTypography.actionIconDp(narrow),
        )
        assertTrue(
            "the Hijri title must grow when wide",
            GridHeaderTypography.hijriTitleSp(wide) > GridHeaderTypography.hijriTitleSp(narrow),
        )
        assertTrue(
            "the Gregorian title must grow when wide",
            GridHeaderTypography.gregorianTitleSp(wide) > GridHeaderTypography.gregorianTitleSp(narrow),
        )
    }

    /**
     * The wide nav arrow keeps the recommended 48dp touch target.
     *
     * `NavigationArrow` pads the icon by 4dp on each side, so a 40dp icon is 48dp of target. Losing the
     * wide tier's full size would put the arrows below the minimum tap target a launcher expects, which
     * is a real accessibility regression rather than a cosmetic one.
     */
    @Test
    fun theWideNavArrowKeepsTheTouchTarget() {
        assertEquals(
            "the wide nav arrow icon is 40dp, which with the 4dp padding on each side is the 48dp target",
            40f,
            GridHeaderTypography.arrowIconDp(GridHeaderTypography.NARROW_WIDTH_DP + 1f),
        )
    }

    /**
     * The narrow tier shrinks every dimension rather than only the title.
     *
     * Shrinking the title alone is the tempting half-measure and it does not work: the four fixed
     * controls are the *majority* of the row's width, so the title's share only genuinely widens once
     * the chrome comes down with it.
     */
    @Test
    fun theNarrowTierShrinksTheChromeAsWellAsTheTitle() {
        val narrow = GridHeaderTypography.NARROW_WIDTH_DP - 1f
        assertEquals(32f, GridHeaderTypography.arrowIconDp(narrow))
        assertEquals(20f, GridHeaderTypography.actionIconDp(narrow))
        assertEquals(13f, GridHeaderTypography.hijriTitleSp(narrow))
        assertEquals(10f, GridHeaderTypography.gregorianTitleSp(narrow))
    }

    /** The Hijri title line is always the larger of the two, so the hierarchy survives both tiers. */
    @Test
    fun theHijriTitleOutranksTheGregorianOneInBothTiers() {
        for (width in listOf(GridHeaderTypography.NARROW_WIDTH_DP - 1f, GridHeaderTypography.NARROW_WIDTH_DP)) {
            assertTrue(
                "at ${width}dp the Hijri line must be larger than the Gregorian one",
                GridHeaderTypography.hijriTitleSp(width) > GridHeaderTypography.gregorianTitleSp(width),
            )
        }
    }

    /**
     * The weekday row sits *between* the two title lines in size, in both tiers.
     *
     * This is the round-2 fix for "the day names are not bold": the weight was already `Bold`, and on
     * Android it cannot be raised further because Glance's `Medium` and `Bold` resolve to the same
     * style, so the row had to move on size and tone instead. Two failures are possible and both are
     * caught here — dropping back to (or below) the Gregorian title's 10sp-12sp, which is what made
     * the row the faintest thing in the header, and overshooting past the Hijri month title, which
     * would put a row of seven labels above the header's hero.
     *
     * A relationship rather than two equalities, so the tiers stay free to move together.
     */
    @Test
    fun theWeekdayRowSitsBetweenTheTwoTitleLines() {
        for (width in listOf(GridHeaderTypography.NARROW_WIDTH_DP - 1f, GridHeaderTypography.NARROW_WIDTH_DP)) {
            val weekday = GridHeaderTypography.weekdaySp(width)
            assertTrue(
                "at ${width}dp the weekday row (${weekday}sp) must not be the fainter line of the " +
                    "header (Gregorian at ${GridHeaderTypography.gregorianTitleSp(width)}sp)",
                weekday >= GridHeaderTypography.gregorianTitleSp(width),
            )
            assertTrue(
                "at ${width}dp the weekday row must stay below the month title",
                weekday < GridHeaderTypography.hijriTitleSp(width),
            )
        }
    }
}
