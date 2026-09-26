package com.muazdev.hijricalendar.widgetdata

import kotlinx.serialization.Serializable

/**
 * Enum controlling how numeric cell text is rendered. [WESTERN] maps digits to `0-9`,
 * [ARABIC_INDIC] to the Eastern Arabic-Indic digits `0-9`. Rendering in widgets is
 * driven by this value rather than the device locale so both widget stacks behave
 * identically.
 */
@Serializable
enum class NumeralStyle {
    WESTERN,
    ARABIC_INDIC
}

/**
 * The display language of a widget. Unlike the device locale this is a per-widget choice:
 * a widget can be Urdu on an English phone (or the reverse). [isRtl] drives the reading
 * direction of the weekday header, the day grid and the header arrows so a widget renders
 * consistently regardless of the host's `layoutDirection`.
 */
@Serializable
enum class WidgetLanguage(val isRtl: Boolean) {
    URDU(isRtl = true),
    ENGLISH(isRtl = false),
}

/**
 * The Hijri source a widget follows. [CALCULATION] is Umm al-Qura, [PAKISTAN] is the
 * Ruet-e-Hilal (Pakistan) calendar; [pakistan] is the flag the projection builders consume.
 * A widget carries this as a small tappable pill so the source can be switched from the
 * home screen without opening the app.
 */
@Serializable
enum class WidgetSource(val pakistan: Boolean) {
    CALCULATION(pakistan = false),
    PAKISTAN(pakistan = true),
    ;

    fun toggled(): WidgetSource = if (this == CALCULATION) PAKISTAN else CALCULATION
}

/**
 * One cell of the 42-day Hijri month grid in render-ready widget form.
 *
 * [gregorianEpochDay] is the integer local-calendar day count (days since 1970-01-01,
 * matching kotlinx-datetime's `LocalDate.toEpochDays()`, which the native renderers can
 * compare against their own "today" anchor without importing any Hijri math).
 */
@Serializable
data class HijriDayWidgetData(
    val hijriDay: Int,
    val dayText: String,
    val gregorianDay: Int,
    val gregorianDayText: String,
    val gregorianEpochDay: Long,
    val isCurrentMonth: Boolean,
    val isWeekend: Boolean,
)

/**
 * One rendered Hijri month in widget form: a 6x7 grid plus the header data the native
 * renderers need (Hijri + Gregorian month titles on one line, Gregorian range line,
 * weekday headers).
 */
@Serializable
data class HijriMonthWidgetData(
    val hijriYear: Int,
    val hijriMonth: Int,
    val hijriMonthName: String,
    /**
     * The Gregorian month name + year of the Hijri month's first day (e.g. "September 2026"),
     * so a header can show both calendars on one line — `hijriMonthName hijriYear ·  title`.
     */
    val gregorianMonthTitle: String,
    val gregorianRange: String,
    val weekdayHeaders: List<String>,
    val days: List<HijriDayWidgetData>,
    val adjustmentDays: Int,
)

/**
 * Compact "today" projection used by the options screen of the widget family and by
 * the iOS small family size. Immutable; intended to be rebuilt on demand rather than
 * cached.
 */
@Serializable
data class TodayHijriWidgetData(
    val hijriDay: Int,
    /**
     * [hijriDay] rendered with the projection's [NumeralStyle] (e.g. `"١٣"` for
     * Arabic-Indic), so the big day figure on a today card follows the digit style
     * without the renderer knowing anything about numerals.
     */
    val hijriDayText: String,
    val hijriMonth: Int,
    val hijriYear: Int,
    val hijriMonthName: String,
    val gregorianDate: String,
    val weekdayName: String,
    val adjustmentDays: Int,
    /**
     * The Gregorian day of the anchor (unshifted by [adjustmentDays]), rendered with the
     * projection's [NumeralStyle]. Lets a dedicated "Gregorian today" widget render a big
     * day figure exactly like [hijriDayText], on the same local-calendar anchor the Hijri
     * number was produced from.
     */
    val gregorianDay: Int,
    val gregorianDayText: String,
    /**
     * The 1-based Gregorian month number of the anchor, plus the localized month name and year a
     * "Gregorian today" widget shows under its big day figure.
     */
    val gregorianMonth: Int,
    val gregorianMonthName: String,
    val gregorianYear: Int,
)