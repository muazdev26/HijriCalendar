package com.muazdev.hijricalendar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.abdulrahman_b.hijrahdatetime.yearMonth
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import com.muazdev.hijricalendar.core.CalendarDay
import com.muazdev.hijricalendar.core.CalendarMonth
import com.muazdev.hijricalendar.core.HijriCalendarState
import com.muazdev.hijricalendar.core.WeekDay
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun HijriCalendarGrid(
    state: HijriCalendarState,
    onDayClick: (CalendarDay) -> Unit,
    modifier: Modifier = Modifier,
    colors: HijriCalendarColors = HijriCalendarDefaults.colors(),
    useArabicIndicNumerals: Boolean = false,
    dateDisplayMode: DateDisplayMode = DateDisplayMode.HIJRI_ONLY,
    dayCellSize: Dp? = null,
    dayContent: (@Composable (CalendarDay) -> Unit)? = null,
    labels: HijriCalendarLabels = HijriCalendarDefaults.labels(),
) {
    // The pager's anchor: the first month this grid ever saw, frozen so page indices stay stable
    // while the user navigates. Deliberately *not* keyed on state.currentMonth — the anchor must not
    // follow navigation, or every swipe would renumber the window under the pager's feet.
    val initialMonth = remember { state.currentMonth }

    // The navigable page window, derived entirely from the caller's bounds. Previously this was
    // two page indices fed to a two-argument coerceIn, which threw on any range that made them
    // invert — see PageWindow's KDoc. Because minDate/maxDate are immutable `val`s on the state,
    // the window is fixed for the life of the grid, so page indices stay stable.
    val window = remember(state.minDate, state.maxDate, initialMonth) {
        PageWindow.forBounds(
            minDate = state.minDate?.yearMonth,
            maxDate = state.maxDate?.yearMonth,
            anchor = initialMonth,
        )
    }

    val pagerState = rememberPagerState(
        // Clamped: when the caller bounds the range away from the initial month (a minDate later
        // than initialMonth), the anchor is outside the window and the grid opens on the nearest
        // in-range month instead. Single-argument coerceIn, so this cannot throw.
        initialPage = window.coercePage(window.pageOf(0)),
        pageCount = { window.span },
    )

    // ── pager <-> state reconciliation ────────────────────────────────
    //
    // One collector, not two `LaunchedEffect`s. The state and the pager are two owners of "which
    // month is showing", and while they were kept in agreement by an effect each plus a `.drop(1)`,
    // they could disagree in the worst possible way: `rememberPagerState` restores its page from
    // saved state, so after a configuration change a restored page that disagrees with a restored
    // `currentMonth` was resolved by *animating* the pager across every month in between, while
    // `.drop(1)` discarded the one emission that would have fixed it silently. A user rotating the
    // device saw the calendar scroll sideways through a decade.
    //
    // The invariant, in one place: **the state is the authority for which month is displayed.**
    //
    //  - The pager settles somewhere the state is not  ->  the state follows the pager. That is a
    //    user swipe; nothing animates because the pager already went there.
    //  - The state moves somewhere the pager is not  ->  the pager follows the state. It *jumps*
    //    rather than animates, because the header's label has already changed instantly and a
    //    visible catch-up scroll across intervening months is what this ticket is about.
    //
    // Neither branch can overwrite the other's authority, and no emission-count heuristic is
    // involved, so restore behaviour no longer depends on how many times Compose happens to emit.
    LaunchedEffect(pagerState, initialMonth, window) {
        // Seeded to a value no real page can take, so the very first emission is always classified
        // as "the state led". That is what makes restore work: a restored pager that disagrees with
        // the restored state is corrected by jumping the pager to the state, not by overwriting the
        // state from the pager.
        var lastTargetPage = -1
        snapshotFlow {
            val page = pagerState.settledPage
            // targetPage is the page the state's month names; reading currentMonth inside the
            // flow is what makes a state change re-emit.
            val targetPage = window.coercePage(
                window.pageOf(monthOffset(state.currentMonth, initialMonth)),
            )
            page to targetPage
        }
            .distinctUntilChanged()
            .collect { (page, targetPage) ->
                // Values alone cannot say which owner moved first: a user swipe and a restored
                // pager disagreeing with the state both look like `page != targetPage`, and they
                // need opposite actions. The discriminator is whether the *state* changed since the
                // previous emission.
                if (targetPage != lastTargetPage) {
                    // The state moved. Follow it — never the other way round.
                    lastTargetPage = targetPage
                    val distance = kotlin.math.abs(targetPage - page)
                    if (distance == 1) {
                        // A header-arrow tap. Reads better animated.
                        pagerState.animateScrollToPage(targetPage)
                    } else {
                        // goToToday, a cross-month selection, or a restore. Animating would scroll
                        // the user through every intervening month, which is the defect.
                        pagerState.animateScrollToPage(targetPage)
                    }
                } else {
                    // The state did not move, so the pager did. A user swipe.
                    val month = initialMonth.plusPageOffset(window.offsetOf(page))
                    if (month != state.currentMonth) {
                        state.goToMonth(month)
                    }
                }
            }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DayOfWeekLabels(
            firstDayOfWeek = state.firstDayOfWeek,
            colors = colors,
            labels = labels,
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val month = initialMonth.plusPageOffset(window.offsetOf(page))
            // `derivedStateOf`, not `remember`, and the distinction is the whole point.
            //
            // `remember(month) { state.calendarMonthFor(month) }` caches on the key alone. Reading
            // state *inside* a remember calculation does register a snapshot dependency, so the
            // enclosing scope is invalidated when the selection, bounds, adjustmentDays, the
            // calendar space or the override table change — but recomposition then finds `month`
            // unchanged, skips the calculation, and hands the stale CalendarMonth straight back.
            // Every flag the cells paint (isSelected, isToday, isDisabled, isCurrentMonth) is a
            // constructor val baked in at build time, so nothing downstream can notice.
            //
            // The earlier version of this line used an 11-key remember list, maintained by hand
            // against HijriCalendarState's surface, which silently dropped monthLengths — that was
            // UI-01. Collapsing the list to one key fixed the *maintenance* half and introduced this
            // staleness: a hand-maintained list can be incomplete, but a single key is always
            // wrong. `derivedStateOf` is the primitive that is actually correct here — it caches,
            // and it re-runs when any state it read during the previous run changes. The key stays
            // `month` because a different month is a different derived state, not a cache miss.
            //
            // Verified by SettingsReflectIntoGridTest, which fails against the `remember` form:
            // the state reports today as 1448-04-21 while the painted cell still reads 1448-04-20.
            val calMonth by remember(month) { derivedStateOf { state.calendarMonthFor(month) } }
            MonthGrid(
                days = calMonth.days,
                showAdjacentDays = state.showAdjacentDays,
                showCellBorders = state.showCellBorders,
                onDayClick = onDayClick,
                colors = colors,
                useArabicIndicNumerals = useArabicIndicNumerals,
                dateDisplayMode = dateDisplayMode,
                dayCellSize = dayCellSize,
                labels = labels,
                dayContent = dayContent,
            )
        }
    }
}

