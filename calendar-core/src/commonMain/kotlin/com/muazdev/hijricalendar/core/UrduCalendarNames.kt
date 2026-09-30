package com.muazdev.hijricalendar.core

/**
 * The single set of Urdu display names used across the app and both widget platforms.
 *
 * Calendar widgets, the sample app's Urdu labels and the library previews all source from
 * these lists rather than each defining its own copy, so month names, weekday names and the
 * Gregorian month list can never drift apart. Widget callers pass them into the
 * `calendar-widget-data` builders (`localizedHijriMonthNames`, `localizedWeekdayNames`,
 * `localizedGregorianMonthNames`) to render a fully Urdu grid/today card.
 *
 * This is the Urdu half of [CalendarNames], which owns the English half. They are kept as two
 * objects rather than one parameterized type because the language lists are compiled-in
 * constants, not runtime data — there is nothing to parameterize, and a lookup table would only
 * add indirection on a path that runs once per month header.
 */
public object UrduCalendarNames {

    /** The twelve Hijri month names, 1-indexed (Muharram .. Dhul-Hijjah). */
    public val hijriMonths: List<String> = listOf(
        "محرم", "صفر", "ربیع الاول", "ربیع الثانی",
        "جمادی الاول", "جمادی الثانی", "رجب", "شعبان",
        "رمضان", "شوال", "ذی القعدہ", "ذی الحجہ",
    )

    /** The twelve Gregorian month names, 1-indexed (January . December). */
    public val gregorianMonths: List<String> = listOf(
        "جنوری", "فروری", "مارچ", "اپریل", "مئی", "جون",
        "جولائی", "اگست", "ستمبر", "اکتوبر", "نومبر", "دسمبر",
    )

    /** Weekday names keyed by [WeekDay] (enum order: Saturday first). */
    public val weekdays: Map<WeekDay, String> = mapOf(
        WeekDay.SATURDAY to "ہفتہ",
        WeekDay.SUNDAY to "اتوار",
        WeekDay.MONDAY to "پیر",
        WeekDay.TUESDAY to "منگل",
        WeekDay.WEDNESDAY to "بدھ",
        WeekDay.THURSDAY to "جمعرات",
        WeekDay.FRIDAY to "جمعہ",
    )

    /** Weekday names as a 7-entry list in [WeekDay] enum order (Saturday first). */
    public val weekdayShortNames: List<String>
        get() = WeekDay.entries.map { weekdays.getValue(it) }
}