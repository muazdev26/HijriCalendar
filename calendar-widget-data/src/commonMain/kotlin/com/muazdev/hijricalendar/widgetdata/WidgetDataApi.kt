package com.muazdev.hijricalendar.widgetdata

import com.abdulrahman_b.hijrahdatetime.toHijrahDate
import com.abdulrahman_b.hijrahdatetime.toLocalDate
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import com.muazdev.hijricalendar.core.CalendarMonth
import com.muazdev.hijricalendar.core.HijriMonthOverrides
import com.muazdev.hijricalendar.core.ObservedHijriCalendar
import com.muazdev.hijricalendar.core.PakistanHijriCalendar
import com.muazdev.hijricalendar.core.UrduCalendarNames
import com.muazdev.hijricalendar.core.WeekDay
import com.muazdev.hijricalendar.core.toCalendarMonth
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

private val DefaultHijriMonthNames = listOf(
    "Muharram",
    "Safar",
    "Rabi' al-awwal",
    "Rabi' al-thani",
    "Jumada al-ula",
    "Jumada al-akhirah",
    "Rajab",
    "Sha'ban",
    "Ramadan",
    "Shawwal",
    "Dhu al-Qa'dah",
    "Dhu al-Hijjah",
)

private val DefaultGregorianMonthNames = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

private val DefaultWeekdayNames = WeekDay.entries.map { it.shortName }

/**
 * Ready-to-hand localization lists for the widget builders. [urduHijriMonthNames],
 * [urduGregorianMonthNames] and [urduWeekdayNames] back the Urdu rendering option and are
 * the same single set of constants the app's own Urdu labels use (UrduCalendarNames in
 * `calendar-core`), so a widget and the in-app calendar can never disagree.
 */
object WidgetLocalization {
    val urduHijriMonthNames: List<String> = UrduCalendarNames.hijriMonths
    val urduGregorianMonthNames: List<String> = UrduCalendarNames.gregorianMonths
    val urduWeekdayNames: List<String> = UrduCalendarNames.weekdayShortNames

    /**
     * The built-in English Hijri month names the projection falls back to when no localized list
     * is supplied. Exposed so UI that needs a month label without building a whole grid (e.g. the
     * widget settings screen's pinned-month stepper) shows the exact same names the widget will.
     */
    val englishHijriMonthNames: List<String> = DefaultHijriMonthNames

    /**
     * The Hijri month names for [language], or `null` for [WidgetLanguage.ENGLISH] so the
     * builder falls back to its built-in English names. Callers pass the result straight into
     * `localizedHijriMonthNames`.
     */
    fun hijriMonthNames(language: WidgetLanguage): List<String>? = when (language) {
        WidgetLanguage.URDU -> urduHijriMonthNames
        WidgetLanguage.ENGLISH -> null
    }

    /** The Gregorian month names for [language]; `null` means the builder's English defaults. */
    fun gregorianMonthNames(language: WidgetLanguage): List<String>? = when (language) {
        WidgetLanguage.URDU -> urduGregorianMonthNames
        WidgetLanguage.ENGLISH -> null
    }

    /** The weekday short names for [language]; `null` means the builder's English defaults. */
    fun weekdayNames(language: WidgetLanguage): List<String>? = when (language) {
        WidgetLanguage.URDU -> urduWeekdayNames
        WidgetLanguage.ENGLISH -> null
    }

    /**
     * The digit style a language defaults to: Eastern Arabic-Indic for Urdu, Western for
     * English. Used to seed a fresh widget's numeral style so a new Urdu widget shows Eastern
     * digits without any extra configuration.
     */
    fun defaultNumeralStyle(language: WidgetLanguage): NumeralStyle = when (language) {
        WidgetLanguage.URDU -> NumeralStyle.ARABIC_INDIC
        WidgetLanguage.ENGLISH -> NumeralStyle.WESTERN
    }

    /**
     * The short label rendered on a widget's source pill, localized to [language] so the pill
     * reads in the widget's chosen language rather than the device locale.
     */
    fun sourceLabel(source: WidgetSource, language: WidgetLanguage): String = when (language) {
        WidgetLanguage.URDU -> if (source.pakistan) "پاکستان" else "حساب"
        WidgetLanguage.ENGLISH -> if (source.pakistan) "Pakistan" else "Calculation"
    }
}

