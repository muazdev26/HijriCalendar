package com.muazdev.hijricalendar.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import com.abdulrahman_b.hijrahdatetime.HijrahDate
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import com.muazdev.hijricalendar.core.CalendarDay
import com.muazdev.hijricalendar.core.HijriCalendarState
import com.muazdev.hijricalendar.core.HijriEvents
import com.muazdev.hijricalendar.core.WeekDay
import com.muazdev.hijricalendar.core.todayHijriDate
import com.muazdev.hijricalendar.core.rememberHijriCalendarState as coreRememberHijriCalendarState
import com.muazdev.hijricalendar.core.rememberSaveableHijriCalendarState as coreRememberSaveableHijriCalendarState

/**
 * A Hijri (Islamic) calendar: month header, weekday row, and a swipeable month grid, all driven by
 * one [HijriCalendarState].
 *
 * ## What this composable does not do
 *
 * Stated here because they are the questions a consumer arrives with, and discovering them by
 * editing the library is worse than reading this:
 *
 * - **No year picker and no month picker.** Navigation is one month at a time through the header
 *   arrows or a swipe. Set `state.goToMonth(...)` to jump programmatically.
 * - **No range or multi-select.** [onDayClick] reports one [CalendarDay]; selection is whatever the
 *   consumer does with it.
 * - **No fixed-height mode.** The grid is a fixed-pitch six-week matrix by design: cells never grow,
 *   including as the system font scale rises. The *text* scales up to a cap
 *   ([HijriCalendarDefaults.maxFontScaleFor]) and the header is uncapped. Pass
 *   [ignoreFontScale] for the previous fully flat behaviour.
 * - **No bounds beyond [HijriCalendarState.minDate] / `maxDate`**, which also gate navigation and the
 *   pager's reachable range.
 *
 * ## Reading the day
 *
 * A [CalendarDay] carries up to three date slots, because this library can render three calendars
 * that legitimately disagree about a given real-world day. [CalendarDay.dayOfMonth] resolves them in
 * order; [CalendarDay.localDate] is the Gregorian day every one of them describes, and is what a
 * consumer should reach for when it wants a single unambiguous answer.
 *
 * @param state The state holder. Owns the current month, the selection, the calendar space and the
 *   bounds; see [HijriCalendarState].
 * @param dateDisplayMode How much of a date each cell draws. Does not affect cell size.
 * @param dayCellSize The fixed cell size. Defaults to
 *   [HijriCalendarDefaults.SingleLineCellSize] and does not change with the font scale.
 * @param onDayClick Invoked with the tapped cell. The state is **not** updated for you: call
 *   [HijriCalendarState.selectDay] (or [defaultOnDayClick]) from here unless the consumer owns
 *   selection itself.
 * @param dayContent Replaces the built-in cell contents. The cell keeps its own background, border,
 *   clip, semantics and click handling, so this is responsible only for what it draws inside.
 * @param header Replaces the built-in [HijriCalendarHeader], for a layout that needs the calendar
 *   somewhere other than the top. A replacement must reproduce the navigability gating or it will
 *   offer months the grid cannot show.
 * @param ignoreFontScale Renders at a flat text size, ignoring the system accessibility font scale.
 *   `false` by default; see the class KDoc for why that default changed.
 * @param labels All user-visible text. Build it once and hold it — it is used as a `remember` key.
 */
