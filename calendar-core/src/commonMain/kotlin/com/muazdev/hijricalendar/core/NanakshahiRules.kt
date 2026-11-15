package com.muazdev.hijricalendar.core

/**
 * The rules for converting between Gregorian and Nanakshahi dates.
 *
 * Holds the version (currently only [NanakshahiVersion.MOOL_2003]) and the leap rule that decides
 * whether Phagun has 30 or 31 days. Pass this explicitly to every conversion function rather than
 * relying on a global setting.
 *
 * @property version The calendar version (month table). Currently only [NanakshahiVersion.MOOL_2003].
 * @property leapRule The rule that decides whether a Nanakshahi year is leap.
 */
public data class NanakshahiRules(
    public val version: NanakshahiVersion,
    public val leapRule: NanakshahiLeapRule,
) {
    public companion object {
        /**
         * The default rules: Mool 2003 with the FEB29_IN_PHAGUN leap rule.
         */
        public val DEFAULT: NanakshahiRules = NanakshahiRules(
            NanakshahiVersion.MOOL_2003,
            NanakshahiLeapRule.FEB29_IN_PHAGUN,
        )
    }
}
