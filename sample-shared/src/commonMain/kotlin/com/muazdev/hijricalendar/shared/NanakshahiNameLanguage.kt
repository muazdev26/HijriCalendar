package com.muazdev.hijricalendar.shared

import com.muazdev.hijricalendar.core.NanakshahiMonthNames

/**
 * Which script the Nanakshahi month name is written in.
 *
 * A sample-level enum rather than a `calendar-core` one: the library owns the four name tables
 * ([NanakshahiMonthNames.english], [NanakshahiMonthNames.gurmukhi], [NanakshahiMonthNames.urdu],
 * [NanakshahiMonthNames.hindi]) and needs no opinion about which of them a screen offers. This names
 * the three the sample shows; Gurmukhi stays reachable for a host that wants it.
 */
enum class NanakshahiNameLanguage {
    /** Latin script: `Chet`, `Phagun`. */
    ENGLISH,

    /** Urdu script (Shahmukhi): `چیت`, `پھگݨ`. */
    URDU,

    /** Devanagari (Hindi): `चेत`, `फग्गन`. */
    HINDI,
    ;

    /**
     * The chip's label, written in the script it selects.
     */
    val label: String
        get() = when (this) {
            ENGLISH -> "English"
            URDU -> "اردو"
            HINDI -> "हिन्दी"
        }

    /**
     * The month's name in this script, `1`-indexed (1 = Chet).
     */
    fun monthName(month: Int): String = when (this) {
        ENGLISH -> NanakshahiMonthNames.englishName(month)
        URDU -> NanakshahiMonthNames.urduName(month)
        HINDI -> NanakshahiMonthNames.hindiName(month)
    }
}
