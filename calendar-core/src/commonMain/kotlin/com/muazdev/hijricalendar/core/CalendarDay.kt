package com.muazdev.hijricalendar.core

import androidx.compose.runtime.Immutable
import com.abdulrahman_b.hijrahdatetime.HijrahDate
import com.abdulrahman_b.hijrahdatetime.toLocalDate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class CalendarDay(
    val hijrahDate: HijrahDate? = null,
    val pakistanDate: PakistanHijriDate? = null,
    val observedDate: ObservedHijriDate? = null,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val isSelected: Boolean,
    val isDisabled: Boolean,
    val isWeekend: Boolean,
    val adjustmentDays: Int = 0,
) {
    /**
     * The day number of this cell in whatever date space it represents.
     *
     * Returns `0` for disabled placeholder cells where all date fields are null
     * (e.g. out-of-range Pakistan cells with `pakistanDate = null`).
     */
    val dayOfMonth: Int
        get() = observedDate?.day ?: hijrahDate?.day ?: pakistanDate?.day ?: 0

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
     *
     * For disabled placeholder cells where all date fields are null, returns today's
     * Gregorian date as a safe fallback.
     */
    val localDate: LocalDate
        get() {
            val base = when {
                observedDate != null -> observedDate.localDate
                hijrahDate != null -> hijrahDate.toLocalDate()
                pakistanDate != null -> pakistanDate.localDate
                else -> return kotlin.time.Clock.System.now()
                    .toLocalDateTime(TimeZone.currentSystemDefault()).date
            }
            return base.minus(adjustmentDays, DateTimeUnit.DAY)
        }
}
