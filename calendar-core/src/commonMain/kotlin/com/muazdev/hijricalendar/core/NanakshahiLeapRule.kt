package com.muazdev.hijricalendar.core

/**
 * The rule that decides whether a Nanakshahi year is leap (Phagun has 31 days).
 *
 * Sources disagree on this one rule. Both are supported; the default is [FEB29_IN_PHAGUN].
 */
public enum class NanakshahiLeapRule {
    /**
     * A Nanakshahi year is leap when its Phagun (12 February to 13 March) contains 29 February.
     *
     * Under this rule, Phagun has 31 days in a leap year and 30 days otherwise.
     * The Gregorian year that contains Phagun is `year + 1469` (since Phagun runs 12 February
     * to 13 March of that Gregorian year).
     */
    FEB29_IN_PHAGUN,

    /**
     * A Nanakshahi year is leap when the Gregorian year in which its Chet begins is a leap year.
     *
     * Chet begins on 14 March of Gregorian year `year + 1468`. Under this rule, the Nanakshahi
     * year is leap when that Gregorian year is a leap year.
     */
    GREGORIAN_YEAR_BEFORE_CHET_IS_LEAP,
}
