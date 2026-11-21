package com.muazdev.hijricalendar.core

/**
 * A version of the Desi (solar) calendar.
 *
 * Only the Mool 2003 version is supported in this release. The SGPC printed calendar is a
 * different, lunisolar calendar — not a version of the Mool Desi — and is a separate task.
 */
public enum class DesiVersion {
    /**
     * The Mool Desi calendar, approved in 2003.
     *
     * Year 1 begins on 14 March 1469 (Gregorian). Each month has a fixed Gregorian start date;
     * only Phagun (month 12) varies between 30 and 31 days depending on the leap rule.
     */
    MOOL_2003,
}
