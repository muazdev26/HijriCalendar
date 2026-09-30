package com.muazdev.hijricalendar.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.abdulrahman_b.hijrahdatetime.HijrahDate
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import com.muazdev.hijricalendar.core.CalendarDay
import com.muazdev.hijricalendar.core.HijriCalendarState
import com.muazdev.hijricalendar.core.WeekDay
import androidx.compose.ui.unit.Dp
import com.muazdev.hijricalendar.core.rememberHijriCalendarState as coreRememberHijriCalendarState
import com.muazdev.hijricalendar.core.rememberSaveableHijriCalendarState as coreRememberSaveableHijriCalendarState
import kotlinx.datetime.LocalDate

@Composable
public fun HijriCalendar(
    state: HijriCalendarState,
    modifier: Modifier = Modifier,
    colors: HijriCalendarColors = HijriCalendarDefaults.colors(),
    useArabicIndicNumerals: Boolean = false,
    dateDisplayMode: DateDisplayMode = DateDisplayMode.HIJRI_ONLY,
    dayCellSize: Dp? = null,
    onDayClick: (CalendarDay) -> Unit,
    dayContent: (@Composable (CalendarDay) -> Unit)? = null,
    labels: HijriCalendarLabels = HijriCalendarDefaults.labels(),
) {
    // Header text is derived from the month alone, not from the full 42-cell grid, so an
    // unrelated recomposition never rebuilds the CalendarMonth just to render the header.
    val currentMonth = state.currentMonth

    val hijriMonthLabel = remember(currentMonth, labels) {
        labels.hijriMonthName(currentMonth.year, currentMonth.month.number)
    }

    val headerContentDescription = remember(hijriMonthLabel, currentMonth) {
        "$hijriMonthLabel ${currentMonth.year}"
    }

    val gregorianMonthText = remember(
        currentMonth,
        labels,
        state.adjustmentDays,
        state.pakistanDates,
        // Reference key for the override table; see the note in HijriCalendarGrid.kt. Without it
        // this label describes the Umm al-Qura month while the grid below paints the observed one.
        state.monthLengths,
        state.overridesRevision,
    ) {
        // The extent comes from core's single resolver, so the header can never describe a
        // different month than the grid below it. Resolved from the year/month alone rather than
        // from state.calendarMonth, which would force the 42-cell grid to be built for a header.
        val range = state.renderGregorianRangeFor(currentMonth)
        sameMonthRangeLabel(range.first, range.last, labels)
    }

    // The rendered grid. Backed by derivedStateOf in HijriCalendarState, so repeated
    // reads within this composition are cheap.
    val calendarMonth = state.calendarMonth

    // Keep the entire calendar rendering at its designed size: ignore the system's
    // font-scale so cells never inflate or shrink from accessibility text sizing.
    val density = LocalDensity.current
    val fixedDensity = remember(density.density) {
        Density(density = density.density, fontScale = 1f)
    }

    Column(modifier = modifier) {
        CompositionLocalProvider(LocalDensity provides fixedDensity) {
            HijriCalendarHeader(
                monthName = hijriMonthLabel,
                year = currentMonth.year,
                onPreviousMonth = state::goToPreviousMonth,
                onNextMonth = state::goToNextMonth,
                colors = colors,
                dateDisplayMode = dateDisplayMode,
                gregorianMonthText = gregorianMonthText,
                contentDescription = headerContentDescription,
                previousMonthContentDescription = labels.previousMonthContentDescription,
                nextMonthContentDescription = labels.nextMonthContentDescription,
                canGoToPreviousMonth = state.canGoToPreviousMonth,
                canGoToNextMonth = state.canGoToNextMonth,
            )

            HijriCalendarGrid(
                state = state,
                calendarMonth = calendarMonth,
                onDayClick = onDayClick,
                colors = colors,
                useArabicIndicNumerals = useArabicIndicNumerals,
                dateDisplayMode = dateDisplayMode,
                dayCellSize = dayCellSize,
                dayContent = dayContent,
                labels = labels,
            )
        }
    }
}

public fun HijriCalendarState.defaultOnDayClick(): (CalendarDay) -> Unit = { day ->
    selectDay(day)
}

@Composable
public fun rememberHijriCalendarState(
    initialMonth: HijrahYearMonth,
    initialSelectedDate: HijrahDate? = null,
    firstDayOfWeek: WeekDay = WeekDay.DEFAULT_FIRST_DAY,
    minDate: HijrahDate? = null,
    maxDate: HijrahDate? = null,
    adjustmentDays: Int = 0,
    pakistanDates: Boolean = false,
    weekendDays: Set<WeekDay> = WeekDay.WEEKEND_DAYS,
): HijriCalendarState = coreRememberHijriCalendarState(
    initialMonth = initialMonth,
    initialSelectedDate = initialSelectedDate,
    firstDayOfWeek = firstDayOfWeek,
    minDate = minDate,
    maxDate = maxDate,
    adjustmentDays = adjustmentDays,
    pakistanDates = pakistanDates,
    weekendDays = weekendDays,
)

@Composable
public fun rememberSaveableHijriCalendarState(
    initialMonth: HijrahYearMonth,
    initialSelectedDate: HijrahDate? = null,
    firstDayOfWeek: WeekDay = WeekDay.DEFAULT_FIRST_DAY,
    minDate: HijrahDate? = null,
    maxDate: HijrahDate? = null,
    adjustmentDays: Int = 0,
    pakistanDates: Boolean = false,
    weekendDays: Set<WeekDay> = WeekDay.WEEKEND_DAYS,
): HijriCalendarState = coreRememberSaveableHijriCalendarState(
    initialMonth = initialMonth,
    initialSelectedDate = initialSelectedDate,
    firstDayOfWeek = firstDayOfWeek,
    minDate = minDate,
    maxDate = maxDate,
    adjustmentDays = adjustmentDays,
    pakistanDates = pakistanDates,
    weekendDays = weekendDays,
)

internal fun sameMonthRangeLabel(first: LocalDate, last: LocalDate, labels: HijriCalendarLabels): String =
    when {
        first.month == last.month && first.year == last.year ->
            "${labels.gregorianMonthName(first.month.ordinal + 1)} ${first.year}"
        first.year == last.year ->
            "${labels.gregorianMonthName(first.month.ordinal + 1)} - " +
                "${labels.gregorianMonthName(last.month.ordinal + 1)} ${first.year}"
        else ->
            "${labels.gregorianMonthName(first.month.ordinal + 1)} ${first.year} - " +
                "${labels.gregorianMonthName(last.month.ordinal + 1)} ${last.year}"
    }
