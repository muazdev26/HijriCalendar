package com.muazdev.hijricalendar.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
public object HijriCalendarDefaults {
    public val TodayBorderWidth: Dp = 2.dp

    /**
     * 48dp, chosen to hold the two-line [DateDisplayMode.BOTH] stack at
     * [BothModeMaxFontScale]: `bodySmall` over `labelSmall` with no vertical gap comes to roughly
     * 31dp at scale 1, which is ~46dp at 1.5x and would overflow the box beyond that. This is the
     * constraint that makes the cell fixed, so it is stated rather than implied.
     */
    public val SingleLineCellSize: Dp = 48.dp

    @Deprecated("Cells use a single fixed size regardless of DateDisplayMode.")
    public val BothModeCellSize: Dp = SingleLineCellSize

    /**
     * Highest accessibility font scale the single-line day cell honours. `bodyMedium` is 14sp, so at
     * 2x its line box is ~40dp and still clears [SingleLineCellSize].
     */
    public const val SingleLineMaxFontScale: Float = 2.0f

    /**
     * Highest accessibility font scale [DateDisplayMode.BOTH] honours. Lower than
     * [SingleLineMaxFontScale] because the cell stacks two lines in a box sized for one; see
     * [SingleLineCellSize].
     */
    public const val BothModeMaxFontScale: Float = 1.5f

    /**
     * The cap for [dateDisplayMode]: the two-line mode fits less text in the same fixed cell.
     */
    public fun maxFontScaleFor(dateDisplayMode: DateDisplayMode): Float = when (dateDisplayMode) {
        DateDisplayMode.BOTH -> BothModeMaxFontScale
        DateDisplayMode.HIJRI_ONLY, DateDisplayMode.GREGORIAN_ONLY -> SingleLineMaxFontScale
    }

    @Composable
    public fun colors(
        selectedDayContainerColor: Color = MaterialTheme.colorScheme.primary,
        selectedDayContentColor: Color = MaterialTheme.colorScheme.onPrimary,
        todayBorderColor: Color = MaterialTheme.colorScheme.primary,
        todayBorderWidth: Dp = TodayBorderWidth,
        disabledDayContentColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        weekendDayContentColor: Color = MaterialTheme.colorScheme.error,
        dayContentColor: Color = MaterialTheme.colorScheme.onSurface,
        dayBackgroundColor: Color = Color.Transparent,
        headerContentColor: Color = MaterialTheme.colorScheme.onSurface,
        navigationIconColor: Color = MaterialTheme.colorScheme.onSurface,
        dayOfWeekLabelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        outsideMonthDayContentColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        gregorianDayContentColor: Color = dayContentColor.copy(alpha = 0.6f),
        gregorianHeaderColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    ): HijriCalendarColors = HijriCalendarColors(
        selectedDayContainerColor = selectedDayContainerColor,
        selectedDayContentColor = selectedDayContentColor,
        todayBorderColor = todayBorderColor,
        todayBorderWidth = todayBorderWidth,
        disabledDayContentColor = disabledDayContentColor,
        weekendDayContentColor = weekendDayContentColor,
        dayContentColor = dayContentColor,
        dayBackgroundColor = dayBackgroundColor,
        headerContentColor = headerContentColor,
        navigationIconColor = navigationIconColor,
        dayOfWeekLabelColor = dayOfWeekLabelColor,
        outsideMonthDayContentColor = outsideMonthDayContentColor,
        gregorianDayContentColor = gregorianDayContentColor,
        gregorianHeaderColor = gregorianHeaderColor,
    )

    public fun labels(labels: HijriCalendarLabels = HijriCalendarLabels()): HijriCalendarLabels = labels
}
