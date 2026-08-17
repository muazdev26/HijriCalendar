package com.muazdev.hijricalendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import com.muazdev.hijricalendar.core.CalendarDay
import com.muazdev.hijricalendar.core.DateDisplayMode
import com.muazdev.hijricalendar.ui.util.calendarDayCell

@Composable
fun HijriCalendarDayCell(
    day: CalendarDay,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colors: HijriCalendarColors = HijriCalendarDefaults.colors(),
    useArabicIndicNumerals: Boolean = false,
    dateDisplayMode: DateDisplayMode = DateDisplayMode.HIJRI_ONLY,
    content: (@Composable () -> Unit)? = null,
) {
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

    val borderColor = when {
        day.isToday -> colors.todayBorderColor
        else -> Color.Transparent
    }

    val borderWidth = when {
        day.isToday -> colors.todayBorderWidth.dp
        else -> 0.dp
    }

    val enabled = !day.isDisabled

    val clickLabel = if (enabled) {
        "Day ${day.dayOfMonth}"
    } else {
        "Day ${day.dayOfMonth}, disabled"
    }

    Box(
        modifier = modifier
            .calendarDayCell()
            .semantics {
                contentDescription = clickLabel
                if (enabled) role = Role.Button
            }
            .clip(CircleShape)
            .background(backgroundColor)
            .border(borderWidth, borderColor, CircleShape)
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = clickLabel,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            )
            .padding(4.dp),
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
                    )
                }
                DateDisplayMode.GREGORIAN_ONLY -> {
                    Text(
                        text = gregorianText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                        textAlign = TextAlign.Center,
                    )
                }
                DateDisplayMode.BOTH -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = hijriText,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = gregorianText,
                            style = MaterialTheme.typography.labelSmall,
                            color = gregorianColor,
                            textAlign = TextAlign.Center,
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
