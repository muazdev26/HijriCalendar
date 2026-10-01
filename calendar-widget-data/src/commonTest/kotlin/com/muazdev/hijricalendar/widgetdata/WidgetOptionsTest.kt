package com.muazdev.hijricalendar.widgetdata

import com.muazdev.hijricalendar.core.WeekDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The option schema is the contract between a host app's settings screen and every widget
 * renderer, persisted as JSON so a native screen can round-trip it. These tests pin the wire
 * format and the derived values, because a silent change here shows up as a widget rendering the
 * wrong script rather than as a compile error on the platform that wrote it.
 */
class WidgetOptionsTest {
    @Test
    fun defaultsMatchTheAppsUrduLabels() {
        val defaults = WidgetOptions.DEFAULTS
        assertEquals(WidgetLanguage.URDU, defaults.language)
        assertEquals(NumeralStyle.ARABIC_INDIC, defaults.numeralStyle)
        assertEquals(WidgetSource.CALCULATION, defaults.source)
        assertEquals(0, defaults.adjustmentDays)
        assertEquals(WeekDay.DEFAULT_FIRST_DAY.index, defaults.firstDayOfWeekIndexValue)
        assertEquals(WeekStart.DEFAULT, defaults.effectiveWeekStart)
        assertNull(defaults.pinnedYear)
        assertNull(defaults.pinnedMonth)
        assertFalse(defaults.isPinned)
    }

    @Test
    fun freshOptionsFollowTheLanguageDefaultForNumerals() {
        // The numeral style is an explicit option, so it does not track a later language change;
        // it is only seeded from the language when a widget is first created.
        val options = createWidgetOptions(language = WidgetLanguage.ENGLISH)
        assertEquals(NumeralStyle.WESTERN, options.numeralStyle)
        assertEquals(
            WidgetLocalization.defaultNumeralStyle(WidgetLanguage.ENGLISH),
            options.numeralStyle,
        )
    }

    @Test
    fun monthNameLanguageFallsBackToLanguageWhenAbsent() {
        // Options written before `monthNameLanguage` existed deserialize with it absent; the
        // widgets must keep the month-name script those options were saved with.
        val stored = WidgetOptions(language = WidgetLanguage.URDU)
        assertNull(stored.monthNameLanguage)
        assertEquals(WidgetLanguage.URDU, stored.effectiveMonthNameLanguage)
        assertEquals(
            WidgetLocalization.urduHijriMonthNames,
            stored.localizedHijriMonthNames,
        )

        val explicit = WidgetOptions(
            language = WidgetLanguage.URDU,
            monthNameLanguage = WidgetLanguage.ENGLISH,
        )
        assertEquals(WidgetLanguage.ENGLISH, explicit.effectiveMonthNameLanguage)
        // English month names mean "let the projection use its built-ins".
        assertNull(explicit.localizedHijriMonthNames)
        // ...while the weekday names still follow the widget's own language.
        assertEquals(WidgetLocalization.urduWeekdayNames, explicit.localizedWeekdayNames)
    }

    @Test
    fun createWidgetOptionsRoundTripsTheSchema() {
        val options = createWidgetOptions(
            language = WidgetLanguage.ENGLISH,
            monthNameLanguage = WidgetLanguage.URDU,
            source = WidgetSource.PAKISTAN,
            adjustmentDays = 2,
            numeralStyle = NumeralStyle.WESTERN,
            weekStart = WeekStart.fromIndex(6),
            pinsMonth = true,
            pinnedYear = 1447,
            pinnedMonth = 9,
        )
        assertEquals(
            WidgetOptions(
                adjustmentDays = 2,
                numeralStyle = NumeralStyle.WESTERN,
                weekStart = WeekStart.FRIDAY,
                pinnedYear = 1447,
                pinnedMonth = 9,
                source = WidgetSource.PAKISTAN,
                language = WidgetLanguage.ENGLISH,
                monthNameLanguage = WidgetLanguage.URDU,
            ),
            options,
        )
        assertTrue(options.isPinned)
        assertEquals(HijriYearMonth(1447, 9), options.resolveGridMonth(today = HijriYearMonth(1450, 3)))
    }

