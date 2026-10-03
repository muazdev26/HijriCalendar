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
     * quadratically slower — and *the further from the anchor, the worse*. The regression was
     * re-injected into `monthStart` to measure what that looks like from here: **8705x**, against a
     * correct ~52x.
     *
     * **Why the anchor year and not the two ends of the range.** This was originally written as
     * `MIN_YEAR` against `MAX_YEAR` with a 4x allowance, and it was wrong twice over.
     *
     * *It had almost no power.* The two range ends are only ~1.3x apart in walk distance — roughly
     * 490 months from the first official fix to 1400, and ~630 to 1500 — so a fully regressed
     * O(N^2) implementation would have measured `(630/490)^2`, about **1.65x**, and sailed through a
     * 4x bound. A test whose passing range contains the bug it exists to catch is decoration.
     *
     * *And it was flaky.* A single un-warmed sample of a ~5 ms batch, compared against another
     * single sample, is a coin flip on a loaded runner. The measured numbers here are ~2.7 ms
     * (MIN_YEAR) and ~5.4 ms (MAX_YEAR), so the whole denominator is milliseconds wide; one GC pause
     * or scheduler stall in the second sample exceeds a 4x ratio on its own. That is not
     * hypothetical — this assertion failed in CI on a two-core runner while passing locally, which
     * is the failure mode a timing gate must be built not to have.
     *
     * The fix is to compare a year next to an official fix against the far end of the range, which
     * is where the signal actually is. `gregorianToHijri` walks month-by-month from the nearest
     * preceding fix, so a year beside a fix resolves in single-digit steps while the far end walks
     * ~630. That makes the *linear* expectation ~52x rather than ~1.3x, so the same 4x-of-headroom
     * style bound now sits far from the regression it guards: [MAX_ALLOWED_RATIO] is 2.9x above the
     * measured linear ratio (52.0 and 51.5 on two runs, ±7% across samples) and 58x below the
     * re-injected quadratic re-sum, which measured 8705x. It also cannot be broken by warm-up:
     * see [measureBothEnds].
     */
    @Test
    fun theCostOfAMonthDoesNotGrowWithDistanceFromTheAnchor() {
        val anchorYear = requireNotNull(PakistanHijriCalendar.FIXES.keys.minOfOrNull { it.first }) {
            "FIXES is empty, so there is no anchor year to measure against"
        }
        val farYear = PakistanHijriCalendar.MAX_YEAR

        val (near, far) = measureBothEnds(anchorYear, farYear)

        assertTrue(
            far <= near * MAX_ALLOWED_RATIO,
            "A year at the far end of the range took $far but one beside an official fix took " +
                "$near, over the ${MAX_ALLOWED_RATIO}x allowance. A walk that costs one step per " +
                "month from the nearest fix is linear in distance; growth faster than that is the " +
                "O(N^2) chain re-sum this test guards against.",
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

    /**
     * Samples both ends, interleaved, and returns the **minimum** duration seen for each.
     *
     * Three decisions here, each of which is a way the previous version of this test failed:
     *
     * * **A long warm-up.** The JIT keeps making the cheap end faster for dozens of rounds, so a
     *   short warm-up does not settle the ratio — it makes it *drift*. Measured here, the
     *   anchor-to-far ratio was 12.6 after two warm-up rounds, 32 after seven samples, and a
     *   stable 52.0 / 51.5 across two runs after forty. An un-warmed measurement is not a
     *   conservative one; it is an arbitrary one.
     * * **Interleaving.** Sampling the near end fully and then the far end lets any load ramp
     *   during the build bias the second measurement. Alternating the two means drift hits both.
     * * **Minimum, not mean or median.** The question is "how fast can this run", and the
     *   distribution is one-sided: JIT, cache and scheduling only ever make a sample slower. The
     *   minimum converges on the true cost and is the estimator a stall cannot poison.
     */
    private fun measureBothEnds(nearYear: Int, farYear: Int): Pair<Duration, Duration> {
        PakistanHijriCalendar.prewarm()

        repeat(WARMUP_ROUNDS) {
            timeMonths(nearYear)
            timeMonths(farYear)
        }

        var near = Duration.INFINITE
        var far = Duration.INFINITE
        repeat(SAMPLES) {
            val nearSample = timeMonths(nearYear)
            if (nearSample < near) near = nearSample
            val farSample = timeMonths(farYear)
            if (farSample < far) far = farSample
        }
        return near to far
    }

    private companion object {
        /**
         * Rounds of untimed work before sampling. Forty settles the ratio; see
         * [measureBothEnds] for the measurements that justify the number. Costs ~300ms.
         */
        const val WARMUP_ROUNDS = 40

        /** Timed interleaved samples per end. Seven is enough for the minimum to settle. */
        const val SAMPLES = 7

        /**
         * How much more the far end may cost than the anchor year.
         *
         * Linear-in-distance measures ~52x. The pre-fix O(N^2) chain re-sum was re-injected and
         * measured 8705x. This bound sits 2.9x above the former and 58x below the latter, so it
         * absorbs a loaded runner without admitting the regression.
         */
        const val MAX_ALLOWED_RATIO = 150
    }
}
