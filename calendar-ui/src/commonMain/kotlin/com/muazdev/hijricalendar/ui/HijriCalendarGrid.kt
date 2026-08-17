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
import androidx.compose.ui.unit.dp
import com.muazdev.hijricalendar.core.CalendarDay
import com.muazdev.hijricalendar.core.CalendarMonth
import com.muazdev.hijricalendar.core.DateDisplayMode
import com.muazdev.hijricalendar.core.HijriCalendarState
import com.muazdev.hijricalendar.core.WeekDay
import com.muazdev.hijricalendar.core.toCalendarMonth
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth

internal const val PAGER_CENTER_PAGE = 500
internal const val PAGER_PAGE_COUNT = PAGER_CENTER_PAGE * 2 + 1

@Composable
fun HijriCalendarGrid(
    state: HijriCalendarState,
    calendarMonth: CalendarMonth,
    onDayClick: (CalendarDay) -> Unit,
    modifier: Modifier = Modifier,
    colors: HijriCalendarColors = HijriCalendarDefaults.colors(),
    useArabicIndicNumerals: Boolean = false,
    dateDisplayMode: DateDisplayMode = DateDisplayMode.HIJRI_ONLY,
    dayContent: (@Composable (CalendarDay) -> Unit)? = null,
) {
    val initialMonth = remember { calendarMonth.yearMonth }

    val pagerState = rememberPagerState(
        initialPage = PAGER_CENTER_PAGE,
        pageCount = { PAGER_PAGE_COUNT },
    )

    // When state changes externally (header arrows, goToToday, selectDate across months),
    // compute the target page and animate the pager there.
    LaunchedEffect(calendarMonth.yearMonth) {
        val offset = monthOffset(calendarMonth.yearMonth, initialMonth)
        val page = PAGER_CENTER_PAGE + offset
        if (page != pagerState.currentPage && page in 0 until PAGER_PAGE_COUNT) {
            pagerState.animateScrollToPage(page)
        }
    }

    // When user finishes swiping, update the state to match the page.
    // Drop the first emission to avoid the pager's restored state (from rememberSaveable)
    // overwriting the correct ViewModel state on configuration changes.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .drop(1)
            .collect { page ->
                val offset = page - PAGER_CENTER_PAGE
                val month = initialMonth.plusPageOffset(offset)
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
            firstDayOfWeek = calendarMonth.firstDayOfWeek,
            colors = colors,
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val offset = page - PAGER_CENTER_PAGE
            val month = initialMonth.plusPageOffset(offset)
            val calMonth = remember(
                month,
                state.firstDayOfWeek,
                state.selectedDate,
                state.minDate,
                state.maxDate,
            ) {
                month.toCalendarMonth(
                    firstDayOfWeek = state.firstDayOfWeek,
                    selectedDate = state.selectedDate,
                    minDate = state.minDate,
                    maxDate = state.maxDate,
                )
            }
            MonthGrid(
                days = calMonth.days,
                onDayClick = onDayClick,
                colors = colors,
                useArabicIndicNumerals = useArabicIndicNumerals,
                dateDisplayMode = dateDisplayMode,
                dayContent = dayContent,
            )
        }
    }
}

@Composable
private fun DayOfWeekLabels(
    firstDayOfWeek: WeekDay,
    colors: HijriCalendarColors,
) {
    val dayLabels = remember(firstDayOfWeek) { generateDayOfWeekLabels(firstDayOfWeek) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        dayLabels.forEach { label ->
            Text(
                text = label,
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
    dayContent: (@Composable (CalendarDay) -> Unit)?,
) {
    val weeks = remember(days) { days.chunked(CalendarMonth.DAYS_IN_WEEK) }

    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        weeks.forEach { weekDays ->
            HijriWeekRow(
                days = weekDays,
                onDayClick = onDayClick,
                colors = colors,
                useArabicIndicNumerals = useArabicIndicNumerals,
                dateDisplayMode = dateDisplayMode,
                dayContent = dayContent,
            )
        }
    }
}

private fun generateDayOfWeekLabels(firstDayOfWeek: WeekDay): List<String> {
    val allDays = WeekDay.entries
    val startIndex = firstDayOfWeek.index
    return (0 until CalendarMonth.DAYS_IN_WEEK).map { offset ->
        val index = (startIndex + offset) % CalendarMonth.DAYS_IN_WEEK
        allDays[index].shortName
    }
}

private fun HijrahYearMonth.plusPageOffset(offset: Int): HijrahYearMonth {
    return if (offset >= 0) {
        this.plusMonth(offset)
    } else {
        this.minusMonth(-offset)
    }
}

private fun monthOffset(from: HijrahYearMonth, to: HijrahYearMonth): Int {
    return (from.year - to.year) * 12 + (from.month.number - to.month.number)
}
