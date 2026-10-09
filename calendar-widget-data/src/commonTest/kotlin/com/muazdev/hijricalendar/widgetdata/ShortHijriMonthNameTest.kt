package com.muazdev.hijricalendar.widgetdata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * FD-10: `TodayHijriWidgetData.hijriMonthShortName` — the abbreviated Hijri month name the dual-date
 * tile puts in its header band.
 *
 * The band is narrow, and the long form of `ربیع الثانی` or `Jumada al-akhirah` does not fit it at a
 * readable size. The rule is deliberately small: **four** months get a short form, because those four
 * are the ones whose name does not distinguish them from a sibling. The other eight render their
 * ordinary name, unchanged.
 *
 * Three properties are asserted here, and each is a way the rule can go quietly wrong:
 *
 * 1. **All twelve months, in both languages.** A rule that only shortens months 3-6 by accident
 *    shortens others too — and a widget showing `رمضان ۱` because the code appended an ordinal to
 *    everything looks like a localization decision rather than a bug.
 * 2. **The base name is the *shared* one, not the full name.** `ربیع الثانی ٢` is not a short form of
 *    anything, and it is exactly the string the band is too narrow for.
 * 3. **The ordinal follows the widget's [NumeralStyle], not the device** — the same rule every other
 *    numeral in the projection obeys, and the reason the digit is formatted by the projection at all
 *    rather than by [WidgetLocalization], which has no numeral style to hand.
 *
 * **Note on argument order.** `kotlin.test` takes the message *last*; JUnit's `assertTrue` takes it
 * first. Several tests in this module use the latter — see [EraMarkersTest] for the convention being
 * worth stating rather than discovering.
 */
class ShortHijriMonthNameTest {

    /**
     * The whole table, both languages, asserted as literals rather than against the name lists.
     *
     * Asserting against `WidgetLocalization.englishHijriMonthNames` would prove only that the short
     * form is a substring of the long one, which is not the property that matters. These are written
     * out so a change to a name list, or to the short-form rule, cannot quietly alter the other.
     */
    @Test
    fun allTwelveMonthsRenderTheExpectedShortFormInBothLanguages() {
        val expectedUrdu = listOf(
            "محرم", // 1  Muharram
            "صفر", // 2  Safar
            "ربیع ۱", // 3  Rabi' I    — paired
            "ربیع ۲", // 4  Rabi' II   — paired
            "جمادی ۱", // 5  Jumada I   — paired
            "جمادی ۲", // 6  Jumada II  — paired
            "رجب", // 7  Rajab
            "شعبان", // 8  Sha'ban
            "رمضان", // 9  Ramadan
            "شوال", // 10 Shawwal
            "ذی القعدہ", // 11 Dhu al-Qa'dah
            "ذی الحجہ", // 12 Dhu al-Hijjah
        )
        val expectedEnglish = listOf(
            "Muharram",
            "Safar",
            "Rabi' 1", // paired
            "Rabi' 2", // paired
            "Jumada 1", // paired
            "Jumada 2", // paired
            "Rajab",
            "Sha'ban",
            "Ramadan",
            "Shawwal",
            "Dhu al-Qa'dah",
            "Dhu al-Hijjah",
        )

        for ((index, expected) in expectedUrdu.withIndex()) {
            assertEquals(
                shortName(index + 1, WidgetLanguage.URDU, NumeralStyle.ARABIC_INDIC),
                expected,
                "Urdu short month name for month ${index + 1}",
            )
        }
        for ((index, expected) in expectedEnglish.withIndex()) {
            assertEquals(
                shortName(index + 1, WidgetLanguage.ENGLISH, NumeralStyle.WESTERN),
                expected,
                "English short month name for month ${index + 1}",
            )
        }
    }

    /**
     * The eight months with no sibling render their **ordinary** name, byte for byte.
     *
     * Asserted separately from the table above, because this is the half of the rule that has no
     * visible failure: a month that gained a stray ordinal still reads as a month name, so only an
     * equality check catches it.
     */
    @Test
    fun theEightUnpairedMonthsAreUntouched() {
        val paired = WidgetLocalization.pairedHijriMonths

        assertEquals(
            setOf(3, 4, 5, 6),
            paired,
            "the rule must cover exactly the four months that share a name",
        )

        for (month in 1..12) {
            val long = monthName(month, WidgetLanguage.ENGLISH)
            val short = shortName(month, WidgetLanguage.ENGLISH, NumeralStyle.WESTERN)
            if (month in paired) {
                assertFalse(
                    short == long,
                    "month $month is paired, so its short form must not be the full name '$long'",
                )
            } else {
                assertEquals(
                    long,
                    short,
                    "month $month has no sibling, so its short form must be its ordinary name",
                )
            }
        }
    }

