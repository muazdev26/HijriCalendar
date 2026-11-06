package com.muazdev.hijricalendar.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import com.abdulrahman_b.hijrahdatetime.HijrahDate
import com.abdulrahman_b.hijrahdatetime.toHijrahDate
import com.abdulrahman_b.hijrahdatetime.toLocalDate
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import com.muazdev.hijricalendar.core.HijriCalendarState
import com.muazdev.hijricalendar.core.HijriDate
import com.muazdev.hijricalendar.core.HijriMonthOverrides
import com.muazdev.hijricalendar.core.HijriYearMonth
import com.muazdev.hijricalendar.core.ObservedHijriCalendar
import com.muazdev.hijricalendar.core.ObservedHijriDate
import com.muazdev.hijricalendar.core.PakistanHijriCalendar
import com.muazdev.hijricalendar.core.PakistanHijriDate
import com.muazdev.hijricalendar.core.WeekDay
import com.muazdev.hijricalendar.core.toHijriDate
import kotlinx.datetime.DateTimeArithmeticException
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus

/**
 * The Compose-facing half of [HijriCalendarState].
 *
 * `HijriCalendarState` itself lives in `calendar-core` and is snapshot-backed, but the two
 * *remember* entry points and the `rememberSaveable` saver are Compose **compiler** concerns: they
 * are `@Composable`, and they exist only to bind a state holder to a composition. Keeping them in
 * the data module forced every consumer of the date model — a widget, a background job, a plain
 * JVM tool — to depend on the Compose compiler and its plugin, for a state holder they never
 * compose. They live here, with the rest of the composables, and `calendar-core`'s published
 * surface no longer names `@Composable` at all.
 */

@Composable
@Suppress("DEPRECATION")
@Deprecated(
    message = "The parameter types are from the third-party alpha hijrah-datetime artifact. " +
        "Use the overload taking `initialHijriMonth: HijriYearMonth` and `HijriDate` values.",
    level = DeprecationLevel.WARNING,
)
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
    showAdjacentDays: Boolean = false,
    showCellBorders: Boolean = false,
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
            showAdjacentDays = showAdjacentDays,
            showCellBorders = showCellBorders,
        )
    }
}

/**
 * The library-owned-types variant of the state factory.
 *
 * Same behaviour and the same one-composition lifetime as the deprecated overload; it exists so a
 * caller starting from [HijriYearMonth] / [HijriDate] never names the third-party alpha type.
 */
@Composable
@Suppress("DEPRECATION")
public fun rememberHijriCalendarState(
    initialHijriMonth: HijriYearMonth,
    initialSelectedHijriDate: HijriDate? = null,
    firstDayOfWeek: WeekDay = WeekDay.DEFAULT_FIRST_DAY,
    minHijriDate: HijriDate? = null,
    maxHijriDate: HijriDate? = null,
    adjustmentDays: Int = 0,
    pakistanDates: Boolean = false,
    initialSelectedPakistanDate: PakistanHijriDate? = null,
    weekendDays: Set<WeekDay> = WeekDay.WEEKEND_DAYS,
    showAdjacentDays: Boolean = false,
    showCellBorders: Boolean = false,
): HijriCalendarState = rememberHijriCalendarState(
    initialMonth = initialHijriMonth.toHijrahYearMonth(),
    initialSelectedDate = initialSelectedHijriDate?.toHijrahDate(),
    firstDayOfWeek = firstDayOfWeek,
    minDate = minHijriDate?.toHijrahDate(),
    maxDate = maxHijriDate?.toHijrahDate(),
    adjustmentDays = adjustmentDays,
    pakistanDates = pakistanDates,
    initialSelectedPakistanDate = initialSelectedPakistanDate,
    weekendDays = weekendDays,
    showAdjacentDays = showAdjacentDays,
    showCellBorders = showCellBorders,
)

/**
 * A `rememberSaveable`-backed variant of [rememberHijriCalendarState]. Unlike the plain
 * `remember` version, the current month and the selected date (Umm al-Qura, observed
 * override-shifted, or Pakistan) survive configuration changes and process death, so the
 * calendar does not silently reset. The immutable range/weekend configuration (first day of
 * week, min/max dates, adjustment days, weekend days) is captured at first composition and
 * reused when the state is restored.
 */
