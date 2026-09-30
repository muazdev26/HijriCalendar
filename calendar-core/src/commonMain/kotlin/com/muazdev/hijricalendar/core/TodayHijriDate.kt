package com.muazdev.hijricalendar.core

import com.abdulrahman_b.hijrahdatetime.HijrahDate
import com.abdulrahman_b.hijrahdatetime.toHijrahDate
import kotlinx.datetime.DateTimeArithmeticException
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Computes today's Hijri date in the given [adjustmentDays]-shifted space.
 *
 * This is the single, shared implementation used for "today" cell highlighting,
 * `goToToday()`, and as a building block for the `calendar-widget-data` projection API
 * via [todayHijriWidgetData]. It returns `null` instead of throwing when today's date
 * cannot be resolved (e.g. a broken system clock or a timezone provider failure);
 * callers treat `null` as "no today" and silently skip today-related behavior.
 *
 * A null means the **environment** failed — an unusable system clock, an unavailable timezone
 * database, or a date outside Umm al-Qura's table. It does not cover internal faults: those
 * propagate. See [orNullIfOutOfRange] for why the range exceptions are two unrelated types.
 *
 * The failure is reported to [CalendarLog] (silently discarded unless a consumer installs a
 * logger) so it is diagnosable in production without the library writing to standard out.
 */
public fun todayHijriDate(adjustmentDays: Int): HijrahDate? {
    return try {
        val now = kotlin.time.Clock.System.now()
        val localDate = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
        localDate.plus(adjustmentDays, DateTimeUnit.DAY).toHijrahDate()
    } catch (e: IllegalArgumentException) {
        // Covers IllegalTimeZoneException, which is an IllegalArgumentException.
        CalendarLog.w(LOG_TAG, "failed to resolve today's Hijri date: unusable clock or timezone", e)
        null
    } catch (e: DateTimeArithmeticException) {
        CalendarLog.w(LOG_TAG, "failed to resolve today's Hijri date", e)
        null
    }
}

private const val LOG_TAG = "HijriCalendar"