/**
 * One Gregorian day [G], represented as a "real-world weekday marking" helper. The
 * observed Hijri date of [G] is `(G + adjustmentDays).toHijrahDate()`; this is the same
 * adjusted-space convention used across `calendar-core` (see `toCalendarMonth`).
 */
private fun observe(date: LocalDate, adjustmentDays: Int): LocalDate =
    date.plus(adjustmentDays, DateTimeUnit.DAY)

/**
 * Builds a render-ready 42-day Hijri month grid for the given [hijriYear]/[hijriMonth].
 *
 * The grid uses the same layout math as `calendar-core`'s `toCalendarMonth` so cells
 * line up with the in-app calendar: alignment follows [firstDayOfWeekIndex] (0-based
 * index into [WeekDay] entries, i.e. 0 = Saturday), and [adjustmentDays] shifts the
 * whole grid on the Gregorian timeline.
 *
 * With [pakistan] = true the grid is generated from the Pakistan (Ruet-e-Hilal) calendar
 * instead of Umm al-Qura: cells carry the observed Pakistani date of each Gregorian day,
 * exactly as the in-app calendar's Pakistan mode renders them. [weekendDays] controls
 * which weekdays are shaded; the default is the in-app default (Friday + Saturday).
 *
 * [localizedHijriMonthNames], [localizedGregorianMonthNames] and [localizedWeekdayNames]
 * may override the default English names; they must be 12 / 12 / 7 entries in [WeekDay]
 * enum order (Saturday-first for weekdays). Returns `null` when the month is outside the
 * supported range (~1300-1600 AH, or 1400-1500 in Pakistan mode) instead of throwing.
 *
 * With [rightToLeft] = true the projection is reordered for a right-to-left reading direction:
 * the weekday headers are reversed and each week's cells are reversed, so the first day of the
 * week sits on the right exactly as it does in the in-app calendar under an RTL layout. This is
 * driven by the widget's own [WidgetLanguage] rather than the host's `layoutDirection`, so an
 * Urdu widget renders RTL on an LTR device and vice versa.
 */
