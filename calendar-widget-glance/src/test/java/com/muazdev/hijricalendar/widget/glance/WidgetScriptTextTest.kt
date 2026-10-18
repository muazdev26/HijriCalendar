package com.muazdev.hijricalendar.widget.glance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two pure decisions behind the bundled Urdu face: **which slots it draws** and **what height a
 * stack drawn with it has to reserve**.
 *
 * The rasteriser itself is not testable here — it needs a real `Context`, `Typeface` and `Bitmap`, and
 * this module's unit tests run against the stubbed `android.jar` with no Robolectric (see
 * [WidgetScriptText]'s KDoc for why the mechanism is a bitmap at all). What *is* testable is the pair
 * of rules that decide whether it runs and how much room it is given, and both are the kind of thing
 * that fails silently: a slot the wrong side of [hasArabicLetters] renders in the wrong face, and a
 * stack measured at the wrong ratio clips.
 *
 * JUnit here, so the assertion message is the **first** argument (see the note in
 * `DateTilePreviewLayoutTest`).
 */
class WidgetScriptTextTest {

    @Test
    fun urduWordsAndMixedMonthLinesCarryArabicLetters() {
        // The slots the bundled face exists for.
        assertTrue("the Urdu month name", "محرم".hasArabicLetters())
        assertTrue("the Urdu weekday", "جمعرات".hasArabicLetters())
        assertTrue("the grid's month + era line", "محرم 1448 ھ".hasArabicLetters())
        assertTrue(
            "the localized 'no event' line",
            "کوئی تاریخی واقعہ نہیں".hasArabicLetters(),
        )
        // Diacritics and the Urdu forms outside the base block, which a range check on 0600-06FF
        // alone would miss.
        assertTrue("a Presentation-Forms ligature", "\uFDF2".hasArabicLetters())
    }

    @Test
    fun latinAndFiguresOnlySlotsDoNot() {
        assertFalse("an English month name", "Ramadan".hasArabicLetters())
        assertFalse("a Gregorian month name", "August".hasArabicLetters())
        assertFalse("Western figures", "1448".hasArabicLetters())
        assertFalse("the dual tile's separator", " - ".hasArabicLetters())
        assertFalse("an empty slot", "".hasArabicLetters())
        // **The load-bearing exclusion.** An Arabic-Indic figure is Arabic script by codepoint but is a
        // figure: sending it through the rasteriser would grow every day cell's content box from the
        // digits' ~1.3em to Nastaliq's 2.5em in the one widget whose type is derived from width, and
        // the grid's rows would clip. Both numeral systems Urdu can be set in are pinned here.
        assertFalse("Arabic-Indic figures", "٢٩".hasArabicLetters())
        assertFalse("Extended Arabic-Indic figures", "۲۹".hasArabicLetters())
    }

    /**
     * The first of the two decisions this file's KDoc describes — *which slots the bundled face
     * draws* — and the one a device caught. The grid's weekday row omitted `maxLines`, silently took
     * the `Int.MAX_VALUE` default, and rendered in the system face while every other Urdu slot on the
     * widget rasterised. [WidgetText] now requires the line count instead of defaulting it; this pins
     * the rule that requirement exists for.
     */
    @Test
    fun aScriptSlotRasterisesOnlyWhenItIsASingleLine() {
        val bundled = HijriWidgetFonts(bundledUrduFont = true)

        assertTrue("an Urdu label on one line", bundled.drawsAsScript("جمعرات", maxLines = 1))
        assertFalse(
            "a slot allowed to wrap cannot be a bitmap, and must not pretend otherwise",
            bundled.drawsAsScript("ہجری تاریخ دستیاب نہیں", maxLines = Int.MAX_VALUE),
        )
        assertFalse(
            "a figures-only slot stays on the system face",
            bundled.drawsAsScript("1448", maxLines = 1),
        )
        assertFalse("so does Latin", bundled.drawsAsScript("Ramadan", maxLines = 1))
        assertFalse(
            "and the bundled face off is off for every slot",
            HijriWidgetFonts().drawsAsScript("جمعرات", maxLines = 1),
        )
    }

    @Test
    fun theScriptRatioFollowsTheBundledFaceSwitch() {
        val system = HijriWidgetFonts()
        val bundled = HijriWidgetFonts(bundledUrduFont = true)

        assertEquals(
            "off is the system fallback's ratio",
            TEXT_LINE_RATIO.toDouble(),
            system.scriptLineRatio().toDouble(),
            0.0001,
        )
        assertEquals(
            "on is the measured Nastaliq ratio, not a guess",
            NASTALIQ_LINE_RATIO.toDouble(),
            bundled.scriptLineRatio().toDouble(),
            0.0001,
        )
        assertTrue(
            "and Nastaliq's box is the taller one, or nothing downstream means anything",
            NASTALIQ_LINE_RATIO > TEXT_LINE_RATIO,
        )
    }

