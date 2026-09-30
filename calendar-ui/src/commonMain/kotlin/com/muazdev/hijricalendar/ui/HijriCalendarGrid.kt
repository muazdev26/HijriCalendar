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
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.drop
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.muazdev.hijricalendar.core.CalendarDay
import com.muazdev.hijricalendar.core.CalendarMonth
import com.muazdev.hijricalendar.core.HijriCalendarState
import com.muazdev.hijricalendar.core.WeekDay
import com.abdulrahman_b.hijrahdatetime.yearMonth
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth

@Composable
public fun HijriCalendarGrid(
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

    // When state changes externally (header arrows, goToToday, selectDate across months),
    // compute the target page and animate the pager there.
    LaunchedEffect(state.currentMonth) {
        val page = window.coercePage(window.pageOf(monthOffset(state.currentMonth, initialMonth)))
        if (page != pagerState.currentPage) {
            pagerState.animateScrollToPage(page)
        }
    }

    // When user finishes swiping, update the state to match the page.
    // Drop the first emission to avoid the pager's restored state (from rememberSaveable)
    // overwriting the correct ViewModel state on configuration changes.
    //
    // No clamping is needed here: pageCount is the window, so the pager cannot settle on a page
    // outside it. The old post-hoc `animateScrollToPage` correction is deleted — it existed only
    // because pageCount used to be a constant wider than the caller's bounds.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .drop(1)
            .collect { page ->
                val month = initialMonth.plusPageOffset(window.offsetOf(page))
                if (month != state.currentMonth) {
                    state.goToMonth(month)
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
            // Keyed on `month` alone, and that is safe *because* calendarMonthFor reads every
            // other input from inside the state, which is @Stable: any change to the selection, the
            // bounds, adjustmentDays, the calendar space or the override table invalidates the
            // state's derived state and this read recomputes with it. The previous 11-key
            // remember list had to be maintained by hand against HijriCalendarState's surface,
            // and silently dropped monthLengths — see UI-01.
            val calMonth = remember(month) { state.calendarMonthFor(month) }
            MonthGrid(
                days = calMonth.days,
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
) {
    Row(
        modifier = Modifier
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

@Composable
private fun MonthGrid(
    days: List<CalendarDay>,
    onDayClick: (CalendarDay) -> Unit,
    colors: HijriCalendarColors,
    useArabicIndicNumerals: Boolean,
    dateDisplayMode: DateDisplayMode,
    dayCellSize: Dp?,
    labels: HijriCalendarLabels,
    dayContent: (@Composable (CalendarDay) -> Unit)?,
) {
    val weeks = remember(days) { days.chunked(CalendarMonth.DAYS_IN_WEEK) }

    Column(
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        weeks.forEach { weekDays ->
            HijriWeekRow(
                days = weekDays,
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
