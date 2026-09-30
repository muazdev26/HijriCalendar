package com.muazdev.hijricalendar.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.abdulrahman_b.hijrahdatetime.HijrahDate
import com.abdulrahman_b.hijrahdatetime.toHijrahDate
import com.abdulrahman_b.hijrahdatetime.toLocalDate
import com.abdulrahman_b.hijrahdatetime.yearMonth
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * State holder for a Hijri calendar.
 *
 * @param initialMonth The Hijri month the grid opens on. Change it with [goToMonth];
 *   [currentMonth] tracks navigation.
 * @param firstDayOfWeek Which weekday the grid's first column is. Independent of
 *   [WeekDay.index]'s Saturday-first default, which is only the *declaration* order.
 * @param minDate Earliest selectable day, or null for no lower bound. Resolved into a
 *   real-world Gregorian day (shifted by [adjustmentDays]) and applied in **all three**
 *   date spaces — Umm al-Qura, Pakistan and observed — via a [CalendarDay]'s
 *   [CalendarDay.localDate]. This is why it is a `HijrahDate` yet still bounds a Pakistan
 *   cell: there is no Umm al-Qura coordinate to compare such a cell against, so the
 *   comparison happens on the one ordering all three spaces share.
 * @param maxDate Latest selectable day, or null for no upper bound. Resolved exactly as
 *   [minDate] is. Also gates navigation: [canGoToPreviousMonth] and [canGoToNextMonth] are
 *   false when the neighbouring month holds no day inside the window.
 * @param weekendDays Days rendered with the weekend styling. Defaults to
 *   [WeekDay.WEEKEND_DAYS]; purely presentational and does not affect selection.
 * @param pakistanDates Whether to use the Pakistan (Ruet-e-Hilal) calendar instead of the
 *   Umm al-Qura calculation. Changing it at runtime keeps the selected date on the same
 *   real-world Gregorian day where possible.
 * @param initialSelectedObservedDate The initially selected date in observed (override-shifted)
 *   space, used when [monthLengths] has any entry. Unlike [initialSelectedDate] it may name a
 *   day beyond a month's calculated length, such as a forced 30th.
 * @param adjustmentDays Shifts the whole calendar relative to the underlying calculation
 *   to compensate for local moon sighting: the observed Hijri date of a Gregorian day is
 *   the conversion of `(date + adjustmentDays)`. All generated cells, weekday alignment,
 *   [selectedDate]/[selectedPakistanDate] and today detection live in this adjusted space.
 *   In Pakistan (Ruet-e-Hilal) mode the same shift is applied on top of the Pakistan
 *   table. Read it at runtime or change it with [setAdjustmentDays] (the selected date is
 *   kept on the same real-world Gregorian day).
 * @param initialSelectedDate The initially selected date. May be given as an unadjusted
 *   (Umm al-Qura) [HijrahDate]; it is normalized into adjusted space at construction, so
 *   passing `today.toHijrahDate()` with `adjustmentDays != 0` still highlights the same
 *   real-world day that `isToday` highlights.
 * @param initialSelectedPakistanDate The initially selected date in Pakistan space. It is
 *   expected to be an observed date (already shifted by [adjustmentDays]).
 */
