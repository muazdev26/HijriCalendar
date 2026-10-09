package com.muazdev.hijricalendar.widgetdata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * FD-05: era markers — `AH` / `AD` in English, `ھ` / `ء` in Urdu.
 *
 * The reason this exists at all: a Hijri year and a Gregorian year are both four digits, and this is
 * a **bilingual** calendar that shows one under the other. `١٤٤٨` above `2026` leaves a reader
 * guessing which is which; one glyph per year removes the ambiguity entirely.
 *
 * Two properties are asserted here that are easy to get wrong and impossible to see in a screenshot:
 *
 * 1. **The right codepoint.** Urdu's *hijri sani* is `ھ` (U+06BE, Heh-goal), not `ہ` (U+06C1,
 *    do-chashmi he, which belongs to words). They are different characters and a wrong one is still a
 *    readable-looking glyph in Urdu, so nothing catches it but an assertion on the code point.
 * 2. **The marker follows the widget, not the device.** Per WG-12: a widget's language is a
 *    [WidgetOptions] field, and one Urdu widget is designed to sit beside an English one on a phone
 *    with no Urdu locale at all.
 */
class EraMarkersTest {

    @Test
    fun englishUsesLatinEraMarkers() {
        assertEquals("AH", WidgetLocalization.ChromeLabels.hijriEra(WidgetLanguage.ENGLISH))
        assertEquals("AD", WidgetLocalization.ChromeLabels.gregorianEra(WidgetLanguage.ENGLISH))
    }

    /**
     * Urdu's Hijri era is U+06BE, not U+06C1.
     *
     * Asserted on the code point rather than by eye, because the wrong character renders as a
     * perfectly plausible Urdu letter — this is precisely the class of defect a visual check cannot
     * catch.
     */
    @Test
    fun urduUsesTheHijriSaniCodepoint() {
        val hijri = WidgetLocalization.ChromeLabels.hijriEra(WidgetLanguage.URDU)

        assertEquals("ھ", hijri)
        // `code`, not `codePointAt`: the latter is JVM-only in the common stdlib, and U+06BE is inside
        // the Basic Multilingual Plane so a single UTF-16 unit *is* the code point here.
        assertEquals(
            0x06BE,
            hijri.first().code,
            "the Urdu Hijri era must be HEH GOAL (U+06BE), not HEH DOACHASHMEE (U+06C1)",
        )
        assertFalse(
            hijri.contains('ہ'),
            "U+06C1 is a word letter and must not be used as a year suffix",
        )
    }

    /**
     * The Gregorian era in Urdu is the standalone hamza, `ء` (U+0621).
     *
     * One character, and it balances the one-character `ھ` opposite it — both years then carry a
     * single glyph, which is what makes the pair readable at the small type a widget renders. The
     * two-character `ئے` that a literal transliteration of "era" suggests is *not* it.
     *
     * Asserted on the codepoint: the neighbouring Urdu letters are visually close, and a wrong one
     * still renders as plausible Urdu — which is exactly why this cannot be an eyeball check.
     */
    @Test
    fun urduUsesTheStandaloneHamzaForTheGregorianEra() {
        val gregorian = WidgetLocalization.ChromeLabels.gregorianEra(WidgetLanguage.URDU)

        assertEquals("ء", gregorian)
        assertEquals(
            0x0621,
            gregorian.first().code,
            "the Gregorian era must be the standalone hamza (U+0621), and must be a single character",
        )
        assertEquals(
            1,
            gregorian.length,
            "one character, to balance the one-character Hijri era beside it",
        )
    }

    /** Both supported languages write the era *after* the year, so there is no prefix case to get wrong. */
    @Test
    fun theEraFollowsTheYearInEveryLanguage() {
        for (language in WidgetLanguage.entries) {
            assertEquals(
                "1447 ${WidgetLocalization.ChromeLabels.hijriEra(language)}",
                WidgetLocalization.ChromeLabels.yearWithEra(1447, language, gregorian = false),
            )
            assertEquals(
                "2026 ${WidgetLocalization.ChromeLabels.gregorianEra(language)}",
                WidgetLocalization.ChromeLabels.yearWithEra(2026, language, gregorian = true),
            )
        }
    }