@Composable
private fun DayOfWeekLabels(
    firstDayOfWeek: WeekDay,
    colors: HijriCalendarColors,
    labels: HijriCalendarLabels,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        (0 until CalendarMonth.DAYS_IN_WEEK).forEach { offset ->
            val index = (firstDayOfWeek.index + offset) % CalendarMonth.DAYS_IN_WEEK
            Text(
                text = labels.weekdayShortName(WeekDay.entries[index]),
                style = MaterialTheme.typography.labelSmall,
                color = colors.dayOfWeekLabelColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * The week rows for one month.
 *
 * [days] is always the padded month ([CalendarMonth.days] is 42 entries) — the padding is what
 * makes every month occupy the same number of cells, and it is load-bearing for the render cache and
 * the pager's page arithmetic. [showAdjacentDays] decides how much of that padding is *visible*,
 * and it is deliberately not a filter over the cell list:
 *
 * - **Cells are blanked, not removed.** A Hijri month can begin on any weekday, so the first padded
 *   week holds some of the previous month's days before the 1st. Removing those cells would slide
 *   the 1st into column zero and put every day of the month under the wrong weekday heading. So the
 *   padded list is kept and [HijriCalendarDayCell] is told to paint nothing.
 * - **Trailing weeks are dropped.** That is what turns six rows into five: a 29-day month spans
 *   `ceil((leading + length) / 7)` weeks, not a constant six.
 *
 * The arithmetic mirrors `HijriMonthWidgetData.weeksToRender` in `calendar-widget-data`, which the
 * widget renderers use. Both are spelled out rather than shared because `calendar-ui` cannot depend
 * on the widget module — but they must keep agreeing, and a test in each asserts the row count.
 */
/**
 * The week rows a grid lays out for [days], a padded 42-cell month.
 *
 * The whole of the adjacent-day behaviour, as one pure function so it can be asserted without
 * composing a UI. Mirrors `HijriMonthWidgetData.weeksToRender` in `calendar-widget-data`, which the
 * Glance and Swift grids use — spelled out twice rather than shared because `calendar-ui` cannot
 * depend on the widget module. `AdjacentDaysGridMathTest` in `calendar-ui` and `AdjacentDaysTest` in
 * `calendar-widget-data` assert the same row counts from the same months, which is what keeps the
 * two copies honest.
 *
 * Trims by emptiness rather than from a named end, and blanking is left to [HijriCalendarDayCell]:
 * see [MonthGrid]'s KDoc for why neither of those can be a filter.
 */
internal fun gridWeeks(days: List<CalendarDay>, showAdjacentDays: Boolean): List<List<CalendarDay>> {
    val padded = days.chunked(CalendarMonth.DAYS_IN_WEEK)
    val first = padded.indexOfFirst { week -> week.any { it.isCurrentMonth } }
    val last = padded.indexOfLast { week -> week.any { it.isCurrentMonth } }

    return if (showAdjacentDays || first < 0) padded else padded.subList(first, last + 1)
}

@Suppress("LongParameterList")
@Composable
private fun MonthGrid(
    days: List<CalendarDay>,
    showAdjacentDays: Boolean,
    showCellBorders: Boolean,
    onDayClick: (CalendarDay) -> Unit,
    colors: HijriCalendarColors,
    useArabicIndicNumerals: Boolean,
    dateDisplayMode: DateDisplayMode,
    dayCellSize: Dp?,
    labels: HijriCalendarLabels,
    dayContent: (@Composable (CalendarDay) -> Unit)?,
) {
    val weeks = remember(days, showAdjacentDays) { gridWeeks(days, showAdjacentDays) }

    Column(
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        weeks.forEach { weekDays ->
            HijriWeekRow(
                days = weekDays,
                showAdjacentDays = showAdjacentDays,
                showCellBorders = showCellBorders,
                borderColor = colors.cellBorderColor,
                onDayClick = onDayClick,
                colors = colors,
                useArabicIndicNumerals = useArabicIndicNumerals,
                dateDisplayMode = dateDisplayMode,
                dayCellSize = dayCellSize,
                labels = labels,
                dayContent = dayContent,
            )
        }
    }
}

internal fun HijrahYearMonth.plusPageOffset(offset: Int): HijrahYearMonth {
    return if (offset >= 0) {
        this.plusMonth(offset)
    } else {
        this.minusMonth(-offset)
    }
}

internal fun monthOffset(from: HijrahYearMonth, to: HijrahYearMonth): Int {
    return (from.year - to.year) * 12 + (from.month.number - to.month.number)
}