@Stable
public class HijriCalendarState(
    initialMonth: HijrahYearMonth,
    initialSelectedDate: HijrahDate? = null,
    public val firstDayOfWeek: WeekDay = WeekDay.DEFAULT_FIRST_DAY,
    /**
     * Earliest selectable day, or null for no lower bound. Resolved into a real-world Gregorian
     * day (shifted by [adjustmentDays]) and applied in **all three** date spaces — Umm al-Qura,
     * Pakistan and observed — via a [CalendarDay]'s [CalendarDay.localDate]. This is why it is a
     * `HijrahDate` yet still bounds a Pakistan cell: there is no Umm al-Qura coordinate to compare
     * such a cell against, so the comparison happens on the one ordering all three spaces share.
     *
     * Also bounds **navigation**: the grid's pager window starts here, so a bound here makes every
     * earlier month unreachable by swipe. An unset bound reaches back to [HijrahDate.MIN], the same
     * edge [canGoToPreviousMonth] stops at. A `minDate` after the initial month leaves that month
     * unreachable and the grid opens on the nearest in-range month instead.
     */
    public val minDate: HijrahDate? = null,
    /**
     * Latest selectable day, or null for no upper bound. Resolved exactly as [minDate] is, and
     * bounds navigation the same way: an unset bound reaches forward to [HijrahDate.MAX], which is
     * where [canGoToNextMonth] stops.
     *
     * An inverted range (`minDate` after `maxDate`) is not rejected. No month is navigable and no
     * day is selectable; the grid renders a single month and both arrow predicates answer false.
     */
    public val maxDate: HijrahDate? = null,
    adjustmentDays: Int = 0,
    pakistanDates: Boolean = false,
    initialSelectedPakistanDate: PakistanHijriDate? = null,
    initialSelectedObservedDate: ObservedHijriDate? = null,
    public val weekendDays: Set<WeekDay> = WeekDay.WEEKEND_DAYS,
    /**
     * Month-length overrides owned by this state holder. Defaults to the process-wide
     * [HijriMonthOverrides.current]; pass your own [HijriMonthLengths] to scope overrides to this
     * calendar so two calendars in one process can disagree about month lengths.
     */
    public val monthLengths: HijriMonthLengths = HijriMonthOverrides.current,
) {
    private var _adjustmentDays by mutableStateOf(adjustmentDays)
    private var _pakistanDates by mutableStateOf(pakistanDates)
    private var _currentMonth by mutableStateOf(initialMonth)
    private var _selectedDate by mutableStateOf(initialSelectedDate.adjustToAdjustedSpace())
    private var _selectedPakistanDate by mutableStateOf(initialSelectedPakistanDate)
    private var _selectedObservedDate by mutableStateOf(initialSelectedObservedDate)
    private var _overridesRevision by mutableStateOf(monthLengths.currentRevision)

    public val adjustmentDays: Int get() = _adjustmentDays

    /**
     * Whether the calendar shows Ruet-e-Hilal (Pakistan) dates instead of the Umm al-Qura
     * calculation. Toggle at runtime with [setPakistanDates].
     */
    public val pakistanDates: Boolean get() = _pakistanDates

    private fun HijrahDate?.adjustToAdjustedSpace(): HijrahDate? {
        if (this == null || adjustmentDays == 0) return this
        // A selection shifted past the edge of Umm al-Qura's table keeps its unshifted value
        // rather than being dropped; see [orNullIfOutOfRange].
        return orNullIfOutOfRange { toLocalDate().plus(adjustmentDays, DateTimeUnit.DAY).toHijrahDate() } ?: this
    }

    public val currentMonth: HijrahYearMonth get() = _currentMonth
    public val selectedDate: HijrahDate? get() = _selectedDate

    /** Selected date in Pakistan (Ruet-e-Hilal) space; non-null when a Pakistan-mode cell is selected. */
    public val selectedPakistanDate: PakistanHijriDate? get() = _selectedPakistanDate

    /**
     * Selected date in the observed (override-shifted) Umm al-Qura space; non-null when a
     * cell generated with [monthLengths] applied is selected.
     */
    public val selectedObservedDate: ObservedHijriDate? get() = _selectedObservedDate

    /**
     * Snapshot of [monthLengths]'s revision at the last recomposition, used to rebuild the grid
     * and header when month-length overrides change at runtime.
     */
    public val overridesRevision: Long get() = _overridesRevision

    /**
     * Whether the previous month can be navigated to without leaving the
     * [minDate]/[maxDate] range. A month is considered navigable if it contains at
     * least one day within the bounded range. `true` when [minDate] is unset.
     */
    public val canGoToPreviousMonth: Boolean
        get() = _currentMonth.minusMonthOrNull(1)?.isNavigableWithin(minDate, maxDate) ?: false

    /**
     * Whether the next month can be navigated to without leaving the
     * [minDate]/[maxDate] range. A month is considered navigable if it contains at
     * least one day within the bounded range. `true` when [maxDate] is unset.
     */
    public val canGoToNextMonth: Boolean
        get() = _currentMonth.plusMonthOrNull(1)?.isNavigableWithin(minDate, maxDate) ?: false

    public val calendarMonth: CalendarMonth by derivedStateOf {
        // A plain read that establishes a snapshot dependency so the grid recomputes when
        // user overrides change, even though overrides are not a Compose state themselves.
        _overridesRevision
        _currentMonth.toCalendarMonth(
            overrides = monthLengths,
            firstDayOfWeek = firstDayOfWeek,
            selectedDate = _selectedDate,
            selectedPakistanDate = _selectedPakistanDate,
            selectedObservedDate = _selectedObservedDate,
            minDate = minDate,
            maxDate = maxDate,
            adjustmentDays = adjustmentDays,
            pakistan = pakistanDates,
            weekendDays = weekendDays,
        )
    }

    public fun goToNextMonth() {
        _currentMonth = _currentMonth.plusMonthOrNull(1) ?: _currentMonth
    }

    public fun goToPreviousMonth() {
        _currentMonth = _currentMonth.minusMonthOrNull(1) ?: _currentMonth
    }

    /**
     * Selects the given Pakistan (Ruet-e-Hilal) date. Falls back to navigating the month
     * only when [minDate]/[maxDate] is configured (unbounded calendars never reject a date).
     */
    public fun selectPakistanDate(date: PakistanHijriDate) {
        if (!dateWindow().contains(date)) return
        _selectedPakistanDate = date
        val yearMonth = HijrahYearMonth(date.year, date.month)
        if (yearMonth != _currentMonth) {
            _currentMonth = yearMonth
        }
    }

    /**
     * Selects the given observed (override-shifted) Umm al-Qura date. Unlike [selectDate] it
     * accepts days beyond a month's calculated length (e.g. a forced 30th), so the selection
     * survives when overrides are cleared via navigation only.
     */
    public fun selectObservedDate(date: ObservedHijriDate) {
        if (!dateWindow().contains(date)) return
        _selectedObservedDate = date
        val yearMonth = HijrahYearMonth(date.year, date.month)
        if (yearMonth != _currentMonth) {
            _currentMonth = yearMonth
        }
    }

    /** Forced length for [year]/[month] (29 or 30), or null when the calculation is used. */
    public fun monthLengthOf(year: Int, month: Int): Int? = monthLengths.monthLength(year, month)

    /**
     * Forces [year]/[month] to [length] (29 or 30) days, overriding both the Umm al-Qura
     * calculation and, in Pakistan mode, the [PakistanHijriCalendar.FIXES] table for that
     * month. Later months re-anchor off the new length until the next re-sync fix.
     */
    public fun setMonthLength(year: Int, month: Int, length: Int) {
        monthLengths.setMonthLength(year, month, length)
        _overridesRevision = monthLengths.currentRevision
    }

    /** Removes a user-forced length for [year]/[month], falling back to the calculation. */
    public fun clearMonthLength(year: Int, month: Int) {
        monthLengths.clearMonthLength(year, month)
        _overridesRevision = monthLengths.currentRevision
    }

    /** Removes every user-forced length, restoring the pure calculated calendar. */
    public fun clearAllMonthLengths() {
        monthLengths.clearAll()
        _overridesRevision = monthLengths.currentRevision
    }

    /**
     * Routes a tapped cell to the selection space it belongs to (Umm al-Qura, observed
     * override-shifted Umm al-Qura, or Pakistan), keeping the behavior identical whichever
     * mode is active.
     */
    public fun selectDay(day: CalendarDay) {
        val pakistanDate = day.pakistanDate
        if (pakistanDate != null) {
            selectPakistanDate(pakistanDate)
        } else {
            val observedDate = day.observedDate
            if (observedDate != null) {
                selectObservedDate(observedDate)
            } else {
                day.hijrahDate?.let(::selectDate)
            }
        }
    }

    public fun selectDate(date: HijrahDate) {
        if (dateWindow().contains(date)) {
            _selectedDate = date
            if (date.yearMonth != _currentMonth) {
                _currentMonth = date.yearMonth
            }
        }
    }

    public fun goToMonth(yearMonth: HijrahYearMonth) {
        _currentMonth = yearMonth
    }

    public fun goToToday() {
        if (pakistanDates) {
            val todayObserved = PakistanHijriCalendar.today()
                ?.localDate
                ?.plus(adjustmentDays, DateTimeUnit.DAY)
                ?.let(PakistanHijriCalendar::gregorianToHijri)
            if (todayObserved != null) {
                _currentMonth = HijrahYearMonth(todayObserved.year, todayObserved.month)
                _selectedPakistanDate = todayObserved
            }
            return
        }
        if (hasObservedOverrides) {
            val todayObserved = ObservedHijriCalendar.today(adjustmentDays)
            if (todayObserved != null) {
                _currentMonth = HijrahYearMonth(todayObserved.year, todayObserved.month)
                _selectedObservedDate = todayObserved
            }
            return
        }
        val today = todayHijriDate(adjustmentDays)
        if (today != null) {
            _currentMonth = today.yearMonth
            _selectedDate = today
        }
    }

    /**
     * Switches between the Umm al-Qura calculation and the Pakistan Ruet-e-Hilal calendar
     * at runtime. The currently selected date is carried across the switch by its
     * real-world Gregorian day.
     */
    public fun setPakistanDates(enabled: Boolean) {
        if (enabled == pakistanDates) return
        if (enabled) {
            val gregorianDay = selectedObservedDate
                ?.localDate
                ?.plus(adjustmentDays, DateTimeUnit.DAY)
                ?: _selectedDate
                    ?.toLocalDate()
                    ?.plus(adjustmentDays, DateTimeUnit.DAY)
            _selectedPakistanDate = gregorianDay?.let(PakistanHijriCalendar::gregorianToHijri)
        } else {
            val gregorianDay = _selectedPakistanDate
                ?.localDate
                ?.minus(adjustmentDays, DateTimeUnit.DAY)
            if (hasObservedOverrides) {
                _selectedObservedDate = gregorianDay
                    ?.toEpochDays()
                    ?.let(ObservedHijriCalendar::observedDateAt)
            } else {
                _selectedDate = gregorianDay?.let { runCatching { it.toHijrahDate() }.getOrNull() }
            }
        }
        _pakistanDates = enabled
    }

    /**
     * Changes the moon-sighting adjustment at runtime.
     *
     * The selected date (if any) is re-mapped so the same real-world Gregorian day stays
     * selected — its Hijri representation in the new adjusted space — and the today
     * highlight shifts accordingly. The current month is kept.
     */
    public fun setAdjustmentDays(newAdjustmentDays: Int) {
        val shift = newAdjustmentDays - adjustmentDays
        if (shift == 0) {
            _adjustmentDays = newAdjustmentDays
            return
        }
        if (pakistanDates) {
            val remapped = _selectedPakistanDate
                ?.localDate
                ?.plus(shift, DateTimeUnit.DAY)
                ?.let(PakistanHijriCalendar::gregorianToHijri)
            _selectedPakistanDate = remapped
            _adjustmentDays = newAdjustmentDays
            return
        }
        if (hasObservedOverrides) {
            val remapped = _selectedObservedDate
                ?.localDate
                ?.plus(shift, DateTimeUnit.DAY)
                ?.toEpochDays()
                ?.let(ObservedHijriCalendar::observedDateAt)
            _selectedObservedDate = remapped
            _adjustmentDays = newAdjustmentDays
            return
        }
        val remapped = _selectedDate?.let { current ->
            orNullIfOutOfRange { current.toLocalDate().plus(shift, DateTimeUnit.DAY).toHijrahDate() } ?: current
        }
        _selectedDate = remapped
        _adjustmentDays = newAdjustmentDays
    }

    /**
     * Whether any user override is in force, which switches selection to observed space. The
     * revision read is what makes this a snapshot dependency, so a change recomposes the grid.
     */
    private val hasObservedOverrides: Boolean
        get() = monthLengths.all().isNotEmpty()

    /**
     * The [minDate]/[maxDate] window resolved into real-world Gregorian days.
     *
     * Recomputed per call rather than cached: it is two date conversions, and caching would mean
     * invalidating on four inputs (both bounds plus [adjustmentDays]) to save work that happens
     * once per tap.
     */
    private fun dateWindow(): DateWindow = DateWindow.of(minDate, maxDate, adjustmentDays)

    private fun DateWindow.contains(date: HijrahDate): Boolean =
        contains(date.toLocalDate().minus(adjustmentDays, DateTimeUnit.DAY))

    private fun DateWindow.contains(date: PakistanHijriDate): Boolean =
        contains(date.localDate.minus(adjustmentDays, DateTimeUnit.DAY))

    private fun DateWindow.contains(date: ObservedHijriDate): Boolean =
        contains(date.localDate.minus(adjustmentDays, DateTimeUnit.DAY))
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
    initialSelectedPakistanDate: PakistanHijriDate? = null,
    weekendDays: Set<WeekDay> = WeekDay.WEEKEND_DAYS,
): HijriCalendarState {
    return remember {
        HijriCalendarState(
            initialMonth = initialMonth,
            initialSelectedDate = initialSelectedDate,
            firstDayOfWeek = firstDayOfWeek,
            minDate = minDate,
            maxDate = maxDate,
            adjustmentDays = adjustmentDays,
            pakistanDates = pakistanDates,
            initialSelectedPakistanDate = initialSelectedPakistanDate,
            weekendDays = weekendDays,
        )
    }
}

