package com.muazdev.hijricalendar.widget.glance

import androidx.glance.text.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
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
            "the title must grow when wide",
            GridHeaderTypography.titleSp(wide) > GridHeaderTypography.titleSp(narrow),
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
        assertEquals(13f, GridHeaderTypography.titleSp(narrow))
    }

    /**
     * Both title lines share one size, one weight and one colour (round 3).
     *
     * The header used to carry a hierarchy — the Gregorian line a step down in size *and* in tone, at
     * `secondaryText` Medium — and on a device that read as a caption under a title rather than as the
     * same month named in the other calendar, which is what the feedback reported. There is now no
     * second size for the renderer to reach for at all ([titleSp] is the only one), so what this pins
     * is the *absence* of one: the two seams `MonthTitle`'s two `Text` calls share are
     * [GridHeaderTypography.TITLE_WEIGHT] and [titleColor], and both are held here. A future edit that
     * gives the Gregorian line a tone or weight of its own has to reintroduce a second seam to do it,
     * and this test is the reason that shows up in CI rather than on a home screen.
     */
    @Test
    fun bothTitleLinesShareOneSizeOneWeightAndOneColour() {
        assertEquals(
            "both title lines are Bold — Glance's `FontWeight` has nothing heavier to reach for",
            FontWeight.Bold,
            GridHeaderTypography.TITLE_WEIGHT,
        )
        assertSame(
            "both title lines take the palette's primary text; `secondaryText` is what made the " +
                "Gregorian line read as a caption beneath the Hijri one",
            WidgetColors.DEFAULT.primaryText,
            titleColor(WidgetColors.DEFAULT),
        )
        assertNotSame(
            "the title's colour must not be the muted one",
            WidgetColors.DEFAULT.secondaryText,
            titleColor(WidgetColors.DEFAULT),
        )
    }

    /**
     * The weekday row stays *below* the month title in size, in both tiers.
     *
     * This is the round-2 fix for "the day names are not bold": the weight was already `Bold`, and on
     * Android it cannot be raised further because Glance's `Medium` and `Bold` resolve to the same
     * style, so the row had to move on size and tone instead. The failure this catches is overshooting
     * past the month title, which would put a row of seven labels above the header's hero — the lower
     * bound it used to be held to was the Gregorian line's size, and round 3 removed that line's own
     * size by making it the title's.
     *
     * A relationship rather than an equality, so the tiers stay free to move together.
     */
    @Test
    fun theWeekdayRowStaysBelowTheMonthTitle() {
        for (width in listOf(GridHeaderTypography.NARROW_WIDTH_DP - 1f, GridHeaderTypography.NARROW_WIDTH_DP)) {
            val weekday = GridHeaderTypography.weekdaySp(width)
            assertTrue(
                "at ${width}dp the weekday row (${weekday}sp) must stay below the month title " +
                    "(${GridHeaderTypography.titleSp(width)}sp)",
                weekday < GridHeaderTypography.titleSp(width),
            )
        }
    }
}