    /**
     * The marker follows the same locale as the month names it sits beside.
     *
     * An era marker is text, and text reads with the names next to it — so `monthNameLanguage`, not
     * `language`. The first version of this rule used `language`, which produced `April - May 2026 ء`
     * for exactly the widget that had asked for English month names: a Latin month range with an Urdu
     * suffix.
     *
     * `language` still decides layout direction and, together with `numeralStyle`, the digits — those
     * are the widget's presentation, not its vocabulary.
     */
    @Test
    fun theEraFollowsTheMonthNameLanguageSoItReadsWithTheNames() {
        val options = createWidgetOptions(
            language = WidgetLanguage.URDU,
            monthNameLanguage = WidgetLanguage.ENGLISH,
        )
        val month = assertNotNull(buildHijriMonthWidgetData(1447, 9, options))
        val today = assertNotNull(todayHijriWidgetData(anchorEpochDay = 20_731, options = options))

        assertEquals(
            "1447 AH",
            month.hijriYearText,
            "the era must be in the same script as the English month names beside it",
        )
        assertTrue(
            month.hijriMonthName.all { it.code < 0x80 },
            "and the month names must still be English, or this test is not exercising the split",
        )
        assertTrue(
            today.hijriYearText.endsWith(" AH"),
            "the today projection's Hijri year carries the same marker, was '${today.hijriYearText}'",
        )
        assertTrue(
            today.gregorianYearText.endsWith(" AD"),
            "and the Gregorian one its own, was '${today.gregorianYearText}'",
        )
    }

    @Test
    fun anUrduWidgetWritesTheUrduMarkers() {
        val options = createWidgetOptions(
            language = WidgetLanguage.URDU,
            numeralStyle = NumeralStyle.ARABIC_INDIC,
        )
        val month = assertNotNull(buildHijriMonthWidgetData(1447, 9, options))

        assertTrue(
            month.hijriYearText.endsWith("ھ"),
            "an Urdu widget's Hijri year must carry U+06BE, was '${month.hijriYearText}'",
        )
        // The digit style still applies to the year itself, era excluded.
        assertTrue(
            month.hijriYearText.first().code in 0x0660..0x0669,
            "the Urdu widget should still use Arabic-Indic digits (U+0660-0669) for the year, was " +
                "'${month.hijriYearText}'",
        )
    }

    /**
     * The bare year is still there for arithmetic.
     *
     * `hijriYearText` exists *alongside* `hijriYear`, not instead of it: a renderer's own month
     * arithmetic, a settings screen's year picker and iOS's navigation all read the number, and giving
     * them a string would have been a breaking change for no gain.
     */
    @Test
    fun theBareYearSurvivesAlongsideTheEraAppendedOne() {
        val options = createWidgetOptions(language = WidgetLanguage.ENGLISH)
        val month = assertNotNull(buildHijriMonthWidgetData(1447, 9, options))

        assertEquals(1447, month.hijriYear)
        assertEquals("1447 AH", month.hijriYearText)
    }

    /**
     * The Gregorian month title carries its own marker.
     *
     * It is the other half of the header's one line, so marking only the Hijri half would leave the
     * reader with `1447 AH · September 2026` — which is the ambiguity this change set out to remove,
     * just moved to the other number.
     */
    @Test
    fun theGregorianHalfOfTheHeaderIsMarkedToo() {
        val options = createWidgetOptions(language = WidgetLanguage.ENGLISH)
        val month = assertNotNull(buildHijriMonthWidgetData(1447, 9, options))

        assertTrue(
            month.gregorianMonthTitle.endsWith(" AD"),
            "the Gregorian half of the header needs its own marker, was " +
                "'${month.gregorianMonthTitle}'",
        )
    }

    /**
     * A caller that passes no marker gets a bare year, unchanged.
     *
     * The leaf `buildHijriMonthWidgetData` takes the era as a nullable parameter precisely so nothing
     * changes for a caller that has not opted in — the additive change it is meant to be.
     */
    @Test
    fun omittingTheMarkerLeavesTheYearBare() {
        val month = assertNotNull(
            buildHijriMonthWidgetData(
                hijriYear = 1447,
                hijriMonth = 9,
                adjustmentDays = 0,
            ),
        )
        assertEquals("1447", month.hijriYearText)
        assertFalse(
            month.gregorianMonthTitle.contains("AD"),
            "with no marker the Gregorian title must be exactly what it was before FD-05",
        )
    }
}
