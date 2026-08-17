package com.muazdev.hijricalendar.core

import androidx.compose.runtime.Immutable
import com.abdulrahman_b.hijrahdatetime.HijrahDate
import com.abdulrahman_b.hijrahdatetime.toLocalDate
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class CalendarDay(
    val hijrahDate: HijrahDate,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val isSelected: Boolean,
    val isDisabled: Boolean,
    val isWeekend: Boolean,
) {
    val dayOfMonth: Int get() = hijrahDate.day

    val dayOfWeek: WeekDay get() = WeekDay.fromDayOfWeek(hijrahDate.dayOfWeek)

    val localDate: LocalDate get() = hijrahDate.toLocalDate()
}
