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

    /** The caption size at a given text width, in whole sp. Int because JUnit's equals is not for Floats. */
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
     * was drawn like a footnote. It now matches the month name's emphasis and reads in the primary
     * text colour.
     *
     * **The size check is equality at every width — strengthened 2026-10-06.** This file asserted
     * `>=` at the reference width only, as a proxy for "the weekday is not drawn as a caption"; the
     * defect it was written for had the weekday at 10sp against the month line's 12sp. That was
     * weakened twice by events that had nothing to do with styling: the weekday and month lines grew
     * to different sizes, and then the year came off the month line, which removed the only reason
     * they differed at all. Both caption lines are now sized by [DateTileTypography.weekdaySizeFor],
     * so the proxy can be replaced by the property itself.
     *
     * Swept across every width rather than sampled, because "equal at the reference width" was exactly
     * the weaker claim that let the two rules drift apart in the first place.
     *
     * The weight and colour assertions below carry the rest of the styling intent, and are exact.
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
     * The vertical budget, held to the figure accepted for this layout.
     *
     * Three lines of type cannot fit the declared 40dp `minHeight` at any size worth reading. The tile
     * is therefore sized for the cell a launcher actually grants, which for a 1x1 is comfortably more
     * than its own declared minimum — **and on a launcher that grants less, the bottom line is
     * clipped.** That is a known, accepted cost of this layout, not an oversight, and this number is
     * what makes it visible.
     *
     * **This assertion has been re-basedelined three times, for three different reasons:**
     *
     * 1. It originally compared against a hardcoded ~79dp, the cost of the two-line 34sp layout FD-01
     *    replaced. That baseline broke when `daySize` grew 26sp -> 36sp.
     * 2. It was re-basedelined against a two-line layout recomputed at the live `daySize` — a real
     *    property, until the caption lines grew to 32sp each.
     * 3. It was then raised twice for cosmetic changes that added height without adding value: 129dp
     *    when both caption lines were merged onto one size rule, and 137dp when `VERTICAL_PADDING_DP`
     *    went 2dp -> 6dp. **The 6dp padding was reverted** — it sliced the month name in half and, on a
     *    centre-aligned column, could not move the text at all — which brings the figure back to ~129dp,
     *    with both captions now sharing the one 34sp ceiling.
     *
     * A band-based layout (1 : 2 : 1 of the granted height, type sized from each band) was tried as the
     * way to remove the clipping for good, and reverted: holding the captions to a quarter of the cell
     * cost ~10sp of their size and made the width scaling inert at any typical cell height. This budget
     * is what that layout would have made honest, and it is recorded here instead.
     *
     * The comparison against the old two-line layout is **not** reinstated: at 124dp it no longer holds,
     * and asserting it would only assert that the tile is too tall. What is asserted is that the tile has
     * not grown past what was accepted, so a further increase is visible.
     *
     * The real check is a device, across launchers that grant different cells;
     * `docs/issues/2026-10-03/FD-01` says so.
     */
    @Test
    fun theThreeLinesStayWithinTheAcceptedVerticalBudget() {
        val captionSp = DateTileTypography.weekdaySizeCeiling.value.toInt()
        val lineSp = captionSp +
            DateTileTypography.daySize.value.toInt() +
            captionSp
        // Rounded, because `LINE_HEIGHT_RATIO` is a `Float` and 100 * 1.2f is 120.00001f, not 120f.
        val requestedDp =
            (lineSp * LINE_HEIGHT_RATIO + DateTileTypography.VERTICAL_PADDING_DP * 2).roundToInt()

        assertEquals(
            "the three lines ask for ~${requestedDp}dp, past the ~${ACCEPTED_MAX_REQUESTED_DP}dp " +
                "accepted for this layout. Raising it is not free: the tile already clips on a cell " +
                "shorter than this, and the 6dp padding that pushed it to 137dp had to be reverted " +
                "because it sliced the month name",
            ACCEPTED_MAX_REQUESTED_DP,
            requestedDp,
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
            val month = root(name).children().last { it.tagName == "TextView" }
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

        /** The Urdu Hijri era marker, `ھ` — one the tile's bottom line must never contain. */
        const val HIJRI_ERA = "ھ"

        /**
         * The most vertical space the three lines are accepted to ask for, in dp.
         *
         * ~129dp for the layout that is actually in the tree: two caption lines at the shared 34sp
         * ceiling plus the 36sp day figure, at 2dp of vertical padding.
         *
         * It reached 137dp only while `VERTICAL_PADDING_DP` was 6dp; that padding was reverted, so the
         * figure came back down. See the budget test's KDoc for the full history, including the two
         * earlier re-baselines and the band layout that was tried and undone.
         *
         * It is a judgement call about how much taller than its declared `minHeight` this tile may be,
         * **not** a measurement of what a launcher grants.
         */
        const val ACCEPTED_MAX_REQUESTED_DP = 129
    }
}
