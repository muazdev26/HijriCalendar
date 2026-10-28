package com.muazdev.hijricalendar.widget.glance

import com.muazdev.hijricalendar.widgetdata.NumeralStyle
import com.muazdev.hijricalendar.widgetdata.WidgetLanguage
import com.muazdev.hijricalendar.widgetdata.createWidgetOptions
import com.muazdev.hijricalendar.widgetdata.todayHijriWidgetData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToInt

class CompactDateTilePreviewLayoutTest {

    @Test
    fun theReferenceCellRendersTheReferenceSizes() {
        val sizes = CompactDateTileTypography.sizesFor(
            availableWidthDp = CompactDateTileTypography.REFERENCE_WIDTH_DP,
            grantedHeightDp = CompactDateTileTypography.REFERENCE_HEIGHT_DP,
        )
        assertEquals(CompactDateTileTypography.daySize.value.toDouble(), sizes.daySize.value.toDouble(), 0.01)
        assertEquals(CompactDateTileTypography.captionSizeAtReference.value.toDouble(), sizes.captionSize.value.toDouble(), 0.01)
    }

    @Test
    fun theStackFitsTheHeightItIsGranted() {
        for (height in listOf(40f, 48f, 60f, 72f, 96f, 160f, 240f)) {
            for (width in listOf(40f, 52f, 60f, 72f, 96f, 160f, 240f)) {
                val requested = CompactDateTileTypography.verticalBudgetDp(width, height)
                assertTrue("at ${width}x${height}dp the stack asks for ${requested}dp", requested <= height + 0.01f)
            }
        }
    }

    @Test
    fun theNastaliqStackFitsTooAndIsSmallerAtTheSameCell() {
        for (height in listOf(40f, 48f, 60f, 72f, 96f, 160f, 240f)) {
            val requested = CompactDateTileTypography.verticalBudgetDp(
                availableWidthDp = CompactDateTileTypography.REFERENCE_WIDTH_DP,
                grantedHeightDp = height,
                scriptRatio = NASTALIQ_INK_BAND_RATIO,
            )
            assertTrue("at ${height}dp the Nastaliq stack asks for ${requested}dp", requested <= height + 0.01f)
        }
        val system = CompactDateTileTypography.sizesFor(84f, 84f, TEXT_LINE_RATIO)
        val nastaliq = CompactDateTileTypography.sizesFor(84f, 84f, NASTALIQ_INK_BAND_RATIO)
        assertTrue("Nastaliq must shrink the type", nastaliq.daySize.value < system.daySize.value)
    }

    @Test
    fun theDayFigureDominatesAtEveryWidth() {
        for (width in 1..400) {
            val sizes = CompactDateTileTypography.sizesForDrawnEdge(width.toFloat())
            assertTrue("at ${width}dp caption reached the figure", sizes.isDayDominant())
        }
    }

    @Test
    fun theWidthOnlyProjectionIsMonotonicAndClamped() {
        var previousDay = 0f
        for (width in 1..400) {
            val sizes = CompactDateTileTypography.sizesForDrawnEdge(width.toFloat())
            assertTrue("day dropped", sizes.daySize.value >= previousDay)
            previousDay = sizes.daySize.value
            assertTrue("lines rendered", sizes.captionSize.value > 0f && sizes.daySize.value > 0f)
        }
        assertEquals(
            CompactDateTileTypography.daySizeCeiling.value.toDouble(),
            CompactDateTileTypography.sizesForDrawnEdge(4000f).daySize.value.toDouble(),
            0.01,
        )
        assertEquals(
            CompactDateTileTypography.captionSizeCeiling.value.toDouble(),
            CompactDateTileTypography.sizesForDrawnEdge(4000f).captionSize.value.toDouble(),
            0.01,
        )
    }