// Suppressed rather than satisfied: the function's job is to resolve the header's strings from the
// state and hand them to the header, and every one of those resolutions needs a `remember` keyed on
// its own inputs — folding them into one helper would need the same key lists threaded through and
// would make the header's freshness a property of a call the reader has to trust.
@Suppress("LongMethod")
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
    /**
     * Replaces the built-in month header. `null` — the default — uses [HijriCalendarHeader].
     */
    header: (@Composable () -> Unit)? = null,
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

    // The whole title line, so a locale controls its ordering and its digits. The header used to
    // join these with a space itself and render the year in Western digits regardless of the
    // calendar's numeral setting -- which the module's own Urdu preview demonstrated, in its own demo.
    val headerTitle = remember(hijriMonthLabel, currentMonth, labels) {
        labels.headerTitle(hijriMonthLabel, currentMonth.year)
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
        labels.gregorianMonthRangeLabel(range.first, range.last)
    }

    // Whether the selection is today itself, which is all `isToday` means for a banner. Compared
    // against the shared `todayHijriDate` rather than by building the month and reading `isToday` off
    // a cell: a header must not force 42 cells to be built for a string, and `isToday` on a cell is
    // defined by exactly this comparison.
    val selectionIsToday = remember(state.selectedDate, state.adjustmentDays) {
        val selected = state.selectedDate ?: return@remember false
        todayHijriDate(state.adjustmentDays) == selected
    }

    // The observance on the selected day, if any (FD-08).
    //
    // Resolved from the *selected* cell rather than from `CalendarDay.event` on the grid, because the
    // grid builds a month at a time and the selection may live in a month the grid has not built. The
    // lookup goes through the state's own routing order — Pakistan, then observed, then Umm al-Qura —
    // so the banner cannot name a different observance than the cell it describes.
    val selectedEvent = remember(
        state.selectedDate,
        state.selectedPakistanDate,
        state.selectedObservedDate,
        state.pakistanDates,
        labels,
    ) {
        val month = state.selectedPakistanDate?.month
            ?: state.selectedObservedDate?.month
            ?: state.selectedDate?.month?.number
        val day = state.selectedPakistanDate?.day
            ?: state.selectedObservedDate?.day
            ?: state.selectedDate?.day
        if (month == null || day == null) null else HijriEvents.forDate(month, day)
    }

    // A read whose only job is to subscribe this scope to the state's derived month, which is why it
    // is a `val` and not a bare expression. HijriCalendarGrid builds each page through
    // HijriCalendarState.calendarMonthFor — the one definition of how a month becomes cells — and each
    // page derives its own, so nothing below needs the built month. What this buys is invalidation of
    // the whole calendar subtree when the current month's identity or its override table changes,
    // which the header's own remember keys do not fully cover.
    //
    // Do not delete it, or this scope stops observing the state. The suppression is for that reason.
    @Suppress("UNUSED_VARIABLE")
    val monthSubscription = state.calendarMonth

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

    val defaultHeader: @Composable () -> Unit = {
        HijriCalendarHeader(
            title = headerTitle,
            onPreviousMonth = state::goToPreviousMonth,
            onNextMonth = state::goToNextMonth,
            colors = colors,
            dateDisplayMode = dateDisplayMode,
            gregorianMonthText = gregorianMonthText,
            contentDescription = headerContentDescription,
            labels = labels,
            canGoToPreviousMonth = state.canGoToPreviousMonth,
            canGoToNextMonth = state.canGoToNextMonth,
            eventText = selectedEvent
                ?.let { labels.eventBanner(it, selectionIsToday) }
                ?.takeIf { it.isNotEmpty() },
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
                if (header != null) header() else defaultHeader()
                grid()
            }
        } else {
            if (header != null) header() else defaultHeader()
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
    /**
     * Whether the grid also renders the neighbouring months' days. `false` — the default — shows
     * only this month's own days, and the grid then takes five or six rows instead of always six.
     * Presentational only; see [HijriCalendarState.showAdjacentDays].
     */
    showAdjacentDays: Boolean = false,
    /**
     * Whether the grid draws a hairline between cells. `false` — the default — leaves it undivided.
     * Presentational only; see [HijriCalendarState.showCellBorders].
     */
    showCellBorders: Boolean = false,
): HijriCalendarState = coreRememberHijriCalendarState(
    initialMonth = initialMonth,
    initialSelectedDate = initialSelectedDate,
    firstDayOfWeek = firstDayOfWeek,
    minDate = minDate,
    maxDate = maxDate,
    adjustmentDays = adjustmentDays,
    pakistanDates = pakistanDates,
    weekendDays = weekendDays,
    showAdjacentDays = showAdjacentDays,
    showCellBorders = showCellBorders,
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
    /**
     * Whether the grid also renders the neighbouring months' days. `false` — the default — shows
     * only this month's own days, and the grid then takes five or six rows instead of always six.
     * Presentational only; see [HijriCalendarState.showAdjacentDays].
     */
    showAdjacentDays: Boolean = false,
    /**
     * Whether the grid draws a hairline between cells. `false` — the default — leaves it undivided.
     * Presentational only; see [HijriCalendarState.showCellBorders].
     */
    showCellBorders: Boolean = false,
): HijriCalendarState = coreRememberSaveableHijriCalendarState(
    initialMonth = initialMonth,
    initialSelectedDate = initialSelectedDate,
    firstDayOfWeek = firstDayOfWeek,
    minDate = minDate,
    maxDate = maxDate,
    adjustmentDays = adjustmentDays,
    pakistanDates = pakistanDates,
    weekendDays = weekendDays,
    showAdjacentDays = showAdjacentDays,
    showCellBorders = showCellBorders,
)