/**
 * A `rememberSaveable`-backed variant of [rememberHijriCalendarState]. Unlike the plain
 * `remember` version, the current month and the selected date (Umm al-Qura, observed
 * override-shifted, or Pakistan) survive configuration changes and process death, so the
 * calendar does not silently reset. The immutable range/weekend configuration (first day of
 * week, min/max dates, adjustment days, weekend days) is captured at first composition and
 * reused when the state is restored.
 */
@Composable
public fun rememberSaveableHijriCalendarState(
    initialMonth: HijrahYearMonth,
    initialSelectedDate: HijrahDate? = null,
    firstDayOfWeek: WeekDay = WeekDay.DEFAULT_FIRST_DAY,
    minDate: HijrahDate? = null,
    maxDate: HijrahDate? = null,
    adjustmentDays: Int = 0,
    pakistanDates: Boolean = false,
    initialSelectedPakistanDate: PakistanHijriDate? = null,
    weekendDays: Set<WeekDay> = WeekDay.WEEKEND_DAYS,
): HijriCalendarState {
    val config = remember(firstDayOfWeek, minDate, maxDate, adjustmentDays, pakistanDates, weekendDays) {
        HijriCalendarStateConfig(firstDayOfWeek, minDate, maxDate, adjustmentDays, pakistanDates, weekendDays)
    }
    val saver = remember(config) { hijriCalendarStateSaver(config) }
    return rememberSaveable(saver = saver) {
        HijriCalendarState(
            initialMonth = initialMonth,
            initialSelectedDate = initialSelectedDate,
            firstDayOfWeek = firstDayOfWeek,
            minDate = minDate,
            maxDate = maxDate,
            adjustmentDays = adjustmentDays,
            pakistanDates = pakistanDates,
            initialSelectedPakistanDate = initialSelectedPakistanDate,
            weekendDays = weekendDays,
        )
    }
}

