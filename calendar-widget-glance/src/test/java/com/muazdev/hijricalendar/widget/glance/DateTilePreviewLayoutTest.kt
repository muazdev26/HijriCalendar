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
 * The three-line order — weekday, day figure, month — is asserted rather than the sizes alone,
 * because a two-line variant with the weekday merged into a caption beside the month name looks
 * plausible and loses the point of the change: the weekday stops being the answer to "what day is
 * it?" and becomes decoration.
 *
 * The sizes are asserted against [DateTileTypography] rather than against literals, which is the
 * point: the mirror is hand-maintained, and a constant is the only thing that can stop it drifting
 * from a composition nobody can render in a test.
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

    private fun Element.attr(name: String): String? = getAttribute(name).takeIf { it.isNotEmpty() }

    private fun Element.textSizesSp(): List<Int> = children()
        .filter { it.tagName == "TextView" }
        .map { element ->
            val size = element.attr(TEXT_SIZE)
            assertTrue("a preview TextView has no $TEXT_SIZE", size != null)
            size!!.removeSuffix("sp").toInt()
        }

    /**
     * Three lines, in order: the weekday name, the day figure, the month name.
     *
     * The order is the assertion; the sizes follow it. A tile with two lines looked like a bigger
     * number, and it was still not a date.
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
                    DateTileTypography.weekdaySize.value.toInt(),
                    DateTileTypography.daySize.value.toInt(),
                    DateTileTypography.monthSize.value.toInt(),
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
            val lines = root(name).children().filter { it.tagName == "TextView" }
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
            assertTrue(
                "$name's weekday and month lines must not be the same text",
                weekday != month,
            )
        }
    }

    /**
     * The vertical budget, recorded rather than asserted.
     *
     * Three lines of type cannot fit the declared 40dp minimum at any size worth reading — 10 + 26 +
     * 11sp at a TextView's 1.2 line height is ~56dp. The tile is therefore sized for the cell a
     * launcher actually grants, which for a 1x1 is comfortably more than its own declared minimum.
     *
     * This is a number, not a guarantee: it says what the layout asks for, and it fails loudly if a
     * future change grows a line without anyone noticing. It is *not* an assertion that the three
     * lines fit 40dp, because they do not and pretending otherwise would be the more dishonest
     * thing. The real check is a device, and [docs/issues/2026-10-03/FD-01] says so.
     */
    @Test
    fun theThreeLinesAskForLessHeightThanThePreFD01LayoutDid() {
        val lineSp = DateTileTypography.weekdaySize.value +
            DateTileTypography.daySize.value +
            DateTileTypography.monthSize.value
        val requestedDp = lineSp * LINE_HEIGHT_RATIO + DateTileTypography.PADDING_DP * 2

        val beforeThisChangeDp = (34f + 12f) * LINE_HEIGHT_RATIO + 12 * 2

        assertTrue(
            "the three lines ask for ~${requestedDp.toInt()}dp against a declared " +
                "${DateTileTypography.TILE_MIN_HEIGHT_DP}dp minimum; that is expected, but it must " +
                "still be less than the ~${beforeThisChangeDp.toInt()}dp the two-line 34sp layout " +
                "asked for",
            requestedDp < beforeThisChangeDp,
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

    private companion object {
        /** A TextView's rendered line height as a multiple of its font size. */
        const val LINE_HEIGHT_RATIO = 1.2f

        /** The preview XML attribute every text size lives in. */
        const val TEXT_SIZE = "android:textSize"

        /** The separator the rejected two-line caption used between the weekday and the month. */
        const val MIDDOT = "·"
    }
}
