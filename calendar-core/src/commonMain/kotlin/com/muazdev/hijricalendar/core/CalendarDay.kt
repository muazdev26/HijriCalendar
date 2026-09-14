package com.muazdev.hijricalendar.core

import androidx.compose.runtime.Immutable
import com.abdulrahman_b.hijrahdatetime.HijrahDate
import com.abdulrahman_b.hijrahdatetime.toLocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class CalendarDay(
    val hijrahDate: HijrahDate? = null,
    val pakistanDate: PakistanHijriDate? = null,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val isSelected: Boolean,
    val isDisabled: Boolean,
    val isWeekend: Boolean,
    val adjustmentDays: Int = 0,
) {
    val dayOfMonth: Int get() = hijrahDate?.day ?: pakistanDate!!.day

    /**
     * The weekday of the real-world day this cell represents, i.e. the grid column it
     * belongs to. With [adjustmentDays] != 0 this differs from `hijrahDate.dayOfWeek`.
     */
    val dayOfWeek: WeekDay get() = WeekDay.fromDayOfWeek(localDate.dayOfWeek)

    /**
     * The Gregorian day this cell represents in the real world.
     * With [adjustmentDays] != 0 this differs from `hijrahDate.toLocalDate()` by
     * exactly [adjustmentDays] days. In Pakistan mode it differs from
     * `pakistanDate.localDate` the same way, i.e. the observed `pakistanDate` of a
     * real-world day [G] is `gregorianToHijri(G + adjustmentDays)`.
     */
    val localDate: LocalDate
        get() = hijrahDate?.toLocalDate()?.minus(adjustmentDays, DateTimeUnit.DAY)
            ?: pakistanDate!!.localDate.minus(adjustmentDays, DateTimeUnit.DAY)
}
