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
 * FD-01: the 1x1 tiles must name the day they are showing, the day name must be sized to the width
 * the launcher granted, and the hand-maintained Android 12-14 `previewLayout` mirror must keep
 * saying the same thing.
 *
 * `TodayHijriWidgetData.weekdayName` has existed since the projection was written and iOS has always
 * rendered it. All four Android widgets dropped it — a field that was projected, consumed on one
 * platform, and silently ignored on the other. The rendering half is asserted here by asserting the
 * *data* is present and localized, because the composable itself is not unit-testable in this module
 * (Glance composition has no JVM host); the layout half is asserted by parsing the XML.
 *
 * Three-line order — weekday, day figure, month — is asserted rather than the sizes alone, because a
 * two-line variant with the weekday merged into a caption beside the month name looks plausible and
 * loses the point of the change.
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

    private fun Element.attr(name: String): String? = getAttribute(name).takeIf { it.isNotEmpty() }

    private fun Element.textSizesSp(): List<Int> = children()
        .filter { it.tagName == "TextView" }
        .map { element ->
            val size = element.attr(TEXT_SIZE)
            assertTrue("a preview TextView has no $TEXT_SIZE", size != null)
            size!!.removeSuffix("sp").toInt()
        }

    /** The weekday size at a given text width, in whole sp. Int because JUnit's equals is not for Floats. */
    private fun weekdaySpAt(width: Float): Int = DateTileTypography.weekdaySizeFor(width).value.toInt()

    /**
     * Three lines, in order: the weekday name, the day figure, the month name.
     *
     * The order is the assertion; the sizes follow it. A tile with two lines looked like a bigger
     * number, and it was still not a date. The weekday's size here is the value at the reference
     * width, because the live tile scales that line and a static XML snapshot cannot follow it.
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
            assertTrue("$name's weekday and month lines must not be the same text", weekday != month)
        }
    }

    /**
     * The weekday line is styled as part of the date, not as a caption under it.
     *
     * It shipped as 10sp Medium in `widget_text_secondary`, which made it the weakest line on a tile
     * whose whole content is a date — the weekday name is half the answer to "what day is it?" and it
     * was drawn like a footnote. It now matches the month name's size and emphasis and reads in the
     * primary text colour.
     *
     * The size assertion is about the reference width and the floor, because the line is now scaled
     * with the tile width rather than fixed.
     */
    @Test
    fun theWeekdayLineIsNotStyledAsACaption() {
        assertEquals(
            "the weekday name must match the month name at the reference width",
            DateTileTypography.monthSize.value.toInt(),
            weekdaySpAt(DateTileTypography.WEEKDAY_REFERENCE_WIDTH_DP),
        )
        assertEquals(
            "the weekday name must never fall below the floor, however narrow the tile",
            DateTileTypography.weekdaySizeFloor.value.toInt(),
            weekdaySpAt(1f),
        )

        for (name in tilePreviews) {
            val weekday = root(name).children().first { it.tagName == "TextView" }

            assertEquals(
                "$name's weekday line must be bold",
                "bold",
                weekday.attr("android:textStyle"),
            )
            assertEquals(
                "$name's weekday line must use the primary text colour, not the muted one — that " +
                    "is what made it read as a caption rather than as part of the date",
                "@color/widget_text_primary",
                weekday.attr("android:textColor"),
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
     * figure. The floor is the narrow end, and it is the one that matters — see the next test.
     */
    @Test
    fun theWeekdaySizeIsClampedToItsFloorAndCeiling() {
        val floor = DateTileTypography.weekdaySizeFloor.value.toInt()
        val ceiling = DateTileTypography.weekdaySizeCeiling.value.toInt()
        for (width in 1..400) {
            val size = weekdaySpAt(width.toFloat())
            assertTrue("at ${width}dp the weekday fell below its floor", size >= floor)
            assertTrue("at ${width}dp the weekday passed its ceiling", size <= ceiling)
        }
    }

    /**
     * A wide tile gets a visibly bigger name, and the declared minimum lands on the floor.
     *
     * The second half is the honest part. The widget-info declares a 40dp minimum, which leaves about
     * 28dp of text width — below the reference — so a tile granted exactly its minimum shows the floor
     * rather than a proportional size. That is deliberate: the weekday name is the longest string on
     * the tile, and a proportional scale there would render it as a clipped stub, which is worse than
     * a small one. Asserted so nobody "fixes" the floor and reintroduces the stub.
     */
    @Test
    fun aWideTileGetsABiggerNameAndTheDeclaredMinimumLandsOnTheFloor() {
        val atMinimum = weekdaySpAt(
            (DateTileTypography.TILE_MIN_WIDTH_DP - DateTileTypography.PADDING_DP * 2).toFloat(),
        )
        val atReference = weekdaySpAt(DateTileTypography.WEEKDAY_REFERENCE_WIDTH_DP)
        val wide = weekdaySpAt(400f)

        assertEquals(
            "the declared 40dp minimum should land on the floor",
            DateTileTypography.weekdaySizeFloor.value.toInt(),
            atMinimum,
        )
        assertTrue(
            "a wide tile must render a larger weekday name than a reference one; got $wide against " +
                "$atReference",
            wide > atReference,
        )
        assertEquals(
            "a very wide tile must stop at the ceiling, not keep growing",
            DateTileTypography.weekdaySizeCeiling.value.toInt(),
            wide,
        )
    }

    /**
     * The vertical budget, recorded rather than asserted.
     *
     * Three lines of type cannot fit the declared 40dp minimum at any size worth reading. The tile is
     * therefore sized for the cell a launcher actually grants, which for a 1x1 is comfortably more
     * than its own declared minimum.
     *
     * This is a number, not a guarantee: it says what the layout asks for, and it fails loudly if a
     * future change grows a line without anyone noticing. It is *not* an assertion that the three
     * lines fit 40dp, because they do not and pretending otherwise would be the more dishonest thing.
     * The real check is a device, and `docs/issues/2026-10-03/FD-01` says so.
     *
     * The weekday figure is the widest case, since the weekday is the longest line.
     */
    @Test
    fun theThreeLinesAskForLessHeightThanThePreFD01LayoutDid() {
        val weekdaySp = DateTileTypography.weekdaySizeCeiling.value.toInt()
        val lineSp = weekdaySp +
            DateTileTypography.daySize.value.toInt() +
            DateTileTypography.monthSize.value.toInt()
        val requestedDp = lineSp * LINE_HEIGHT_RATIO + DateTileTypography.VERTICAL_PADDING_DP * 2
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
     * A tile that resolved its strings from `res/values` would pass every layout assertion above and
     * still be wrong on exactly the case the widget is designed for: an Urdu widget on a phone with no
     * Urdu locale. Asserting the script rather than a table of strings keeps this honest when the Urdu
     * weekday list changes.
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