    @Test
    fun thePreviewSquareIsTheDrawnEdge() {
        assertEquals(72.0, COMPACT_DATE_TILE_PREVIEW_SIZE.width.value.toDouble(), 0.01)
        assertEquals(COMPACT_DATE_TILE_PREVIEW_SIZE.width.value, COMPACT_DATE_TILE_PREVIEW_SIZE.height.value, 0.01f)
        assertEquals(
            CompactDateTileTypography.DRAWN_EDGE_DP.toDouble(),
            COMPACT_DATE_TILE_PREVIEW_SIZE.width.value.toDouble(),
            0.01,
        )
    }

    @Test
    fun theDrawnSquareIsSeventyTwoAndNeverLarger() {
        assertEquals(72f, CompactDateTileTypography.DRAWN_EDGE_DP, 0.01f)
        val grants = listOf(40f, 48f, 56f, 63f, 72f, 80f, 88f, 120f, 160f, 240f, 400f)
        for (width in grants) {
            for (height in grants) {
                val edge = CompactDateTileTypography.drawnEdgeDp(width, height)
                assertTrue(
                    "a ${width}x${height}dp grant drew a ${edge}dp tile",
                    edge <= CompactDateTileTypography.DRAWN_EDGE_DP + 0.01f,
                )
                // Never wider or taller than the cell: a 72dp square inside a 63dp grant would be
                // clipped, not scaled.
                assertTrue("a ${width}x${height}dp grant drew a ${edge}dp tile", edge <= minOf(width, height) + 0.01f)
            }
        }
        // A cell that already contains the square in both axes keeps the full 72dp.
        assertEquals(
            72f,
            CompactDateTileTypography.drawnEdgeDp(300f, 88f),
            0.01f,
        )
        // The one thing that moves it: a cell *smaller* than the square. 63x88 is what a Pixel 6a
        // launcher grants a 1x1, so this is the case a placed tile actually hits there.
        assertEquals(63f, CompactDateTileTypography.drawnEdgeDp(63f, 88f), 0.01f)
        assertEquals(48f, CompactDateTileTypography.drawnEdgeDp(120f, 48f), 0.01f)
    }

    @Test
    fun anUnspecifiedAxisIsIgnoredRatherThanCollapsingTheSquare() {
        assertEquals(72f, CompactDateTileTypography.drawnEdgeDp(Float.NaN, 88f), 0.01f)
        assertEquals(72f, CompactDateTileTypography.drawnEdgeDp(300f, Float.NaN), 0.01f)
        assertEquals(72f, CompactDateTileTypography.drawnEdgeDp(Float.NaN, Float.NaN), 0.01f)
        // A known axis still caps: an unspecified height does not rescue a 63dp-wide cell.
        assertEquals(63f, CompactDateTileTypography.drawnEdgeDp(63f, Float.NaN), 0.01f)
        assertEquals(1f, CompactDateTileTypography.drawnEdgeDp(0f, 88f), 0.01f)
    }

    @Test
    fun theSquareSizesTheTypeAtTheCellItActuallyDraws() {
        // The tile is sized from its drawn edge, not from the cell: a 300x88 grant draws 72dp, so it
        // renders the reference type rather than 300dp-wide type.
        val edge = CompactDateTileTypography.drawnEdgeDp(300f, 88f)
        val sizes = CompactDateTileTypography.sizesFor(edge, edge)
        assertEquals(CompactDateTileTypography.daySize.value.toDouble(), sizes.daySize.value.toDouble(), 0.01)
    }