    /**
     * [systemFaceRuns] is the half of the digits rule the slot check cannot express: a figure
     * standing alone never reached the rasteriser, but the year inside a caption did — one bitmap
     * carries one face unless the caption's non-script runs are pinned away from the bundled one.
     */
    @Test
    fun everyNonScriptRunOfACaptionIsPinnedToTheSystemFace() {
        assertEquals(
            "the year and the spaces around it, and only those",
            listOf(4 until 10),
            "محرم 1448 ھ".systemFaceRuns(),
        )
        assertEquals(
            "Arabic-Indic figures are figures too — same rule, both numeral systems",
            listOf(5 until 6, 12 until 18),
            "جمادى الأولى ١٤٤٨ ھ".systemFaceRuns(),
        )
        assertEquals(
            "a caption with Latin in it pins the Latin as well",
            listOf(5 until 14),
            "رمضان Oct 2026".systemFaceRuns(),
        )
        assertEquals("a pure script line pins nothing", emptyList<IntRange>(), "محرم".systemFaceRuns())
        assertEquals(
            "a Latin line is one system run — it never reaches the rasteriser anyway",
            listOf(0 until 7),
            "Ramadan".systemFaceRuns(),
        )
        assertEquals(
            "and the dual tile's separator",
            listOf(0 until 3),
            " - ".systemFaceRuns(),
        )
        assertEquals("an empty slot pins nothing", emptyList<IntRange>(), "".systemFaceRuns())
    }

    /**
     * The runs must tile the string exactly: a character skipped by both lists is a character drawn
     * in the wrong face (or twice), which is the failure the pinning exists to prevent.
     */
    @Test
    fun scriptAndSystemRunsCoverEveryCharacterExactlyOnce() {
        val samples = listOf(
            "محرم 1448 ھ",
            "جمادى الأولى ١٤٤٨ ھ",
            "رمضان Oct 2026",
            "کوئی تاریخی واقعہ نہیں 2026",
            "Ramadan",
            "محرم",
            "",
        )
        for (sample in samples) {
            val pinned = sample.systemFaceRuns()
            // No Arabic letter may sit inside a pinned range: that is the rule itself.
            for (range in pinned) {
                for (index in range) {
                    assertFalse(
                        "$sample: index $index is an Arabic letter and must stay on the bundled face",
                        sample[index].isArabicScriptLetter(),
                    )
                }
            }
            // And nothing may be left unaccounted for: pinned ranges plus the letters between them
            // tile the whole string with no gaps and no overlaps.
            var covered = 0
            var cursor = 0
            for (range in pinned) {
                val gap = range.first - cursor
                assertTrue("$sample: range $range starts after the previous one", gap >= 0)
                covered += gap + (range.last - range.first + 1)
                cursor = range.last + 1
            }
            covered += sample.length - cursor
            assertEquals("$sample: runs must cover the string exactly", sample.length, covered)
        }
    }

    /**
     * The crop that fixed "in urdu Monday is not properly displayed".
     *
     * `پیر` measures ~1.53 em of ink against ~0.71 em of advance, and Nastaliq's ink overhangs the
     * advance box on both sides — so the bitmap cut to `getLineLeft`/`getLineRight` silently lost part
     * of the word. The raster now draws into a scratch bitmap with slack and keeps what painted
     * ([inkColumnRange]); this models that shape: one row of ink that starts inside the left slack and
     * runs past the advance box into the right one.
     */
    @Test
    fun theCropKeepsInkThatOverhangsTheAdvanceBox() {
        val margin = 10
        val advance = 7
        val width = advance + margin * 2
        val pixels = IntArray(width)
        // 1.53 em of ink for a 0.71 em advance, laid out the way the RTL paragraph places it: the ink
        // begins *left* of the advance box by more than the box is wide.
        for (x in margin - 3 until margin + advance + 6) {
            pixels[x] = OPAQUE
        }
        val ink = inkColumnRange(width, 1, pixels)!!
        assertTrue(
            "the ink ($ink) must be wider than the advance box (<$advance) the crop used to be cut to",
            ink.last - ink.first + 1 > advance,
        )
    }

