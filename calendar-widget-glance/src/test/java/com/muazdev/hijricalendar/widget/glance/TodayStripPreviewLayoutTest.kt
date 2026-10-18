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
 * The "today" strip's weekday band, its one-scale typography, and the hand-maintained widget-info
 * XMLs that have to agree with both.
 *
 * **Why this needs a gate.** The weekday line turned a two-line widget into a three-line one, and a
 * three-line widget does not fit the 40dp minimum its widget-info declared — so the declared minimum
 * had to rise, in **six** XMLs (three qualifiers in the library, three app-side overrides in the
 * sample) that nothing checks. A widget that clips its own caption is a silent, plausible-looking
 * failure: the widget still renders, it just stops saying what it is.
 *
 * This round replaced the "the figure grows into whatever the other two lines leave" rule with **one
 * scale for all three lines** ([TodayStripTypography.sizesFor]) and switched `sizeMode` to `Exact`
 * so the composition actually sees the granted height. Both are asserted here, because both are
 * exactly the kind of change that is invisible until it is on a device: a scale that is not monotonic
 * renders a *smaller* figure on a *taller* strip, and a stack that does not fit the granted height
 * clips whichever line is last.
 *
 * Three things are asserted, from constants rather than literals: the three lines at the reference
 * height (the strip's mirror), the scale's monotonicity and bounds, and the fit at the declared
 * minimum.
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

    /** The sizes the live strip renders at the mirror's reference height. */
    private fun previewSizes() =
        TodayStripTypography.sizesFor(TodayStripTypography.DAY_HEIGHT_REFERENCE_DP)

    /**
     * The preview carries the live strip's five lines at the live sizes, in the live order: the
     * centred weekday first, then each half's day figure over its caption.
     *
     * The sizes are rendered at [TodayStripTypography.DAY_HEIGHT_REFERENCE_DP] — the declared
     * minimum, since a static XML has only one height to render and the minimum is the smallest a
     * placed strip can be. This is the same concession `DateTilePreviewLayoutTest` makes for the
     * tiles' width-scaled lines, and it is why the scaling rule itself is tested separately below
     * rather than here.
     */
    @Test
    fun theStripPreviewCarriesTheLiveLinesAtTheLiveSizes() {
        val sizes = previewSizes()
        assertEquals(
            "the strip preview must carry a weekday, then a day figure and caption per half",
            listOf(
                sizes.weekdaySize.value.roundToInt(),
                sizes.daySize.value.roundToInt(),
                sizes.captionSize.value.roundToInt(),
                sizes.daySize.value.roundToInt(),
                sizes.captionSize.value.roundToInt(),
            ),
            stripPreview.textSizesSp(),
        )
    }

    /**
     * The day figure grows with the granted height, and never shrinks.
     *
     * The whole point of deriving it: the strip is vertically resizable, so the height is the one
     * dimension the host genuinely varies. A rule that was not monotonic would render a *smaller*
     * figure on a *taller* widget at some height — which is both wrong and exactly the kind of thing a
     * single visual check never catches. Swept, not sampled, for the same reason the tiles' rule is.
     */
    @Test
    fun theDayFigureNeverShrinksAsTheStripGetsTaller() {
        var previous = 0
        for (height in 1..400) {
            val size = TodayStripTypography.daySizeFor(height.toFloat()).value.roundToInt()
            assertTrue(
                "at ${height}dp tall the day figure dropped to ${size}sp from ${previous}sp",
                size >= previous,
            )
            previous = size
        }
    }

    /**
     * Every line is bounded by the same two guards: a legibility floor and the widget's own ceiling.
     *
     * The floor is [MIN_TYPE_SCALE] rather than a per-line size, because a single scale is the whole
     * design: the figures cannot be at their floor while the caption is not. The ceiling keeps a strip
     * stretched across several launcher cells reading as a strip and not as a clock.
     */
    @Test
    fun everyLineIsBoundedByTheMinScaleAndTheDayCeiling() {
        val minDay = TodayStripTypography.daySize.value * MIN_TYPE_SCALE
        val maxDay = TodayStripTypography.daySizeCeiling.value
        for (height in 1..400) {
            val sizes = TodayStripTypography.sizesFor(height.toFloat())
            assertTrue(
                "at ${height}dp tall the day figure (${sizes.daySize}) fell below the min scale",
                sizes.daySize.value >= minDay - 0.001f,
            )
            assertTrue(
                "at ${height}dp tall the day figure (${sizes.daySize}) passed its ceiling",
                sizes.daySize.value <= maxDay + 0.001f,
            )
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
        val atMinimum =
            TodayStripTypography.daySizeFor(TodayStripTypography.DAY_HEIGHT_REFERENCE_DP).value
        val tall = TodayStripTypography.daySizeFor(200f).value

        assertTrue(
            "a 200dp strip must render a larger day figure than one at the " +
                "${TodayStripTypography.DAY_HEIGHT_REFERENCE_DP}dp declared minimum; got $tall " +
                "against $atMinimum",
            tall > atMinimum,
        )
    }

    /**
     * The weekday line is drawn in the family's accent band, not as bare text.
     *
     * It shipped as plain `widget_text_primary` text while every 1x1 tile put the same weekday on the
     * accent — four widgets rendering one line two ways, which is the divergence this band exists to
     * remove. The colour is `widget_on_today` because the text sits *on* the accent; the fill is
     * `widget_accent` for the same reason the tiles' band is.
     */
    @Test
    fun theWeekdayLineIsInTheAccentBand() {
        val weekday = stripPreview.children().first { it.tagName == "TextView" }

        assertEquals(
            "the strip's weekday line must be bold, like the tiles'",
            "bold",
            weekday.attr("android:textStyle"),
        )
        assertEquals(
            "the strip's weekday must sit on the accent band, like the tiles'",
            "@color/widget_accent",
            weekday.attr("android:background"),
        )
        assertEquals(
            "text drawn on the accent must use the on-accent token",
            "@color/widget_on_today",
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
     * The date row centres in the height the band left, rather than the whole strip being pinned.
     *
     * The live root `Column` stacks a flush band over a weighted `Box` that centres its content, and
     * that centring is what splits the leftover height between the space above the figures and the
     * space below the captions. A top-pinned column instead renders its content first and dumps every
     * dp of slack in one place — under the day figures, as the dead leading inside their line boxes,
     * because Glance boxes each `Text` at ascent + descent and that gap is neither padding nor
     * removable (`PaddingModifier` is non-negative; Glance 1.2's `TextStyle` has no `lineHeight`).
     */
    @Test
    fun theDateRowCentresInTheHeightTheBandLeft() {
        val row = stripPreview.children().first { it.tagName == "LinearLayout" }
        assertEquals(
            "the date row must take the height the band did not",
            "0dp",
            row.attr("android:layout_height"),
        )
        assertEquals(
            "the date row must be the weighted slot under the band",
            "1",
            row.attr("android:layout_weight"),
        )
        assertEquals(
            "the date row must centre its content vertically; see TodayStripTypography.sizesFor",
            "center_vertical",
            row.attr("android:gravity"),
        )
    }

    /**
     * The declared minimum height holds all three lines, at the figure size that height affords.
     *
     * The assertion that matters. It is the one that fails if a fourth line is ever added, or a
     * non-figure size is raised, without the widget-info being raised with it — which is precisely how
     * this widget ended up declaring a height its own content did not fit.
     *
     * The figure is part of the request through [TodayStripTypography.sizesFor] rather than through a
     * constant, because it is derived from the height in the first place: sizing it independently here
     * would assert the arithmetic against a number the live render does not use.
     */
    @Test
    fun theThreeLinesFitInsideTheDeclaredMinimumHeight() {
        val granted = TodayStripTypography.STRIP_MIN_HEIGHT_DP.toFloat()
        val requested = TodayStripTypography.requestedHeightDp(TodayStripTypography.sizesFor(granted))

        assertTrue(
            "the strip's three lines ask for ~${requested}dp but its widget-info declares a " +
                "${TodayStripTypography.STRIP_MIN_HEIGHT_DP}dp minimum, so the caption is clipped. " +
                "Raise the minimum, or take a line off",
            requested <= granted + 0.01f,
        )
    }

    /**
     * …and the resize floor is not *below* the point where the stack still fits, which would let a
     * launcher clip the caption for no reason.
     *
     * Asserted as a fit rather than an ordering: the strip scales to whatever height it is granted, so
     * the floor is safe exactly while the type still fits there.
     */
    @Test
    fun theResizeFloorStillHoldsTheLines() {
        val floor = TodayStripTypography.STRIP_MIN_RESIZE_HEIGHT_DP.toFloat()
        val requested = TodayStripTypography.requestedHeightDp(TodayStripTypography.sizesFor(floor))

        assertTrue(
            "minResizeHeight (${TodayStripTypography.STRIP_MIN_RESIZE_HEIGHT_DP}dp) must be at or " +
                "below minHeight (${TodayStripTypography.STRIP_MIN_HEIGHT_DP}dp), or the widget " +
                "cannot be resized at all",
            TodayStripTypography.STRIP_MIN_RESIZE_HEIGHT_DP <= TodayStripTypography.STRIP_MIN_HEIGHT_DP,
        )
        assertTrue(
            "minResizeHeight (${TodayStripTypography.STRIP_MIN_RESIZE_HEIGHT_DP}dp) is below the " +
                "lines' own ~${requested}dp request, so shrinking to it would clip for no reason",
            requested <= floor + 0.01f,
        )
    }

    /**
     * …and the same two fits with the **bundled Nastaliq** face drawing the script lines.
     *
     * Nastaliq's box is 2.5 em against the system fallback's 1.7 ([NASTALIQ_LINE_RATIO]). The strip is
     * the widget where that matters most: its scale comes from the granted height alone, so a stack
     * measured at the wrong ratio is the whole stack shrunk in the wrong proportion, and at the
     * declared 72dp minimum the difference is the difference between fitting and losing the caption.
     *
     * This is also the price of the feature, stated: the strip's type is *smaller* with Nastaliq at the
     * same height, because the box it needs is taller.
     */
    @Test
    fun theBundledNastaliqStackFitsTheDeclaredHeightsToo() {
        for (granted in listOf(
            TodayStripTypography.STRIP_MIN_RESIZE_HEIGHT_DP.toFloat(),
            TodayStripTypography.STRIP_MIN_HEIGHT_DP.toFloat(),
            96f,
            140f,
        )) {
            val sizes = TodayStripTypography.sizesFor(granted, NASTALIQ_LINE_RATIO)
            val requested = TodayStripTypography.requestedHeightDp(sizes, NASTALIQ_LINE_RATIO)
            assertTrue(
                "at ${granted}dp the Nastaliq stack asks for ${requested}dp, which does not fit",
                requested <= granted + 0.01f,
            )
        }

        val height = TodayStripTypography.STRIP_MIN_HEIGHT_DP.toFloat()
        val system = TodayStripTypography.sizesFor(height, TEXT_LINE_RATIO)
        val nastaliq = TodayStripTypography.sizesFor(height, NASTALIQ_LINE_RATIO)
        assertTrue(
            "Nastaliq's taller box must shrink the strip's type at a fixed height; got " +
                "${nastaliq.daySize} against ${system.daySize}",
            nastaliq.daySize.value < system.daySize.value,
        )
    }

    /**
     * Every qualifier of the widget-info declares the same minimum.
     *
     * Three files, edited by hand, with the app-side overrides in the sample app a fourth through
     * sixth — and the sample's are outside this module's `src/main/res`, so nothing here can see them.
     * These three are asserted because they are the ones this module owns.
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

    private companion object {
        /** The preview XML attribute every text size lives in. */
        const val TEXT_SIZE = "android:textSize"
    }
}
