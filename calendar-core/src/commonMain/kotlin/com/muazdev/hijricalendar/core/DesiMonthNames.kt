package com.muazdev.hijricalendar.core

/**
 * The month names for the Desi (solar) calendar.
 *
 * Follows the same structure as [CalendarNames] and [UrduCalendarNames]: compiled-in constants,
 * not runtime data. The four tables are the same twelve months in four scripts — English, Gurmukhi,
 * Shahmukhi (Urdu script) and Devanagari (Hindi) — so they are indexed identically and cannot drift
 * apart in ordering.
 *
 * The non-Latin tables are **transliterations of the Desi month names**, not the Hindu Bikrami
 * month set (चैत्र, वैशाख, ज्येष्ठ …). Those are a different naming for a different calendar and their
 * month starts do not line up with these, so mixing the two would mislabel every date.
 */
public object DesiMonthNames {

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

    /** The twelve Shahmukhi (Urdu script) month names, 1-indexed (چیت .. پھگݨ). */
    public val urdu: List<String> = listOf(
        "چیت",
        "وساکھ",
        "جیٹھ",
        "ہاڑھ",
        "ساوݨ",
        "بھادوں",
        "اسو",
        "کتک",
        "مگھر",
        "پوہ",
        "ماگھ",
        "پھگݨ",
    )

    /** The twelve Devanagari (Hindi) month names, 1-indexed (चेत .. फग्गन). */
    public val hindi: List<String> = listOf(
        "चेत",
        "बैसाख",
        "जेठ",
        "हाड़",
        "सावन",
        "भादों",
        "अस्सू",
        "कातक",
        "मग्घर",
        "पोह",
        "माघ",
        "फग्गन",
    )

    /**
     * Returns the English name for month [m] (1-12).
     */
    public fun englishName(m: Int): String = english[m - 1]

    /**
     * Returns the Gurmukhi name for month [m] (1-12).
     */
    public fun gurmukhiName(m: Int): String = gurmukhi[m - 1]

    /**
     * Returns the Shahmukhi (Urdu script) name for month [m] (1-12).
     */
    public fun urduName(m: Int): String = urdu[m - 1]

    /**
     * Returns the Devanagari (Hindi) name for month [m] (1-12).
     */
    public fun hindiName(m: Int): String = hindi[m - 1]
}
