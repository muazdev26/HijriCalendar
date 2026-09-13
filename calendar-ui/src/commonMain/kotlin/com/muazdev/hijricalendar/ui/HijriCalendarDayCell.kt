package com.muazdev.hijricalendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.muazdev.hijricalendar.core.CalendarDay
import com.muazdev.hijricalendar.core.DateDisplayMode
import com.muazdev.hijricalendar.ui.util.calendarDayCell
import com.muazdev.hijricalendar.ui.util.clickableIfEnabled

@Composable
fun HijriCalendarDayCell(
    day: CalendarDay,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: HijriCalendarColors = HijriCalendarDefaults.colors(),
    useArabicIndicNumerals: Boolean = false,
    dateDisplayMode: DateDisplayMode = DateDisplayMode.HIJRI_ONLY,
    dayCellSize: Dp? = null,
    labels: HijriCalendarLabels = HijriCalendarDefaults.labels(),
    content: (@Composable () -> Unit)? = null,
) {
    val cellSize = dayCellSize ?: HijriCalendarDefaults.SingleLineCellSize

    val hijriText = if (useArabicIndicNumerals) {
        day.dayOfMonth.toArabicIndicNumerals()
    } else {
        day.dayOfMonth.toString()
    }

    val gregorianText = day.localDate.day.toString()

    val contentColor = when {
        day.isSelected -> colors.selectedDayContentColor
        day.isDisabled -> colors.disabledDayContentColor
        !day.isCurrentMonth -> colors.outsideMonthDayContentColor
        day.isWeekend -> colors.weekendDayContentColor
        else -> colors.dayContentColor
    }

    val gregorianColor = when {
        day.isSelected -> colors.selectedDayContentColor.copy(alpha = 0.7f)
        !day.isCurrentMonth -> colors.outsideMonthDayContentColor.copy(alpha = 0.7f)
        else -> colors.gregorianDayContentColor
    }

    val backgroundColor = when {
        day.isSelected -> colors.selectedDayContainerColor
        else -> colors.dayBackgroundColor
    }

    val showTodayBorder = day.isToday && !day.isSelected

    val borderColor = when {
        day.isSelected -> colors.selectedDayContainerColor
        showTodayBorder -> colors.todayBorderColor
        else -> Color.Transparent
    }

    val borderWidth = when {
        day.isSelected -> HijriCalendarDefaults.TodayBorderWidth
        showTodayBorder -> colors.todayBorderWidth
        else -> 0.dp
    }

    val enabled = !day.isDisabled

    val clickLabel = remember(day, labels) { labels.dayContentDescription(day) }

    Box(
        modifier = modifier
            .calendarDayCell(cellSize)
            .semantics(mergeDescendants = true) {
                contentDescription = clickLabel
                if (enabled) role = Role.Button
            }
            .clip(CircleShape)
            .background(backgroundColor)
            .border(borderWidth, borderColor, CircleShape)
            .clickableIfEnabled(
                enabled = enabled,
                onClickLabel = clickLabel,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (content != null) {
            content()
        } else {
            when (dateDisplayMode) {
                DateDisplayMode.HIJRI_ONLY -> {
                    Text(
                        text = hijriText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                DateDisplayMode.GREGORIAN_ONLY -> {
                    Text(
                        text = gregorianText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                DateDisplayMode.BOTH -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                    ) {
                        Text(
                            text = hijriText,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                        )
                        Text(
                            text = gregorianText,
                            style = MaterialTheme.typography.labelSmall,
                            color = gregorianColor,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

internal fun Int.toArabicIndicNumerals(): String {
    val arabicIndicDigits = charArrayOf(
        '\u0660', // ٠
        '\u0661', // ١
        '\u0662', // ٢
        '\u0663', // ٣
        '\u0664', // ٤
        '\u0665', // ٥
        '\u0666', // ٦
        '\u0667', // ٧
        '\u0668', // ٨
        '\u0669', // ٩
    )
    return toString().map { char ->
        if (char.isDigit()) {
            arabicIndicDigits[char - '0']
        } else {
            char
        }
    }.joinToString("")
}
