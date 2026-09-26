package com.muazdev.hijricalendar.widgetdata

import com.muazdev.hijricalendar.core.UrduCalendarNames
import com.muazdev.hijricalendar.core.WeekDay
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers ticket 03's shared behaviour: the per-widget language and source choices, the
 * language-driven reading direction of the grid and the localized source pill label.
 */
class WidgetLanguageAndSourceTest {

    private val year = 1448
    private val month = 4

    private fun monthData(
        language: WidgetLanguage,
        source: WidgetSource = WidgetSource.CALCULATION,
        firstDayOfWeekIndex: Int = WeekDay.DEFAULT_FIRST_DAY.index,
        rightToLeft: Boolean = language.isRtl,
    ): HijriMonthWidgetData = assertNotNull(
        buildHijriMonthWidgetData(
            hijriYear = year,
            hijriMonth = month,
            adjustmentDays = 0,
            firstDayOfWeekIndex = firstDayOfWeekIndex,
            numeralStyle = WidgetLocalization.defaultNumeralStyle(language),
            pakistan = source.pakistan,
            rightToLeft = rightToLeft,
            localizedHijriMonthNames = WidgetLocalization.hijriMonthNames(language),
            localizedGregorianMonthNames = WidgetLocalization.gregorianMonthNames(language),
            localizedWeekdayNames = WidgetLocalization.weekdayNames(language),
        ),
    )

    // ── Direction flags ───────────────────────────────────────────────────────

    @Test
    fun language_directionFlags() {
        assertTrue(WidgetLanguage.URDU.isRtl)
        assertFalse(WidgetLanguage.ENGLISH.isRtl)
    }

    @Test
    fun source_togglesBetweenCalculationAndPakistan() {
        assertFalse(WidgetSource.CALCULATION.pakistan)
        assertTrue(WidgetSource.PAKISTAN.pakistan)
        assertEquals(WidgetSource.PAKISTAN, WidgetSource.CALCULATION.toggled())
        assertEquals(WidgetSource.CALCULATION, WidgetSource.PAKISTAN.toggled())
    }

    // ── Localization resolvers ────────────────────────────────────────────────

    @Test
    fun localization_resolvesUrduListsAndNullsForEnglish() {
        assertEquals(UrduCalendarNames.hijriMonths, WidgetLocalization.hijriMonthNames(WidgetLanguage.URDU))
        assertEquals(UrduCalendarNames.gregorianMonths, WidgetLocalization.gregorianMonthNames(WidgetLanguage.URDU))
        assertEquals(UrduCalendarNames.weekdayShortNames, WidgetLocalization.weekdayNames(WidgetLanguage.URDU))
        assertNull(WidgetLocalization.hijriMonthNames(WidgetLanguage.ENGLISH))
        assertNull(WidgetLocalization.gregorianMonthNames(WidgetLanguage.ENGLISH))
        assertNull(WidgetLocalization.weekdayNames(WidgetLanguage.ENGLISH))
    }

    @Test
    fun localization_defaultNumeralStyleFollowsLanguage() {
        assertEquals(NumeralStyle.ARABIC_INDIC, WidgetLocalization.defaultNumeralStyle(WidgetLanguage.URDU))
        assertEquals(NumeralStyle.WESTERN, WidgetLocalization.defaultNumeralStyle(WidgetLanguage.ENGLISH))
    }

    @Test
    fun localization_sourceLabelIsLocalized() {
        assertEquals("حساب", WidgetLocalization.sourceLabel(WidgetSource.CALCULATION, WidgetLanguage.URDU))
        assertEquals("پاکستان", WidgetLocalization.sourceLabel(WidgetSource.PAKISTAN, WidgetLanguage.URDU))
        assertEquals("Calculation", WidgetLocalization.sourceLabel(WidgetSource.CALCULATION, WidgetLanguage.ENGLISH))
        assertEquals("Pakistan", WidgetLocalization.sourceLabel(WidgetSource.PAKISTAN, WidgetLanguage.ENGLISH))
    }

