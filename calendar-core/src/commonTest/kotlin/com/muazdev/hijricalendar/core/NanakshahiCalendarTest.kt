package com.muazdev.hijricalendar.core

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NanakshahiCalendarTest {

    // ── validation ───────────────────────────────────────────────────────

    @Test
    fun nanakshahiDate_rejectsAMonthOutsideTheRange() {
        assertFailsWith<IllegalArgumentException> { NanakshahiDate(555, 0, 1) }
        assertFailsWith<IllegalArgumentException> { NanakshahiDate(555, 13, 1) }
    }

    @Test
    fun nanakshahiDate_rejectsADayOutsideTheRange() {
        assertFailsWith<IllegalArgumentException> { NanakshahiDate(555, 1, 0) }
        assertFailsWith<IllegalArgumentException> { NanakshahiDate(555, 1, 32) }
    }

    // ── test vectors from the spec ───────────────────────────────────────

    @Test
    fun toNanakshahi_2023_03_14_isChet1_555() {
        val ns = NanakshahiCalendar.toNanakshahi(LocalDate(2023, 3, 14))
        assertEquals(NanakshahiDate(555, 1, 1), ns)
    }

    @Test
    fun toNanakshahi_2024_03_13_isPhagun31_555_leapYear() {
        // 2024 is a Gregorian leap year. Under FEB29_IN_PHAGUN, NS 555's Phagun
        // (12 Feb 2024 to 13 Mar 2024) contains Feb 29, so it's a leap year.
        val ns = NanakshahiCalendar.toNanakshahi(LocalDate(2024, 3, 13))
        assertEquals(NanakshahiDate(555, 12, 31), ns)
    }

    @Test
    fun toNanakshahi_2024_03_14_isChet1_556() {
        val ns = NanakshahiCalendar.toNanakshahi(LocalDate(2024, 3, 14))
        assertEquals(NanakshahiDate(556, 1, 1), ns)
    }

    @Test
    fun toNanakshahi_2026_03_14_isChet1_558() {
        val ns = NanakshahiCalendar.toNanakshahi(LocalDate(2026, 3, 14))
        assertEquals(NanakshahiDate(558, 1, 1), ns)
    }

    @Test
    fun toNanakshahi_2025_02_12_isPhagun1_556() {
        // 2025-02-12 is before March 14 → NS year = 2025 - 1469 = 556.
        // Chet 1 of NS 556 = March 14, 2024.
        // NS 556 is NOT leap under FEB29_IN_PHAGUN (2025 is not a Gregorian leap year).
        // Phagun starts on 12 February. 2025-02-12 is Phagun 1.
        val ns = NanakshahiCalendar.toNanakshahi(LocalDate(2025, 2, 12))
        assertEquals(NanakshahiDate(556, 12, 1), ns)
    }

    // ── round trip ───────────────────────────────────────────────────────

    @Test
    fun roundTrip_everyDayFrom2023_03_14_to_2028_03_13() {
        var d = LocalDate(2023, 3, 14)
        val end = LocalDate(2028, 3, 13)
        while (d <= end) {
            val ns = NanakshahiCalendar.toNanakshahi(d)
            val back = NanakshahiCalendar.toGregorian(ns)
            assertEquals(d, back, "round trip failed for $d → $ns → $back")
            d = d.plus(1, DateTimeUnit.DAY)
        }
    }

    // ── month starts ─────────────────────────────────────────────────────

    @Test
    fun monthStarts_nonLeapYear_chetStartsOn14March() {
        // NS 557 starts on 14 March 2025 (non-leap Gregorian year)
        val chet1 = NanakshahiCalendar.toNanakshahi(LocalDate(2025, 3, 14))
        assertEquals(1, chet1.month)
        assertEquals(1, chet1.day)
    }

    @Test
    fun monthStarts_nonLeapYear_eachMonthStartsOnFixedDate() {
        // NS 557: Chet 1 = 2025-03-14 (non-leap year for Gregorian 2025)
        val rules = NanakshahiRules.DEFAULT
        val year = 557

        // Vaisakh 1 = 14 April 2025
        val vaisakh1 = NanakshahiCalendar.toNanakshahi(LocalDate(2025, 4, 14))
        assertEquals(NanakshahiDate(year, 2, 1), vaisakh1)

        // Jeth 1 = 15 May 2025
        val jeth1 = NanakshahiCalendar.toNanakshahi(LocalDate(2025, 5, 15))
        assertEquals(NanakshahiDate(year, 3, 1), jeth1)

        // Harh 1 = 15 June 2025
        val harh1 = NanakshahiCalendar.toNanakshahi(LocalDate(2025, 6, 15))
        assertEquals(NanakshahiDate(year, 4, 1), harh1)

        // Sawan 1 = 16 July 2025
        val sawan1 = NanakshahiCalendar.toNanakshahi(LocalDate(2025, 7, 16))
        assertEquals(NanakshahiDate(year, 5, 1), sawan1)

        // Bhadon 1 = 16 August 2025
        val bhadon1 = NanakshahiCalendar.toNanakshahi(LocalDate(2025, 8, 16))
        assertEquals(NanakshahiDate(year, 6, 1), bhadon1)

        // Assu 1 = 15 September 2025
        val assu1 = NanakshahiCalendar.toNanakshahi(LocalDate(2025, 9, 15))
        assertEquals(NanakshahiDate(year, 7, 1), assu1)

        // Katak 1 = 15 October 2025
        val katak1 = NanakshahiCalendar.toNanakshahi(LocalDate(2025, 10, 15))
        assertEquals(NanakshahiDate(year, 8, 1), katak1)

        // Maghar 1 = 14 November 2025
        val maghar1 = NanakshahiCalendar.toNanakshahi(LocalDate(2025, 11, 14))
        assertEquals(NanakshahiDate(year, 9, 1), maghar1)

        // Poh 1 = 14 December 2025
        val poh1 = NanakshahiCalendar.toNanakshahi(LocalDate(2025, 12, 14))
        assertEquals(NanakshahiDate(year, 10, 1), poh1)

        // Magh 1 = 13 January 2026
        val magh1 = NanakshahiCalendar.toNanakshahi(LocalDate(2026, 1, 13))
        assertEquals(NanakshahiDate(year, 11, 1), magh1)

        // Phagun 1 = 12 February 2026
        val phagun1 = NanakshahiCalendar.toNanakshahi(LocalDate(2026, 2, 12))
        assertEquals(NanakshahiDate(year, 12, 1), phagun1)
    }

    // ── leap year and Phagun length ──────────────────────────────────────

    @Test
    fun phagunLength_nonLeapYear_is30() {
        // NS 557: Gregorian 2026 is not a leap year → Phagun has 30 days
        assertEquals(30, NanakshahiCalendar.monthLength(557, 12))
    }

    @Test
    fun phagunLength_leapYear_is31() {
        // NS 555: Gregorian 2024 is a leap year → Phagun has 31 days
        assertEquals(31, NanakshahiCalendar.monthLength(555, 12))
    }

    @Test
    fun phagunLength_underFeb29Rule_555isLeap() {
        // NS 555's Phagun contains Feb 29, 2024 → leap
        assertTrue(NanakshahiCalendar.isLeapYear(555, NanakshahiLeapRule.FEB29_IN_PHAGUN.let {
            NanakshahiRules(NanakshahiVersion.MOOL_2003, it)
        }))
    }

    @Test
    fun phagunLength_underGregorianYearBeforeChetRule_555isNotLeap() {
        // NS 555's Chet begins 14 March 2023. 2023 is not a Gregorian leap year.
        assertFalse(NanakshahiCalendar.isLeapYear(555, NanakshahiLeapRule.GREGORIAN_YEAR_BEFORE_CHET_IS_LEAP.let {
            NanakshahiRules(NanakshahiVersion.MOOL_2003, it)
        }))
    }

    // ── year length ──────────────────────────────────────────────────────

    @Test
    fun yearLength_is365or366() {
        for (year in 550..570) {
            val len = NanakshahiCalendar.yearLength(year)
            assertTrue(len == 365 || len == 366, "year $year has length $len")
        }
    }

    @Test
    fun leapYearCount_in400YearCycle_is97() {
        // The Gregorian leap cycle is 400 years with 97 leap years.
        // Under FEB29_IN_PHAGUN, the Nanakshahi leap years follow the same pattern
        // (just offset by 1469), so any 400 consecutive years should have 97 leap years.
        val feb29Rules = NanakshahiRules.DEFAULT
        var count = 0
        for (year in 555..954) {
            if (NanakshahiCalendar.isLeapYear(year, feb29Rules)) count++
        }
        assertEquals(97, count, "400-year cycle should have 97 leap years")
    }

    @Test
    fun leapYearCount_in600YearRange_1469_to_2068_gregorian() {
        // 1469..2068 is 600 Gregorian years. The actual count of leap years is 146.
        // (150 candidates from 1472..2068 step 4, minus 4 non-leap centuries
        // 1700/1800/1900/2100-out-of-range, plus 2 leap centuries 1600/2000 = 146)
        var count = 0
        for (gYear in 1469..2068) {
            val isLeap = (gYear % 4 == 0 && gYear % 100 != 0) || gYear % 400 == 0
            if (isLeap) count++
        }
        assertEquals(146, count, "Gregorian leap years in 1469..2068")
    }

    // ── two leap rules disagree ──────────────────────────────────────────

    @Test
    fun leapRules_disagreeOnYear555() {
        // NS 555: Phagun contains Feb 29, 2024 (leap) → FEB29_IN_PHAGUN says leap
        // NS 555: Chet begins 14 March 2023 (not leap) → GREGORIAN_YEAR_BEFORE_CHET_IS_LEAP says not leap
        val feb29Rules = NanakshahiRules(NanakshahiVersion.MOOL_2003, NanakshahiLeapRule.FEB29_IN_PHAGUN)
        val chetRules = NanakshahiRules(NanakshahiVersion.MOOL_2003, NanakshahiLeapRule.GREGORIAN_YEAR_BEFORE_CHET_IS_LEAP)

        assertTrue(NanakshahiCalendar.isLeapYear(555, feb29Rules))
        assertFalse(NanakshahiCalendar.isLeapYear(555, chetRules))

        // This means Phagun 555 has different lengths:
        assertEquals(31, NanakshahiCalendar.monthLength(555, 12, feb29Rules))
        assertEquals(30, NanakshahiCalendar.monthLength(555, 12, chetRules))
    }

    // ── chetOneOf ────────────────────────────────────────────────────────

    @Test
    fun chetOneOf_returns14March() {
        assertEquals(LocalDate(2023, 3, 14), NanakshahiCalendar.chetOneOf(555))
        assertEquals(LocalDate(2024, 3, 14), NanakshahiCalendar.chetOneOf(556))
        assertEquals(LocalDate(2026, 3, 14), NanakshahiCalendar.chetOneOf(558))
    }

    // ── yearOf ───────────────────────────────────────────────────────────

    @Test
    fun yearOf_onOrAfter14March() {
        assertEquals(555, NanakshahiCalendar.yearOf(LocalDate(2023, 3, 14)))
        assertEquals(555, NanakshahiCalendar.yearOf(LocalDate(2023, 12, 31)))
        assertEquals(556, NanakshahiCalendar.yearOf(LocalDate(2024, 3, 14)))
    }

    @Test
    fun yearOf_before14March() {
        assertEquals(554, NanakshahiCalendar.yearOf(LocalDate(2023, 3, 13)))
        assertEquals(555, NanakshahiCalendar.yearOf(LocalDate(2024, 3, 13)))
    }

    // ── toString ─────────────────────────────────────────────────────────

    @Test
    fun nanakshahiDate_toString() {
        assertEquals("555-1-1", NanakshahiDate(555, 1, 1).toString())
    }
}