fun buildHijriMonthWidgetData(
    hijriYear: Int,
    hijriMonth: Int,
    adjustmentDays: Int,
    firstDayOfWeekIndex: Int = WeekDay.DEFAULT_FIRST_DAY.index,
    numeralStyle: NumeralStyle = NumeralStyle.WESTERN,
    pakistan: Boolean = false,
    weekendDays: Set<WeekDay> = WeekDay.WEEKEND_DAYS,
    rightToLeft: Boolean = false,
    localizedHijriMonthNames: List<String>? = null,
    localizedGregorianMonthNames: List<String>? = null,
    localizedWeekdayNames: List<String>? = null,
): HijriMonthWidgetData? {
    val yearMonth = try {
        if (hijriMonth in 1..12) HijrahYearMonth(hijriYear, hijriMonth) else return null
    } catch (_: Exception) {
        return null
    }
    val firstDayOfWeek = WeekDay.entries[
        firstDayOfWeekIndex.coerceIn(0, CalendarMonth.DAYS_IN_WEEK - 1)
    ]

    val calendarMonth = try {
        yearMonth.toCalendarMonth(
            firstDayOfWeek = firstDayOfWeek,
            adjustmentDays = adjustmentDays,
            pakistan = pakistan,
            weekendDays = weekendDays,
        )
    } catch (_: Exception) {
        return null
    }

    val hijriMonthName = localizedHijriMonthNames?.getOrNull(hijriMonth - 1)
        ?: DefaultHijriMonthNames.getOrNull(hijriMonth - 1)
        ?: "Month $hijriMonth"

    val weekdayHeaders = (0 until CalendarMonth.DAYS_IN_WEEK).map { offset ->
        val index = (firstDayOfWeek.index + offset) % CalendarMonth.DAYS_IN_WEEK
        localizedWeekdayNames?.getOrNull(index) ?: DefaultWeekdayNames[index]
    }.let { if (rightToLeft) it.reversed() else it }

    // The Gregorian first/last day must reflect the same source the in-app header uses: a
    // Pakistan month spans the Ruet-e-Hilal first/last day, an override-shifted month spans the
    // observed first/last day, and a plain Umm al-Qura month spans the calculated first/last day
    // (all minus adjustmentDays).
    val (gregorianFirst, gregorianLast) = when {
        pakistan -> {
            val first = PakistanHijriCalendar.hijriToGregorian(hijriYear, hijriMonth, 1)
                .minus(adjustmentDays, DateTimeUnit.DAY)
            val last = first.plus(
                PakistanHijriCalendar.lengthOfMonth(hijriYear, hijriMonth) - 1,
                DateTimeUnit.DAY,
            )
            first to last
        }
        HijriMonthOverrides.all().isNotEmpty() -> {
            val first = ObservedHijriCalendar.observedToGregorian(hijriYear, hijriMonth, 1)
                .minus(adjustmentDays, DateTimeUnit.DAY)
            val last = ObservedHijriCalendar.observedToGregorian(
                hijriYear,
                hijriMonth,
                ObservedHijriCalendar.observedLength(hijriYear, hijriMonth),
            ).minus(adjustmentDays, DateTimeUnit.DAY)
            first to last
        }
        else -> calendarMonth.gregorianFirstDay to calendarMonth.gregorianLastDay
    }
    val gregorianRange = formatGregorianRange(
        gregorianFirst,
        gregorianLast,
        localizedGregorianMonthNames,
    )
    val gregorianNames = localizedGregorianMonthNames ?: DefaultGregorianMonthNames
    val gregorianMonthTitle = "${gregorianNames[gregorianFirst.month.ordinal]} ${gregorianFirst.year}"

    val days = calendarMonth.days.map { day ->
        val local = day.localDate
        HijriDayWidgetData(
            hijriDay = day.dayOfMonth,
            dayText = formatNumber(day.dayOfMonth, numeralStyle),
            gregorianDay = local.day,
            gregorianDayText = formatNumber(local.day, numeralStyle),
            gregorianEpochDay = local.toEpochDays(),
            isCurrentMonth = day.isCurrentMonth,
            isWeekend = day.isWeekend,
        )
    }.let { cells ->
        // Reverse within each 7-cell week (not the whole list) so the rows still read as weeks
        // while the first day of the week lands on the right, matching the RTL in-app grid.
        if (rightToLeft) {
            cells.chunked(CalendarMonth.DAYS_IN_WEEK).flatMap { it.reversed() }
        } else {
            cells
        }
    }

    return HijriMonthWidgetData(
        hijriYear = hijriYear,
        hijriMonth = hijriMonth,
        hijriMonthName = hijriMonthName,
        gregorianMonthTitle = gregorianMonthTitle,
        gregorianRange = gregorianRange,
        weekdayHeaders = weekdayHeaders,
        days = days,
        adjustmentDays = adjustmentDays,
    )
}

private fun formatGregorianRange(first: LocalDate, last: LocalDate, localizedNames: List<String>?): String {
    val names = localizedNames ?: DefaultGregorianMonthNames
    return when {
        first.month == last.month && first.year == last.year ->
            "${names[first.month.ordinal]} ${first.year}"
        first.year == last.year ->
            "${names[first.month.ordinal]} - " +
                "${names[last.month.ordinal]} ${first.year}"
        else ->
            "${names[first.month.ordinal]} ${first.year} - " +
                "${names[last.month.ordinal]} ${last.year}"
    }
}

/**
 * Compact "today" projection for the widget family's option/summary display and the
 * iOS small family size.
 *
 * [anchorEpochDay] is the local-calendar day count of the real-world Gregorian day to
 * report (the widget's "today", computed by the caller via the platform calendar, never
 * by dividing an absolute time instant by 86400). The returned Hijri date is the
 * observed date `(anchor + adjustmentDays).toHijrahDate()`, matching the adjusted-space
 * convention everywhere else.
 *
 * [localizedHijriMonthNames], [localizedGregorianMonthNames] and [localizedWeekdayNames]
 * localize the month name, the Gregorian date line and the weekday label respectively
 * (12/12/7 entries, [WeekDay] enum order for weekdays); [numeralStyle] controls the digit
 * rendering of [TodayHijriWidgetData.hijriDayText] and the day figure inside
 * [TodayHijriWidgetData.gregorianDate]. Together they make a fully Urdu today card
 * possible.
 *
 * Returns `null` when the anchor falls outside the supported Umm al-Qura range.
 */