internal data class HijriCalendarStateConfig(
    val firstDayOfWeek: WeekDay,
    val minDate: HijrahDate?,
    val maxDate: HijrahDate?,
    val adjustmentDays: Int,
    val pakistanDates: Boolean = false,
    val weekendDays: Set<WeekDay>,
)

/**
 * `Saver<HijriCalendarState, List<Int>>` for `rememberSaveable`.
 *
 * ## This is a persisted format on someone else's device
 *
 * A `rememberSaveable` bundle outlives the process: it is written by one build of the app and
 * read back by the next. Nothing here fails at compile time if the layout changes — it either
 * restores the wrong thing or throws `IndexOutOfBoundsException` on `saved[4]`, and only on the
 * user who happened to rotate their device mid-edit.
 *
 * The layout is **positional**, so treat it like a wire format:
 *
 * ```
 * index 0        current month year
 * index 1        current month number (1-12)
 * index 2        1 if config.pakistanDates, else 0
 * index 3        selection type tag (see SELECTION_* below)
 * index 4-6      selYear, selMonth, selDay — present for tags 1, 2 and 3 only
 * index 7        observed effective month length — present for tag 3 only
 * ```
 *
 * Contract:
 *
 * - **Appending is safe.** A new trailing value can be read by an old build only if the old
 *   build ignores it, so append together with a reader that tolerates a short list; within one
 *   app version this is always paired.
 * - **Reordering, removing or reinterpreting an existing index is not.** It restores silently
 *   wrong state on upgrade. If a change is unavoidable, branch on the old layout in `restore`
 *   (as `WidgetOptionsJson.decodeLegacyOrdinalJson` does for the widget schema) rather than
 *   editing the positions in place.
 * - Adding a new [SELECTION_*] tag is safe: old builds see a tag they do not recognise and fall
 *   through to "no selection".
 *
 * The selection type tags are `0` none, `1` hijrah (Umm al-Qura unadjusted), `2` pakistan and
 * `3` observed. The selected date is saved in its own space and re-normalized on restore because
 * [HijriCalendarState] shifts `initialSelectedDate` by
 * [HijriCalendarStateConfig.adjustmentDays] at construction.
 *
 * See `docs/issues/CORE-03-api-stability.md`.
 */
