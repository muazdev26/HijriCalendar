package com.muazdev.hijricalendar.widget.glance

import com.muazdev.hijricalendar.widgetdata.NumeralStyle
import com.muazdev.hijricalendar.widgetdata.WidgetLanguage
import com.muazdev.hijricalendar.widgetdata.createWidgetOptions
import com.muazdev.hijricalendar.widgetdata.todayHijriWidgetData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToInt

/**
 * FD-01: the 1x1 tiles must name the day they are showing, the day name must be sized to the cell
 * the launcher granted, and the hand-maintained Android 12-14 `previewLayout` mirror must keep
 * saying the same thing.
 *
 * `TodayHijriWidgetData.weekdayName` has existed since the projection was written and iOS has always
 * rendered it. Every Android widget dropped it — a field that was projected, consumed on one
 * platform, and silently ignored on the other. The rendering half is asserted here by asserting the
 * *data* is present and localized, because the composable itself is not unit-testable in this module
 * (Glance composition has no JVM host); the layout half is asserted by parsing the XML.
 *
 * Three-line order — weekday, day figure, month — is asserted rather than the sizes alone, because a
 * two-line variant with the weekday merged into a caption beside the month name looks plausible and
 * loses the point of the change.
 *
 * **The tiles now size from one scale over both granted axes** ([DateTileTypography.sizesFor]), and
 * the weekday sits in the family's shared accent band. Both are reflected here: the weekday's colour
 * and fill are asserted as the band's, and the fit tests compute the request from the same
 * per-line ratios the typography does rather than re-deriving it from a single `LINE_HEIGHT_RATIO`.
 *
 * **Note on argument order.** This file uses JUnit, whose `assertTrue`/`assertEquals` take the
 * message *first*. Several other tests in this repo use `kotlin.test`, whose order is the opposite.
 * Mixing them in one file is the single most common compile error in this suite; that is why the
 * [JUnitMessageOrderTest] convention is worth stating rather than discovering.
 */
class DateTilePreviewLayoutTest {

    /** Both 1x1 tiles, whose previews are hand-maintained mirrors of [DateTileRoot]. */
    private val tilePreviews =
        listOf("hijri_date_widget_preview_layout", "gregorian_date_widget_preview_layout")

    private fun layout(name: String): File {
        val file = File("src/main/res/layout/$name.xml")
        assertTrue(
            "preview layout not found at ${file.absolutePath}; if the res directory moved, fix " +
                "this path rather than deleting this test",
            file.isFile,
        )
        return file
    }

    private fun root(name: String): Element {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
        return factory.newDocumentBuilder().parse(layout(name)).documentElement
            .also { assertEquals("root of $name", "LinearLayout", it.tagName) }
    }