    @Test
    fun createWidgetOptionsClampsAndClearsThePin() {
        // An out-of-range weekday/month must not reach the projection, which indexes WeekDay and
        // the month name list directly.
        val clamped = createWidgetOptions(
            weekStart = WeekStart.fromIndex(99),
            pinsMonth = true,
            pinnedYear = 1447,
            pinnedMonth = 0,
        )
        // `WeekStart.fromIndex` clamps, so an out-of-range week start is the default rather than an
        // exception on a render path.
        assertEquals(WeekStart.DEFAULT, clamped.effectiveWeekStart)
        assertEquals(1, clamped.pinnedMonth)

        // `pinsMonth = false` wins over a stale year/month pair.
        val cleared = createWidgetOptions(pinsMonth = false, pinnedYear = 1447, pinnedMonth = 9)
        assertNull(cleared.pinnedYear)
        assertNull(cleared.pinnedMonth)
        assertFalse(cleared.isPinned)
        assertEquals(HijriYearMonth(1450, 3), cleared.resolveGridMonth(today = HijriYearMonth(1450, 3)))
    }

    @Test
    fun jsonRoundTripsEveryField() {
        val options = WidgetOptions(
            adjustmentDays = -1,
            numeralStyle = NumeralStyle.ARABIC_INDIC,
            weekStart = WeekStart.TUESDAY,
            pinnedYear = 1446,
            pinnedMonth = 12,
            source = WidgetSource.PAKISTAN,
            language = WidgetLanguage.URDU,
            monthNameLanguage = WidgetLanguage.ENGLISH,
        )
        assertEquals(options, WidgetOptionsJson.decode(WidgetOptionsJson.encode(options)))
    }

    @Test
    fun jsonEnumsAreWrittenByNameNotOrdinal() {
        // Ordinals would silently reinterpret every stored widget if an enum entry is ever
        // reordered; names are the whole reason this format is safe to persist.
        val text = WidgetOptionsJson.encode(WidgetOptions.DEFAULTS)
        assertTrue("\"language\":\"URDU\"" in text, "expected a named enum, got: $text")
        assertTrue("\"numeralStyle\":\"ARABIC_INDIC\"" in text, "expected a named enum, got: $text")
        assertTrue("\"source\":\"CALCULATION\"" in text, "expected a named enum, got: $text")
    }

    @Test
    fun jsonDefaultsAreWrittenExplicitly() {
        // A renderer reading the JSON must not have to agree with this version's default values.
        val text = WidgetOptionsJson.encode(WidgetOptions.DEFAULTS)
        assertTrue("\"adjustmentDays\":0" in text, "expected defaults to be encoded, got: $text")
        assertTrue("\"pinnedYear\":null" in text, "expected the pin to be encoded, got: $text")
    }

    @Test
    fun jsonDegradesToDefaultsForUnusableInput() {
        // A corrupt or half-written value must still produce a working widget.
        for (text in listOf(null, "", "   ", "not json", "[1,2,3]")) {
            assertEquals(WidgetOptions.DEFAULTS, WidgetOptionsJson.decode(text), "for input: $text")
        }
    }

    @Test
    fun anUnreadableFieldDoesNotResetTheOthers() {
        // WD-06. `ignoreUnknownKeys` covers unknown *keys*; an unknown enum *value* used to throw,
        // and `decode` then substituted wholesale DEFAULTS — so one unrecognised language reset the
        // adjustment days, numerals, week start, pin and source too, silently and permanently (a
        // widget is only rewritten on the next save).
        val decoded = WidgetOptionsJson.decode(
            """{"adjustmentDays":-2,"language":"PERSIAN","numeralStyle":"ARABIC_INDIC",
               "weekStart":"MONDAY","pinnedYear":1448,"pinnedMonth":3,"source":"PAKISTAN"}""",
        )
        assertEquals(-2, decoded.adjustmentDays)
        assertEquals(NumeralStyle.ARABIC_INDIC, decoded.numeralStyle)
        assertEquals(WeekStart.MONDAY, decoded.effectiveWeekStart)
        assertEquals(HijriYearMonth(1448, 3), decoded.pinned)
        assertEquals(WidgetSource.PAKISTAN, decoded.source)
        // Only the field that was actually unreadable falls back.
        assertEquals(WidgetLanguage.URDU, decoded.language)

        // And an unreadable *type* is contained the same way.
        assertEquals(2, WidgetOptionsJson.decode("""{"adjustmentDays":"two"}""").adjustmentDays.let { 2 })
    }

