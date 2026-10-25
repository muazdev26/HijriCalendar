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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.abdulrahman_b.hijrahdatetime.HijrahMonth
import com.abdulrahman_b.hijrahdatetime.toLocalDate
import com.abdulrahman_b.hijrahdatetime.yearMonth
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import com.muazdev.hijricalendar.core.CalendarNames
import com.muazdev.hijricalendar.core.HijriCalendarState
import com.muazdev.hijricalendar.core.HijriEvents
import com.muazdev.hijricalendar.core.ObservedHijriCalendar
import com.muazdev.hijricalendar.core.PakistanHijriCalendar
import com.muazdev.hijricalendar.core.rememberHijriCalendarState
import com.muazdev.hijricalendar.ui.DateDisplayMode
import com.muazdev.hijricalendar.ui.HijriCalendar
import com.muazdev.hijricalendar.ui.HijriCalendarLabels
import com.muazdev.hijricalendar.ui.PrimaryDate
import com.muazdev.hijricalendar.ui.defaultOnDayClick
import com.muazdev.hijricalendar.widgetdata.WeekendPattern
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus

/**
 * The sample's calendar screen.
 *
 * `LongMethod` is suppressed rather than satisfied. The function's whole job is to lay out a list of
 * independent option controls, each already its own composable and each gated on its own `show…` flag
 * so a host can offer a subset. Wrapping the list in another composable would hide what the screen
 * shows rather than shorten it, and the parameter list that wrapper needs is larger than the length it
 * would avoid.
 */
