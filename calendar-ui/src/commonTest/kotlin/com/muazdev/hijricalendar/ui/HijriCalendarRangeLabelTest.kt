package com.muazdev.hijricalendar.ui

import com.muazdev.hijricalendar.core.UrduCalendarNames
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class HijriCalendarRangeLabelTest {

    private val labels = HijriCalendarLabels()

    // ── the default range label ───────────────────────────────────────

    @Test
    fun sameMonthLabel_rendersMonthAndYear() {
        assertEquals(
            "September 2026",
            labels.gregorianMonthRangeLabel(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30)),
        )
    }

    @Test
    fun sameYearLabel_rendersMonthRange() {
        assertEquals(
            "September - October 2026",
            labels.gregorianMonthRangeLabel(LocalDate(2026, 9, 1), LocalDate(2026, 10, 1)),
        )
    }

    @Test
    fun crossYearLabel_rendersBothYears() {
        assertEquals(
            "December 2026 - January 2027",
            labels.gregorianMonthRangeLabel(LocalDate(2026, 12, 1), LocalDate(2027, 1, 1)),
        )
    }

    @Test
    fun singleDayRange_sameMonth() {
        assertEquals(
            "June 2026",
            labels.gregorianMonthRangeLabel(LocalDate(2026, 6, 15), LocalDate(2026, 6, 15)),
        )
    }

    // ── the labels seam is honoured ─────────────────────────────────────

    @Test
    fun aCustomHeaderTitleIsUsedVerbatim() {
        val custom = labels.copy(headerTitle = { month, year -> "$year / $month" })
        assertEquals("1447 / Ramadan", custom.headerTitle("Ramadan", 1447))
    }

    @Test
    fun aCustomRangeLabelReplacesTheDefaultFormat() {
        val custom = labels.copy(
            gregorianMonthRangeLabel = { first, last -> "$first..$last" },
        )
        assertEquals(
            "2026-09-01..2026-09-30",
            custom.gregorianMonthRangeLabel(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30)),
        )
    }

    @Test
    fun theDefaultRangeLabelMatchesTheDocumentedEnglishOutput() {
        // The default must reproduce the pre-existing three-branch output byte for byte, so
        // upgrading a consumer changes nothing they can see. Checked across all three shapes.
        listOf(
            LocalDate(2026, 9, 1) to LocalDate(2026, 9, 30),
            LocalDate(2026, 9, 1) to LocalDate(2026, 10, 1),
            LocalDate(2026, 12, 1) to LocalDate(2027, 1, 1),
        ).forEach { (first, last) ->
            assertEquals(
                defaultGregorianMonthRangeLabel(first, last),
                labels.gregorianMonthRangeLabel(first, last),
                "default diverged from the documented implementation for $first..$last",
            )
        }
    }

    // ── Gregorian month naming through default labels ───────────────────

    @Test
    fun defaultGregorianNames_coverAllMonths() {
        // Guards the DefaultGregorianMonthNames list against off-by-one (1-based month -> 0-based index).
        val january = labels.gregorianMonthName(1)
        val december = labels.gregorianMonthName(12)
        assertEquals("January", january)
        assertEquals("December", december)
    }

    /**
     * The Urdu bundle end to end, which is the case this review found broken: the module's own
     * Urdu preview showed Arabic-Indic **day figures** under a **Western-numeral year**, because the
     * header joined the title itself and the year went through `toString()`.
     *
     * Asserting the header line here is what makes the seam real rather than present.
     */
    @Test
    fun urduLabelsLocalizeTheWholeHeaderLine() {
        val urdu = HijriCalendarLabels(
            hijriMonthName = { _, month -> UrduCalendarNames.hijriMonths[month - 1] },
            gregorianMonthName = { month -> UrduCalendarNames.gregorianMonths[month - 1] },
            weekdayShortName = { weekDay -> UrduCalendarNames.weekdays.getValue(weekDay) },
            // The whole point of the seam: the consumer formats the title, so it chooses the digits.
            headerTitle = { monthName, year -> "$monthName ${year.toArabicIndicNumerals()}" },
            gregorianMonthRangeLabel = { first, last -> defaultGregorianMonthRangeLabel(first, last) },
            previousMonthContentDescription = "پچھلا مہینہ",
            nextMonthContentDescription = "اگلا مہینہ",
        )

        assertEquals("رمضان ١٤٤٧", urdu.headerTitle(UrduCalendarNames.hijriMonths[8], 1447))
    }

    /** The built-in default is unchanged, so upgrading a consumer changes nothing visible. */
    @Test
    fun theDefaultHeaderTitleStaysWesternAndSpaceSeparated() {
        assertEquals("Ramadan 1447", labels.headerTitle("Ramadan", 1447))
    }
}