internal fun hijriCalendarStateSaver(
    config: HijriCalendarStateConfig,
): Saver<HijriCalendarState, List<Int>> = Saver(
    save = { state ->
        buildList {
            add(state.currentMonth.year)
            add(state.currentMonth.month.number)
            add(if (config.pakistanDates) 1 else 0)
            when {
                config.pakistanDates -> {
                    val selected = state.selectedPakistanDate
                    if (selected == null) {
                        add(SELECTION_NONE)
                    } else {
                        add(SELECTION_PAKISTAN)
                        add(selected.year)
                        add(selected.month)
                        add(selected.day)
                    }
                }
                state.selectedObservedDate != null -> {
                    val selected = state.selectedObservedDate!!
                    add(SELECTION_OBSERVED)
                    add(selected.year)
                    add(selected.month)
                    add(selected.day)
                    add(selected.monthLength)
                }
                else -> {
                    val selected = state.selectedDate.toUnadjustedSpace(config.adjustmentDays)
                    if (selected == null) {
                        add(SELECTION_NONE)
                    } else {
                        add(SELECTION_HIJRAH)
                        add(selected.year)
                        add(selected.month.number)
                        add(selected.day)
                    }
                }
            }
        }
    },
    restore = { saved ->
        val year = saved[0]
        val month = saved[1]
        val pakistan = saved[2] == 1
        val selectionType = saved[3]
        val initialSelectedDate =
            if (selectionType == SELECTION_HIJRAH && !pakistan) HijrahDate(saved[4], saved[5], saved[6]) else null
        val initialSelectedPakistanDate =
            if (selectionType == SELECTION_PAKISTAN && pakistan) PakistanHijriDate(saved[4], saved[5], saved[6]) else null
        val initialSelectedObservedDate =
            if (selectionType == SELECTION_OBSERVED && !pakistan) ObservedHijriDate(saved[4], saved[5], saved[6], saved[7]) else null
        HijriCalendarState(
            initialMonth = HijrahYearMonth(year, month),
            initialSelectedDate = initialSelectedDate,
            firstDayOfWeek = config.firstDayOfWeek,
            minDate = config.minDate,
            maxDate = config.maxDate,
            adjustmentDays = config.adjustmentDays,
            pakistanDates = config.pakistanDates,
            initialSelectedPakistanDate = initialSelectedPakistanDate,
            initialSelectedObservedDate = initialSelectedObservedDate,
            weekendDays = config.weekendDays,
        )
    },
)

