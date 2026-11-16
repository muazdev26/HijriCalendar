package com.muazdev.hijricalendar.core

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Conversion functions between Gregorian and Nanakshahi (solar) dates.
 *
 * The Gregorian calendar is proleptic for dates before 1582 — the same convention used by
 * `kotlinx-datetime`'s [LocalDate]. The Nanakshahi epoch is 14 March 1469 (Gregorian),
 * which is Chet 1 of Nanakshahi year 1.
 *
 * Every function takes [rules: NanakshahiRules] explicitly; there is no global mutable state.
 * The default parameter is [NanakshahiRules.DEFAULT] (Mool 2003, FEB29_IN_PHAGUN).
 */
public object NanakshahiCalendar {

    // ── Mool 2003 month table ────────────────────────────────────────────

    /**
     * The fixed lengths of the first 11 months. Phagun (month 12) is 30 or 31 depending on
     * the leap rule.
     */
    private val mool2003FixedLengths = intArrayOf(
        0, // unused
        31, // Chet
        31, // Vaisakh
        31, // Jeth
        31, // Harh
        31, // Sawan
        30, // Bhadon
        30, // Assu
        30, // Katak
        30, // Maghar
        30, // Poh
        30, // Magh
        // Phagun is not here — it depends on the leap rule
    )

    // ── public API ───────────────────────────────────────────────────────

    /**
     * Returns the Nanakshahi year for the given Gregorian [date].
     *
     * If the date is on or after 14 March of Gregorian year Y, the Nanakshahi year is
     * `Y - 1468`. Otherwise it is `Y - 1469`.
     */
    public fun yearOf(date: LocalDate): Int {
        val gregorianYear = date.year
        val isOnOrAfterChet = (date.month.ordinal + 1) * 100 + date.day >= 314
        return if (isOnOrAfterChet) gregorianYear - 1468 else gregorianYear - 1469
    }

    /**
     * Returns true when Nanakshahi [year] is a leap year under the given [rules].
     *
     * A leap year means Phagun has 31 days instead of 30.
     */
    public fun isLeapYear(year: Int, rules: NanakshahiRules = NanakshahiRules.DEFAULT): Boolean {
        return when (rules.leapRule) {
            NanakshahiLeapRule.FEB29_IN_PHAGUN -> {
                // Phagun runs 12 Feb to 13 Mar of Gregorian year (year + 1469).
                // The year is leap when that Gregorian year contains Feb 29.
                val gregorianYearContainingPhagun = year + 1469
                isGregorianLeap(gregorianYearContainingPhagun)
            }
            NanakshahiLeapRule.GREGORIAN_YEAR_BEFORE_CHET_IS_LEAP -> {
                // Chet begins on 14 March of Gregorian year (year + 1468).
                val gregorianYearOfChet = year + 1468
                isGregorianLeap(gregorianYearOfChet)
            }
        }
    }

    /**
     * Returns the total number of days in Nanakshahi [year] under the given [rules].
     *
     * Returns 365 in a common year, 366 in a leap year.
     */
    public fun yearLength(year: Int, rules: NanakshahiRules = NanakshahiRules.DEFAULT): Int {
        return if (isLeapYear(year, rules)) 366 else 365
    }

    /**
     * Returns the number of days in [month] of Nanakshahi [year] under the given [rules].
     *
     * Months 1-11 have fixed lengths. Month 12 (Phagun) is 30 or 31 depending on the leap rule.
     */
    public fun monthLength(
        year: Int,
        month: Int,
        rules: NanakshahiRules = NanakshahiRules.DEFAULT,
    ): Int {
        require(month in 1..12) { "month must be 1..12, was $month" }
        return if (month == 12) {
            // Phagun: 31 in a leap year, 30 otherwise
            if (isLeapYear(year, rules)) 31 else 30
        } else {
            mool2003FixedLengths[month]
        }
    }

    /**
     * Returns the Gregorian date of Chet 1 in Nanakshahi [year].
     *
     * Chet 1 is always 14 March of the Gregorian year that starts the Nanakshahi year.
     */
    public fun chetOneOf(year: Int): LocalDate {
        return LocalDate(year + 1468, 3, 14)
    }

    /**
     * Converts a Gregorian [date] to a [NanakshahiDate] under the given [rules].
     */
    public fun toNanakshahi(
        date: LocalDate,
        rules: NanakshahiRules = NanakshahiRules.DEFAULT,
    ): NanakshahiDate {
        val nsYear = yearOf(date)
        val chetOne = chetOneOf(nsYear)
        var dayIndex = (date.toEpochDays() - chetOne.toEpochDays()).toInt()

        var month = 1
        while (true) {
            val len = monthLength(nsYear, month, rules)
            if (dayIndex < len) {
                return NanakshahiDate(nsYear, month, dayIndex + 1)
            }
            dayIndex -= len
            month++
        }
    }

    /**
     * Converts a [NanakshahiDate] to a Gregorian [LocalDate] under the given [rules].
     */
    public fun toGregorian(
        date: NanakshahiDate,
        rules: NanakshahiRules = NanakshahiRules.DEFAULT,
    ): LocalDate {
        val chetOne = chetOneOf(date.year)
        var offsetDays = date.day - 1
        for (m in 1 until date.month) {
            offsetDays += monthLength(date.year, m, rules)
        }
        return chetOne.plus(offsetDays.toLong(), DateTimeUnit.DAY)
    }

    // ── private helpers ──────────────────────────────────────────────────

    private fun isGregorianLeap(year: Int): Boolean {
        return (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
    }
}
