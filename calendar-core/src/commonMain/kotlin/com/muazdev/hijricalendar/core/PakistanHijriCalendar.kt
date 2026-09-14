package com.muazdev.hijricalendar.core

import com.abdulrahman_b.hijrahdatetime.toHijrahDate
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

/**
 * A Gregorian <-> Pakistani Hijri date converter for the Ruet-e-Hilal (moon sighting)
 * calendar used in Pakistan.
 *
 * Pakistan decides each month's start by moon sighting, so its calendar is not an
 * algorithm — it is a list of committee decisions. This class encodes those decisions as
 * a set of absolute [Fix]es (the verified Gregorian day a specific Pakistani Hijri month
 * starts on, plus that month's length) and chains everything else from the nearest fix
 * using Umm al-Qura month lengths as the default for months with no official data.
 *
 * Data provenance (see [FIXES]):
 * - `1440` Shawwal through `1442` Rabi al-Thani: official Ruet-e-Hilal dates, scraped from
 *   the government calendar at github.com/bilgrami/hijri-calendar (derived from the PDFs on
 *   the now-unreachable moonsighting.pk/pakmoonsighting.pk portal).
 * - `1448` Rabi al-Awwal: verified first hand on 2026-08-15..2026-09-13 (a 30th was observed;
 *   Umm al-Qura gives only 29).
 * - Everything else: Umm al-Qura length (a proxy; Pakistan is usually within +-2 days of Saudi).
 *
 * This is a "base table + manual correction" model (AGENTS.md): months without a known
 * fix follow [HijrahYearMonth.numberOfDays], and fixes re-sync the chain whenever an
 * official decision is published. Extend [FIXES] to cover more months as data becomes
 * available.
 */
@Serializable
data class PakistanHijriDate(
    val year: Int,
    val month: Int,
    val day: Int,
) : Comparable<PakistanHijriDate> {

    /** The real-world Gregorian day of this date. */
    val localDate: LocalDate get() = PakistanHijriCalendar.hijriToGregorian(year, month, day)

    override fun compareTo(other: PakistanHijriDate): Int =
        compareValuesBy(this, other, { it.year }, { it.month }, { it.day })

    override fun toString(): String = "$year-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
}

object PakistanHijriCalendar {

    /** Absolute anchor of a Pakistani Hijri month. */
    data class Fix(val firstGregorian: LocalDate, val length: Int) {
        val monthLength: Int get() = length
        val startEpochDays: Long get() = firstGregorian.toEpochDays()
    }

    /**
     * Verified Pakistani month starts + lengths, keyed by `(year, month)`.
     *
     * Months 1440-10..1442-04 come from the official government scrap; 1448-03 and
     * 1448-04 cover the user-verified current window (1448-04's start is the continuation
     * of 1448-03's 30th and is an estimate, not an official decision).
     */
    val FIXES: Map<Pair<Int, Int>, Fix> = mapOf(
        // Official Ruet-e-Hilal (bilgrami/hijri-calendar)
        (1440 to 10) to Fix(LocalDate(2019, 6, 5), 29),
        (1440 to 11) to Fix(LocalDate(2019, 7, 4), 30),
        (1440 to 12) to Fix(LocalDate(2019, 8, 3), 29),
        (1441 to 1) to Fix(LocalDate(2019, 9, 1), 29),
        (1441 to 2) to Fix(LocalDate(2019, 9, 30), 30),
        (1441 to 3) to Fix(LocalDate(2019, 10, 30), 29),
        (1441 to 4) to Fix(LocalDate(2019, 11, 28), 30),
        (1441 to 5) to Fix(LocalDate(2019, 12, 28), 30),
        (1441 to 6) to Fix(LocalDate(2020, 1, 27), 29),
        (1441 to 7) to Fix(LocalDate(2020, 2, 25), 30),
        (1441 to 8) to Fix(LocalDate(2020, 3, 26), 30),
        (1441 to 9) to Fix(LocalDate(2020, 4, 25), 29),
        (1441 to 10) to Fix(LocalDate(2020, 5, 24), 30),
        (1441 to 11) to Fix(LocalDate(2020, 6, 23), 29),
        (1441 to 12) to Fix(LocalDate(2020, 7, 22), 30),
        (1442 to 1) to Fix(LocalDate(2020, 8, 21), 29),
        (1442 to 2) to Fix(LocalDate(2020, 9, 19), 29),
        (1442 to 3) to Fix(LocalDate(2020, 10, 18), 30),
        (1442 to 4) to Fix(LocalDate(2020, 11, 17), 29),
        // Current window (2026-2027): Rabi al-Awwal 1448 observed a 30th.
        (1448 to 3) to Fix(LocalDate(2026, 8, 15), 30),
        (1448 to 4) to Fix(LocalDate(2026, 9, 14), 30),
    )

    const val MIN_YEAR = 1400
    const val MAX_YEAR = 1500

    private val sortedFixes: List<Pair<Pair<Int, Int>, Fix>> = FIXES.toList().sortedBy { prolepticMonth(it.first.first, it.first.second) }

    /** Length of the Pakistani [year]/[month] (1-12), Umm al-Qura by default. */
    fun lengthOfMonth(year: Int, month: Int): Int {
        require(year in MIN_YEAR..MAX_YEAR) { "Year $year is out of the supported range $MIN_YEAR..$MAX_YEAR" }
        return FIXES[year to month]?.length
            ?: HijrahYearMonth(year, month).numberOfDays
    }

    /** The Gregorian day [day] of Pakistani [year]/[month] falls on. */
    fun hijriToGregorian(year: Int, month: Int, day: Int): LocalDate {
        val maxDay = lengthOfMonth(year, month)
        require(day in 1..maxDay) { "Invalid day $day for year-year $year-$month (max $maxDay)" }
        val start = monthStart(year, month)
        return LocalDate.fromEpochDays(start + (day - 1))
    }