    @Test
    fun everyLineReadsAProjectedFieldInTheWidgetsLanguage() {
        val anchor = HijriWidgetRefreshScheduler.todayEpochDay()
        val urdu = checkNotNull(todayHijriWidgetData(anchorEpochDay = anchor, options = createWidgetOptions(language = WidgetLanguage.URDU, numeralStyle = NumeralStyle.ARABIC_INDIC))) { "no today record" }
        val english = checkNotNull(todayHijriWidgetData(anchorEpochDay = anchor, options = createWidgetOptions(language = WidgetLanguage.ENGLISH, numeralStyle = NumeralStyle.WESTERN))) { "no today record" }
        for ((_, data) in listOf(WidgetLanguage.URDU to urdu, WidgetLanguage.ENGLISH to english)) {
            assertTrue(data.weekdayTileName.isNotBlank())
            assertTrue(data.hijriDayText.isNotBlank())
            assertTrue(data.gregorianDayText.isNotBlank())
            assertTrue(data.hijriMonthShortName.isNotBlank())
            assertTrue(data.gregorianMonthShortName.isNotBlank())
        }
        assertEquals(urdu.hijriMonthName, urdu.hijriMonthShortName)
        assertTrue(urdu.weekdayTileName.any { it.code > 0x7F })
        assertTrue(english.gregorianMonthShortName.length <= 4)
    }

    @Test
    fun aShorterBandWordSpendsItsSpareHeightOnTheBody() {
        // The band's reserve is the word's own ink, not the face's worst case. `بدھ` measures 0.77em
        // and `منگل` 1.82em in the bundled face (device-measured); the compact tile is the one fitted
        // typography whose stack arithmetic sets its scale, so the difference between those two has to
        // reach the body — that is where the Gregorian date and the month name gained their size.
        val shortest = 0.77f
        val worstCase = WeekdayBandFit.inkEmFor(NASTALIQ_INK_BAND_RATIO)
        val wednesday = CompactDateTileTypography.sizesFor(62f, 62f, NASTALIQ_INK_BAND_RATIO, shortest)
        val assumed = CompactDateTileTypography.sizesFor(62f, 62f, NASTALIQ_INK_BAND_RATIO, worstCase)
        assertTrue(
            "a ${shortest}em word must render its captions larger than a ${worstCase}em reserve",
            wednesday.captionSize.value > assumed.captionSize.value + 0.5f,
        )
        assertTrue(
            "a ${shortest}em word must render a larger figure too",
            wednesday.daySize.value > assumed.daySize.value,
        )
        // Both still fit the cell they were granted — measuring cannot overrun the tile.
        for ((label, sizes) in listOf("measured" to wednesday, "worst case" to assumed)) {
            val requested = sizes.requestedHeightDp(NASTALIQ_INK_BAND_RATIO)
            assertTrue("the $label stack asks for ${requested}dp of 62dp", requested <= 62.05f)
        }
    }

    @Test
    fun theReserveIsThePaintForEveryMeasuredWeekday() {
        // Every ink this project has measured, in both faces, at the cells a phone grants. The point
        // is that no weekday's band overruns the height the stack reserved for it, and that the gap
        // between reserve and paint — the defect — is zero rather than a third of the band.
        for (inkEm in listOf(0.59f, 0.65f, 0.69f, 0.77f, 0.87f, 0.94f, 0.97f, 1.0f, 1.41f, 1.82f)) {
            for (edge in listOf(40f, 47f, 56f, 62f, 63f, 72f)) {
                for (ratio in listOf(TEXT_LINE_RATIO, NASTALIQ_INK_BAND_RATIO)) {
                    val sizes = CompactDateTileTypography.sizesFor(edge, edge, ratio, inkEm)
                    val requested = sizes.requestedHeightDp(ratio)
                    assertTrue(
                        "ink=${inkEm}em at ${edge}dp ratio=$ratio asks for ${requested}dp",
                        requested <= edge + 0.05f,
                    )
                    // The band's painted height is the reserve by construction.
                    assertEquals(
                        WeekdayBandFit.bandHeightDp(sizes.weekdaySize, inkEm).toDouble(),
                        (WeekdayBandFit.inkHeightDp(sizes.weekdaySize, inkEm) +
                            WidgetBand.TOP_PADDING_DP + WidgetBand.BOTTOM_PADDING_DP).toDouble(),
                        0.01,
                    )
                }
            }
        }
    }

