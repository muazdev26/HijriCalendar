package com.muazdev.hijricalendar.core

import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

/**
 * Regression test for the prev-month navigation freeze. Before the fix, `monthStart()`
 * re-summed the month chain from the nearest official fix on every call, making each
 * grid O(42·N²) — months before the first fix (1440-10) took ~2 s each and a full-range
 * scan never completed. After the fix, `monthStart()` is an O(1) table lookup.
 */
class PakistanCalendarPerformanceTest {

    @Test
    fun fullRangeScanIsFast() {
        PakistanHijriCalendar.prewarm()
        val total = measureTime {
            for (year in PakistanHijriCalendar.MIN_YEAR..PakistanHijriCalendar.MAX_YEAR) {
                for (month in 1..12) {
                    HijrahYearMonth(year, month).toCalendarMonth(pakistan = true)
                }
            }
        }
        assertTrue(
            total.inWholeSeconds < 5,
            "Full ${PakistanHijriCalendar.MIN_YEAR}-${PakistanHijriCalendar.MAX_YEAR} Pakistan scan took $total (was minutes/hung)",
        )
    }

    @Test
    fun previouslySlowMonthsAreNowFast() {
        PakistanHijriCalendar.prewarm()
        val suspects = listOf(
            HijrahYearMonth(1400, 1),
            HijrahYearMonth(1401, 10),
            HijrahYearMonth(1403, 12),
            HijrahYearMonth(1442, 4),
            HijrahYearMonth(1447, 12),
            HijrahYearMonth(1448, 1),
            HijrahYearMonth(1448, 2),
        )
        suspects.forEach { ym ->
            val (_, dur) = measureTimedValue { ym.toCalendarMonth(pakistan = true) }
            assertTrue(
                dur.inWholeMilliseconds < 250,
                "$ym took ${dur.inWholeMilliseconds}ms to generate a grid (was ~2000ms before the fix)",
            )
        }
    }

    /**
     * [PakistanHijriCalendar.isWarmForCurrentOverrides] is the gate the warm-up coordinator (and
     * any caller that wants to avoid being "the thread that builds") uses to skip an already-done
     * build. It reports warmness against the *current* overrides revision: a table built for an
     * older revision is cold again, because an override change re-anchors the month chain.
     */
    @Test
    fun isWarmForCurrentOverridesTracksRevision() {
        HijriMonthOverrides.setMonthLength(1450, 4, 29)
        HijriMonthOverrides.clearMonthLength(1450, 4)
        // The table (any previously-built one) is stale for the bumped revision.
        assertFalse(PakistanHijriCalendar.isWarmForCurrentOverrides())
        PakistanHijriCalendar.prewarm()
        assertTrue(PakistanHijriCalendar.isWarmForCurrentOverrides())
        // Still warm after a second prewarm: the build is one-time for a revision.
        PakistanHijriCalendar.prewarm()
        assertTrue(PakistanHijriCalendar.isWarmForCurrentOverrides())
    }
}