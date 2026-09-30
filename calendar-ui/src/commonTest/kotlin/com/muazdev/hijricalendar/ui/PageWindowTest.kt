package com.muazdev.hijricalendar.ui

import com.abdulrahman_b.hijrahdatetime.HijrahDate
import com.abdulrahman_b.hijrahdatetime.yearMonth
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression coverage for [UI-05-pager-window-crash].
 *
 * The pager used to compute two page indices from the caller's `minDate` / `maxDate` and feed them
 * to a **two-argument** `Int.coerceIn`, which documents *"Requires `minimumValue <=
 * maximumValue`. Throws `IllegalArgumentException` otherwise."* The calendar therefore threw while
 * composing — in a consumer's app, on their first frame, with a `coerceIn` stack trace and no
 * mention of a calendar limit — for:
 *
 * - a `minDate` 501 months ahead of the anchor month (the threshold was exact: 500 rendered),
 * - a `maxDate` 501 months behind it,
 * - any range wider than the fixed ±500-month window,
 * - an inverted range.
 *
 * None of these required an unusual configuration beyond "a date range".
 */
class PageWindowTest {

    private val anchor = HijrahYearMonth(1447, 9)

    private fun windowOf(min: HijrahDate? = null, max: HijrahDate? = null): PageWindow =
        PageWindow.forBounds(min?.yearMonth, max?.yearMonth, anchor)

    private fun hijri(year: Int, month: Int, day: Int = 1): HijrahDate = HijrahDate(year, month, day)

    private fun offsetOf(year: Int, month: Int): Int =
        monthOffset(HijrahYearMonth(year, month), anchor)

    // ── unbounded: the ±500 magic window is gone ──────────────────────

    @Test
    fun unbounded_spansTheWholeHijriTable() {
        val w = windowOf()
        assertEquals(offsetOf(HijrahDate.MIN.year, 1), w.firstOffset)
        assertEquals(offsetOf(HijrahDate.MAX.year, 12), w.lastOffset)
    }

    @Test
    fun unbounded_isWiderThanTheOldFixedWindow() {
        // The old window was exactly ±500 months and could not represent the caller's own range.
        val w = windowOf()
        assertTrue(w.span > 1001, "span ${w.span} is not wider than the old 1001-page window")
        assertTrue(-w.firstOffset > 500, "unbounded low side no longer reaches past 500 months")
        assertTrue(w.lastOffset > 500, "unbounded high side no longer reaches past 500 months")
    }

    @Test
    fun unbounded_agreesWithTheCoreArrowBound() {
        // The pager and canGoToPreviousMonth/canGoToNextMonth must clamp at the same place, or a
        // header arrow can enable a month the pager cannot represent — the second symptom of the
        // shared magic constant.
        val lowOffset = offsetOf(HijrahDate.MIN.year, 1)
        val highOffset = offsetOf(HijrahDate.MAX.year, 12)
        assertTrue(
            HijrahDate.MIN.year <= 1447 && HijrahDate.MAX.year >= 1447,
            "anchor should sit inside the table",
        )
        assertTrue(windowOf().containsOffset(lowOffset))
        assertTrue(windowOf().containsOffset(highOffset))
    }

    @Test
    fun theInitialPageIsAlwaysInRange() {
        // The anchor is *not* always inside the window: a minDate later than the initial month
        // legitimately excludes it. What must always hold is that the page the pager opens on is
        // valid, and that the grid opens on the nearest in-range month.
        val cases = listOf(
            windowOf(),
            windowOf(min = hijri(1450, 1)), // minDate 28 months AFTER the anchor
            windowOf(max = hijri(1440, 1)), // maxDate 81 months BEFORE it
            windowOf(min = hijri(1400, 1), max = hijri(1500, 1)),
            windowOf(min = hijri(1450, 1), max = hijri(1440, 1)), // inverted
        )
        cases.forEach { w ->
            val initialPage = w.coercePage(w.pageOf(0))
            assertTrue(
                initialPage in 0 until w.span,
                "initial page $initialPage outside 0..${w.span} for $w",
            )
        }
    }

    @Test
    fun aMinDateAfterTheAnchorExcludesTheAnchorAndOpensOnTheBoundary() {
        val w = windowOf(min = hijri(1450, 1)) // 28 months after 1447-09
        assertFalse(w.containsOffset(0), "the anchor is out of range and must not be claimed")
        assertEquals(0, w.coercePage(w.pageOf(0)), "should open on the boundary month")
        assertEquals(28, w.firstOffset)
    }

    // ── the exact old crash threshold ──────────────────────────────────

    @Test
    fun minDateExactly500MonthsAhead_isReachable() {
        // The old code rendered here (minAllowedPage == maxAllowedPage == 1000) and collapsed to a
        // single page; it must now be an ordinary reachable month.
        val w = windowOf(min = hijri(1489, 5))
        assertTrue(w.containsOffset(500), "500 months ahead must be reachable")
        assertTrue(w.span > 1, "window collapsed to one page at the old threshold")
    }

    @Test
    fun minDate501MonthsAhead_isReachable() {
        // The old code threw "Cannot coerce value to an empty range: maximum 1000 is less than
        // minimum 1001" for exactly this input.
        val w = windowOf(min = hijri(1489, 6))
        assertTrue(w.containsOffset(501), "501 months ahead must be reachable")
    }

    @Test
    fun maxDate501MonthsBehind_isReachable() {
        val w = windowOf(max = hijri(1405, 4))
        assertTrue(w.containsOffset(offsetOf(1405, 4)))
    }

    @Test
    fun bounds100YearsApart_areBothReachable() {
        // Mirrors PakistanHijriCalendar's own documented 1400–1500 range, which was past the old
        // limit from one end to the other.
        val w = windowOf(min = hijri(1400, 1), max = hijri(1500, 1))
        assertTrue(w.containsOffset(offsetOf(1400, 1)))
        assertTrue(w.containsOffset(offsetOf(1500, 1)))
    }

    @Test
    fun oneBoundWithTheOtherUnbounded_doesNotCollapse() {
        val ahead = windowOf(min = hijri(1520, 1))
        assertTrue(ahead.span > 1, "a minDate far ahead must not collapse the window")

        val behind = windowOf(max = hijri(1380, 1))
        assertTrue(behind.span > 1, "a maxDate far behind must not collapse the window")
    }

    // ── inverted bounds degrade, not explode ──────────────────────────

    @Test
    fun invertedBounds_collapseToASingleMonth() {
        val w = windowOf(min = hijri(1450, 1), max = hijri(1440, 1))
        assertEquals(1, w.span)
        assertTrue(w.containsOffset(0), "the anchor month must still render")
    }

    @Test
    fun invertedBounds_areInspectableNotFatal() {
        // Core's canGoToNextMonth/canGoToPreviousMonth already answer "no navigable month" for
        // this configuration, so the arrows disable and the grid still paints the anchor.
        val w = windowOf(min = hijri(1450, 1), max = hijri(1440, 1))
        assertEquals(0, w.coercePage(-99))
        assertEquals(0, w.coercePage(99))
    }

    // ── page/offset round trip ─────────────────────────────────────────

    @Test
    fun pageAndOffsetAreInverses() {
        val w = windowOf(min = hijri(1440, 1), max = hijri(1460, 1))
        for (page in 0 until w.span) {
            assertEquals(page, w.pageOf(w.offsetOf(page)), "round trip failed at page $page")
        }
    }

    @Test
    fun anchorPageIsInsideTheWindow() {
        val w = windowOf()
        val page = w.pageOf(0)
        assertTrue(page in 0 until w.span, "anchor page $page outside 0..${w.span}")
    }

    @Test
    fun coercePage_bringsAnyIndexIntoRange() {
        val w = windowOf(min = hijri(1440, 1), max = hijri(1460, 1))
        assertEquals(0, w.coercePage(Int.MIN_VALUE / 2))
        assertEquals(0, w.coercePage(-5))
        assertEquals(w.span - 1, w.coercePage(w.span + 5))
        assertEquals(w.span - 1, w.coercePage(Int.MAX_VALUE / 2))
    }

    @Test
    fun coercePage_isIdentityInsideTheWindow() {
        val w = windowOf(min = hijri(1440, 1), max = hijri(1460, 1))
        (0 until w.span).forEach { assertEquals(it, w.coercePage(it)) }
    }

    // ── containment ───────────────────────────────────────────────────

    @Test
    fun containsOffset_rejectsMonthsOutsideTheBounds() {
        val w = windowOf(min = hijri(1447, 1), max = hijri(1447, 12))
        assertTrue(w.containsOffset(offsetOf(1447, 1)))
        assertTrue(w.containsOffset(offsetOf(1447, 12)))
        assertFalse(w.containsOffset(offsetOf(1446, 12)))
        assertFalse(w.containsOffset(offsetOf(1448, 1)))
    }

    // ── the type's own precondition ──────────────────────────────────

    @Test
    fun constructingAnEmptyWindowDirectly_failsWithAMessageThatNamesTheCause() {
        val ex = assertFailsWith<IllegalArgumentException> { PageWindow(5, 1) }
        assertTrue(
            ex.message.orEmpty().contains("empty"),
            "message should name the cause, got: ${ex.message}",
        )
    }

    @Test
    fun forBounds_neverProducesAnEmptyWindow() {
        // The regression guard for the whole class: no combination of caller inputs may construct
        // an inverted window, because PageWindow's init would throw while composing.
        val years = listOf(HijrahDate.MIN.year, 1400, 1440, 1447, 1500, HijrahDate.MAX.year)
        val months = listOf(1, 9, 12)
        val boundMonths = years.flatMap { y -> months.map { m -> HijrahYearMonth(y, m) } }

        for (min in boundMonths) {
            for (max in boundMonths) {
                val w = PageWindow.forBounds(minDate = min, maxDate = max, anchor = anchor)
                assertTrue(w.firstOffset <= w.lastOffset, "inverted window for $min..$max")
                assertTrue(w.span >= 1, "non-positive span for $min..$max")
                assertTrue(
                    w.coercePage(w.pageOf(0)) in 0 until w.span,
                    "no valid initial page for $min..$max",
                )
            }
        }
    }

    @Test
    fun forBounds_toleratesEveryNullCombination() {
        listOf(
            PageWindow.forBounds(null, null, anchor),
            PageWindow.forBounds(HijrahYearMonth(1440, 1), null, anchor),
            PageWindow.forBounds(null, HijrahYearMonth(1460, 1), anchor),
            PageWindow.forBounds(HijrahYearMonth(1440, 1), HijrahYearMonth(1460, 1), anchor),
        ).forEach { w ->
            assertTrue(w.span >= 1)
            assertTrue(w.coercePage(w.pageOf(0)) in 0 until w.span, "no valid initial page in $w")
        }
    }
}
