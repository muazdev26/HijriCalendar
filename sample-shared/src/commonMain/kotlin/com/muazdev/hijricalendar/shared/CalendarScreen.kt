package com.muazdev.hijricalendar.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.abdulrahman_b.hijrahdatetime.HijrahMonth
import com.abdulrahman_b.hijrahdatetime.toLocalDate
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import com.muazdev.hijricalendar.core.CalendarNames
import com.muazdev.hijricalendar.core.HijriCalendarState
import com.muazdev.hijricalendar.core.ObservedHijriCalendar
import com.muazdev.hijricalendar.core.PakistanHijriCalendar
import com.muazdev.hijricalendar.core.rememberHijriCalendarState
import com.muazdev.hijricalendar.ui.DateDisplayMode
import com.muazdev.hijricalendar.ui.HijriCalendar
import com.muazdev.hijricalendar.ui.HijriCalendarLabels
import com.muazdev.hijricalendar.ui.defaultOnDayClick
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus

@Composable
fun CalendarScreen(
    modifier: Modifier = Modifier,
    state: HijriCalendarState = rememberHijriCalendarState(
        initialMonth = HijrahYearMonth(1447, 9),
    ),
    dateDisplayMode: DateDisplayMode = DateDisplayMode.HIJRI_ONLY,
    onDateDisplayModeChange: (DateDisplayMode) -> Unit = {},
    onJumpToToday: (() -> Unit)? = null,
    labels: HijriCalendarLabels? = null,
    showAdjustmentSelector: Boolean = false,
    onAdjustmentDaysChange: ((Int) -> Unit)? = null,
    showPakistanToggle: Boolean = false,
    onPakistanDatesChange: ((Boolean) -> Unit)? = null,
    showMonthLengthSettings: Boolean = false,
    onMonthLengthOverridesChanged: (() -> Unit)? = null,
    showAdjacentDaysToggle: Boolean = false,
    onShowAdjacentDaysChange: ((Boolean) -> Unit)? = null,
) {
    val selectedDateText = rememberSelectedDateText(state, dateDisplayMode)

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HijriCalendar(
            state = state,
            onDayClick = state.defaultOnDayClick(),
            dateDisplayMode = dateDisplayMode,
            modifier = Modifier.fillMaxWidth(),
            labels = labels ?: HijriCalendarLabels(),
        )

        Spacer(modifier = Modifier.height(16.dp))

        DateDisplayModeSelector(
            selectedMode = dateDisplayMode,
            onModeSelected = onDateDisplayModeChange,
        )

        if (showAdjustmentSelector) {
            Spacer(modifier = Modifier.height(16.dp))
            AdjustmentSelector(
                current = state.adjustmentDays,
                onSelect = { value ->
                    state.setAdjustmentDays(value)
                    onAdjustmentDaysChange?.invoke(value)
                },
            )
        }

        if (showPakistanToggle) {
            Spacer(modifier = Modifier.height(16.dp))
            PakistanModeSelector(
                pakistan = state.pakistanDates,
                onSelect = { enabled ->
                    state.setPakistanDates(enabled)
                    onPakistanDatesChange?.invoke(enabled)
                },
            )
        }

        if (showAdjacentDaysToggle) {
            Spacer(modifier = Modifier.height(16.dp))
            AdjacentDaysSelector(
                showAdjacentDays = state.showAdjacentDays,
                onSelect = { show ->
                    state.setShowAdjacentDays(show)
                    onShowAdjacentDaysChange?.invoke(show)
                },
            )
        }

        if (showMonthLengthSettings) {
            Spacer(modifier = Modifier.height(16.dp))
            MonthLengthSettingsPanel(
                state = state,
                onOverridesChanged = onMonthLengthOverridesChanged,
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedDateText != null) {
            Text(
                text = selectedDateText,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Text(
                text = "No date selected",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (onJumpToToday != null) {
            Spacer(modifier = Modifier.height(16.dp))
            androidx.compose.material3.Button(onClick = onJumpToToday) {
                Text("Jump to Today")
            }
        }
    }
}

/**
 * The selected-date card's text, or null when nothing is selected.
 *
 * Selection lives in one of three spaces and the caller does not choose which: `selectDay` routes to
 * observed space whenever a month-length override is in force, because a day past a month's
 * calculated length has no Umm al-Qura coordinate to land on. An earlier version of this read only
 * `selectedDate` and `selectedPakistanDate`, so the moment the sample's month-length panel was used
 * the card reported "No date selected" while the grid directly above it highlighted the day.
 *
 * Observed space comes first for the same reason. Its `month` is an `Int`, so the name comes from the
 * shared [CalendarNames] list — which is also what keeps this card and the calendar header spelling a
 * month identically.
 *
 * All three spaces agree on the real-world day, so the Gregorian half is computed once. The
 * adjustment is subtracted because `localDate` is already in adjusted space.
 */
private fun selectedDateSummary(state: HijriCalendarState, mode: DateDisplayMode): String? {
    val observed = state.selectedObservedDate
    val pakistan = state.selectedPakistanDate
    val plain = state.selectedDate

    val hijri = observed?.let { "${it.day} ${CalendarNames.englishHijriMonths[it.month - 1]} ${it.year}" }
        ?: pakistan?.let { "${it.day} ${HijrahMonth.entries[it.month - 1].name} ${it.year}" }
        ?: plain?.let { "${it.day} ${it.month.name} ${it.year}" }
    val gregorianDay = observed?.localDate ?: pakistan?.localDate ?: plain?.toLocalDate()
    val gregorian = gregorianDay?.let {
        val real = it.minus(state.adjustmentDays, DateTimeUnit.DAY)
        "${real.day} ${real.month.name} ${real.year}"
    }

    return if (hijri == null || gregorian == null) {
        null
    } else {
        when (mode) {
            DateDisplayMode.HIJRI_ONLY -> hijri
            DateDisplayMode.GREGORIAN_ONLY -> gregorian
            DateDisplayMode.BOTH -> "$hijri\n$gregorian"
        }
    }
}

@Composable
private fun DateDisplayModeSelector(
    selectedMode: DateDisplayMode,
    onModeSelected: (DateDisplayMode) -> Unit,
) {
    val modes = DateDisplayMode.entries
    val labels = listOf("Hijri", "Gregorian", "Both")

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        modes.forEachIndexed { index, mode ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = modes.size,
                ),
                onClick = { onModeSelected(mode) },
                selected = selectedMode == mode,
                label = { Text(labels[index]) },
            )
        }
    }
}