    /**
     * Ink from **every** row widens the crop.
     *
     * `Bitmap.getPixels` fills row-major, so index = `y * width + x`. A scan that assumed one row — or
     * that walked the array without dividing by the width — would clip a descender that only reaches
     * the last row, which is where a Nastaliq tail ends up.
     */
    @Test
    fun inkIsTakenFromEveryRowOfTheBitmap() {
        val width = 5
        val height = 3
        val pixels = IntArray(width * height)
        pixels[0 * width + 1] = OPAQUE
        pixels[2 * width + 4] = OPAQUE

        assertEquals(1..4, inkColumnRange(width, height, pixels))
    }

    /**
     * Alpha alone decides, and a column either side of the ink is dropped.
     *
     * `0x00FFFFFF` is a fully transparent white, not ink — the crop must not be widened by a colour
     * that cannot be seen on the widget.
     */
    @Test
    fun alphaDecidesTheCropAndColourDoesNot() {
        val pixels = intArrayOf(0x00000000, 0xFF123456.toInt(), 0x00FFFFFF)

        assertEquals(1..1, inkColumnRange(width = 3, height = 1, pixels = pixels))
    }

    /**
     * Nothing painted has no crop rather than a 0-wide one.
     *
     * `rasterizeScriptText` falls back to the advance box on `null`; a `0..-1` or `0..0` answer would
     * instead build a bitmap the launcher measures as empty.
     */
    @Test
    fun anUnpaintedBufferHasNoInkAtAll() {
        assertNull("nothing painted anywhere", inkColumnRange(width = 4, height = 2, pixels = IntArray(8)))
    }

    /**
     * The other half of the same crop: the **leading band** above a raster's ink.
     *
     * A bundled Nastaliq line's declared box is 2.5 em while its ink is far shorter, and the grid's
     * chrome — its two title lines, its weekday row and its footer — is a fixed cost against six
     * weighted day rows, so that empty band is height the cells never get. `trimScriptLeading` drops
     * the rows above the first that painted and keeps the bottom at the layout's descent, which is a
     * constant offset below the baseline and therefore what still aligns a bottom-aligned row of
     * labels. `Bitmap.getPixels` fills row-major, so this scans row by row where [inkColumnRange]
     * scans column by column.
     */
    @Test
    fun theLeadingBandAboveTheInkIsFoundRowWise() {
        val width = 4
        val height = 6
        val pixels = IntArray(width * height)
        // Ink in two non-adjacent rows: a scan that stopped at the first painted row would answer
        // `3..3` and a later crop to `first..last` would then cut the second one off.
        pixels[3 * width + 0] = OPAQUE
        pixels[5 * width + 3] = OPAQUE

        assertEquals("ink from every row, not just the first", 3..5, inkRowRange(width, height, pixels))
        assertEquals("and the trim starts where the ink does", 3, inkRowRange(width, height, pixels)!!.first)
    }

    @Test
    fun aRowWithNoInkIsNullRatherThanAnEmptyRange() {
        assertNull("nothing painted anywhere", inkRowRange(width = 4, height = 6, pixels = IntArray(24)))
        // Transparent white is not ink: alpha alone decides here too.
        assertNull("transparent every row", inkRowRange(2, 2, intArrayOf(0x00FFFFFF, 0x00FFFFFF, 0, 0)))
    }

    /**
     * The trimmed band is *shorter* than the declared box under Nastaliq and identical to it otherwise.
     *
     * The two functions differ only where a raster exists: with the bundled face off a slot is Glance's
     * own `Text` and there is no band to drop, so the row's rule must keep the ratio it always had.
     * Pinned because a scan that used the ink band ratio on the Glance path would shrink the rule on
     * every English widget, and the failure would be a rule that no longer spans its labels.
     */
    @Test
    fun theInkBandRatioBracketsTheDeclaredLineBox() {
        val system = HijriWidgetFonts()
        val bundled = HijriWidgetFonts(bundledUrduFont = true)

        assertEquals(
            "with no raster there is no band to drop, so the rule keeps the ratio it always had",
            system.scriptLineRatio().toDouble(),
            system.scriptInkBandRatio().toDouble(),
            0.0001,
        )
        assertTrue(
            "under Nastaliq the ink band is shorter than the declared box (${bundled.scriptInkBandRatio()} " +
                "vs ${bundled.scriptLineRatio()})",
            bundled.scriptInkBandRatio() < bundled.scriptLineRatio(),
        )
        assertTrue(
            "and it still reserves more than the ink of the tallest measured label (" +
                "1.5 em of ink + 0.596 em of descent)",
            bundled.scriptInkBandRatio() > 2f,
        )
    }

    private companion object {
        /** Opaque black: an interlaced ARGB the scan reads as painted. */
        const val OPAQUE = 0xFF000000.toInt()
    }
}