    /** The Pakistani Hijri date of the given [date], or null when outside the supported window. */
    fun gregorianToHijri(date: LocalDate): PakistanHijriDate? {
        val target = date.toEpochDays()
        return try {
            val anchorIndex = sortedFixes.indexOfLast { it.second.startEpochDays <= target }
            if (anchorIndex >= 0) {
                walkFrom(sortedFixes[anchorIndex], target)
            } else {
                walkBackFrom(sortedFixes[0], target)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Today's Pakistani Hijri date in the system's timezone, or null when unresolvable. */
    fun today(): PakistanHijriDate? {
        return try {
            val now = kotlin.time.Clock.System.now()
            val localDate = now.toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date
            gregorianToHijri(localDate)
        } catch (exception: Exception) {
            println("PakistanHijriCalendar: failed to resolve today's date: $exception")
            null
        }
    }

    private fun walkFrom(anchor: Pair<Pair<Int, Int>, Fix>, target: Long): PakistanHijriDate? {
        var (year, month) = anchor.first
        var start = anchor.second.startEpochDays
        if (target < start) return walkBackFrom(anchor, target)
        if (target < start + anchor.second.length) return PakistanHijriDate(year, month, (target - start + 1).toInt())
        var iterations = 0
        while (iterations++ < 12 * (MAX_YEAR - MIN_YEAR + 1)) {
            val next = nextMonth(year, month)
            year = next.first
            month = next.second
            val nextStart = monthStart(year, month)
            if (target < nextStart) return walkBackFromMonth(year, month, nextStart, target)
            val length = lengthOfMonth(year, month)
            if (target < nextStart + length) {
                return PakistanHijriDate(year, month, (target - nextStart + 1).toInt())
            }
        }
        return null
    }

    private fun walkBackFrom(anchor: Pair<Pair<Int, Int>, Fix>, target: Long): PakistanHijriDate? {
        var (year, month) = anchor.first
        var iterations = 0
        while (iterations++ < 12 * (MAX_YEAR - MIN_YEAR + 1)) {
            val previous = previousMonth(year, month)
            val (previousYear, previousMonth) = previous
            val previousStart = monthStart(previousYear, previousMonth)
            if (target >= previousStart) {
                // Clamp so a few stray gap days around a re-sync fix collapse onto the
                // previous month's last day instead of producing an out-of-range day.
                val day = minOf((target - previousStart + 1).toLong(), lengthOfMonth(previousYear, previousMonth).toLong()).toInt()
                return PakistanHijriDate(previousYear, previousMonth, day)
            }
            year = previousYear
            month = previousMonth
        }
        return null
    }

    private fun walkBackFromMonth(year: Int, month: Int, start: Long, target: Long): PakistanHijriDate? {
        if (target >= start) return null
        var (y, m) = year to month
        var iterations = 0
        while (iterations++ < 12 * (MAX_YEAR - MIN_YEAR + 1)) {
            val previous = previousMonth(y, m)
            val (previousYear, previousMonth) = previous
            val previousStart = monthStart(previousYear, previousMonth)
            if (target >= previousStart) {
                val day = minOf((target - previousStart + 1).toLong(), lengthOfMonth(previousYear, previousMonth).toLong()).toInt()
                return PakistanHijriDate(previousYear, previousMonth, day)
            }
            y = previousYear
            m = previousMonth
        }
        return null
    }

    private fun monthStart(year: Int, month: Int): Long {
        // Chain forward from the last fix at-or-before this month (backward for earlier
        // months). Months with their own fix return the fix's absolute start; the sparse
        // 1442..1448 window chains on Umm al-Qura lengths and the next fix re-syncs it.
        val anchor = sortedFixes.lastOrNull { prolepticMonth(it.first.first, it.first.second) <= prolepticMonth(year, month) }
            ?: sortedFixes.first()
        val (fixYear, fixMonth) = anchor.first
        val fixStart = anchor.second.startEpochDays
        val deltaMonths = prolepticMonth(year, month) - prolepticMonth(fixYear, fixMonth)
        return if (deltaMonths == 0) {
            fixStart
        } else if (deltaMonths > 0) {
            // Sum the lengths of the fix month itself plus every month up to (but not
            // including) the queried month. Including the fix month is essential: without
            // it, the month immediately after a fix would reuse the fix's own start day,
            // shifting every subsequent month a full 29-30 days early (reproduced as the
            // "today leaks into the next month + whole months skipped" grid bug).
            var days = 0L
            for (i in prolepticMonth(fixYear, fixMonth) until prolepticMonth(year, month)) {
                val (y, m) = fromProleptic(i)
                days += lengthOfMonth(y, m)
            }
            fixStart + days
        } else {
            var days = 0L
            for (i in prolepticMonth(year, month) until prolepticMonth(fixYear, fixMonth)) {
                val (y, m) = fromProleptic(i)
                days -= lengthOfMonth(y, m)
            }
            fixStart + days
        }
    }

    private fun prolepticMonth(year: Int, month: Int): Int = year * 12 + (month - 1)

    private fun fromProleptic(proleptic: Int): Pair<Int, Int> = proleptic.floorDiv(12) to (proleptic.mod(12) + 1)

    private fun nextMonth(year: Int, month: Int): Pair<Int, Int> =
        if (month == 12) (year + 1) to 1 else year to (month + 1)

    private fun previousMonth(year: Int, month: Int): Pair<Int, Int> =
        if (month == 1) (year - 1) to 12 else year to (month - 1)
}