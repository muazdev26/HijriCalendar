package com.muazdev.hijricalendar.widgetdata

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * `TodayHijriWidgetData.hijriMonthShortName` — the month name a 1x1 tile prints where a full one
 * would clip (FD-10).
 *
 * The rule is small and deliberately **asymmetric by language**:
 *
 * 1. **English shortens.** The four months whose name does not distinguish them from a sibling
 *    carry a Roman ordinal — `Rabi' I`, `Rabi' II`, `Jumada I`, `Jumada II` — and the other eight
 *    are the ordinary name, byte for byte.
 * 2. **Urdu never shortens.** For a non-English month-name language the value *is* the full month
 *    name: `ربیع الاول` in full, never `ربیع ١` — the same rule `gregorianMonthShortName`
 *    follows, and the host's decision restated in the field's own KDoc.
 * 3. **The projection decides, not the renderer** (WG-12): the month-name language is a
 *    `WidgetOptions` field, so iOS reads the same string with no Swift edit and no renderer can
 *    pick a language on its own.
 *
 * Each of those is a way the rule can go quietly wrong, so each has a test:
 *
 * 1. **All twelve months, in both languages.** A rule that appends an ordinal to everything looks
 *    like a localization decision rather than a bug — a widget showing `رمضان` with a `١` glued on
 *    still reads as a month name at a glance.
 * 2. **Urdu equals the full name exactly** (in a paired month, under both numeral styles) — the
 *    four paired months are precisely where "shorten the ambiguous four" overreaches into a
 *    language that was never asked for shortening.
 * 3. **The English ordinal is Roman and ignores [NumeralStyle]** — `Rabi' 1` beside English month
 *    names reads as "the first Rabi' in a list" rather than as the month's name, and a numeral
 *    style is figures while `I`/`II` are letters.
 *
 * And [theOrdinalIsTakenVerbatimFromTheCaller] pins *why* the ordinal choice belongs to the
 * projection: the primitive itself appends whatever it is handed, nothing more.
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
     * out so a change to a name list, or to the short-form rule, cannot quietly alter the other —
     * and they are read off the **projection**, not off a restatement of the rule, so the wiring and
     * the rule are under test at once for every month.
     */
    @Test
    fun allTwelveMonthsRenderTheExpectedShortFormInBothLanguages() {
        val expectedUrdu = listOf(
            "محرم", // 1  Muharram
            "صفر", // 2  Safar
            "ربیع الاول", // 3  Rabi' al-awwal  — paired, still full: Urdu never shortens
            "ربیع الثانی", // 4  Rabi' al-thani   — paired, still full
            "جمادى الأولى", // 5  Jumada al-ula     — paired, still full
            "جمادی الآخِرۃ", // 6  Jumada al-akhirah — paired, still full
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
            "Rabi' I", // paired
            "Rabi' II", // paired
            "Jumada I", // paired
            "Jumada II", // paired
            "Rajab",
            "Sha'ban",
            "Ramadan",
            "Shawwal",
            "Dhu al-Qa'dah",
            "Dhu al-Hijjah",
        )

        for (month in 1..12) {
            val anchor = anchorFor(month)
            val english = assertNotNull(
                todayHijriWidgetData(anchorEpochDay = anchor, options = englishOptions),
                "the projection produced no today record for month $month",
            )
            assertEquals(
                expectedEnglish[month - 1],
                english.hijriMonthShortName,
                "English short month name for month $month",
            )

            val urdu = assertNotNull(
                todayHijriWidgetData(anchorEpochDay = anchor, options = urduOptions(NumeralStyle.ARABIC_INDIC)),
                "the projection produced no today record for month $month",
            )
            assertEquals(
                expectedUrdu[month - 1],
                urdu.hijriMonthShortName,
                "Urdu short month name for month $month — Urdu month names are never shortened",
            )
        }
    }

    /**
     * The eight months with no sibling render their **ordinary** name, byte for byte.
     *
     * Asserted separately from the table above, because this is the half of the rule that has no
     * visible failure: a month that gained a stray ordinal still reads as a month name, so only an
     * equality check catches it — and here it is checked against the shipped list itself rather
     * than against a second copy of it.
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
            val short = assertNotNull(
                todayHijriWidgetData(anchorEpochDay = anchorFor(month), options = englishOptions),
                "the projection produced no today record for month $month",
            ).hijriMonthShortName
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
     * In English, a paired month is the **shared base** plus a Roman numeral — never the full name
     * with anything appended.
     *
     * The failure this pins is `Jumada al-akhirah I`: long enough to defeat the point of the short
     * form, and looking entirely plausible in a tile caption until it is measured. Urdu is
     * deliberately *not* looped here — its paired months equal the full name by design, which is
     * what [theProjectionNeverShortensUrduMonthNames] asserts.
     */
    @Test
    fun aPairedMonthNeverCarriesItsLongName() {
        for (month in WidgetLocalization.pairedHijriMonths) {
            val data = assertNotNull(
                todayHijriWidgetData(anchorEpochDay = anchorFor(month), options = englishOptions),
                "the projection produced no today record for month $month",
            )
            val long = monthName(month, WidgetLanguage.ENGLISH)
            val short = data.hijriMonthShortName

            assertFalse(
                short.contains(long),
                "month $month: the short form '$short' still contains the long name '$long'",
            )
            val base = WidgetLocalization.pairedHijriMonthBase(month, WidgetLanguage.ENGLISH)
            assertNotNull(base, "month $month should have a shared base name")
            assertTrue(
                short.startsWith(base),
                "month $month: the short form '$short' should start with the shared base name '$base'",
            )
        }
    }

    /**
     * **Urdu month names are never shortened** — not for the paired four, not under either numeral
     * style.
     *
     * This is the host's decision, stated in [TodayHijriWidgetData.hijriMonthShortName]'s KDoc, and
     * the paired months are where the old rule overreached: `ربیع ١` was a code built for the Latin
     * spelling's ambiguity, and a reader of Urdu reads `ربیع الاول` in full. Both numeral styles are
     * looped because the value must be byte-identical to the full name regardless — a shortening
     * that only appeared under one style would be a stranger bug, still.
     */
    @Test
    fun theProjectionNeverShortensUrduMonthNames() {
        for (month in WidgetLocalization.pairedHijriMonths) {
            for (style in NumeralStyle.entries) {
                val data = assertNotNull(
                    todayHijriWidgetData(
                        anchorEpochDay = anchorFor(month),
                        options = urduOptions(style),
                    ),
                    "the projection produced no today record for month $month",
                )
                val full = monthName(month, WidgetLanguage.URDU)
                assertEquals(
                    full,
                    data.hijriMonthName,
                    "setup: month $month must resolve to its full Urdu name",
                )
                assertEquals(
                    full,
                    data.hijriMonthShortName,
                    "month $month with $style: an Urdu month name must never be shortened — got " +
                        "'${data.hijriMonthShortName}'",
                )
            }
        }
    }

    /**
     * The English ordinal is **Roman**, and [NumeralStyle] cannot change it.
     *
     * The style is looped because it is the *only* widget option that could plausibly leak into
     * this string: every other numeral in the projection obeys it, and this one must not — `Rabi' 1`
     * beside English month names reads as "the first Rabi' in a list" rather than as the month's
     * name, while `I`/`II` are Latin letters that neither digit system translates.
     */
    @Test
    fun theProjectionSpellsTheEnglishOrdinalAsARomanNumeral() {
        val expected = mapOf(
            3 to "Rabi' I",
            4 to "Rabi' II",
            5 to "Jumada I",
            6 to "Jumada II",
        )
        for (month in WidgetLocalization.pairedHijriMonths) {
            for (style in NumeralStyle.entries) {
                val data = assertNotNull(
                    todayHijriWidgetData(
                        anchorEpochDay = anchorFor(month),
                        options = createWidgetOptions(
                            language = WidgetLanguage.ENGLISH,
                            monthNameLanguage = WidgetLanguage.ENGLISH,
                            numeralStyle = style,
                        ),
                    ),
                    "the projection produced no today record for month $month",
                )
                assertEquals(
                    expected.getValue(month),
                    data.hijriMonthShortName,
                    "month $month with $style must still spell its ordinal as a Roman numeral",
                )
            }
        }
    }

    /**
     * The short form follows `monthNameLanguage`, which is **not** `language`.
     *
     * They are separate options, and a widget is explicitly allowed to pair Urdu weekday names and
     * digits with English month names. Reading `language` here would put the Urdu full name
     * `ربیع الثانی` in the caption of a widget that asked for English months — the same defect FD-05
     * fixed for era markers, where using `language` produced "April - May 2026 ء".
     */
    @Test
    fun theShortFormFollowsTheMonthNameLanguageNotTheWidgetsLanguage() {
        // Language = Urdu (Eastern digits, RTL), month names = English. The caption must read English.
        val englishNamesInAnUrduWidget = createWidgetOptions(
            language = WidgetLanguage.URDU,
            monthNameLanguage = WidgetLanguage.ENGLISH,
            numeralStyle = NumeralStyle.ARABIC_INDIC,
        )
        val data = assertNotNull(
            todayHijriWidgetData(anchorEpochDay = ANCHOR, options = englishNamesInAnUrduWidget),
            "the projection produced no today record",
        )

        assertEquals(
            englishShortName(data.hijriMonth, monthName(data.hijriMonth, WidgetLanguage.ENGLISH)),
            data.hijriMonthShortName,
            "the short name for month ${data.hijriMonth} must follow monthNameLanguage, not language",
        )
    }

    /**
     * A month with no name at all has no short form either.
     *
     * A caption reading just `٢` is a number with nothing saying which month it belongs to, which
     * is worse than an empty caption: it looks populated. Reached through the public seam with a
     * full-length list of blank names — the projection discards lists that are *shorter* than
     * twelve (WD-04), so a `null` or truncated list falls back to the built-in names, but an
     * all-blank one of full length passes through.
     */
    @Test
    fun aMonthWithNoNameHasNoShortFormEither() {
        val data = assertNotNull(
            todayHijriWidgetData(
                anchorEpochDay = ANCHOR,
                adjustmentDays = 0,
                localizedHijriMonthNames = List(12) { "" },
                monthNameLanguage = WidgetLanguage.ENGLISH,
            ),
            "the projection produced no today record",
        )

        assertEquals(
            "",
            data.hijriMonthName,
            "setup: the blank list must have been used as given, not replaced by the defaults",
        )
        assertEquals(
            "",
            data.hijriMonthShortName,
            "a blank month name must not gain an ordinal",
        )
    }

    /**
     * The projection is what populates the field, for a real anchor in a paired month.
     *
     * The table above pins *what* the strings are; this pins *where they come from*: the short form
     * is derived from the same month number the long name resolved to, computed through a
     * restatement of the English rule — a builder that forgot the field, or filled it from a second
     * lookup, fails here while every literal table still passes.
     */
    @Test
    fun theProjectionPopulatesTheShortNameFromTheMonthsOwnName() {
        val data = assertNotNull(
            todayHijriWidgetData(anchorEpochDay = anchorFor(3), options = englishOptions),
            "the projection produced no today record",
        )

        assertTrue(
            data.hijriMonthShortName.isNotBlank(),
            "the short name must not be blank for a resolvable month",
        )
        assertEquals(
            englishShortName(data.hijriMonth, monthName(data.hijriMonth, WidgetLanguage.ENGLISH)),
            data.hijriMonthShortName,
            "the short form must be derived from the long form's month, not from a second lookup",
        )
    }

    /**
     * The long name survives alongside the short one.
     *
     * Additive, not replacing: the grid header and the today strip's wider rungs still show
     * [TodayHijriWidgetData.hijriMonthName], and the short form is shorter for four months, and
     * only in English. A change that made the short form overwrite the long one would silently
     * shorten every other slot's month line.
     */
    @Test
    fun theLongNameIsStillThereAndIsADifferentStringForAPairedMonth() {
        val data = assertNotNull(
            todayHijriWidgetData(anchorEpochDay = ANCHOR, options = createWidgetOptions()),
            "the projection produced no today record",
        )

        assertTrue(data.hijriMonthName.isNotBlank(), "the long month name must survive")
        if (data.hijriMonth in WidgetLocalization.pairedHijriMonths) {
            assertFalse(
                data.hijriMonthShortName == data.hijriMonthName,
                "a paired month's English short form must differ from its long name " +
                    "'${data.hijriMonthName}'",
            )
        } else {
            assertEquals(
                data.hijriMonthName,
                data.hijriMonthShortName,
                "an unpaired month's short form is its long name",
            )
        }
    }

    /**
     * The primitive appends **exactly** what it is given — it never derives, formats or translates
     * an ordinal, and an unpaired month ignores the ordinal entirely.
     *
     * This is *why* the choice of ordinal lives in the projection rather than here: the projection
     * picks Roman for English and hands Urdu nothing at all (Urdu month names are never shortened),
     * while this function's whole contract is base + caller's ordinal. A primitive that started
     * formatting its own digits would silently re-import the device's locale question (WG-12) and
     * undo the asymmetry the tests above pin.
     */
    @Test
    fun theOrdinalIsTakenVerbatimFromTheCaller() {
        assertEquals(
            "Rabi' 42",
            WidgetLocalization.hijriMonthShortName(
                month = 3,
                monthName = "Rabi' al-awwal",
                ordinal = "42",
                language = WidgetLanguage.ENGLISH,
            ),
            "the primitive appends the caller's ordinal verbatim",
        )
        assertEquals(
            "ربیع ۴۲",
            WidgetLocalization.hijriMonthShortName(
                month = 4,
                monthName = "ربیع الثانی",
                ordinal = "۴۲",
                language = WidgetLanguage.URDU,
            ),
            "the primitive does not inspect the script it is handed either",
        )
        assertEquals(
            "رمضان",
            WidgetLocalization.hijriMonthShortName(
                month = 9,
                monthName = "رمضان",
                ordinal = "٩",
                language = WidgetLanguage.URDU,
            ),
            "an unpaired month ignores the ordinal entirely",
        )
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** The localized long name, straight from the shipped lists. */
    private fun monthName(month: Int, language: WidgetLanguage): String {
        val names = WidgetLocalization.hijriMonthNames(language)
            ?: WidgetLocalization.englishHijriMonthNames
        return names[month - 1]
    }

    /**
     * The projection's **English** rule restated: shared base plus the Roman ordinal.
     *
     * Restated rather than read off production so a test can assert against a second
     * implementation instead of the value under test; the literal tables pin the strings
     * themselves, and [theProjectionPopulatesTheShortNameFromTheMonthsOwnName] uses this to
     * prove the wiring.
     */
    private fun englishShortName(month: Int, monthName: String): String {
        if (monthName.isEmpty()) return ""
        // Only the second month of each pair carries a `II`.
        val second = month == WidgetLocalization.RABI_AL_THANI || month == WidgetLocalization.JUMADA_AL_AKHIRAH
        return WidgetLocalization.hijriMonthShortName(
            month = month,
            monthName = monthName,
            ordinal = if (second) "II" else "I",
            language = WidgetLanguage.ENGLISH,
        )
    }

    private fun urduOptions(style: NumeralStyle): WidgetOptions = createWidgetOptions(
        language = WidgetLanguage.URDU,
        monthNameLanguage = WidgetLanguage.URDU,
        numeralStyle = style,
    )

    private companion object {
        /** 2024-03-15, comfortably inside the supported Umm al-Qura window. */
        const val ANCHOR = 19_830L

        /** English month names — the only language the short form exists for. */
        val englishOptions: WidgetOptions = createWidgetOptions(
            language = WidgetLanguage.ENGLISH,
            monthNameLanguage = WidgetLanguage.ENGLISH,
        )

        /**
         * One epoch day per Hijri month (1..12), found by asking the projection itself rather than
         * by writing dates down: which Gregorian day a Hijri month starts on is the calendar's own
         * arithmetic — and the default source is Pakistan, not Umm al-Qura — so a literal date
         * pinned here would fail on a calendar correction rather than on a rule change.
         *
         * Scanned once per class ([lazy] lives on the companion, while JUnit creates an instance
         * per test method). The window is longer than a lunar year, so every month must appear.
         */
        val monthAnchors: Map<Int, Long> by lazy {
            val start = LocalDate(2024, 8, 25).toEpochDays()
            val found = linkedMapOf<Int, Long>()
            var offset = 0
            while (found.size < 12 && offset < 400) {
                val epoch = start + offset
                val month = todayHijriWidgetData(
                    anchorEpochDay = epoch,
                    options = createWidgetOptions(),
                )?.hijriMonth
                if (month != null && month !in found) {
                    found[month] = epoch
                }
                offset++
            }
            found
        }

        /** An epoch day inside [month], or a loud failure — a missing month means the scan broke. */
        fun anchorFor(month: Int): Long =
            monthAnchors[month] ?: error("no day inside Hijri month $month in the scan window")
    }
}
