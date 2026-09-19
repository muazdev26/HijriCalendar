package com.muazdev.hijricalendar.widgetdata

import com.abdulrahman_b.hijrahdatetime.toHijrahDate
import com.abdulrahman_b.hijrahdatetime.toLocalDate
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import com.muazdev.hijricalendar.core.CalendarMonth
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
 * [localizedHijriMonthNames] and [localizedWeekdayNames] may override the default
 * English names; both must be 12 / 7 entries in [WeekDay] enum order (Saturday-first
 * for weekdays). Returns `null` when the month is outside the supported Umm al-Qura
 * range (~1300-1600 AH) instead of throwing.
 */
fun buildHijriMonthWidgetData(
    hijriYear: Int,
    hijriMonth: Int,
    adjustmentDays: Int,
    firstDayOfWeekIndex: Int = WeekDay.DEFAULT_FIRST_DAY.index,
    numeralStyle: NumeralStyle = NumeralStyle.WESTERN,
    localizedHijriMonthNames: List<String>? = null,
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
    }

    val firstLocal = calendarMonth.gregorianFirstDay
    val lastLocal = calendarMonth.gregorianLastDay
    val gregorianRange = when {
        firstLocal.month == lastLocal.month && firstLocal.year == lastLocal.year ->
            "${DefaultGregorianMonthNames[firstLocal.month.ordinal]} ${firstLocal.year}"
        firstLocal.year == lastLocal.year ->
            "${DefaultGregorianMonthNames[firstLocal.month.ordinal]} - " +
                "${DefaultGregorianMonthNames[lastLocal.month.ordinal]} ${firstLocal.year}"
        else ->
            "${DefaultGregorianMonthNames[firstLocal.month.ordinal]} ${firstLocal.year} - " +
                "${DefaultGregorianMonthNames[lastLocal.month.ordinal]} ${lastLocal.year}"
    }

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
    }

    return HijriMonthWidgetData(
        hijriYear = hijriYear,
        hijriMonth = hijriMonth,
        hijriMonthName = hijriMonthName,
        gregorianRange = gregorianRange,
        weekdayHeaders = weekdayHeaders,
        days = days,
        adjustmentDays = adjustmentDays,
    )
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
 * Returns `null` when the anchor falls outside the supported Umm al-Qura range.
 */
fun todayHijriWidgetData(anchorEpochDay: Long, adjustmentDays: Int): TodayHijriWidgetData? {
    val anchor = try {
        LocalDate.fromEpochDays(anchorEpochDay)
    } catch (_: Exception) {
        return null
    }
    val hijri = try {
        observe(anchor, adjustmentDays).toHijrahDate()
    } catch (_: Exception) {
        return null
    }
    val hijriMonthNumber = hijri.month.number
    return TodayHijriWidgetData(
        hijriDay = hijri.day,
        hijriMonth = hijriMonthNumber,
        hijriYear = hijri.year,
        hijriMonthName = DefaultHijriMonthNames.getOrNull(hijriMonthNumber - 1) ?: "",
        gregorianDate = "${anchor.day} ${DefaultGregorianMonthNames[anchor.month.ordinal]} ${anchor.year}",
        weekdayName = WeekDay.fromDayOfWeek(anchor.dayOfWeek).shortName,
        adjustmentDays = adjustmentDays,
    )
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