    // ── RTL reordering ────────────────────────────────────────────────────────

    @Test
    fun rtl_reversesWeekdayHeadersAndEachWeek() {
        val ltr = monthData(WidgetLanguage.ENGLISH, rightToLeft = false)
        val rtl = monthData(WidgetLanguage.ENGLISH, rightToLeft = true)

        assertEquals(ltr.weekdayHeaders.reversed(), rtl.weekdayHeaders)
        assertEquals(ltr.days.size, rtl.days.size)

        val ltrWeeks = ltr.days.chunked(7)
        val rtlWeeks = rtl.days.chunked(7)
        ltrWeeks.forEachIndexed { weekIndex, week ->
            assertEquals(
                week.reversed(),
                rtlWeeks[weekIndex],
                "week $weekIndex should be reversed for RTL",
            )
        }
    }

    @Test
    fun direction_headersLineUpWithTheirWeekColumns() {
        for (language in WidgetLanguage.entries) {
            val data = monthData(language)
            val firstWeek = data.days.take(7)
            firstWeek.forEachIndexed { column, cell ->
                val weekday = WeekDay.fromDayOfWeek(
                    LocalDate.fromEpochDays(cell.gregorianEpochDay).dayOfWeek,
                )
                val expectedHeader = when (language) {
                    WidgetLanguage.ENGLISH -> weekday.shortName
                    WidgetLanguage.URDU -> UrduCalendarNames.weekdays.getValue(weekday)
                }
                assertEquals(
                    expectedHeader,
                    data.weekdayHeaders[column],
                    "column $column header for $language",
                )
            }
        }
    }

    @Test
    fun rtl_urduGridUsesUrduNamesAndEasternDigits() {
        val data = monthData(WidgetLanguage.URDU)
        assertEquals(UrduCalendarNames.hijriMonths[month - 1], data.hijriMonthName)
        assertEquals(UrduCalendarNames.weekdayShortNames.reversed(), data.weekdayHeaders)
        val current = data.days.filter { it.isCurrentMonth }
        assertTrue(current.all { cell -> cell.dayText.all { it in '٠'..'٩' } })
    }

    @Test
    fun rtl_pakistanSourceKeepsPakistanCells() {
        val rtlPakistan = monthData(WidgetLanguage.URDU, WidgetSource.PAKISTAN)
        val ltrPakistan = monthData(WidgetLanguage.ENGLISH, WidgetSource.PAKISTAN)
        // Reordering must not change which Gregorian days are shaded as weekend/current month.
        assertEquals(
            ltrPakistan.days.filter { it.isWeekend }.map { it.gregorianEpochDay }.toSet(),
            rtlPakistan.days.filter { it.isWeekend }.map { it.gregorianEpochDay }.toSet(),
        )
        assertEquals(
            ltrPakistan.days.filter { it.isCurrentMonth }.map { it.gregorianEpochDay }.toSet(),
            rtlPakistan.days.filter { it.isCurrentMonth }.map { it.gregorianEpochDay }.toSet(),
        )
    }

    // ── Today card in the chosen language ─────────────────────────────────────

    @Test
    fun today_urduCardHasUrduNamesAndEasternDigits() {
        val anchor = LocalDate(2026, 9, 13).toEpochDays()
        val today = assertNotNull(
            todayHijriWidgetData(
                anchorEpochDay = anchor,
                adjustmentDays = 0,
                localizedHijriMonthNames = WidgetLocalization.hijriMonthNames(WidgetLanguage.URDU),
                localizedGregorianMonthNames = WidgetLocalization.gregorianMonthNames(WidgetLanguage.URDU),
                localizedWeekdayNames = WidgetLocalization.weekdayNames(WidgetLanguage.URDU),
                numeralStyle = WidgetLocalization.defaultNumeralStyle(WidgetLanguage.URDU),
            ),
        )
        assertEquals("اتوار", today.weekdayName)
        assertTrue(today.hijriDayText.all { it in '٠'..'٩' })
        assertTrue(today.gregorianDate.contains("ستمبر"))
    }
}