    /**
     * A paired month is the **shared base** plus a digit — never the full name with a digit appended.
     *
     * The failure this pins is `ربیع الثانی ٢` / `Jumada al-akhirah 2`: long enough to defeat the
     * point of the short form, and looking entirely plausible in a header band until it is measured.
     */
    @Test
    fun aPairedMonthNeverCarriesItsLongName() {
        for (month in WidgetLocalization.pairedHijriMonths) {
            for (language in WidgetLanguage.entries) {
                val style = styleFor(language)
                val long = monthName(month, language)
                val short = shortName(month, language, style)

                assertFalse(
                    short.contains(long),
                    "month $month in $language: the short form '$short' still contains the long " +
                        "name '$long'",
                )
                val base = WidgetLocalization.pairedHijriMonthBase(month, language)
                assertNotNull(base, "month $month in $language should have a shared base name")
                assertTrue(
                    short.startsWith(base),
                    "month $month in $language: the short form '$short' should start with the " +
                        "shared base name '$base'",
                )
            }
        }
    }

    /**
     * The ordinal is a **number in the widget's numeral style**, not a literal `1`/`2`.
     *
     * A Western-digit Urdu widget showing `ربیع 2` beside `۲۱` is internally inconsistent in a way
     * that reads as a font problem rather than a data problem, which is why this is asserted rather
     * than left to a screenshot.
     */
    @Test
    fun theOrdinalFollowsTheWidgetsNumeralStyle() {
        // Months 4 and 6 are the only paired months whose ordinal is `2`, so they are the ones that
        // distinguish the two digit systems.
        assertEquals(
            "ربیع ۲",
            shortName(4, WidgetLanguage.URDU, NumeralStyle.ARABIC_INDIC),
            "Rabi' II with Eastern digits",
        )
        assertEquals(
            "ربیع 2",
            shortName(4, WidgetLanguage.URDU, NumeralStyle.WESTERN),
            "Rabi' II with Western digits",
        )
        assertEquals(
            "جمادی ۱",
            shortName(5, WidgetLanguage.URDU, NumeralStyle.ARABIC_INDIC),
            "Jumada I with Eastern digits",
        )
        assertEquals(
            "جمادی 1",
            shortName(5, WidgetLanguage.URDU, NumeralStyle.WESTERN),
            "Jumada I with Western digits",
        )

        // And the unpaired months are unaffected by the style, since they carry no digit at all.
        assertEquals(
            "رمضان",
            shortName(9, WidgetLanguage.URDU, NumeralStyle.WESTERN),
            "Ramadan has no ordinal, so its numeral style cannot change it",
        )
    }

    /**
     * The short form follows `monthNameLanguage`, which is **not** `language`.
     *
     * They are separate options, and a widget is explicitly allowed to pair Urdu weekday names and
     * digits with English month names. Reading `language` here would put `ربیع ۱` in the band of a
     * widget that asked for English months — the same defect FD-05 fixed for era markers, where using
     * `language` produced "April - May 2026 ء".
     */
    @Test
    fun theShortFormFollowsTheMonthNameLanguageNotTheWidgetsLanguage() {
        // Language = Urdu (Eastern digits, RTL), month names = English. The band must read English,
        // and the band is the one place the distinction is visible at a glance.
        val englishNamesInAnUrduWidget = createWidgetOptions(
            language = WidgetLanguage.URDU,
            monthNameLanguage = WidgetLanguage.ENGLISH,
            numeralStyle = NumeralStyle.ARABIC_INDIC,
        )
        val data = todayHijriWidgetData(anchorEpochDay = ANCHOR, options = englishNamesInAnUrduWidget)

        assertNotNull(data, "the projection produced no today record")
        val shortNameInEnglish = shortName(
            data.hijriMonth,
            WidgetLanguage.ENGLISH,
            NumeralStyle.ARABIC_INDIC,
        )
        assertEquals(
            shortNameInEnglish,
            data.hijriMonthShortName,
            "the short name for month ${data.hijriMonth} must follow monthNameLanguage, not language",
        )
    }

    /**
     * A month with no name at all has no short form either.
     *
     * A band reading just `۲` is a number with nothing saying which month it belongs to, which is
     * worse than an empty band: it looks populated. Reached through the public seam with a blank
     * [WidgetOptions.hijriMonthName] name — the projection normalises a short list back to the
     * built-in names (WD-04), so this branch is not reachable from a `null` or truncated list.
     */
    @Test
    fun aMonthWithNoNameHasNoShortFormEither() {
        assertEquals(
            "",
            shortName(4, WidgetLanguage.ENGLISH, NumeralStyle.WESTERN, monthNameOverride = ""),
            "a blank month name must not gain an ordinal",
        )
        assertEquals(
            "",
            shortName(3, WidgetLanguage.URDU, NumeralStyle.ARABIC_INDIC, monthNameOverride = ""),
            "a blank month name must not gain an ordinal in Urdu either",
        )
    }