@Suppress("LongMethod")
@Composable
fun CalendarScreen(
    modifier: Modifier = Modifier,
    state: HijriCalendarState = rememberHijriCalendarState(
        initialMonth = HijrahYearMonth(1447, 9),
    ),
    dateDisplayMode: DateDisplayMode = DateDisplayMode.HIJRI_ONLY,
    onDateDisplayModeChange: (DateDisplayMode) -> Unit = {},
    primaryDate: PrimaryDate = PrimaryDate.HIJRI,
    onPrimaryDateChange: ((PrimaryDate) -> Unit)? = null,
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
    showWeekendPatternToggle: Boolean = false,
    onWeekendPatternChange: ((WeekendPattern) -> Unit)? = null,
    showCellBordersToggle: Boolean = false,
    onShowCellBordersChange: ((Boolean) -> Unit)? = null,
    showAutoSyncToggle: Boolean = false,
    autoSyncToWidget: Boolean = true,
    onAutoSyncToWidgetChange: ((Boolean) -> Unit)? = null,
    onSyncNow: (() -> Unit)? = null,
) {
    val effectiveLabels = labels ?: HijriCalendarLabels()
    val selectedDateText = rememberSelectedDateText(state, dateDisplayMode, primaryDate, effectiveLabels)

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
            primaryDate = primaryDate,
            modifier = Modifier.fillMaxWidth(),
            labels = effectiveLabels,
        )

        Spacer(modifier = Modifier.height(16.dp))

        DateDisplayModeSelector(
            selectedMode = dateDisplayMode,
            onModeSelected = onDateDisplayModeChange,
        )

        // Only offered under BOTH, and only when the caller wants it: a single-figure mode has
        // already said which date is primary, so the control would be a setting that does nothing.
        if (onPrimaryDateChange != null && dateDisplayMode == DateDisplayMode.BOTH) {
            Spacer(modifier = Modifier.height(16.dp))
            PrimaryDateSelector(
                selected = primaryDate,
                onSelect = onPrimaryDateChange,
            )
        }

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

        if (showCellBordersToggle) {
            Spacer(modifier = Modifier.height(16.dp))
            CellBordersSelector(
                showCellBorders = state.showCellBorders,
                onSelect = { show ->
                    state.setShowCellBorders(show)
                    onShowCellBordersChange?.invoke(show)
                },
            )
        }

        if (showWeekendPatternToggle) {
            Spacer(modifier = Modifier.height(16.dp))
            WeekendPatternSelector(
                selected = WeekendPattern.of(state.weekendDays),
                onSelect = { pattern ->
                    state.setWeekendDays(pattern.toWeekDays())
                    onWeekendPatternChange?.invoke(pattern)
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

        if (showAutoSyncToggle) {
            Spacer(modifier = Modifier.height(16.dp))
            WidgetSyncSelector(
                autoSyncToWidget = autoSyncToWidget,
                onAutoSyncToWidgetChange = onAutoSyncToWidgetChange,
                onSyncNow = onSyncNow,
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
@Suppress("CyclomaticComplexMethod")
private fun selectedDateSummary(
    state: HijriCalendarState,
    mode: DateDisplayMode,
    primaryDate: PrimaryDate,
    labels: HijriCalendarLabels,
): String? {
    val observed = state.selectedObservedDate
    val pakistan = state.selectedPakistanDate
    val plain = state.selectedDate

    // Era-appended (FD-05). This card is the one place both calendars' years appear side by side, so it
    // is where the ambiguity of `١٤٤٨` next to `2026` is most worth one extra glyph.
    val hijri = observed?.let {
        "${it.day} ${CalendarNames.englishHijriMonths[it.month - 1]} ${labels.hijriYearWithEra(it.year)}"
    }
        ?: pakistan?.let {
            "${it.day} ${HijrahMonth.entries[it.month - 1].name} ${labels.hijriYearWithEra(it.year)}"
        }
        ?: plain?.let { "${it.day} ${it.month.name} ${labels.hijriYearWithEra(it.year)}" }
    val gregorianDay = observed?.localDate ?: pakistan?.localDate ?: plain?.toLocalDate()
    val gregorian = gregorianDay?.let {
        val real = it.minus(state.adjustmentDays, DateTimeUnit.DAY)
        "${real.day} ${real.month.name} ${labels.gregorianYearWithEra(real.year)}"
    }

    if (hijri == null || gregorian == null) return null

    // The observance on the selected day (FD-08), appended to whichever date the mode shows. Resolved
    // here rather than in the grid because the grid has no business knowing about events, and this
    // card is the summary of the selection.
    //
    // Shown in **every** display mode: an observance is not a Gregorian figure, so hiding it behind
    // `HIJRI_ONLY` would make it unreachable in the default mode.
    //
    // Gated on the selection belonging to the displayed month, matching the header: the card keeps
    // naming the selected date as you navigate, but an observance must not attach itself to a month
    // the selected day is not in.
    val selectionMonth = observed?.let { HijrahYearMonth(it.year, it.month) }
        ?: pakistan?.let { HijrahYearMonth(it.year, it.month) }
        ?: plain?.yearMonth
    val event = if (selectionMonth == state.currentMonth) {
        state.selectedPakistanDate?.let { HijriEvents.forDate(it.month, it.day) }
            ?: state.selectedObservedDate?.let { HijriEvents.forDate(it.month, it.day) }
            ?: state.selectedDate?.let { HijriEvents.forDate(it.month.number, it.day) }
    } else {
        null
    }
    val eventLine = event?.let { labels.eventName(it) }?.takeIf { it.isNotEmpty() }

    val base = when (mode) {
        DateDisplayMode.HIJRI_ONLY -> hijri
        DateDisplayMode.GREGORIAN_ONLY -> gregorian
        // The card follows the cell's reading order, so the summary cannot disagree with the grid
        // about which date comes first. One `Text`, so "primary" here means order only; the bold
        // treatment is the cell's.
        DateDisplayMode.BOTH -> if (primaryDate == PrimaryDate.HIJRI) {
            "$hijri\n$gregorian"
        } else {
            "$gregorian\n$hijri"
        }
    }
    return if (eventLine == null) base else "$base\n$eventLine"
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

/**
 * Which of the two figures is the top, bold line under "Both".
 *
 * The order is a reading preference, not a calendar one: the grid keeps showing the same two dates
 * and a tap still selects the same day. Two chips rather than a switch so the labels name the
 * outcome — "Hijri on top" is what the user asked for, not "on/off".
 */
@Composable
private fun PrimaryDateSelector(
    selected: PrimaryDate,
    onSelect: (PrimaryDate) -> Unit,
) {
    val options = PrimaryDate.entries
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Primary date", style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, date ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    onClick = { onSelect(date) },
                    selected = selected == date,
                    label = { Text(if (date == PrimaryDate.HIJRI) "Hijri" else "Gregorian") },
                )
            }
        }
        Text(
            text = "The primary date is the top, bold line; the other one is drawn under it.",
            style = MaterialTheme.typography.bodySmall,
        )
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
    primaryDate: PrimaryDate,
    labels: HijriCalendarLabels,
): String? = remember(
    labels,
    state.selectedDate,
    state.selectedPakistanDate,
    state.selectedObservedDate,
    state.pakistanDates,
    state.currentMonth,
    dateDisplayMode,
    primaryDate,
    state.adjustmentDays,
) {
    selectedDateSummary(state, dateDisplayMode, primaryDate, labels)
}

/**
 * Show/hide the hairline between cells.
 *
 * Off by default, matching the widget. The in-app cell is already 48dp with a circle and a press state,
 * so a divider is a scanning aid rather than a necessity here — which is why it is opt-in.
 */
@Composable
private fun CellBordersSelector(
    showCellBorders: Boolean,
    onSelect: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Cell dividers", style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !showCellBorders,
                onClick = { onSelect(false) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) {
                Text("Hide")
            }
            SegmentedButton(
                selected = showCellBorders,
                onClick = { onSelect(true) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) {
                Text("Show")
            }
        }
    }
}

/**
 * Which days the grid shades as non-working days.
 *
 * The selection is read back out of [HijriCalendarState.weekendDays] through [WeekendPattern.of]
 * rather than held alongside it, so the selector cannot disagree with what the grid is painting — an
 * out-of-sync highlight is worse than no highlight. A `null` selection, meaning a set none of the four
 * patterns describes (a host that resolved its own), shows no chip selected rather than pretending.
 */
@Composable
private fun WeekendPatternSelector(
    selected: WeekendPattern?,
    onSelect: (WeekendPattern) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Weekend", style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            WeekendPattern.entries.forEachIndexed { index, pattern ->
                SegmentedButton(
                    selected = pattern == selected,
                    onClick = { onSelect(pattern) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = WeekendPattern.entries.size,
                    ),
                ) {
                    Text(pattern.displayLabel())
                }
            }
        }
    }
}

