package com.muazdev.hijricalendar.core

import com.abdulrahman_b.hijrahdatetime.HijrahDate
import com.abdulrahman_b.hijrahdatetime.toHijrahDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Computes today's Hijri date in the given [adjustmentDays]-shifted space.
 *
 * This is the single, shared implementation used both for "today" cell highlighting and
 * for `goToToday()`. It returns `null` instead of throwing when today's date cannot be
 * resolved (e.g. a broken system clock or a timezone provider failure); callers treat
 * `null` as "no today" and silently skip today-related behavior. The failure is echoed
 * to standard error so it is diagnosable in production logs.
 */
internal fun todayHijriDate(adjustmentDays: Int): HijrahDate? {
    return try {
        val now = kotlin.time.Clock.System.now()
        val localDate = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
        localDate.plus(adjustmentDays, DateTimeUnit.DAY).toHijrahDate()
    } catch (exception: Exception) {
        println("HijriCalendar: failed to resolve today's Hijri date: $exception")
        null
    }
}