    /**
     * The projection is what populates the field, for a real anchor.
     *
     * The rest of this file tests the rule directly, which leaves the wiring unproven: a field the
     * builder forgets to set still satisfies every other test here, because they all call
     * [WidgetLocalization.hijriMonthShortName] themselves. This is the assertion that the value
     * actually arrives on [TodayHijriWidgetData].
     */
    @Test
    fun theProjectionPopulatesTheShortNameFromTheMonthsOwnName() {
        val urdu = todayHijriWidgetData(
            anchorEpochDay = ANCHOR,
            options = createWidgetOptions(
                language = WidgetLanguage.URDU,
                numeralStyle = NumeralStyle.ARABIC_INDIC,
            ),
        )

        assertNotNull(urdu, "the projection produced no today record")
        assertTrue(
            urdu.hijriMonthShortName.isNotBlank(),
            "the short name must not be blank for a resolvable month",
        )
        assertEquals(
            shortName(urdu.hijriMonth, WidgetLanguage.URDU, NumeralStyle.ARABIC_INDIC),
            urdu.hijriMonthShortName,
            "the short form must be derived from the long form's month, not from a second lookup",
        )
    }

    /**
     * The long name survives alongside the short one.
     *
     * Additive, not replacing: the two other 1x1 tiles and the grid header still show
     * [TodayHijriWidgetData.hijriMonthName], and the short form is only shorter for four months in
     * two languages. A change that made the short form overwrite the long one would silently shorten
     * every other tile's month line.
     */
    @Test
    fun theLongNameIsStillThereAndIsADifferentStringForAPairedMonth() {
        val data = todayHijriWidgetData(anchorEpochDay = ANCHOR, options = createWidgetOptions())

        assertNotNull(data, "the projection produced no today record")
        assertTrue(data.hijriMonthName.isNotBlank(), "the long month name must survive")
        if (data.hijriMonth in WidgetLocalization.pairedHijriMonths) {
            assertFalse(
                data.hijriMonthShortName == data.hijriMonthName,
                "a paired month's short form must differ from its long name '${data.hijriMonthName}'",
            )
        } else {
            assertEquals(
                data.hijriMonthName,
                data.hijriMonthShortName,
                "an unpaired month's short form is its long name",
            )
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** The localized long name, straight from the shipped lists. */
    private fun monthName(month: Int, language: WidgetLanguage): String {
        val names = WidgetLocalization.hijriMonthNames(language)
            ?: WidgetLocalization.englishHijriMonthNames
        return names[month - 1]
    }

    /**
     * The short form for [month], through the same two steps the projection takes: the base-name
     * lookup in [WidgetLocalization], and an ordinal rendered in [style].
     *
     * The ordinal is rebuilt here rather than read off a production value so the *rule* is under
     * test; [theProjectionPopulatesTheShortNameFromTheMonthsOwnName] covers the wiring.
     */
    private fun shortName(
        month: Int,
        language: WidgetLanguage,
        style: NumeralStyle,
        monthNameOverride: String? = null,
    ): String {
        val name = monthNameOverride ?: monthName(month, language)
        if (name.isEmpty()) return ""
        // Only the second month of each pair carries a `2`.
        val second = month == WidgetLocalization.RABI_AL_THANI || month == WidgetLocalization.JUMADA_AL_AKHIRAH
        return WidgetLocalization.hijriMonthShortName(
            month = month,
            monthName = name,
            ordinal = ordinal(second, style),
            language = language,
        )
    }

    /** A digit in [style] — the same two values the projection's own `formatNumber` produces. */
    private fun ordinal(second: Boolean, style: NumeralStyle): String {
        val index = if (second) 2 else 1
        return if (style == NumeralStyle.ARABIC_INDIC) {
            EASTERN_DIGITS[index].toString()
        } else {
            index.toString()
        }
    }

    /** A language's own default numerals, so a test reads the way the widget would be configured. */
    private fun styleFor(language: WidgetLanguage): NumeralStyle =
        WidgetLocalization.defaultNumeralStyle(language)

    private companion object {
        /** 2024-03-15, comfortably inside the supported Umm al-Qura window. */
        const val ANCHOR = 19_830L

        /** `۰۱۲۳۴۵۶۷۸۹` — restated so the test does not depend on the projection's private copy. */
        const val EASTERN_DIGITS = "۰۱۲۳۴۵۶۷۸۹"
    }
}