    @Test
    fun theMirrorCentresItsBodyLikeTheLiveTile() {
        // The live body is centred; pinning it to the bottom is what pooled every unspent dp above the
        // Gregorian row. The static mirror has to agree, or the Android 12-14 picker advertises a tile
        // that no longer exists.
        val body = childrenOf(layoutRoot())[1]
        assertEquals("center_horizontal|center_vertical", attr(body, "android:gravity"))
    }

    @Test
    fun thePreviewLayoutMatchesTheLiveSizes() {
        val sizes = CompactDateTileTypography.sizesForDrawnEdge(CompactDateTileTypography.REFERENCE_WIDTH_DP)
        val lines = zoneTextViews()
        assertEquals(5, lines.size)
        // The band's word is its own size now, not a body caption's: five TextViews are the band, the
        // Gregorian row's two halves, the figure and the Hijri month.
        assertEquals(lines.size, CompactDateTileTypography.CAPTION_LINES + 3)
        assertEquals(spInXml(sizes.weekdaySize.value), spOf(lines[WEEKDAY_LINE_INDEX]))
        for (index in CAPTION_LINE_INDICES) {
            assertEquals(spInXml(sizes.captionSize.value), spOf(lines[index]))
        }
        assertEquals(spInXml(sizes.daySize.value), spOf(lines[HIJRI_DAY_LINE_INDEX]))
    }

    @Test
    fun theMirrorReproducesTheStackTopToBottom() {
        val root = layoutRoot()
        val children = childrenOf(root)
        assertEquals(2, children.size)
        val band = children[0]
        assertBand(band)
        val body = children[1]
        assertEquals("vertical", attr(body, "android:orientation"))
        assertEquals("1", attr(body, "android:layout_weight"))
        val stack = childrenOf(body)
        assertEquals(3, stack.size)
        assertEquals("horizontal", attr(stack[0], "android:orientation"))
        assertSpreadGregorianRow(stack[0])
        assertEquals("@color/widget_accent", attr(stack[1], "android:textColor"))
        assertNoDigits("Hijri month", stack[2])
    }

    @Test
    fun noLineCarriesAYearAnEraOrASeparator() {
        val lines = zoneTextViews()
        assertEquals(5, lines.size)
        for (index in FIGURE_LINE_INDICES) {
            val day = dayNumberIn(lines[index])
            assertTrue(day != null && day in 1..31)
        }
        for (index in NO_DIGIT_LINE_INDICES) {
            val text = attr(lines[index], "android:text").orEmpty()
            assertTrue(text.none { it.isDigit() })
            assertTrue(text.isNotBlank())
        }
        for (line in lines) {
            val text = attr(line, "android:text").orEmpty()
            assertTrue(text.none { it in SEPARATORS })
        }
    }

    @Test
    fun theWidgetInfoIsAFixedSingleCellWithAFortyDpFloor() {
        val info = widgetInfoFile()
        val root = parse(info)
        assertEquals("appwidget-provider", root.tagName)
        assertEquals("none", attr(root, "android:resizeMode"))
        assertEquals("1", attr(root, "android:targetCellWidth"))
        assertEquals("1", attr(root, "android:targetCellHeight"))
        assertEquals("${CompactDateTileTypography.TILE_MIN_HEIGHT_DP}dp", attr(root, "android:minHeight"))
        assertEquals("${CompactDateTileTypography.TILE_MIN_WIDTH_DP}dp", attr(root, "android:minWidth"))
        assertEquals("1800000", attr(root, "android:updatePeriodMillis"))
        assertEquals("@layout/hijri_compact_date_widget_preview_layout", attr(root, "android:previewLayout"))
        assertEquals("@drawable/hijri_compact_date_widget_preview", attr(root, "android:previewImage"))
        assertTrue(attr(root, "android:configure") == null)
        assertTrue(File("src/main/res/layout/hijri_compact_date_widget_preview_layout.xml").isFile)
    }

