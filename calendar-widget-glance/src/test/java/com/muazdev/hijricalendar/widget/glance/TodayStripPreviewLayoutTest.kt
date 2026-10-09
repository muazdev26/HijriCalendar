package com.muazdev.hijricalendar.widget.glance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToInt

/**
 * The "today" strip's weekday line and hairline divider: the vertical budget they add, and the three
 * hand-maintained places that have to agree with them.
 *
 * **Why this needs a gate.** The weekday line turned a two-line widget into a three-line one, and a
 * three-line widget does not fit the 40dp minimum its widget-info declared — so the declared minimum
 * had to rise, in **six** XMLs (three qualifiers in the library, three app-side overrides in the
 * sample) that nothing checks. A widget that clips its own caption is a silent, plausible-looking
 * failure: the widget still renders, it just stops saying what it is.
 *
 * Two things are asserted, both from constants rather than literals so they cannot drift from the
 * composition:
 *
 * 1. **The budget.** The declared minimum must actually hold three lines at the sizes
 *    [TodayStripTypography] asks for, at the line-box ratio Glance gives each `Text`.
 * 2. **The mirror and the infos.** The Android 12-14 `previewLayout` must carry the same three lines at
 *    the same sizes, and every qualifier of the widget-info must declare the same minimum.
 *
 * The rendering half is asserted this way rather than by composing it because Glance composition has
 * no JVM host in this module — the same reason `DateTilePreviewLayoutTest` parses the tile previews.
 *
 * **Note on argument order.** JUnit's `assertTrue`/`assertEquals` take the message *first*. Several
 * other tests in this repo use `kotlin.test`, whose order is the opposite.
 */
class TodayStripPreviewLayoutTest {

    private fun resFile(path: String): File {
        val file = File("src/main/res/$path")
        assertTrue(
            "resource not found at ${file.absolutePath}; if the res directory moved, fix this path " +
                "rather than deleting this test",
            file.isFile,
        )
        return file
    }

    private fun parse(path: String): Element {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
        return factory.newDocumentBuilder().parse(resFile(path)).documentElement
    }

    private val stripPreview: Element get() = parse("layout/hijri_today_widget_preview_layout.xml")

