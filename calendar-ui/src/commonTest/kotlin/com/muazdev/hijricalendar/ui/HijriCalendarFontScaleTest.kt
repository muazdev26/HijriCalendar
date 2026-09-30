package com.muazdev.hijricalendar.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import com.muazdev.hijricalendar.core.HijriCalendarState
import com.muazdev.hijricalendar.core.WeekDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Coverage for [UI-04-accessible-text-scaling](UI-04-accessible-text-scaling.md).
 *
 * The calendar used to wrap its whole subtree in `Density(fontScale = 1f)`, so a user who had raised
 * Android's font size to 200% got a calendar at exactly 100%. That is a WCAG 1.4.4 failure, it was
 * invisible, and `README.md` presented it as a feature.
 *
 * The fix separates two problems the original conflated. The **cell must not grow**, because a month
 * grid is a fixed-pitch matrix — that was a real bug, fixed in `337f631` by decoupling the cell from
 * the font scale. The **text should scale**, up to a cap chosen so it still fits the fixed cell.
 *
 * These tests assert both halves of that, because either alone would be satisfied by the old code:
 * asserting only "the cell did not grow" passes today, and asserting only "the text grew" is the
 * behaviour that was missing.
 */
@OptIn(ExperimentalTestApi::class)
class HijriCalendarFontScaleTest {

    private val dayText = "15"
    private val cellDescription = "Day 15"

    private fun stateFor() =
        HijriCalendarState(
            initialMonth = HijrahYearMonth(1447, 9),
            firstDayOfWeek = WeekDay.SATURDAY,
        )

