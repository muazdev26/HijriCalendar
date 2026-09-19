package com.muazdev.hijricalendar.core

import com.abdulrahman_b.hijrahdatetime.toHijrahDate
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
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

@OptIn(ExperimentalAtomicApi::class)
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

    private val sortedFixes: List<Pair<Pair<Int, Int>, Fix>> =
        FIXES.toList().sortedBy { prolepticMonth(it.first.first, it.first.second) }

    // ── Thread-safe month table (atomic snapshot) ─────────────────────────
    //
    // Both the starts chain and the override-free default-length cache are bundled
    // into a single immutable [MonthTable] snapshot.  Readers load the reference
    // once and access both maps without any further coordination.  Writers
    // (override changes) build a fresh table and CAS-publish it; concurrent
    // rebuilds are idempotent — only the winner is visible to readers.

    private class MonthTable(
        val starts: Map<Int, Long>,
        val lengths: Map<Int, Int>,
        val revision: Long,
    )

    private val monthTable: AtomicReference<MonthTable?> = AtomicReference(null)

    private fun monthTableIfNeeded(): MonthTable {
        val revision = HijriMonthOverrides.currentRevision
        val current = monthTable.load()
        if (current != null && current.revision == revision) return current
        val built = buildMonthTable(revision)
        // CAS loop: discard the build if another thread already published for this revision.
        while (true) {
            val existing = monthTable.load()
            if (existing != null && existing.revision == revision) return existing
            if (monthTable.compareAndSet(existing, built)) return built
        }
    }

    private fun buildMonthTable(revision: Long): MonthTable {
        val minKey = prolepticMonth(MIN_YEAR, 1)
        val maxKey = prolepticMonth(MAX_YEAR, 12)
        val lengths = (minKey..maxKey).associateWith { key ->
            val (y, m) = fromProleptic(key)
            FIXES[y to m]?.length ?: HijrahYearMonth(y, m).numberOfDays
        }
        val starts = buildMonthStarts(lengths)
        return MonthTable(starts, lengths, revision)
    }

    /**
     * Builds the absolute epoch-day start of every Pakistani Hijri month in the
     * supported range, keyed by [prolepticMonth], chaining from the first official
     * fix backward/forward.  Uses the precomputed [defaultLengths] for the walk;
     * user overrides are folded in via [lengthOfMonthForBuild] so a forced length
     * re-anchors all later months until the next fix snaps the chain back.
     */
    private fun buildMonthStarts(defaultLengths: Map<Int, Int>): Map<Int, Long> {
        val table = HashMap<Int, Long>((MAX_YEAR - MIN_YEAR + 1) * 12)
        val firstFix = sortedFixes.first()
        val anchorKey = prolepticMonth(firstFix.first.first, firstFix.first.second)
        val anchorStart = firstFix.second.startEpochDays
        val minKey = prolepticMonth(MIN_YEAR, 1)
        val maxKey = prolepticMonth(MAX_YEAR, 12)
        val fixStarts = sortedFixes.associate {
            prolepticMonth(it.first.first, it.first.second) to it.second.startEpochDays
        }
        table[anchorKey] = anchorStart

        // Walk backward from the first fix to MIN_YEAR.
        var running = anchorStart
        for (key in anchorKey - 1 downTo minKey) {
            val (y, m) = fromProleptic(key)
            running -= lengthOfMonthForBuild(y, m, defaultLengths)
            fixStarts[key]?.let { running = it }
            table[key] = running
        }

        // Walk forward from the first fix to MAX_YEAR.
        running = anchorStart
        for (key in anchorKey until maxKey) {
            val (y, m) = fromProleptic(key)
            running += lengthOfMonthForBuild(y, m, defaultLengths)
            fixStarts[key + 1]?.let { running = it }
            table[key + 1] = running
        }
        return table
    }

    /** Override-aware length used only during table construction (avoids re-entrancy). */
    private fun lengthOfMonthForBuild(year: Int, month: Int, defaultLengths: Map<Int, Int>): Int =
        HijriMonthOverrides.monthLength(year, month)
            ?: defaultLengths.getValue(prolepticMonth(year, month))

    /**
     * Builds the [monthTable] eagerly.  Call from a background thread at app
     * startup so the one-time warm-up never runs on the main thread during a
     * composition.  Overrides are folded in at this point, so a later override
     * change rebuilds the table on the next access.
     */
    fun prewarm() {
        monthTableIfNeeded()
    }

    /**
     * Effective length of the Pakistani [year]/[month] (1-12): a user override wins, then
     * the [FIXES] table, then the Umm al-Qura default.
     */
    fun lengthOfMonth(year: Int, month: Int): Int {
        require(year in MIN_YEAR..MAX_YEAR) { "Year $year is out of the supported range $MIN_YEAR..$MAX_YEAR" }
        return HijriMonthOverrides.monthLength(year, month)
            ?: defaultLengthOfMonth(year, month)
    }

    /**
     * The calculated Pakistani [year]/[month] length (FIXES table, falling back to Umm
     * al-Qura) — the value used when no override is set. Exposed so callers can render
     * "reset to calculation".
     */
    fun defaultLengthOfMonth(year: Int, month: Int): Int {
        require(year in MIN_YEAR..MAX_YEAR) { "Year $year is out of the supported range $MIN_YEAR..$MAX_YEAR" }
        return monthTableIfNeeded().lengths.getValue(prolepticMonth(year, month))
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
        val start = anchor.second.startEpochDays
        if (target < start) return walkBackFrom(anchor, target)
        if (target < start + lengthOfMonth(year, month)) return PakistanHijriDate(year, month, (target - start + 1).toInt())
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
        require(year in MIN_YEAR..MAX_YEAR) { "Year $year is out of the supported range $MIN_YEAR..$MAX_YEAR" }
        return monthTableIfNeeded().starts.getValue(prolepticMonth(year, month))
    }

    private fun prolepticMonth(year: Int, month: Int): Int = year * 12 + (month - 1)

    private fun fromProleptic(proleptic: Int): Pair<Int, Int> = proleptic.floorDiv(12) to (proleptic.mod(12) + 1)

    private fun nextMonth(year: Int, month: Int): Pair<Int, Int> =
        if (month == 12) (year + 1) to 1 else year to (month + 1)

    private fun previousMonth(year: Int, month: Int): Pair<Int, Int> =
        if (month == 1) (year - 1) to 12 else year to (month - 1)
}