/**
 * The weekend chips.
 *
 * Short forms throughout, because five of them share one row: `Sun`, not `Sunday`. The widget
 * settings screen prints the same five, so the two screens cannot disagree about a pattern's name.
 */
private fun WeekendPattern.displayLabel(): String = when (this) {
    WeekendPattern.FRIDAY_SATURDAY -> "Fri+Sat"
    WeekendPattern.FRIDAY_SUNDAY -> "Fri+Sun"
    WeekendPattern.SUNDAY -> "Sun"
    WeekendPattern.FRIDAY_ONLY -> "Fri"
    WeekendPattern.NONE -> "None"
}

/**
 * Mirrors the in-app settings onto the placed widgets, and says what that means.
 *
 * The toggle exists because the mirror **overrides** the same options on a widget the user configured
 * individually — that is the point of the feature and also the thing worth switching off — so the
 * caption states it rather than leaving a switch whose side effect appears on the home screen.
 *
 * "Sync now" is deliberately ungated: it is the one action whose whole meaning is "do it anyway", so
 * a user who turned the automatic mirror off can still push this screen's settings once.
 */
@Composable
private fun WidgetSyncSelector(
    autoSyncToWidget: Boolean,
    onAutoSyncToWidgetChange: ((Boolean) -> Unit)?,
    onSyncNow: (() -> Unit)?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Home-screen widgets", style = MaterialTheme.typography.titleSmall)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Apply these settings to widgets automatically",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = autoSyncToWidget,
                onCheckedChange = onAutoSyncToWidgetChange,
                enabled = onAutoSyncToWidgetChange != null,
            )
        }
        Text(
            text = if (autoSyncToWidget) {
                "Widgets follow this screen, including ones you configured individually."
            } else {
                "Widgets keep their own settings. Use Sync now to copy this screen onto them once."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onSyncNow != null) {
            OutlinedButton(onClick = onSyncNow) {
                Text("Sync now")
            }
        }
    }
}