private fun HijrahDate?.toUnadjustedSpace(adjustmentDays: Int): HijrahDate? {
    if (this == null || adjustmentDays == 0) return this
    return orNullIfOutOfRange { toLocalDate().minus(adjustmentDays, DateTimeUnit.DAY).toHijrahDate() } ?: this
}

private const val SELECTION_NONE = 0
private const val SELECTION_HIJRAH = 1
private const val SELECTION_PAKISTAN = 2
private const val SELECTION_OBSERVED = 3

/**
 * Returns null when shifting [months] would leave Umm al-Qura's table, which is what stops
 * navigation at the ends of the supported range.
 *
 * The bound is checked *before* calling into the library on purpose: `hijrah-datetime` signals an
 * out-of-range month with an unguarded `ArrayIndexOutOfBoundsException` from its rule table rather
 * than a documented exception type, so there is nothing here worth catching. Pre-checking states
 * the intent and does not depend on that implementation detail. [orNullIfOutOfRange] stays as a
 * second net for a day that becomes invalid for a month inside the range.
 */
private fun HijrahYearMonth.plusMonthOrNull(months: Int): HijrahYearMonth? =
    if (wouldLeaveHijriTable(months)) null else orNullIfOutOfRange { plusMonth(months) }

private fun HijrahYearMonth.minusMonthOrNull(months: Int): HijrahYearMonth? =
    if (wouldLeaveHijriTable(-months)) null else orNullIfOutOfRange { minusMonth(months) }

private fun HijrahYearMonth.wouldLeaveHijriTable(months: Int): Boolean {
    val targetYear = (year * 12 + (month.number - 1) + months).floorDiv(12)
    return targetYear !in HijrahDate.MIN.year..HijrahDate.MAX.year
}

private fun HijrahYearMonth.isNavigableWithin(min: HijrahDate?, max: HijrahDate?): Boolean {
    if (min != null && lastDay < min) return false
    if (max != null && firstDay > max) return false
    return true
}