fun todayHijriWidgetData(
    anchorEpochDay: Long,
    adjustmentDays: Int,
    localizedHijriMonthNames: List<String>? = null,
    localizedGregorianMonthNames: List<String>? = null,
    localizedWeekdayNames: List<String>? = null,
    numeralStyle: NumeralStyle = NumeralStyle.WESTERN,
    pakistan: Boolean = false,
): TodayHijriWidgetData? {
    val anchor = try {
        LocalDate.fromEpochDays(anchorEpochDay)
    } catch (_: Exception) {
        return null
    }
    val shifted = observe(anchor, adjustmentDays)
    val (hDay, hMonth, hYear) = if (pakistan) {
        val pDate = PakistanHijriCalendar.gregorianToHijri(shifted)
        if (pDate != null && pDate.year in PakistanHijriCalendar.MIN_YEAR..PakistanHijriCalendar.MAX_YEAR) {
            Triple(pDate.day, pDate.month, pDate.year)
        } else {
            return null
        }
    } else {
        val hDate = try {
            shifted.toHijrahDate()
        } catch (_: Exception) {
            return null
        }
        Triple(hDate.day, hDate.month.number, hDate.year)
    }
    val weekday = WeekDay.fromDayOfWeek(anchor.dayOfWeek)
    val gregorianMonthName = localizedGregorianMonthNames?.getOrNull(anchor.month.ordinal)
        ?: DefaultGregorianMonthNames.getOrNull(anchor.month.ordinal)
        ?: anchor.month.name
    return TodayHijriWidgetData(
        hijriDay = hDay,
        hijriDayText = formatNumber(hDay, numeralStyle),
        hijriMonth = hMonth,
        hijriYear = hYear,
        hijriMonthName = localizedHijriMonthNames?.getOrNull(hMonth - 1)
            ?: DefaultHijriMonthNames.getOrNull(hMonth - 1)
            ?: "",
        gregorianDate = "${formatNumber(anchor.day, numeralStyle)} $gregorianMonthName ${anchor.year}",
        weekdayName = localizedWeekdayNames?.getOrNull(weekday.index) ?: weekday.shortName,
        adjustmentDays = adjustmentDays,
        gregorianDay = anchor.day,
        gregorianDayText = formatNumber(anchor.day, numeralStyle),
        gregorianMonth = anchor.month.ordinal + 1,
        gregorianMonthName = gregorianMonthName,
        gregorianYear = anchor.year,
    )
}

/**
 * Returns the Hijri year-month that is [offset] months away from [hijriYear]/[hijriMonth],
 * stepping forward for a positive [offset] and backward for a negative one and wrapping
 * across the year boundary (e.g. Dhul-Hijjah +1 yields Muharram of the following year,
 * Muharram -1 yields Dhul-Hijjah of the previous year).
 *
 * Returns `null` instead of throwing when [hijriMonth] is not a valid month (1-12) or the
 * result falls outside the supported Umm al-Qura range (~1300-1600 AH). Widget navigation
 * can treat `null` as "reached the supported edge" and no-op or hide the arrows.
 */
fun offsetHijriMonth(hijriYear: Int, hijriMonth: Int, offset: Int): HijrahYearMonth? {
    if (hijriMonth !in 1..12) return null
    return try {
        HijrahYearMonth(hijriYear, hijriMonth).plusMonth(offset)
    } catch (_: Exception) {
        null
    }
}

private fun formatNumber(value: Int, numeralStyle: NumeralStyle): String =
    if (numeralStyle == NumeralStyle.ARABIC_INDIC) {
        value.toString().map { digit ->
            ARABIC_INDIC_DIGITS[digit.code - '0'.code]
        }.joinToString("")
    } else {
        value.toString()
    }

private val ARABIC_INDIC_DIGITS = "٠١٢٣٤٥٦٧٨٩".toCharArray()