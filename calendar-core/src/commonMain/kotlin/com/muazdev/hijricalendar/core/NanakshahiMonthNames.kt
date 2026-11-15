package com.muazdev.hijricalendar.core

/**
 * The month names for the Nanakshahi (Sikh solar) calendar.
 *
 * Follows the same structure as [CalendarNames] and [UrduCalendarNames]: compiled-in constants,
 * not runtime data. English names match the Mool 2003 specification exactly. Gurmukhi names
 * are the standard spellings used in the Sikh calendar.
 *
 * Shahmukhi names are not included — they require confirmation from a source and should not
 * be invented.
 */
public object NanakshahiMonthNames {

    /** The twelve English month names, 1-indexed (Chet .. Phagun). */
    public val english: List<String> = listOf(
        "Chet",
        "Vaisakh",
        "Jeth",
        "Harh",
        "Sawan",
        "Bhadon",
        "Assu",
        "Katak",
        "Maghar",
        "Poh",
        "Magh",
        "Phagun",
    )

    /** The twelve Gurmukhi month names, 1-indexed (ਚੇਤ .. ਫੱਗਣ). */
    public val gurmukhi: List<String> = listOf(
        "ਚੇਤ",
        "ਵੈਸਾਖ",
        "ਜੇਠ",
        "ਹਾੜ",
        "ਸਾਵਣ",
        "ਭਾਦੋਂ",
        "ਅੱਸੂ",
        "ਕੱਤਕ",
        "ਮੱਘਰ",
        "ਪੋਹ",
        "ਮਾਘ",
        "ਫੱਗਣ",
    )

    // TODO(shahmukhi): names pending confirmation from the user. Do not invent.

    /**
     * Returns the English name for month [m] (1-12).
     */
    public fun englishName(m: Int): String = english[m - 1]

    /**
     * Returns the Gurmukhi name for month [m] (1-12).
     */
    public fun gurmukhiName(m: Int): String = gurmukhi[m - 1]
}
