package com.muazdev.hijricalendar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.muazdev.hijricalendar.core.CalendarDay
import com.muazdev.hijricalendar.core.CalendarMonth

@Composable
internal fun HijriWeekRow(
    days: List<CalendarDay>,
    onDayClick: (CalendarDay) -> Unit,
    modifier: Modifier = Modifier,
    colors: HijriCalendarColors = HijriCalendarDefaults.colors(),
    useArabicIndicNumerals: Boolean = false,
    dateDisplayMode: DateDisplayMode = DateDisplayMode.HIJRI_ONLY,
    dayCellSize: Dp? = null,
    labels: HijriCalendarLabels = HijriCalendarDefaults.labels(),
    dayContent: (@Composable (CalendarDay) -> Unit)? = null,
    showAdjacentDays: Boolean = true,
    showCellBorders: Boolean = false,
    borderColor: Color = Color.Transparent,
    /** Which of the two figures is the top, bold line under [DateDisplayMode.BOTH]; see [PrimaryDate]. */
    primaryDate: PrimaryDate = PrimaryDate.HIJRI,
) {
    // Not a consumer-reachable crash any more: this composable is internal, so the only caller
    // chunks the grid's 42 cells. The check stays because a silent wrong row would be harder to
    // diagnose than a throw, but it can no longer be triggered from outside the module.
    require(days.size == CalendarMonth.DAYS_IN_WEEK) {
        "HijriWeekRow renders exactly one week; got ${days.size} days"
    }

    Row(modifier = modifier) {
        days.forEachIndexed { index, day ->
            val isLastColumn = index == days.lastIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    // The divider is on the *cell*, not the row: a rule drawn once per row would have
                    // to know the row's height, and `RowScope.weight` gives width, not height. The
                    // cell is a fixed-size Box, so a rule on its edge is exact.
                    .then(
                        if (showCellBorders && !isLastColumn) {
                            Modifier.drawWithContent {
                                drawContent()
                                // 1.dp, not `1f`. The horizontal rules are `Box(height(1.dp))` and
                                // the widget's `CellDivider` is `width(1.dp)`; a raw `1f` here is one
                                // *device pixel*, which at a 2.75x density is ~0.36dp. The verticals
                                // came out much fainter than the horizontals and rounded away entirely
                                // at some columns, which read as "the border is missing there". Same
                                // unit as every other rule in the family.
                                val strokePx = 1.dp.toPx()
                                // The *reading* trailing edge, not the physical right one. The last
                                // column is skipped so the grid has no outer border; under RTL the
                                // first cell is on the right and the skipped one is on the left, so
                                // the unpainted edge became an interior one — with a Saturday-first
                                // week that is Thursday|Friday, and the two cells read as one
                                // double-width cell. `layoutDirection` is the same signal `Row` used
                                // to place the cells.
                                val x = if (layoutDirection == LayoutDirection.Rtl) {
                                    strokePx / 2f
                                } else {
                                    size.width - strokePx / 2f
                                }
                                drawLine(
                                    color = borderColor,
                                    start = Offset(x, 0f),
                                    end = Offset(x, size.height),
                                    strokeWidth = strokePx,
                                )
                            }
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                HijriCalendarDayCell(
                    day = day,
                    onClick = { onDayClick(day) },
                    colors = colors,
                    useArabicIndicNumerals = useArabicIndicNumerals,
                    dateDisplayMode = dateDisplayMode,
                    dayCellSize = dayCellSize,
                    labels = labels,
                    primaryDate = primaryDate,
                    content = dayContent,
                    visible = showAdjacentDays || day.isCurrentMonth,
                    cellHasDividers = showCellBorders,
                )
            }
        }
    }
}
