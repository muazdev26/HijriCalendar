package com.muazdev.hijricalendar.ui

import androidx.compose.runtime.Immutable
import com.abdulrahman_b.hijrahdatetime.HijrahDate
import com.abdulrahman_b.hijrahdatetime.yearMonth
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth

/**
 * The months the grid's pager can navigate, expressed as offsets in months from the grid's frozen
 * anchor month.
 *
 * The anchor is `HijriCalendarGrid`'s `initialMonth` — the first month the grid ever saw, held in
 * a `remember { }` so page indices stay stable while the user navigates. Offset `0` is the anchor.
 *
 * ## Why this is a type
 *
 * This replaces a two-argument `Int.coerceIn` on caller-supplied bounds, which **threw**
 * `IllegalArgumentException` while composing whenever the resulting range was empty:
 *
 * ```kotlin
 * // before
 * rememberPagerState(initialPage = PAGER_CENTER_PAGE.coerceIn(minAllowedPage, maxAllowedPage), …)
 * // …Cannot coerce value to an empty range: maximum 1000 is less than minimum 1368.
 * ```
 *
 * Both bounds came from `minDate` / `maxDate`, which the **caller** controls, against a fixed
 * ±500-month window. A `minDate` 501 months (≈41 years 9 months) ahead of the anchor, a `maxDate`
 * 100 years behind it, or an inverted range all crashed the calendar on its first composition. The
 * threshold was exact and undocumented, and a consumer mirroring
 * `PakistanHijriCalendar`'s own 1400–1500 range was past it from one end to the other.
 *
 * A value type fixes both halves at once. The `init` block can only ever *reject* an empty range
 * with a message that names the cause, and [coercePage] is single-argument, so it cannot throw.
 * Nothing derives a page count from a constant the caller does not control.
 *
 * The fixed ±500 window is also gone: an unbounded side now reaches the edge of the Hijri table,
 * which is what `canGoToPreviousMonth` / `canGoToNextMonth` already allow. See [forBounds].
 *
 * @see UI-05-pager-window-crash
 */
@Immutable
internal data class PageWindow(
    /** Offset of the earliest navigable month, ≤ 0. */
    val firstOffset: Int,
    /** Offset of the latest navigable month, ≥ 0. */
    val lastOffset: Int,
) {
    init {
        require(firstOffset <= lastOffset) {
            "PageWindow is empty: $firstOffset..$lastOffset. The bounds were inverted, which " +
                "PageWindow.forBounds normally resolves — reaching here means the window was " +
                "constructed directly."
        }
    }

    /** Page count, i.e. what `HorizontalPager(pageCount = { … })` should report. */
    val span: Int get() = lastOffset - firstOffset + 1

    fun containsOffset(offset: Int): Boolean = offset in firstOffset..lastOffset

    /** Page index for a month [offset] months from the anchor. May fall outside `0 until span`. */
    fun pageOf(offset: Int): Int = offset - firstOffset

    /** Month offset for a page index produced by the pager. */
    fun offsetOf(page: Int): Int = page + firstOffset

    /** Page index clamped into `0 until span`. Single-argument, so it cannot throw. */
    fun coercePage(page: Int): Int = page.coerceIn(0, span - 1)

    companion object {
        /**
         * Builds the window for [anchor] from the caller's optional bounds.
         *
         * - **An unbounded side reaches the edge of the Hijri table** — `HijrahDate.MIN` /
         *   `HijrahDate.MAX`, the same bound `HijriCalendarState.canGoToPreviousMonth` /
         *   `canGoToNextMonth` clamp at via `plusMonthOrNull`. The previous code used a fixed
         *   ±500-month window instead, which is why a header arrow could enable a month the pager
         *   could not represent. Reading the bound from the same place makes the two agree by
         *   construction, and removes the magic number entirely.
         * - **Inverted bounds** (`minDate` after `maxDate`) collapse to a single month rather than
         *   throwing. Core's `canGoToPreviousMonth` / `canGoToNextMonth` already answer "no
         *   navigable month" for that configuration, so the arrows disable and the anchor month
         *   still renders — predictable, and inspectable, instead of a `coerceIn` stack trace.
         *
         * The window cannot exceed the whole Hijri table, so no span ceiling is needed: a bound
         * outside it cannot be constructed in the first place, since `minDate` and `maxDate` are
         * `HijrahDate`, whose constructor validates its own range.
         */
        fun forBounds(
            minDate: HijrahYearMonth?,
            maxDate: HijrahYearMonth?,
            anchor: HijrahYearMonth,
        ): PageWindow {
            val low = minDate?.let { monthOffset(it, anchor) }
                ?: monthOffset(HijrahDate.MIN.yearMonth, anchor)
            val high = maxDate?.let { monthOffset(it, anchor) }
                ?: monthOffset(HijrahDate.MAX.yearMonth, anchor)
            if (low > high) return PageWindow(0, 0)
            return PageWindow(low, high)
        }
    }
}

/**
 * How the pager should move from one page to another.
 *
 * This was a one-line `if` inside the pager's reconciliation effect, and it is the whole of
 * [UI-09-pager-effect-sync](UI-09-pager-effect-sync.md): a one-month move is a header-arrow tap and
 * reads better animated, while anything larger is a `goToToday`, a cross-month selection, or a
 * month restored from saved state disagreeing with a restored page — and animating those scrolls
 * the user through every month in between.
 *
 * It is a named type rather than an inline branch so the rule can be asserted directly. It cannot
 * be asserted through the pager: `HorizontalPager` composes the same nodes either way, so the only
 * observable difference is the scroll offset over time, and a Compose test clock does not give a
 * reliable window between "finished" and "finished animating".
 */
internal enum class PageJump {
    /** One page: a deliberate, pleasant, short animation. */
    ANIMATE,

    /** Any other distance: move immediately. */
    JUMP,
}

/**
 * The [PageJump] policy for moving from [fromPage] to [toPage].
 *
 * Exactly one page animates. That is the header arrows and nothing else.
 *
 * The subtraction is done in [Long] on purpose. Page indices derive from caller-supplied bounds, so
 * they are not guaranteed to be near each other, and an  subtraction of  and
 *  wraps to  — whose absolute value is 1, which would classify the single
 * largest possible jump as a one-page animation.  pins that.
 */
internal fun pageJump(fromPage: Int, toPage: Int): PageJump =
    if (kotlin.math.abs(toPage.toLong() - fromPage.toLong()) == 1L) PageJump.ANIMATE else PageJump.JUMP