    private fun assertBand(band: Element) {
        assertEquals("TextView", band.tagName)
        assertEquals(KNOWN_WEEKDAY, attr(band, "android:text"))
        assertEquals("@color/widget_accent", attr(band, "android:background"))
        assertEquals("@color/widget_on_today", attr(band, "android:textColor"))
    }

    private fun assertSpreadGregorianRow(row: Element) {
        val halves = childrenOf(row).filter { it.tagName == "TextView" }
        assertEquals(2, halves.size)
        assertTrue(halves.all { attr(it, "android:layout_weight") == "1" })
        assertEquals("left", attr(halves[0], "android:gravity"))
        assertEquals("right", attr(halves[1], "android:gravity"))
        val gregorianDay = dayNumberIn(halves[0])
        assertTrue(gregorianDay != null && gregorianDay in 1..31)
        assertNoDigits("Gregorian month", halves[1])
    }

    private fun assertNoDigits(what: String, element: Element) {
        val text = attr(element, "android:text").orEmpty()
        assertTrue("$what is blank", text.isNotBlank())
        assertTrue("$what carries a digit", text.none { it.isDigit() })
    }

    private fun widgetInfoFile(): File {
        val file = File("src/main/res/xml/hijri_compact_date_widget_info.xml")
        assertTrue(file.isFile)
        return file
    }

    private fun layoutFile(): File {
        val file = File("src/main/res/layout/hijri_compact_date_widget_preview_layout.xml")
        assertTrue(file.isFile)
        return file
    }

    private fun parse(file: File): Element {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
        return factory.newDocumentBuilder().parse(file).documentElement
    }

    private fun layoutRoot(): Element = parse(layoutFile()).also {
        assertEquals("LinearLayout", it.tagName)
    }

    private fun childrenOf(element: Element): List<Element> = buildList {
        val nodes = element.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) add(node as Element)
        }
    }

    private fun descendants(element: Element): List<Element> = buildList {
        for (child in childrenOf(element)) {
            add(child)
            addAll(descendants(child))
        }
    }

    private fun zoneTextViews(): List<Element> =
        descendants(layoutRoot()).filter { it.tagName == "TextView" }

    private fun attr(element: Element, name: String): String? =
        element.getAttribute(name).takeIf { it.isNotEmpty() }

    private fun spOf(element: Element): Int =
        attr(element, "android:textSize")?.removeSuffix("sp")?.toInt()
            ?: error("no textSize")

    private fun spInXml(sizeSp: Float): Int = sizeSp.roundToInt()

    private fun dayNumberIn(element: Element): Int? {
        val digits = attr(element, "android:text").orEmpty()
        if (digits.isEmpty() || digits.any { !it.isDigit() }) return null
        return digits.fold(0) { acc, c -> acc * 10 + c.digitToInt() }
    }

    private companion object {
        const val TEXT_SIZE = "android:textSize"
        const val WEEKDAY_LINE_INDEX = 0
        const val GREGORIAN_DAY_LINE_INDEX = 1
        const val GREGORIAN_MONTH_LINE_INDEX = 2
        const val HIJRI_DAY_LINE_INDEX = 3
        const val HIJRI_MONTH_LINE_INDEX = 4
        val FIGURE_LINE_INDICES = listOf(GREGORIAN_DAY_LINE_INDEX, HIJRI_DAY_LINE_INDEX)
        val CAPTION_LINE_INDICES = listOf(GREGORIAN_DAY_LINE_INDEX, GREGORIAN_MONTH_LINE_INDEX, HIJRI_MONTH_LINE_INDEX)
        val NO_DIGIT_LINE_INDICES = listOf(WEEKDAY_LINE_INDEX, GREGORIAN_MONTH_LINE_INDEX, HIJRI_MONTH_LINE_INDEX)
        const val SEPARATORS = "·-–—/|,."
        const val KNOWN_WEEKDAY = "بدھ"
    }
}