@Composable
private fun PakistanModeSelector(
    pakistan: Boolean,
    onSelect: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Hijri calendar source",
            style = MaterialTheme.typography.titleSmall,
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            listOf("Calculation" to false, "Pakistan" to true).forEach { (label, value) ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(
                        index = if (value) 1 else 0,
                        count = 2,
                    ),
                    onClick = { onSelect(value) },
                    selected = pakistan == value,
                    label = { Text(label) },
                )
            }
        }
        Text(
            text = if (pakistan) {
                "Pakistan (Ruet-e-Hilal moon sighting)"
            } else {
                "Umm al-Qura (Saudi calculation)"
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun AdjustmentSelector(
    current: Int,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Moon-sighting adjustment",
            style = MaterialTheme.typography.titleSmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (-2..2).forEach { value ->
                if (value == current) {
                    Button(onClick = { onSelect(value) }) {
                        Text(if (value > 0) "+$value" else value.toString())
                    }
                } else {
                    OutlinedButton(onClick = { onSelect(value) }) {
                        Text(if (value > 0) "+$value" else value.toString())
                    }
                }
            }
        }
        Text(
            text = when (current) {
                0 -> "Umm al-Qura (Saudi calculation)"
                else -> if (current > 0) {
                    "Show Hijri $current day(s) ahead"
                } else {
                    "Show Hijri ${-current} day(s) behind"
                }
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * Lets the user manually force the length (29 or 30 days) of the current, previous and next
 * Hijri months — the Ruet-e-Hilal scenario of declaring a 30th (or clipping to a 29th)
 * before the calculated month end. Overrides are recommended to be persisted by the caller
 * via [onOverridesChanged] (they otherwise survive only for the process lifetime).
 */
@Composable
private fun MonthLengthSettingsPanel(
    state: HijriCalendarState,
    onOverridesChanged: (() -> Unit)?,
) {
    val currentMonth = state.currentMonth
    val months = remember(currentMonth) {
        listOfNotNull(
            currentMonth.minusMonthOrNull(1),
            currentMonth,
            currentMonth.plusMonthOrNull(1),
        )
    }
    val hasOverrideForCurrentMonth = remember(state.overridesRevision, currentMonth) {
        state.monthLengthOf(currentMonth.year, currentMonth.month.number) != null
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Month-end length overrides",
            style = MaterialTheme.typography.titleSmall,
        )
        months.forEach { month ->
            MonthLengthOverrideRow(
                state = state,
                month = month,
                onOverridesChanged = onOverridesChanged,
            )
        }
        Text(
            text = if (hasOverrideForCurrentMonth) {
                "Monthly override set — resets take effect immediately"
            } else {
                "No overrides set for this month"
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun MonthLengthOverrideRow(
    state: HijriCalendarState,
    month: HijrahYearMonth,
    onOverridesChanged: (() -> Unit)?,
) {
    val year = month.year
    val monthNumber = month.month.number

    val lengths = remember(
        state.overridesRevision,
        state.pakistanDates,
        year,
        monthNumber,
    ) {
        if (state.pakistanDates) {
            PakistanHijriCalendar.defaultLengthOfMonth(year, monthNumber) to
                PakistanHijriCalendar.lengthOfMonth(year, monthNumber)
        } else {
            ObservedHijriCalendar.defaultLength(year, monthNumber) to
                ObservedHijriCalendar.observedLength(year, monthNumber)
        }
    }
    val defaultLength = lengths.first
    val effectiveLength = lengths.second

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "${month.month.name} ${year}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "calc ${defaultLength}d",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(29, 30).forEach { length ->
                if (effectiveLength == length) {
                    Button(onClick = {
                        state.clearMonthLength(year, monthNumber)
                        onOverridesChanged?.invoke()
                    }) {
                        Text("$length days")
                    }
                } else {
                    OutlinedButton(onClick = {
                        state.setMonthLength(year, monthNumber, length)
                        onOverridesChanged?.invoke()
                    }) {
                        Text("$length days")
                    }
                }
            }
        }
    }
}

private fun HijrahYearMonth.plusMonthOrNull(months: Int): HijrahYearMonth? {
    return try {
        plusMonth(months)
    } catch (_: Exception) {
        null
    }
}

private fun HijrahYearMonth.minusMonthOrNull(months: Int): HijrahYearMonth? {
    return try {
        minusMonth(months)
    } catch (_: Exception) {
        null
    }
}

/**
 * Show/hide the neighbouring months' days in the grid.
 *
 * Hidden by default: a Hijri month is 29 or 30 days, so a padded six-row grid spends a quarter of
 * its cells on days that belong to the month before or after. Hiding them lets the grid take five
 * rows when the month does not need a sixth.
 */
@Composable
private fun AdjacentDaysSelector(
    showAdjacentDays: Boolean,
    onSelect: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Neighbouring months", style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !showAdjacentDays,
                onClick = { onSelect(false) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) {
                Text("Hide")
            }
            SegmentedButton(
                selected = showAdjacentDays,
                onClick = { onSelect(true) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) {
                Text("Show")
            }
        }
    }
}

/**
 * The selected date, rendered as one line of text.
 *
 * Selection lives in one of three spaces, and which one is not a free choice the caller makes:
 * `selectDay` routes to observed space whenever a month-length override is in force, because a day
 * past the calculated length has no Umm al-Qura coordinate. So reading only the first two made the
 * summary say "No date selected" while the grid right above it highlighted the day — the same class
 * of bug the UI review fixed in the grid. `pakistanDates` is keyed explicitly because switching
 * spaces moves the selection between holders.
 */
@Composable
private fun rememberSelectedDateText(
    state: HijriCalendarState,
    dateDisplayMode: DateDisplayMode,
): String? = remember(
    state.selectedDate,
    state.selectedPakistanDate,
    state.selectedObservedDate,
    state.pakistanDates,
    dateDisplayMode,
    state.adjustmentDays,
) {
    selectedDateSummary(state, dateDisplayMode)
}
