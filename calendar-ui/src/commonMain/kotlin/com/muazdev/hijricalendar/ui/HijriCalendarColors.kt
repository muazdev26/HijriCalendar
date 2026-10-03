package com.muazdev.hijricalendar.ui

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * The calendar's colours and the one dimension it draws.
 *
 * Obtain one from [HijriCalendarDefaults.colors], which fills every field from
 * [androidx.compose.material3.MaterialTheme]. There is no no-argument constructor on purpose: the
 * 14 values are meant to track a Material colour scheme rather than be restated per call site.
 *
 * **One field is derived, not independent.** [gregorianDayContentColor] defaults to
 * `dayContentColor.copy(alpha = 0.6f)` (`HijriCalendarDefaults.colors`), so overriding only
 * `dayContentColor` also moves the Gregorian sub-label, and overriding both means the derivation no
 * longer holds. That is deliberate — the sub-label should track its parent — and it is the one
 * coupling a consumer is most likely to be surprised by.
 */
@Immutable
public data class HijriCalendarColors(
    /** Filled container of the selected day. */
    val selectedDayContainerColor: Color,

    /** Content of the selected day, on top of [selectedDayContainerColor]. */
    val selectedDayContentColor: Color,

    /** Border of today's cell, when it is not also the selected day. */
    val todayBorderColor: Color,

    /** Width of that border. [HijriCalendarDefaults.TodayBorderWidth] by default. */
    val todayBorderWidth: Dp,

    /** Content of a day outside [HijriCalendarState.minDate] / `maxDate`, or a placeholder cell. */
    val disabledDayContentColor: Color,

    /** Content of a day in [HijriCalendarState.weekendDays]. Presentational only. */
    val weekendDayContentColor: Color,
    /**
     * The hairline drawn between cells when [HijriCalendarState.showCellBorders] is on (FD-04).
     *
     * A field on this type rather than the widget's `widget_cell_border` resource: `calendar-ui` has no
     * `res/` and no `WidgetColors`, and everything visible here comes from `MaterialTheme` through
     * [HijriCalendarDefaults.colors]. A host can therefore match its own palette, which a hardcoded
     * resource would not allow.
     */
    val cellBorderColor: Color,

    /** Content of an ordinary in-month, enabled day. */
    val dayContentColor: Color,

    /** Filled background behind every cell. Transparent by default. */
    val dayBackgroundColor: Color,

    /** Content of the header's month title. */
    val headerContentColor: Color,

    /** Tint of the navigation arrows, dimmed automatically when their month is not navigable. */
    val navigationIconColor: Color,

    /** Content of the weekday column labels. */
    val dayOfWeekLabelColor: Color,

    /** Content of a leading or trailing cell belonging to the adjacent month. */
    val outsideMonthDayContentColor: Color,

    /**
     * Content of the Gregorian day number shown under the Hijri figure in
     * [DateDisplayMode.BOTH].
     *
     * Defaults to `dayContentColor.copy(alpha = 0.6f)`; see the type KDoc.
     */
    val gregorianDayContentColor: Color,

    /** Content of the header's Gregorian range line. */
    val gregorianHeaderColor: Color,
)
