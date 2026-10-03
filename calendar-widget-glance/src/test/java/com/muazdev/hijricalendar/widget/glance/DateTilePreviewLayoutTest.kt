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

/**
 * FD-01: the 1x1 tiles must name the day they are showing, and the hand-maintained Android 12-14
 * `previewLayout` mirror must keep saying the same thing.
 *
 * `TodayHijriWidgetData.weekdayName` has existed since the projection was written and iOS has always
 * rendered it. All four Android widgets dropped it — a field that was projected, consumed on one
 * platform, and silently ignored on the other. The rendering half is asserted here by asserting the
 * *data* is present and localized, because the composable itself is not unit-testable in this
 * module (Glance composition has no JVM host); the layout half is asserted by parsing the XML.
 *
 * The sizes are asserted against [DateTileTypography] rather than against literals, which is the
 * point: the mirror is hand-maintained, and a constant is the only thing that can stop it drifting
 * from a composition nobody can render in a test.
 */
class DateTilePreviewLayoutTest {

    /** Both 1x1 tiles, whose previews are hand-maintained mirrors of [DateTileRoot]. */
    private val tilePreviews =
        listOf("hijri_date_widget_preview_layout", "gregorian_date_widget_preview_layout")

    private companion object {
        /** A TextView's rendered line height as a multiple of its font size. */
        const val LINE_HEIGHT_RATIO = 1.2f

        /** The preview XML attribute every text size lives in. */
        const val TEXT_SIZE = "android:textSize"
    }

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

    private fun Element.attr(name: String): String? =
        getAttribute(name).takeIf { it.isNotEmpty() }

    private fun Element.textSizesSp(): List<Int> = children()
        .filter { it.tagName == "TextView" }
        .map { element ->
            val size = element.attr(TEXT_SIZE)
            assertTrue("a preview TextView has no $TEXT_SIZE", size != null)
            size!!.removeSuffix("sp").toInt()
        }

    /**
     * The tile is two lines: a caption (weekday + month) over the day figure.
     *
     * A 40dp tile with two lines looked like a bigger number. It was still not a date. Asserting
     * the *count* and the *order* is what stops a future "let's also drop the month name" edit from
     * quietly removing the weekday half of the caption along with it.
     */
    @Test
    fun bothTilePreviewsStackCaptionThenDay() {
        for (name in tilePreviews) {
            val sizes = root(name).textSizesSp()
            assertEquals("$name must have exactly two text lines (caption, day)", 2, sizes.size)
            assertEquals(
                "$name line sizes must match DateTileTypography, so the mirror cannot drift from " +
                    "the live composition",
                listOf(
                    DateTileTypography.captionSize.value.toInt(),
                    DateTileTypography.daySize.value.toInt(),
                ),
                sizes,
            )
        }
    }

    /**
     * The caption must actually contain both halves.
     *
     * `joinToString` over a list with one entry silently produces a caption that is only a weekday
     * name — which still renders, still fits, and has quietly lost the month. This is the assertion
     * that catches that.
     */
    @Test
    fun theTileCaptionCarriesBothTheWeekdayAndTheMonth() {
        for (name in tilePreviews) {
            val caption = root(name).children()
                .first { it.tagName == "TextView" }
                .attr("android:text")
                .orEmpty()
            assertTrue(
                "$name's caption has no weekday name",
                caption.contains("·") && caption.split("·").first().isNotBlank(),
            )
            assertTrue(
                "$name's caption has no month name",
                caption.split("·").last().isNotBlank(),
            )
        }
    }

    /**
     * The two lines plus their padding have to fit the fixed 40dp the widget-info declares, or
     * Glance clips the day figure and the tile stops saying what day it is.
     *
     * 1.2 is a `TextView`'s line height as a multiple of its font size once the font's own ascent
     * and descent are counted — the reason `fontSize` alone never adds up to the height it occupies.
     */
    @Test
    fun theTwoLinesFitTheTileMinimum() {
        val lineHeightDp = (DateTileTypography.captionSize.value + DateTileTypography.daySize.value) * LINE_HEIGHT_RATIO
        val occupied = lineHeightDp + DateTileTypography.PADDING_DP * 2
        assertTrue(
            "the tile's two lines occupy ~${occupied.toInt()}dp but the widget-info declares a " +
                "${DateTileTypography.TILE_MIN_HEIGHT_DP}dp minimum; drop a size rather than letting " +
                "Glance clip the day figure",
            occupied <= DateTileTypography.TILE_MIN_HEIGHT_DP,
        )
    }

    /**
     * The weekday name must be the widget's own language, not the device's — the whole reason
     * `WidgetLocalization` exists (WG-12).
     *
     * A tile that resolved its strings from `res/values` would pass every layout assertion above
     * and still be wrong on exactly the case the widget is designed for: an Urdu widget on a phone
     * with no Urdu locale. Asserting the script rather than a table of strings keeps this honest
     * when the Urdu weekday list changes.
     */
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

    private fun urduOptions() = createWidgetOptions(
        language = WidgetLanguage.URDU,
        numeralStyle = NumeralStyle.ARABIC_INDIC,
    )

    private fun englishOptions() = createWidgetOptions(
        language = WidgetLanguage.ENGLISH,
        numeralStyle = NumeralStyle.WESTERN,
    )
}
