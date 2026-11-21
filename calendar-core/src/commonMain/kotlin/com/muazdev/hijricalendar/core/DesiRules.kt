package com.muazdev.hijricalendar.core

/**
 * The rules for converting between Gregorian and Desi dates.
 *
 * Holds the version (currently only [DesiVersion.MOOL_2003]) and the leap rule that decides
 * whether Phagun has 30 or 31 days. Pass this explicitly to every conversion function rather than
 * relying on a global setting.
 *
 * @property version The calendar version (month table). Currently only [DesiVersion.MOOL_2003].
 * @property leapRule The rule that decides whether a Desi year is leap.
 */
public data class DesiRules(
    public val version: DesiVersion,
    public val leapRule: DesiLeapRule,
) {
    public companion object {
        /**
         * The default rules: Mool 2003 with the FEB29_IN_PHAGUN leap rule.
         */
        public val DEFAULT: DesiRules = DesiRules(
            DesiVersion.MOOL_2003,
            DesiLeapRule.FEB29_IN_PHAGUN,
        )
    }
}
