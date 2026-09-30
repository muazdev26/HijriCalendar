package com.muazdev.hijricalendar.core

import com.abdulrahman_b.hijrahdatetime.toHijrahDate
import com.abdulrahman_b.hijrahdatetime.toLocalDate
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import kotlin.math.abs
import kotlinx.datetime.DateTimeArithmeticException
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Resolves "observed" Hijri dates: the calendar that results from applying user
 * month-length overrides ([HijriMonthLengths]) on top of the Umm al-Qura calculation.
 *
 * Without overrides the observed calendar equals the Umm al-Qura calculation. Forcing a
 * month to 29 or 30 days moves that month's end and shifts every later month by the same
 * cumulative delta, until a re-sync point (a Pakistan fix) snaps the chain back.
 *
 * All epoch-day math is done in Gregorian epoch days (never by dividing time by 86400),
 * mirroring the approach in [PakistanHijriCalendar].
 *
 * Every function takes an explicit `overrides` table, defaulting to the process-wide
 * [HijriMonthOverrides.current]. Pass your own [HijriMonthLengths] to scope overrides to one
 * calendar; two tables can disagree without seeing each other.
 */
public object ObservedHijriCalendar {

    /**
     * The calculated (default) Umm al-Qura length of [year]/[month] — the value used when
     * no override is set. Exposed so callers can render "reset to calculation".
     */
    public fun defaultLength(year: Int, month: Int): Int = HijrahYearMonth(year, month).numberOfDays

    /**
     * The effective length of [year]/[month] after applying user overrides.
     *
     * @param overrides table to consult; defaults to the process-wide one.
     */
    public fun observedLength(year: Int, month: Int, overrides: HijriMonthLengths = HijriMonthOverrides.current): Int =
        overrides.monthLength(year, month) ?: defaultLength(year, month)

    /**
     * The absolute Gregorian epoch-day on which the observed "1" of [year]/[month] falls.
     *
     * @param overrides table to consult; defaults to the process-wide one.
     */
    public fun observedMonthStartEpoch(year: Int, month: Int, overrides: HijriMonthLengths = HijriMonthOverrides.current): Long =
        observedMonthStartEpoch(prolepticMonth(year, month), overrides)

    private fun observedMonthStartEpoch(proleptic: Int, overrides: HijriMonthLengths): Long {
        val base = HijrahYearMonth(proleptic / 12, proleptic % 12 + 1)
            .firstDay.toLocalDate().toEpochDays()
        return base + cumulativeDeltaBefore(proleptic, overrides)
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
     *
     * @param overrides table to consult; defaults to the process-wide one.
     */
    public fun observedDateAt(epochDay: Long, overrides: HijriMonthLengths = HijriMonthOverrides.current): ObservedHijriDate? {
        // A date outside Umm al-Qura's table is the only legitimate null here. Anything else
        // (a missing month start, an arithmetic slip) propagates rather than reading as "no date".
        val base = orNullIfOutOfRange(
            onFailure = { CalendarLog.d(LOG_TAG, "epoch day $epochDay is outside Umm al-Qura's table", it) },
        ) { LocalDate.fromEpochDays(epochDay).toHijrahDate() } ?: return null
        val baseProleptic = prolepticMonth(base.year, base.month.number)

        // Total override drift magnitude, in days, bounds how far any observed month can sit
        // from its UAQ counterpart (mixed-sign overrides can peak between extremes, so use the
        // sum of absolute deltas). Each override shifts by at most one day, and month starts
        // are strictly increasing, so the containing month lies within ±(drift/29 + 1) months
        // of the UAQ month. Search [base - window, base + window].
        val totalDriftDays = overrides.all().entries.sumOf { (key, length) ->
            abs(length - defaultLength(key.first, key.second)).toLong()
        }
        val window = (totalDriftDays / 29 + 1).toInt()

        // Largest proleptic month whose observed start is on or before epochDay.
        var lo = baseProleptic - window
        var hi = baseProleptic + window
        while (lo < hi) {
            val mid = lo + (hi - lo + 1) / 2
            if (observedMonthStartEpoch(mid, overrides) <= epochDay) {
                lo = mid
            } else {
                hi = mid - 1
            }
        }
        val year = lo / 12
        val month = lo % 12 + 1
        val start = observedMonthStartEpoch(year, month, overrides)
        return ObservedHijriDate(
            year = year,
            month = month,
            day = (epochDay - start + 1).toInt(),
            monthLength = observedLength(year, month, overrides),
            // Resolved here rather than left to the constructor default, so the stored Gregorian
            // day belongs to *this* overrides table.
            localDate = LocalDate.fromEpochDays(epochDay),
        )
    }

    /**
     * The Gregorian day [day] of observed [year]/[month] falls on.
     *
     * @param overrides table to consult; defaults to the process-wide one.
     */
    public fun observedToGregorian(year: Int, month: Int, day: Int, overrides: HijriMonthLengths = HijriMonthOverrides.current): LocalDate =
        LocalDate.fromEpochDays(observedMonthStartEpoch(year, month, overrides) + (day - 1))

    /**
     * Today's observed Hijri date in [adjustmentDays]-shifted space, or null when it cannot be
     * resolved.
     *
     * Null here means the **environment** failed (an unusable system clock or timezone
     * database). "Date is out of range" is [observedDateAt]'s case, and it logs separately.
     *
     * @param overrides table to consult; defaults to the process-wide one.
     */
    public fun today(adjustmentDays: Int, overrides: HijriMonthLengths = HijriMonthOverrides.current): ObservedHijriDate? {
        return try {
            val now = kotlin.time.Clock.System.now()
            val localDate = now.toLocalDateTime(TimeZone.currentSystemDefault()).date
            observedDateAt(localDate.plus(adjustmentDays, DateTimeUnit.DAY).toEpochDays(), overrides)
        } catch (e: IllegalArgumentException) {
            // Covers IllegalTimeZoneException, which is an IllegalArgumentException.
            CalendarLog.w(LOG_TAG, "failed to resolve today's date: unusable clock or timezone", e)
            null
        } catch (e: DateTimeArithmeticException) {
            CalendarLog.w(LOG_TAG, "failed to resolve today's date", e)
            null
        }
    }

    private fun cumulativeDeltaBefore(proleptic: Int, overrides: HijriMonthLengths): Long {
        var delta = 0L
        for ((key, length) in overrides.all()) {
            val (year, month) = key
            if (prolepticMonth(year, month) >= proleptic) continue
            delta += (length - defaultLength(year, month)).toLong()
        }
        return delta
    }

    private fun prolepticMonth(year: Int, month: Int): Int = year * 12 + (month - 1)
}

private const val LOG_TAG = "ObservedHijriCalendar"