    private fun Element.children(): List<Element> = buildList {
        val nodes = childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) add(node as Element)
        }
    }

    /** This element *and* its descendants, in document order. */
    private fun Element.deep(): List<Element> = buildList {
        add(this@deep)
        children().forEach { addAll(it.deep()) }
    }

    private fun Element.attr(name: String): String? = getAttribute(name).takeIf { it.isNotEmpty() }

    /**
     * The tile's three `TextView`s in document order.
     *
     * Flattened rather than read off the root, because both tiles now stack a flush weekday band over
     * a weighted body, so only the band is a direct child: the day and month live one level down.
     * Document order is still weekday, day, month, which is what the order test relies on.
     */
    private fun Element.textViews(): List<Element> = deep().filter { it.tagName == "TextView" }

    private fun Element.textSizesSp(): List<Int> = textViews().map { element ->
        val size = element.attr(TEXT_SIZE)
        assertTrue("a preview TextView has no $TEXT_SIZE", size != null)
        size!!.removeSuffix("sp").toInt()
    }

    /** The caption size at a given text width, in whole sp. Int because JUnit's equals is not for Floats. */
    private fun weekdaySpAt(width: Float): Int =
        DateTileTypography.weekdaySizeFor(width).value.roundToInt()

    /**
     * Three lines, in order: the weekday name, the day figure, the month name.
     *
     * The order is the assertion; the sizes follow it. A tile with two lines looked like a bigger
     * number, and it was still not a date. The weekday's size here is the value at the reference
     * width, because the live tile scales every line with the granted cell and a static XML snapshot
     * cannot follow it.
     */
    @Test
    fun bothTilePreviewsStackWeekdayThenDayThenMonth() {
        for (name in tilePreviews) {
            val sizes = root(name).textSizesSp()
            assertEquals(
                "$name must have exactly three text lines (weekday, day, month)",
                3,
                sizes.size,
            )
            assertEquals(
                "$name line sizes must match DateTileTypography, so the mirror cannot drift from " +
                    "the live composition",
                listOf(
                    weekdaySpAt(DateTileTypography.WEEKDAY_REFERENCE_WIDTH_DP),
                    DateTileTypography.daySize.value.roundToInt(),
                    weekdaySpAt(DateTileTypography.WEEKDAY_REFERENCE_WIDTH_DP),
                ),
                sizes,
            )
        }
    }

    /**
     * The weekday line is its own line, not part of a caption beside the month.
     *
     * The regression this file exists for. Merging the weekday and the month into one small line
     * renders without error, fits the tile, and quietly reduces the change to "a number with some
     * small print above it".
     */
    @Test
    fun theWeekdayNameHasALineOfItsOwn() {
        for (name in tilePreviews) {
            val lines = root(name).textViews()
            val weekday = lines.first().attr("android:text").orEmpty()
            val month = lines.last().attr("android:text").orEmpty()

            assertTrue(
                "$name's top line has no weekday name: '$weekday'",
                weekday.isNotBlank() && !weekday.contains(MIDDOT),
            )
            assertTrue(
                "$name's bottom line should be the month alone, with no weekday in it: '$month'",
                month.isNotBlank() && !month.contains(MIDDOT),
            )
            assertTrue("$name's weekday and month lines must not be the same text", weekday != month)
        }
    }

    /**
     * The weekday line is drawn in the family's shared accent band, not as bare text.
     *
     * It shipped as 10sp Medium in `widget_text_secondary`, which made it the weakest line on a tile
     * whose whole content is a date — the weekday name is half the answer to "what day is it?" and it
     * was drawn like a footnote. It then moved to plain `widget_text_primary` text, which fixed the
     * weight but still left four widgets drawing the same line two ways: the dual-date tile had an
     * accent band, the three others did not. It is the band now, from one [WeekdayBand] composable.
     *
     * **The size equality check is at every width — strengthened 2026-10-06.** This file asserted
     * `>=` at the reference width only, as a proxy for "the weekday is not drawn as a caption"; the
     * defect it was written for had the weekday at 10sp against the month line's 12sp. Both caption
     * lines are now sized by [DateTileTypography.weekdaySizeFor], so the proxy can be replaced by the
     * property itself.
     */
    @Test
    fun theWeekdayLineIsNotStyledAsACaption() {
        // The live composable calls one size function for both caption lines, so what is left to
        // assert is that the hand-maintained mirror agrees — and that it agrees at *every* line, not
        // only the two the other test happens to sample.
        for (name in tilePreviews) {
            val sizes = root(name).textSizesSp()
            assertEquals(
                "$name's top and bottom caption lines must be the same size; both are sized by " +
                    "DateTileTypography.weekdaySizeFor and a differing pair means the mirror drifted",
                sizes.first(),
                sizes.last(),
            )
        }
        assertEquals(
            "the weekday name must never fall below the min scale's floor, however narrow the tile",
            (DateTileTypography.weekdaySizeAtReference.value * MIN_TYPE_SCALE).roundToInt(),
            weekdaySpAt(1f),
        )
        assertEquals(
            "the tile is now the same object as the tiles beside it: the weekday sits on the accent " +
                "band, not on bare text",
            "@color/widget_on_today",
            root(tilePreviews.first()).textViews().first().attr("android:textColor"),
        )
        assertEquals(
            "the band's fill must be the family accent",
            "@color/widget_accent",
            root(tilePreviews.first()).textViews().first().attr("android:background"),
        )

        for (name in tilePreviews) {
            val weekday = root(name).textViews().first()

            assertEquals(
                "$name's weekday line must be bold",
                "bold",
                weekday.attr("android:textStyle"),
            )
        }
    }

    /**
     * The weekday size never shrinks as the tile gets wider.
     *
     * The whole point of scaling it. A rule that was not monotonic would render a smaller name on a
     * bigger tile at some width, which is both wrong and the kind of thing a visual check at one size
     * never catches — hence a sweep rather than a pair of samples.
     */
    @Test
    fun theWeekdaySizeNeverShrinksAsTheTileGetsWider() {
        var previous = 0
        for (width in 1..400) {
            val size = weekdaySpAt(width.toFloat())
            assertTrue(
                "at ${width}dp the weekday dropped to ${size}sp from ${previous}sp",
                size >= previous,
            )
            previous = size
        }
    }

    /**
     * It is clamped at both ends.
     *
     * The ceiling is the tile's other two lines: a name cannot grow until it crowds out the day
     * figure. The floor is [MIN_TYPE_SCALE], the family's legibility bound rather than a per-line
     * size — a *tile* whose caption is set independently of its figure is exactly the drift the
     * single scale exists to prevent.
     */
    @Test
    fun theWeekdaySizeIsClampedToItsFloorAndCeiling() {
        val floor = (DateTileTypography.weekdaySizeAtReference.value * MIN_TYPE_SCALE).roundToInt()
        val ceiling = DateTileTypography.weekdaySizeCeiling.value.roundToInt()
        for (width in 1..400) {
            val size = weekdaySpAt(width.toFloat())
            assertTrue("at ${width}dp the weekday fell below its floor", size >= floor)
            assertTrue("at ${width}dp the weekday passed its ceiling", size <= ceiling)
        }
    }

    /**
     * A wide tile gets a visibly bigger name, and the declared minimum scales proportionally.
     *
     * **The floor is gone, on purpose.** This test used to assert that a launch granting the declared
     * 40dp minimum landed on a 10sp weekday floor rather than a proportional size, on the reasoning
     * that a proportional scale there would render the name a clipped stub. The premise was wrong in
     * the direction that matters: a floor is what *keeps* a line from shrinking, so on a short cell
     * the floor is the thing that pushes the last line off the bottom. The whole stack now scales, so
     * the declared minimum renders a small complete name and a wide tile renders a large one.
     * Asserted so nobody re-adds the floor.
     */
    @Test
    fun aWideTileGetsABiggerNameAndTheDeclaredMinimumScalesWithIt() {
        val atMinimum = weekdaySpAt(
            (DateTileTypography.TILE_MIN_WIDTH_DP - DateTileTypography.PADDING_DP * 2).toFloat(),
        )
        val atReference = weekdaySpAt(DateTileTypography.WEEKDAY_REFERENCE_WIDTH_DP)
        val wide = weekdaySpAt(400f)

        assertTrue(
            "the declared 40dp minimum must scale below the reference size, not be floored to it; " +
                "got $atMinimum against $atReference at the reference",
            atMinimum < atReference,
        )
        assertTrue(
            "a wide tile must render a larger weekday name than a reference one; got $wide against " +
                "$atReference",
            wide > atReference,
        )
        assertEquals(
            "a very wide tile must stop at the ceiling, not keep growing",
            DateTileTypography.weekdaySizeCeiling.value.roundToInt(),
            wide,
        )
    }

    /**
     * The tile's two contracts with the cell it is given, both asserted: **the type grows with the
     * cell** (and the whole reason `sizeMode` is `Exact`), and **the three lines fit the height they
     * were told about**.
     *
     * This test used to pin a single figure, `ACCEPTED_MAX_REQUESTED_DP`, and then to compute the
     * request as the sum of the three lines times one `LINE_HEIGHT_RATIO`. Both were pre-single-scale. The
     * request is now read from [DateTileSizes.requestedHeightDp], which is the same arithmetic the
     * typography sizes against, so the assertion cannot silently use a different model than the
     * renderer — and "the layout asks for at most N dp" is the wrong claim now that the request is a
     * function of the granted cell.
     */
    @Test
    fun theTypeGrowsWithTheCellAndFitsTheHeightItWasGiven() {
        val narrow = DateTileTypography.sizesFor(28f, 36f) // the declared 40dp cell, less padding
        val wide = DateTileTypography.sizesFor(138f, 138f) // ~2x2 on a phone launcher

        assertTrue(
            "a larger cell must render a larger caption; got ${narrow.captionSize} then " +
                "${wide.captionSize}",
            wide.captionSize.value > narrow.captionSize.value,
        )
        assertTrue(
            "and a larger day figure; got ${narrow.daySize} then ${wide.daySize}",
            wide.daySize.value > narrow.daySize.value,
        )

        // The stack must fit the height the tile was given, at every plausible cell.
        for (height in listOf(48f, 60f, 72f, 96f, 140f, 240f)) {
            val sizes = DateTileTypography.sizesFor(400f, height)
            val requested = sizes.requestedHeightDp()
            assertTrue(
                "at ${height}dp the three lines ask for ${requested}dp; a tile must fit the height " +
                    "it was given or its last line is clipped",
                requested <= height + 0.01f,
            )
        }

        // And the ceilings bound it, so no placement can outgrow its own reading distance.
        val huge = DateTileTypography.sizesFor(4000f, 4000f)
        assertEquals(
            "the caption ceiling",
            DateTileTypography.weekdaySizeCeiling.value.toDouble(),
            huge.captionSize.value.toDouble(),
            0.01,
        )
        assertEquals(
            "the figure ceiling",
            DateTileTypography.daySizeCeiling.value.toDouble(),
            huge.daySize.value.toDouble(),
            0.01,
        )
    }

    /**
     * The same fit contract, with the **bundled Nastaliq** face drawing the script lines.
     *
     * Nastaliq's line box is 2.5 em against the system fallback's 1.7 ([NASTALIQ_LINE_RATIO]), and the
     * typography is told which one it sizes for. Sizing a Nastaliq stack at the system ratio
     * under-reserves its height by ~47% — the exact failure the ratio exists to prevent — so this is
     * asserted separately rather than assumed to follow from the default-ratio case above.
     */
    @Test
    fun theBundledNastaliqStackFitsTheCellToo() {
        for (height in listOf(40f, 48f, 60f, 72f, 96f, 140f, 240f)) {
            val sizes = DateTileTypography.sizesFor(
                availableWidthDp = 400f,
                availableHeightDp = height,
                scriptRatio = NASTALIQ_LINE_RATIO,
            )
            val requested = sizes.requestedHeightDp(NASTALIQ_LINE_RATIO)
            assertTrue(
                "at ${height}dp the Nastaliq stack asks for ${requested}dp, which does not fit",
                requested <= height + 0.01f,
            )
        }

        // The taller box has to cost something at a fixed cell, or the ratio was not threaded through
        // at all. This is the assertion that would fail if `sizesFor` ignored `scriptRatio`.
        val system = DateTileTypography.sizesFor(400f, 72f, TEXT_LINE_RATIO)
        val nastaliq = DateTileTypography.sizesFor(400f, 72f, NASTALIQ_LINE_RATIO)
        assertTrue(
            "Nastaliq's taller box must shrink the caption in a fixed cell; got " +
                "${nastaliq.captionSize} against ${system.captionSize}",
            nastaliq.captionSize.value < system.captionSize.value,
        )
    }

    @Test
    fun theWeekdayNameFollowsTheWidgetsLanguageNotTheDevices() {
        val anchor = HijriWidgetRefreshScheduler.todayEpochDay()

        val urdu = todayHijriWidgetData(anchorEpochDay = anchor, options = urduOptions())
        val english = todayHijriWidgetData(anchorEpochDay = anchor, options = englishOptions())

        assertNotNull("the projection produced no today record for Urdu", urdu)
        assertNotNull("the projection produced no today record for English", english)

        assertTrue("the Urdu weekday name was blank", urdu!!.weekdayName.isNotBlank())
        assertTrue(
            "an Urdu widget must not render an English weekday name; got '${urdu.weekdayName}'",
            urdu.weekdayName.any { it.code > 0x7F },
        )
        assertTrue("the English weekday name was blank", english!!.weekdayName.isNotBlank())
        assertTrue(
            "an English widget must not render an Urdu weekday name; got '${english.weekdayName}'",
            english.weekdayName.all { it.code < 0x80 },
        )
    }

    /**
     * The 1x1 tiles' bottom line is the month name alone — no year, and no era marker.
     *
     * It used to read `محرم ١٤٤٨ ھ` / `August 2026 AD` (FD-05). Both halves are gone: the projection
     * still carries the year and the marker because the grid widget shows them, but a tile has no
     * room for either.
     *
     * Asserted on the static preview text because that is the only render path a JVM test can reach —
     * the composable has no host here. It is a mirror, so the assertion only holds while the mirror
     * holds; the live `monthText` path is guarded by construction instead, since `DateTileRoot` takes
     * the month name directly and has no parameter a year could arrive through.
     */
    @Test
    fun theBottomLineIsTheMonthNameWithNoYearOrEra() {
        for (name in tilePreviews) {
            val month = root(name).textViews().last()
                .attr("android:text").orEmpty()

            assertTrue("$name's bottom line is blank", month.isNotBlank())
            assertTrue(
                "$name's bottom line still carries a year or era marker: '$month'. The tile is " +
                    "supposed to show the month name alone",
                month.none { it.isDigit() } && !month.contains(HIJRI_ERA) && !month.contains("AD"),
            )
            assertEquals(
                "$name's bottom line should be the month name on its own, with no trailing year",
                month.trim(),
                month.trim().substringBefore(' '),
            )
        }
    }

    /**
     * The two single-date tiles' widget-info XMLs are **resizable 1x1** widgets: a 40dp floor on both
     * axes, and `android:resizeMode` on both axes.
     *
     * **Why this needs a gate.** A tile that declares `resizeMode="none"` renders perfectly — it just
     * cannot be resized, and nothing in the renderer can tell the difference. These infos really did
     * say `none` until 2.2.0, and a host that copied them to add its own `android:configure` (which it
     * must: the library ships no configure activity) kept the stale value and silently lost resizing.
     * That is a report we have already had once, and the copy lives in the consumer's APK where no
     * test here can see it — so this pins the library's own XMLs and makes a future edit that flips a
     * tile back to `none` fail in CI instead of on a home screen. The dual tile's twin assertion is in
     * `DualDateTilePreviewLayoutTest`; this covers the two single-date tiles it does not.
     */
    @Test
    fun theSingleDateTileInfosAreResizableSingleCellWidgets() {
        for (name in listOf("hijri_date", "gregorian_date")) {
            val file = File("src/main/res/xml/${name}_widget_info.xml")
            assertTrue(
                "widget-info not found at ${file.absolutePath}; if the res directory moved, fix " +
                    "this path rather than deleting this test",
                file.isFile,
            )
            val root = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
                .newDocumentBuilder().parse(file).documentElement

            assertEquals("$name widget-info root", "appwidget-provider", root.tagName)
            assertEquals(
                "$name must be resizable on both axes, or its type can never grow with the cell",
                "horizontal|vertical",
                root.attr("android:resizeMode"),
            )
            assertEquals("$name targets one cell", "1", root.attr("android:targetCellWidth"))
            assertEquals("$name targets one cell", "1", root.attr("android:targetCellHeight"))
            assertEquals(
                "$name's declared minimum must stay at the tiles' 40dp: on launchers before Android 12 " +
                    "the cell count is derived from minWidth/minHeight, so a larger value makes it a " +
                    "two-cell widget there",
                "${DateTileTypography.TILE_MIN_HEIGHT_DP}dp",
                root.attr("android:minHeight"),
            )
        }
    }

    private fun urduOptions() = createWidgetOptions(
        language = WidgetLanguage.URDU,
        numeralStyle = NumeralStyle.ARABIC_INDIC,
    )

    private fun englishOptions() = createWidgetOptions(
        language = WidgetLanguage.ENGLISH,
        numeralStyle = NumeralStyle.WESTERN,
    )

    private companion object {
        /** The preview XML attribute every text size lives in. */
        const val TEXT_SIZE = "android:textSize"

        /** The separator the rejected two-line caption used between the weekday and the month. */
        const val MIDDOT = "·"

        /** The Urdu Hijri era marker, `ھ` — one the tile's bottom line must never contain. */
        const val HIJRI_ERA = "ھ"
    }
}
