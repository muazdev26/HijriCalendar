package com.muazdev.hijricalendar.core

import com.abdulrahman_b.hijrahdatetime.toHijrahDate
import com.abdulrahman_b.hijrahdatetime.toLocalDate
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import kotlin.math.abs
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Resolves "observed" Hijri dates: the calendar that results from applying the user's
 * manual month-length overrides ([HijriMonthOverrides]) on top of the Umm al-Qura
 * calculation.
 *
 * Without overrides the observed calendar equals the Umm al-Qura calculation. Forcing a
 * month to 29 or 30 days moves that month's end and shifts every later month by the same
 * cumulative delta, until a re-sync point (a Pakistan fix) snaps the chain back.
 *
 * All epoch-day math is done in Gregorian epoch days (never by dividing time by 86400),
 * mirroring the approach in [PakistanHijriCalendar].
 */
object ObservedHijriCalendar {

    /**
     * The calculated (default) Umm al-Qura length of [year]/[month] — the value used when
     * no override is set. Exposed so callers can render "reset to calculation".
     */
    fun defaultLength(year: Int, month: Int): Int = HijrahYearMonth(year, month).numberOfDays

    /** The effective length of [year]/[month] after applying user overrides. */
    fun observedLength(year: Int, month: Int): Int =
        HijriMonthOverrides.monthLength(year, month) ?: defaultLength(year, month)

    /** The absolute Gregorian epoch-day on which the observed "1" of [year]/[month] falls. */
    fun observedMonthStartEpoch(year: Int, month: Int): Long =
        observedMonthStartEpoch(prolepticMonth(year, month))

    private fun observedMonthStartEpoch(proleptic: Int): Long {
        val base = HijrahYearMonth(proleptic / 12, proleptic % 12 + 1)
            .firstDay.toLocalDate().toEpochDays()
        return base + cumulativeDeltaBefore(proleptic)
    }

    /**
     * The observed Hijri date of the given Gregorian [epochDay], or null when outside the
     * supported range.
     *
     * Because observed month starts are strictly increasing and deviate from the Umm al-Qura
     * calculation by at most the cumulative drift of the user's overrides, the containing
     * month is found without walking: binary search over the proleptic-month axis, bounded
     * by a window sized to the total override drift. This terminates in O(log drift) probes
     * regardless of how extreme the override set is.
     */
    fun observedDateAt(epochDay: Long): ObservedHijriDate? {
        val base = try {
            LocalDate.fromEpochDays(epochDay).toHijrahDate()
        } catch (_: Exception) {
            return null
        }
        val baseProleptic = prolepticMonth(base.year, base.month.number)

        // Total override drift magnitude, in days, bounds how far any observed month can sit
        // from its UAQ counterpart (mixed-sign overrides can peak between extremes, so use the
        // sum of absolute deltas). Each override shifts by at most one day, and month starts
        // are strictly increasing, so the containing month lies within ±(drift/29 + 1) months
        // of the UAQ month. Search [base - window, base + window].
        val totalDriftDays = HijriMonthOverrides.all().entries.sumOf { (key, length) ->
            abs(length - defaultLength(key.first, key.second)).toLong()
        }
        val window = (totalDriftDays / 29 + 1).toInt()

        // Largest proleptic month whose observed start is on or before epochDay.
        var lo = baseProleptic - window
        var hi = baseProleptic + window
        while (lo < hi) {
            val mid = lo + (hi - lo + 1) / 2
            if (observedMonthStartEpoch(mid) <= epochDay) {
                lo = mid
            } else {
                hi = mid - 1
            }
        }
        val year = lo / 12
        val month = lo % 12 + 1
        val start = observedMonthStartEpoch(year, month)
        return ObservedHijriDate(
            year,
            month,
            (epochDay - start + 1).toInt(),
            observedLength(year, month),
        )
    }

    /** The Gregorian day [day] of observed [year]/[month] falls on. */
    fun observedToGregorian(year: Int, month: Int, day: Int): LocalDate =
        LocalDate.fromEpochDays(observedMonthStartEpoch(year, month) + (day - 1))

    /** Today's observed Hijri date in [adjustmentDays]-shifted space, or null on failure. */
    fun today(adjustmentDays: Int): ObservedHijriDate? {
        return try {
            val now = kotlin.time.Clock.System.now()
            val localDate = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
            observedDateAt(localDate.plus(adjustmentDays, DateTimeUnit.DAY).toEpochDays())
        } catch (_: Exception) {
            null
        }
    }

    private fun cumulativeDeltaBefore(proleptic: Int): Long {
        var delta = 0L
        for ((key, length) in HijriMonthOverrides.all()) {
            val (year, month) = key
            if (prolepticMonth(year, month) >= proleptic) continue
            delta += (length - defaultLength(year, month)).toLong()
        }
        return delta
    }

    private fun prolepticMonth(year: Int, month: Int): Int = year * 12 + (month - 1)
}