@Composable
@Suppress("DEPRECATION")
@Deprecated(
    message = "The parameter types are from the third-party alpha hijrah-datetime artifact. " +
        "Use the overload taking `initialHijriMonth: HijriYearMonth` and `HijriDate` values.",
    level = DeprecationLevel.WARNING,
)
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
    showAdjacentDays: Boolean = false,
    showCellBorders: Boolean = false,
): HijriCalendarState {
    // `remember` takes a vararg, so the keys are assembled as a list rather than named one per
    // line. Everything the restored state is configured from has to be here: a key this list
    // forgets is a change the host makes that a restore silently ignores.
    val configKeys = arrayOf<Any?>(
        firstDayOfWeek,
        minDate,
        maxDate,
        adjustmentDays,
        pakistanDates,
        weekendDays,
        showAdjacentDays,
        showCellBorders,
    )

    @Suppress("SpreadOperator")
    val config = remember(*configKeys) {
        HijriCalendarStateConfig(
            firstDayOfWeek = firstDayOfWeek,
            minDate = minDate,
            maxDate = maxDate,
            adjustmentDays = adjustmentDays,
            pakistanDates = pakistanDates,
            weekendDays = weekendDays,
            showAdjacentDays = showAdjacentDays,
        )
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

/**
 * The library-owned-types variant of the saveable state factory.
 *
 * Delegates to the deprecated overload, so the persisted format, the restore behaviour and the
 * configuration keys are shared — only the caller-facing types differ.
 */
@Composable
@Suppress("DEPRECATION")
public fun rememberSaveableHijriCalendarState(
    initialHijriMonth: HijriYearMonth,
    initialSelectedHijriDate: HijriDate? = null,
    firstDayOfWeek: WeekDay = WeekDay.DEFAULT_FIRST_DAY,
    minHijriDate: HijriDate? = null,
    maxHijriDate: HijriDate? = null,
    adjustmentDays: Int = 0,
    pakistanDates: Boolean = false,
    initialSelectedPakistanDate: PakistanHijriDate? = null,
    weekendDays: Set<WeekDay> = WeekDay.WEEKEND_DAYS,
    showAdjacentDays: Boolean = false,
    showCellBorders: Boolean = false,
): HijriCalendarState = rememberSaveableHijriCalendarState(
    initialMonth = initialHijriMonth.toHijrahYearMonth(),
    initialSelectedDate = initialSelectedHijriDate?.toHijrahDate(),
    firstDayOfWeek = firstDayOfWeek,
    minDate = minHijriDate?.toHijrahDate(),
    maxDate = maxHijriDate?.toHijrahDate(),
    adjustmentDays = adjustmentDays,
    pakistanDates = pakistanDates,
    initialSelectedPakistanDate = initialSelectedPakistanDate,
    weekendDays = weekendDays,
    showAdjacentDays = showAdjacentDays,
    showCellBorders = showCellBorders,
)

internal data class HijriCalendarStateConfig(
    val firstDayOfWeek: WeekDay,
    val minDate: HijrahDate?,
    val maxDate: HijrahDate?,
    val adjustmentDays: Int,
    val pakistanDates: Boolean = false,
    val weekendDays: Set<WeekDay>,
    val showAdjacentDays: Boolean = false,
    val showCellBorders: Boolean = false,
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
 * - Adding a new `SELECTION_*` tag is safe: old builds see a tag they do not recognise and fall
 *   through to "no selection".
 *
 * The selection type tags are `0` none, `1` hijrah (Umm al-Qura unadjusted), `2` pakistan and
 * `3` observed. The selected date is saved in its own space and re-normalized on restore because
 * [HijriCalendarState] shifts `initialSelectedDate` by
 * [HijriCalendarStateConfig.adjustmentDays] at construction.
 */
@Suppress("DEPRECATION")
internal fun hijriCalendarStateSaver(
    config: HijriCalendarStateConfig,
): Saver<HijriCalendarState, List<Int>> = Saver(
    save = { state ->
        buildList {
            add(state.currentHijriMonth.year)
            add(state.currentHijriMonth.month)
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
                    val selected = state.selectedHijriDate.toUnadjustedSpace(config.adjustmentDays)
                    if (selected == null) {
                        add(SELECTION_NONE)
                    } else {
                        add(SELECTION_HIJRAH)
                        add(selected.year)
                        add(selected.month)
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
            if (selectionType == SELECTION_HIJRAH && !pakistan) {
                HijrahDate(saved[4], saved[5], saved[6])
            } else {
                null
            }
        val initialSelectedPakistanDate =
            if (selectionType == SELECTION_PAKISTAN && pakistan) restoredPakistanDate(saved) else null
        val initialSelectedObservedDate =
            if (selectionType == SELECTION_OBSERVED && !pakistan) restoredObservedDate(saved) else null
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
            showAdjacentDays = config.showAdjacentDays,
            showCellBorders = config.showCellBorders,
        )
    },
)

private fun HijriDate?.toUnadjustedSpace(adjustmentDays: Int): HijriDate? {
    if (this == null || adjustmentDays == 0) return this
    return orNullIfOutOfRange {
        toHijrahDate().toLocalDate().minus(adjustmentDays, DateTimeUnit.DAY).toHijrahDate().toHijriDate()
    } ?: this
}

/**
 * Rebuilds the saved Pakistan selection, clamped to the month length in force.
 *
 * The saver carries display configuration only, not an override table, so the restored state is
 * built with the process-global [HijriMonthOverrides.current] — the same table the saver's own
 * `save` reads through [HijriCalendarState.monthLengths] by default.
 */
private fun restoredPakistanDate(saved: List<Int>): PakistanHijriDate? =
    pakistanHijriDateAt(saved[4], saved[5], saved[6], HijriMonthOverrides.current)

/**
 * Rebuilds the saved observed selection, resolving its Gregorian day against the process-global
 * table (the saver carries no table). Resolving it lazily was the hazard: a value type cannot read a
 * table, and deriving against [HijriMonthOverrides.current] would report another calendar's day.
 */
private fun restoredObservedDate(saved: List<Int>): ObservedHijriDate = ObservedHijriDate(
    year = saved[4],
    month = saved[5],
    day = saved[6],
    monthLength = saved[7],
    localDate = ObservedHijriCalendar.observedToGregorian(
        saved[4],
        saved[5],
        saved[6],
        HijriMonthOverrides.current,
    ),
)

/**
 * Builds a [PakistanHijriDate] for `year`/`month`/`day`, resolving its real-world Gregorian day
 * against [overrides] and clamping a day the table no longer admits.
 *
 * A saved selection and a saved override table come back independently, so an upgrade can hand the
 * state a day its month no longer has; naming that day would make [PakistanHijriDate.localDate]
 * throw on every recomposition. Returns `null` only when the month is outside
 * [PakistanHijriCalendar]'s supported range.
 *
 * This mirrors `calendar-core`'s internal `pakistanHijriDateAt`, which the state has to keep for
 * its own `selectDate` path and which cannot be reached across a module boundary. It is built from
 * this module's public API only — `lengthOfMonth` to clamp, `hijriToGregorian` to resolve — so the
 * vanity of sharing one line of arithmetic does not become public surface in the data module.
 */
private fun pakistanHijriDateAt(
    year: Int,
    month: Int,
    day: Int,
    overrides: com.muazdev.hijricalendar.core.HijriMonthLengths,
): PakistanHijriDate? {
    // Single return on purpose: detekt caps the happy-path early-returns at two, and the three
    // clamps read as one chain anyway.
    val length = orNullIfOutOfRange { PakistanHijriCalendar.lengthOfMonth(year, month, overrides) }
    val resolvedDay = length?.let { minOf(day, it) }
    val localDate = resolvedDay?.let { dayInTable ->
        orNullIfOutOfRange { PakistanHijriCalendar.hijriToGregorian(year, month, dayInTable, overrides) }
    }
    return if (resolvedDay != null && localDate != null) {
        PakistanHijriDate(year, month, resolvedDay, localDate)
    } else {
        null
    }
}

private const val SELECTION_NONE = 0
private const val SELECTION_HIJRAH = 1
private const val SELECTION_PAKISTAN = 2
private const val SELECTION_OBSERVED = 3

/**
 * Runs [block], answering null only when a date falls **outside the supported calendar range**.
 *
 * `calendar-core` owns the canonical version of this predicate (its internal
 * `orNullIfOutOfRange`, which documents why exactly these two exception types mean "no answer"
 * rather than "defect"). It is private there, and this module needs it only on the restore path,
 * so it is restated here rather than exposed as public API purely to be shared.
 */
private inline fun <T> orNullIfOutOfRange(block: () -> T): T? = try {
    block()
} catch (_: IllegalArgumentException) {
    null
} catch (_: DateTimeArithmeticException) {
    null
}