    /** Renders the calendar as if the system font scale were [fontScale]. */
    private fun ComposeUiTest.hostAtFontScale(
        fontScale: Float,
        ignoreFontScale: Boolean = false,
    ) {
        val state = stateFor()
        setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(density = 1f, fontScale = fontScale),
            ) {
                MaterialTheme {
                    Surface {
                        HijriCalendar(
                            state = state,
                            dateDisplayMode = DateDisplayMode.HIJRI_ONLY,
                            ignoreFontScale = ignoreFontScale,
                            onDayClick = {},
                        )
                    }
                }
            }
        }
        waitForIdle()
    }

    /**
     * The rendered height of the day figure itself.
     *
     * Uses the **unmerged** tree deliberately. `HijriCalendarDayCell` sets
     * `mergeDescendants = true`, so the merged node is the 48dp cell and its `Text` descendants are
     * hidden — querying the merged tree measures the box, which is constant by design, and the
     * first version of this test failed for exactly that reason with a reassuringly specific
     * "48px at 1.0, 48px at 2.0".
     */
    private fun ComposeUiTest.dayTextHeight(): Int =
        onNodeWithText(dayText, useUnmergedTree = true).fetchSemanticsNode().size.height

    private fun ComposeUiTest.cellSize(): androidx.compose.ui.unit.IntSize =
        onNodeWithContentDescription(cellDescription).fetchSemanticsNode().size

    // ── the text scales ───────────────────────────────────────────────

    @Test
    fun dayTextGrowsWithTheSystemFontScale() = runComposeUiTest {
        hostAtFontScale(fontScale = 1f)
        val atOne = dayTextHeight()

        hostAtFontScale(fontScale = 2f)
        val atTwo = dayTextHeight()

        assertTrue(
            atTwo > atOne,
            "day text did not grow with the font scale (${atOne}px at 1.0, ${atTwo}px at 2.0); " +
                "the calendar is still forcing fontScale to 1",
        )
    }

    @Test
    fun dayTextIsStillRenderedNotClippedAway() = runComposeUiTest {
        hostAtFontScale(fontScale = 2f)
        // Present and non-zero. maxLines = 1 clips rather than truncating, so this asserts the text
        // exists at all rather than that it fits perfectly.
        assertTrue(dayTextHeight() > 0, "day text has no height at 2x")
        onNodeWithText(dayText, useUnmergedTree = true).assertExists()
    }

    // ── the geometry does not ─────────────────────────────────────────

    @Test
    fun cellGeometryIsIdenticalAtEveryFontScale() = runComposeUiTest {
        hostAtFontScale(fontScale = 1f)
        val atOne = cellSize()

        hostAtFontScale(fontScale = 2f)
        val atTwo = cellSize()

        assertEquals(atOne, atTwo, "the cell grew with the font scale; a month grid must be a fixed pitch")
    }

    /**
     * The whole point of decoupling: the text grows *inside* a box that does not. Together with the
     * two tests above this is the invariant — text scales, geometry does not — and it is the
     * property the original code got wrong in both directions.
     */
    @Test
    fun textScalesWithinAnUnchangingCell() = runComposeUiTest {
        hostAtFontScale(fontScale = 1f)
        val cellAtOne = cellSize()
        val textAtOne = dayTextHeight()

        hostAtFontScale(fontScale = 2f)
        val cellAtTwo = cellSize()
        val textAtTwo = dayTextHeight()

        assertEquals(cellAtOne, cellAtTwo, "cell geometry changed")
        assertTrue(textAtTwo > textAtOne, "text did not scale")
    }

    // ── the cap ───────────────────────────────────────────────────────

    @Test
    fun theCapLimitsScalingRatherThanRemovingIt() = runComposeUiTest {
        hostAtFontScale(fontScale = 1.5f)
        val atOneAndAHalf = dayTextHeight()
        hostAtFontScale(fontScale = 2f)
        val atTwo = dayTextHeight()
        hostAtFontScale(fontScale = 4f)
        val atFour = dayTextHeight()

        assertTrue(atOneAndAHalf > 0 && atTwo >= atOneAndAHalf)
        assertEquals(
            atTwo,
            atFour,
            "past the cap the text should stop growing; at 4x it is still changing, so either the " +
                "cap is not applied or the text overflows the fixed cell",
        )
    }

    @Test
    fun bothModeCapsLowerThanSingleLine() {
        // The two-line cell fits less text in the same box, so it gets a lower cap. Stated as a fact
        // about the policy rather than measured through the pager.
        assertEquals(1.5f, HijriCalendarDefaults.BothModeMaxFontScale)
        assertEquals(2.0f, HijriCalendarDefaults.SingleLineMaxFontScale)
        assertTrue(
            HijriCalendarDefaults.BothModeMaxFontScale <
                HijriCalendarDefaults.SingleLineMaxFontScale,
        )
        assertEquals(
            HijriCalendarDefaults.BothModeMaxFontScale,
            HijriCalendarDefaults.maxFontScaleFor(DateDisplayMode.BOTH),
        )
        assertEquals(
            HijriCalendarDefaults.SingleLineMaxFontScale,
            HijriCalendarDefaults.maxFontScaleFor(DateDisplayMode.HIJRI_ONLY),
        )
        assertEquals(
            HijriCalendarDefaults.SingleLineMaxFontScale,
            HijriCalendarDefaults.maxFontScaleFor(DateDisplayMode.GREGORIAN_ONLY),
        )
    }

    // ── the opt-out ───────────────────────────────────────────────────

    @Test
    fun ignoreFontScaleReproducesTheOldFlatRendering() = runComposeUiTest {
        hostAtFontScale(fontScale = 1f, ignoreFontScale = true)
        val flatAtOne = dayTextHeight()

        hostAtFontScale(fontScale = 2f, ignoreFontScale = true)
        val flatAtTwo = dayTextHeight()

        assertEquals(
            flatAtOne,
            flatAtTwo,
            "ignoreFontScale = true must reproduce the pre-existing flat rendering",
        )
    }

    @Test
    fun theDefaultIsToRespectTheFontScale() = runComposeUiTest {
        hostAtFontScale(fontScale = 1f)
        val atOne = dayTextHeight()
        hostAtFontScale(fontScale = 2f)
        val atTwo = dayTextHeight()

        assertTrue(
            atTwo > atOne,
            "the default must honour the system font scale; ignoring must be opt-in",
        )
    }

    // ── the cap constant is documented where it is defined ────────────

    @Test
    fun everyDisplayModeHasACap() {
        DateDisplayMode.entries.forEach { mode ->
            val cap = HijriCalendarDefaults.maxFontScaleFor(mode)
            assertTrue(
                cap >= 1f,
                "mode $mode has a cap below 1, which would shrink text below its design size",
            )
        }
    }
}
