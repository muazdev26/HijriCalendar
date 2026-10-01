package com.muazdev.hijricalendar.core

import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

/**
 * Regression test for the prev-month navigation freeze. Before the fix, `monthStart()`
 * re-summed the month chain from the nearest official fix on every call, making each
 * grid O(42·N²) — months before the first fix (1440-10) took ~2 s each and a full-range
 * scan never completed. After the fix, `monthStart()` is an O(1) table lookup.
 *
 * **Why these assertions are ratios rather than wall-clock budgets.** A millisecond threshold is
 * only meaningful against one platform. A debug Kotlin/Native build on the iOS *simulator* is
 * roughly an order of magnitude slower than a JVM desktop run at comparable optimisation, so a
 * budget tuned on the JVM fails there for reasons that have nothing to do with the code. That is not
 * hypothetical: the full-range scan measured ~1.3 s on the JVM and ~7 s on the simulator against one
 * 5 s budget, and platform-sniffing was rejected as a fix because neither `kotlin.native.Platform`
 * nor `java.lang.Class` can be named from `commonTest` at all.
 *
 * A ratio sidesteps the question entirely and tests the *actual* invariant: if the lookup is O(1),
 * the cost of a month is the same wherever in the range it sits, and on every platform. If it is
 * O(N²) in the distance from the anchor, a month at the far end of the range costs far more than one
 * at the near end — on any platform, by whatever factor. The absolute ceilings that remain are set
 * far above any plausible correct implementation and exist only to turn "hung" into a failure rather
 * than a timeout, which is what the original symptom was.
 */
class PakistanCalendarPerformanceTest {

    /**
     * The invariant: the cost of building a grid does not grow across the range.
     *
     * This is the assertion that would have caught the original bug. The pre-fix `monthStart()`
     * re-summed the month chain from the nearest official fix, so months far from that anchor were
     * quadratically slower — and *the further from the anchor, the worse*, which is precisely what a
     * comparison of two batches at opposite ends of the range measures and a single fixed budget
     * does not.
     *
     * The allowance is 4x. Under the fix the two batches are equal up to timer noise; the pre-fix
     * behaviour was orders of magnitude, not a few percent, so 4x has a wide margin against both a
     * real regression and a noisy simulator.
     */
    @Test
    fun theCostOfAMonthDoesNotGrowAcrossTheRange() {
        PakistanHijriCalendar.prewarm()

        val early = timeMonths(PakistanHijriCalendar.MIN_YEAR)
        val late = timeMonths(PakistanHijriCalendar.MAX_YEAR)

        assertTrue(
            late <= early * 4,
            "A year at the far end of the range took $late but one at the near end took $early. " +
                "An O(1) month lookup costs the same wherever it sits; growth with distance from " +
                "the anchor is the O(N²) chain re-sum this test guards against.",
        )
    }

    /**
     * A single month must be cheap even at the far end of the range, which is where the original
     * freeze was reported.
     *
     * 1500 ms against a pre-fix cost of ~2000 ms per grid: tight enough to fail on the regression,
     * loose enough that no platform — including a debug build on a simulator — trips it for being
     * slow rather than for being quadratic.
     */
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
                dur < Duration.parse("1500ms"),
                "$ym took $dur to generate a grid, over the 1500ms ceiling " +
                    "(was ~2000ms before the fix)",
            )
        }
    }

    /**
     * A full-range scan must *terminate*. Not a performance assertion in any useful sense — the
     * bound is two minutes, which a correct implementation clears by a wide margin on any platform
     * and the pre-fix implementation never cleared at all.
     *
     * Kept as a separate test rather than folded into the ratio above because the two answer
     * different questions: one asks whether the cost is bounded, the other whether the worst case is
     * finite at all. Before the fix, only this one would have been able to fail.
     */
    @Test
    fun fullRangeScanTerminates() {
        PakistanHijriCalendar.prewarm()
        val total = measureTime {
            for (year in PakistanHijriCalendar.MIN_YEAR..PakistanHijriCalendar.MAX_YEAR) {
                for (month in 1..12) {
                    HijrahYearMonth(year, month).toCalendarMonth(pakistan = true)
                }
            }
        }
        assertTrue(
            total < Duration.parse("2m"),
            "Full ${PakistanHijriCalendar.MIN_YEAR}-${PakistanHijriCalendar.MAX_YEAR} Pakistan " +
                "scan took $total; before the fix it never completed",
        )
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

    /**
     * Total time to build all twelve months of [year].
     *
     * A whole year rather than a single month, because at these durations a single month is close
     * to timer granularity on a debug build and a ratio computed from noise is a flaky test rather
     * than a sharp one.
     */
    private fun timeMonths(year: Int): Duration {
        val warmUp = measureTime { HijrahYearMonth(year, 1).toCalendarMonth(pakistan = true) }
        return measureTime {
            for (month in 1..12) {
                HijrahYearMonth(year, month).toCalendarMonth(pakistan = true)
            }
        } + warmUp
    }
}
