package com.muazdev.hijricalendar.widgetdata

import kotlinx.serialization.Serializable

/**
 * Enum controlling how numeric cell text is rendered. [WESTERN] maps digits to `0-9`,
 * [ARABIC_INDIC] to the Eastern Arabic-Indic digits `0-9`. Rendering in widgets is
 * driven by this value rather than the device locale so both widget stacks behave
 * identically.
 */
enum class NumeralStyle {
    WESTERN,
    ARABIC_INDIC
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
 * renderers need (month name, Gregorian range line, weekday headers).
 */
@Serializable
data class HijriMonthWidgetData(
    val hijriYear: Int,
    val hijriMonth: Int,
    val hijriMonthName: String,
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
    val hijriMonth: Int,
    val hijriYear: Int,
    val hijriMonthName: String,
    val gregorianDate: String,
    val weekdayName: String,
    val adjustmentDays: Int,
)