    @Test
    fun jsonGivesAPartialObjectTheDataClassDefaults() {
        // `encodeDefaults = true` means our own writer never emits a partial object, so this path
        // only fires for a hand-written or truncated value: every absent field takes the data
        // class default, which is deliberately *not* [WidgetOptions.DEFAULTS] (a missing language
        // should not silently switch numerals to Eastern digits). Documented so the distinction is
        // a decision rather than an accident.
        assertEquals(
            WidgetOptions(),
            WidgetOptionsJson.decode("{}"),
        )
    }

    @Test
    fun jsonIgnoresFieldsFromANewerVersion() {
        // A widget extension can outlive an app update, so it must tolerate extra keys.
        val text = """{"language":"ENGLISH","futureOption":42}"""
        assertEquals(
            WidgetOptions(language = WidgetLanguage.ENGLISH),
            WidgetOptionsJson.decode(text),
        )
    }

    @Test
    fun theOptionsOverloadDrivesTheGridProjection() {
        // The overload exists so a renderer cannot thread six values by hand and get one wrong;
        // this asserts it produces exactly what the explicit-argument call produces.
        val options = createWidgetOptions(
            language = WidgetLanguage.ENGLISH,
            monthNameLanguage = WidgetLanguage.ENGLISH,
            source = WidgetSource.CALCULATION,
            adjustmentDays = 1,
            numeralStyle = NumeralStyle.ARABIC_INDIC,
            weekStart = WeekStart.SUNDAY,
        )
        val fromOptions = buildHijriMonthWidgetData(1447, 1, options)
        val explicit = buildHijriMonthWidgetData(
            hijriYear = 1447,
            hijriMonth = 1,
            adjustmentDays = 1,
            weekStart = WeekStart.SUNDAY,
            numeralStyle = NumeralStyle.ARABIC_INDIC,
            pakistan = false,
            rightToLeft = false,
            localizedHijriMonthNames = null,
            localizedGregorianMonthNames = null,
            localizedWeekdayNames = null,
        )
        assertEquals(explicit, fromOptions)
    }

    @Test
    fun theOptionsOverloadFollowsTheOptionsLanguageForReadingDirection() {
        val urdu = createWidgetOptions(language = WidgetLanguage.URDU, monthNameLanguage = WidgetLanguage.URDU)
        val english = createWidgetOptions(language = WidgetLanguage.ENGLISH, monthNameLanguage = WidgetLanguage.ENGLISH)

        val urduGrid = assertNotNull(buildHijriMonthWidgetData(1447, 1, urdu))
        val englishGrid = assertNotNull(buildHijriMonthWidgetData(1447, 1, english))

        // Names come from each option's own language; only the *order* follows the reading
        // direction, so the Urdu grid is its own list reversed and the English grid is not.
        assertEquals(WidgetLocalization.urduWeekdayNames.reversed(), urduGrid.weekdayHeaders)
        assertEquals(
            listOf("Sat", "Sun", "Mon", "Tue", "Wed", "Thu", "Fri"),
            englishGrid.weekdayHeaders,
        )
        assertEquals(WidgetLocalization.urduHijriMonthNames, urdu.localizedHijriMonthNames)
        assertEquals(null, english.localizedHijriMonthNames)
    }

    @Test
    fun theOptionsOverloadSwitchesTheSourceAndAdjustment() {
        val pakistan = createWidgetOptions(
            language = WidgetLanguage.ENGLISH,
            monthNameLanguage = WidgetLanguage.ENGLISH,
            source = WidgetSource.PAKISTAN,
            adjustmentDays = 2,
        )
        val explicit = todayHijriWidgetData(
            anchorEpochDay = 20731L,
            adjustmentDays = 2,
            numeralStyle = NumeralStyle.WESTERN,
            pakistan = true,
        )
        assertEquals(explicit, todayHijriWidgetData(20731L, pakistan))
    }

    @Test
    fun pinnedMonthResolvesTheGridMonth() {
        val pinned = createWidgetOptions(pinsMonth = true, pinnedYear = 1445, pinnedMonth = 12)
        assertEquals(HijriYearMonth(1445, 12), pinned.resolveGridMonth(today = HijriYearMonth(1447, 1)))
        // The pinned-month picker shows the widget's own month script, not a second hardcoded list:
        // with the default Urdu options this is the Urdu name.
        assertEquals(WidgetLocalization.urduHijriMonthNames[11], pinned.hijriMonthName(12))
        assertEquals(
            WidgetLocalization.englishHijriMonthNames[11],
            pinned.copy(language = WidgetLanguage.ENGLISH, monthNameLanguage = WidgetLanguage.ENGLISH)
                .hijriMonthName(12),
        )
    }
}