    private fun Element.children(): List<Element> = buildList {
        val nodes = childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) add(node as Element)
        }
    }

    private fun Element.attr(name: String): String? = getAttribute(name).takeIf { it.isNotEmpty() }

    /** This element *and* its descendants. */
    private fun Element.deep(): List<Element> = buildList {
        add(this@deep)
        children().forEach { addAll(it.deep()) }
    }

    private fun Element.textSizesSp(): List<Int> = deep()
        .filter { it.tagName == "TextView" }
        .map { element ->
            val size = element.attr(TEXT_SIZE)
            assertTrue("a preview TextView has no $TEXT_SIZE", size != null)
            size!!.removeSuffix("sp").toInt()
        }

    /** The day figure at a given granted height, in whole sp. Int because JUnit's equals is not for Floats. */
    private fun daySpAt(height: Float): Int =
        TodayStripTypography.daySizeFor(height).value.toInt()

    /**
     * The preview carries the live strip's five lines at the live sizes, in the live order: the
     * centred weekday first, then each half's day figure over its caption.
     *
     * The day figures are rendered at [TodayStripTypography.DAY_HEIGHT_REFERENCE_DP] — the declared
     * minimum — because the live figures are *derived* from the granted height and a static XML
     * snapshot has only one height to render. This is the same concession
     * `DateTilePreviewLayoutTest` makes for the tiles' width-scaled weekday, and it is why the
     * scaling rule itself is tested separately below rather than here.
     */
    @Test
    fun theStripPreviewCarriesTheLiveLinesAtTheLiveSizes() {
        assertEquals(
            "the strip preview must carry a weekday, then a day figure and caption per half",
            listOf(
                TodayStripTypography.weekdaySize.value.toInt(),
                daySpAt(TodayStripTypography.DAY_HEIGHT_REFERENCE_DP),
                TodayStripTypography.captionSize.value.toInt(),
                daySpAt(TodayStripTypography.DAY_HEIGHT_REFERENCE_DP),
                TodayStripTypography.captionSize.value.toInt(),
            ),
            stripPreview.textSizesSp(),
        )
    }

    /**
     * The day figure grows with the granted height, and never shrinks.
     *
     * The whole point of deriving it: the strip is vertically resizable, so the height is the one
     * dimension the host genuinely varies. A rule that was not monotonic would render a *smaller* figure
     * on a *taller* widget at some height — which is both wrong and exactly the kind of thing a single
     * visual check never catches. Swept, not sampled, for the same reason the tiles' rule is.
     */
    @Test
    fun theDayFigureNeverShrinksAsTheStripGetsTaller() {
        var previous = 0
        for (height in 1..400) {
            val size = daySpAt(height.toFloat())
            assertTrue(
                "at ${height}dp tall the day figure dropped to ${size}sp from ${previous}sp",
                size >= previous,
            )
            previous = size
        }
    }

    /**
     * It is clamped at both ends.
     *
     * The ceiling is "a strip should still read as a strip". The floor is the one that matters: it sits
     * *below* what the declared minimum can hold, so a launcher that grants less than the minimum —
     * `minResizeHeight` allows 4dp less — still cannot shrink the figure into a clipped month line.
     */
    @Test
    fun theDayFigureIsClampedToItsFloorAndCeiling() {
        val floor = TodayStripTypography.daySizeFloor.value.toInt()
        val ceiling = TodayStripTypography.daySizeCeiling.value.toInt()
        for (height in 1..400) {
            val size = daySpAt(height.toFloat())
            assertTrue("at ${height}dp tall the day figure fell below its floor", size >= floor)
            assertTrue("at ${height}dp tall the day figure passed its ceiling", size <= ceiling)
        }
    }

    /**
     * The figures actually take the height: a tall strip renders a visibly bigger number than the
     * declared minimum does.
     *
     * Without this, a rule that returned a constant above the floor would pass both clamping tests and
     * the monotonicity sweep while failing the entire request — which is the failure mode a clamped
     * function always has.
     */
    @Test
    fun aTallerStripGetsABiggerDayFigure() {
        val atMinimum = daySpAt(TodayStripTypography.DAY_HEIGHT_REFERENCE_DP)
        val tall = daySpAt(200f)

        assertTrue(
            "a 200dp strip must render a larger day figure than one at the " +
                "${TodayStripTypography.DAY_HEIGHT_REFERENCE_DP}dp declared minimum; got $tall against " +
                "$atMinimum",
            tall > atMinimum,
        )
    }

    /**
     * The weekday line is styled as part of the date, not as a caption under it.
     *
     * Same reasoning, and the same styling, as the 1x1 tiles' weekday line (FD-01): drawn in the muted
     * caption tone it reads as small print above two numbers rather than as the answer to "what day is
     * it?", which is the whole reason the line was added.
     */
    @Test
    fun theWeekdayLineIsBoldAndInThePrimaryTextColour() {
        val weekday = stripPreview.children().first { it.tagName == "TextView" }

        assertEquals(
            "the strip's weekday line must be bold, like the tiles'",
            "bold",
            weekday.attr("android:textStyle"),
        )
        assertEquals(
            "the strip's weekday line must use the primary text colour, not the muted caption one",
            "@color/widget_text_primary",
            weekday.attr("android:textColor"),
        )
    }

    /**
     * It is one centred line above both halves, not one per side.
     *
     * Both halves are the same physical day, so a second copy says nothing the first did not — and the
     * count is the cheapest way to keep it from creeping back in, which is what a reviewer reading only
     * the preview XML could not tell.
     */
    @Test
    fun theWeekdayIsOneLineAboveBothHalves() {
        val root = stripPreview
        assertEquals(
            "the weekday must be a direct child of the strip's root, i.e. above the whole row",
            "TextView",
            root.children().first().tagName,
        )
        assertEquals(
            "the strip's root must stack the weekday above the row of two halves",
            "vertical",
            root.attr("android:orientation"),
        )
    }

    /**
     * The content is centred vertically, not pinned to the top.
     *
     * The live root `Column` is `Alignment.Vertical.CenterVertically`; `gravity="center_vertical"` is
     * the framework's name for the same thing, which is why the hand-maintained mirror can express it.
     *
     * **Why it is not cosmetic.** A top-pinned column renders its content first and dumps every dp of
     * slack in one place — under the day figures, as the dead leading inside their line boxes, because
     * Glance boxes each `Text` at ascent + descent and that gap is neither padding nor removable
     * (`PaddingModifier` is non-negative; Glance 1.2's `TextStyle` has no `lineHeight`). On a strip
     * granted more height than its type needs, that reads as the numbers hanging in space with the
     * caption crammed underneath. Centring splits the slack instead.
     */
    @Test
    fun theContentIsCentredVerticallyRatherThanPinnedToTheTop() {
        assertEquals(
            "the strip's root must centre its content vertically; see " +
                "TodayStripTypography.VERTICAL_PADDING_DP",
            "center_vertical",
            stripPreview.attr("android:gravity"),
        )
    }

    /**
     * The declared minimum height holds all three lines, at the figure size that height affords.
     *
     * The assertion that matters. It is the one that fails if a fourth line is ever added, or a
     * non-figure size is raised, without the widget-info being raised with it — which is precisely how
     * this widget ended up declaring a height its own content did not fit.
     *
     * The day figure is part of [requestedHeightDp] through [daySpAt] rather than through a constant,
     * because it is derived from the height in the first place: sizing it independently here would
     * assert the arithmetic against a number the live render does not use.
     */
    @Test
    fun theThreeLinesFitInsideTheDeclaredMinimumHeight() {
        val requestedDp = requestedHeightDp()

        assertTrue(
            "the strip's three lines ask for ~${requestedDp}dp but its widget-info declares a " +
                "${TodayStripTypography.STRIP_MIN_HEIGHT_DP}dp minimum, so the caption is clipped. " +
                "Raise the minimum, or take a line off",
            requestedDp <= TodayStripTypography.STRIP_MIN_HEIGHT_DP,
        )
    }

    /**
     * …and the resize floor is not *above* it, which would make the widget unresizable in practice.
     *
     * Asserted as an ordering rather than a fit: the floor exists so a launcher that does grant the
     * minimum still has somewhere to shrink to, and it is allowed to clip a little there.
     */
    @Test
    fun theResizeFloorSitsBelowTheMinimumButAtTheLinesOwnHeight() {
        val requestedDp = requestedHeightDp()

        assertTrue(
            "minResizeHeight (${TodayStripTypography.STRIP_MIN_RESIZE_HEIGHT_DP}dp) must be at or " +
                "below minHeight (${TodayStripTypography.STRIP_MIN_HEIGHT_DP}dp), or the widget " +
                "cannot be resized at all",
            TodayStripTypography.STRIP_MIN_RESIZE_HEIGHT_DP <= TodayStripTypography.STRIP_MIN_HEIGHT_DP,
        )
        assertTrue(
            "minResizeHeight (${TodayStripTypography.STRIP_MIN_RESIZE_HEIGHT_DP}dp) is above the " +
                "lines' own ~${requestedDp}dp, so shrinking to it would clip for no reason",
            TodayStripTypography.STRIP_MIN_RESIZE_HEIGHT_DP <= requestedDp,
        )
    }

    /**
     * Every qualifier of the widget-info declares the same minimum.
     *
     * Three files, edited by hand, with the app-side overrides in the sample app a fourth through
     * sixth — and the sample's are outside this module's `src/main/res`, so nothing here can see them.
     * These three are asserted because they are the ones this change edited.
     */
    @Test
    fun everyWidgetInfoQualifierDeclaresTheSameMinimum() {
        listOf("xml", "xml-v28", "xml-v31").forEach { qualifier ->
            val info = parse("$qualifier/hijri_today_widget_info.xml")

            assertEquals(
                "$qualifier/hijri_today_widget_info.xml minHeight must match " +
                    "TodayStripTypography.STRIP_MIN_HEIGHT_DP",
                "${TodayStripTypography.STRIP_MIN_HEIGHT_DP}dp",
                info.attr("android:minHeight"),
            )
            assertEquals(
                "$qualifier/hijri_today_widget_info.xml minResizeHeight must match " +
                    "TodayStripTypography.STRIP_MIN_RESIZE_HEIGHT_DP",
                "${TodayStripTypography.STRIP_MIN_RESIZE_HEIGHT_DP}dp",
                info.attr("android:minResizeHeight"),
            )
        }
    }

    /**
     * The height the three lines ask for, in whole dp: each line's box at the font's own ascent plus
     * descent, plus the gaps between them and the strip's vertical padding.
     *
     * The day figure is read back from [daySpAt] at the declared minimum, which is the size the live
     * render itself derives there.
     */
    private fun requestedHeightDp(): Int {
        val lineSp = TodayStripTypography.weekdaySize.value +
            daySpAt(TodayStripTypography.DAY_HEIGHT_REFERENCE_DP) +
            TodayStripTypography.captionSize.value
        // Rounded, because `LINE_HEIGHT_RATIO` is a `Float` and the product is not a whole number.
        return (lineSp * TodayStripTypography.LINE_HEIGHT_RATIO).roundToInt() +
            TodayStripTypography.lineGap.value.toInt() * 2 +
            TodayStripTypography.VERTICAL_PADDING_DP * 2
    }

    private companion object {
        /** The preview XML attribute every text size lives in. */
        const val TEXT_SIZE = "android:textSize"
    }
}
