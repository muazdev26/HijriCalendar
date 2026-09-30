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
    /**
     * Renders the calendar at a flat text size, ignoring the system accessibility font scale.
     *
     * `false` — the default, and a change from 1.0.0 — honours the system setting: the day text
     * scales up to [HijriCalendarDefaults.maxFontScaleFor], the header scales without limit, and
     * the cell geometry stays fixed. `true` reproduces the pre-existing behaviour, where nothing in
     * the calendar scales at all.
     *
     * Prefer the default. Set this only for a layout that genuinely cannot accommodate larger text
     * — a fixed-height slot, a print layout — since it is an accessibility regression for everyone
     * who has raised their system font size.
     */
    ignoreFontScale: Boolean = false,
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
        // The extent comes from the state's own resolver, so the header can never describe a
        // different month than the grid below it — and it reads the same monthLengths the grid
        // does. Deliberately NOT state.calendarMonth: that would force the 42-cell grid to be
        // built for a header. gregorianRangeFor resolves the extent without the cells.
        val range = state.gregorianRangeFor(currentMonth)
        sameMonthRangeLabel(range.first, range.last, labels)
    }

    // The current month's identity and weekday origin, used to label the header and to anchor the
    // pager. The *cells* are not read here: HijriCalendarGrid builds each page through
    // HijriCalendarState.calendarMonthFor, which is the one definition of how a month becomes cells.
    val calendarMonth = state.calendarMonth

    // ── font scaling ───────────────────────────────────────────────────
    //
    // A month grid is a fixed-pitch matrix, so the *cell* must not grow: that is what commit
    // 337f631 fixed, and it was a real bug — the cell used to be scaled by the system font scale,
    // which grew every row and broke the layout.
    //
    // The mistake that fix made was to go further and pin fontScale to 1f for the whole calendar, so
    // the *text* stopped scaling too. Text and geometry are different problems and are now handled
    // separately:
    //
    //  - the grid's text scales, up to a cap chosen so it still fits the fixed cell
    //    ([HijriCalendarDefaults.maxFontScaleFor] — lower for the two-line BOTH mode);
    //  - the header is outside the override entirely. It is a Row with no fixed box, so scaling it
    //    is free and it should honour the system setting outright;
    //  - the cell geometry does not move at all.
    //
    // [ignoreFontScale] restores the old flat behaviour for a consumer who needs it (a fixed-height
    // slot, a print layout).
    val density = LocalDensity.current
    val cap = HijriCalendarDefaults.maxFontScaleFor(dateDisplayMode)
    val cappedDensity = remember(density.density, density.fontScale, cap) {
        Density(density = density.density, fontScale = density.fontScale.coerceAtMost(cap))
    }
    val unscaledDensity = remember(density.density) {
        Density(density = density.density, fontScale = 1f)
    }

    val header: @Composable () -> Unit = {
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
    }
    val grid: @Composable () -> Unit = {
        HijriCalendarGrid(
            state = state,
            onDayClick = onDayClick,
            colors = colors,
            useArabicIndicNumerals = useArabicIndicNumerals,
            dateDisplayMode = dateDisplayMode,
            dayCellSize = dayCellSize,
            dayContent = dayContent,
            labels = labels,
        )
    }

    Column(modifier = modifier) {
        if (ignoreFontScale) {
            CompositionLocalProvider(LocalDensity provides unscaledDensity) {
                header()
                grid()
            }
        } else {
            header()
            CompositionLocalProvider(LocalDensity provides cappedDensity) { grid() }
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
