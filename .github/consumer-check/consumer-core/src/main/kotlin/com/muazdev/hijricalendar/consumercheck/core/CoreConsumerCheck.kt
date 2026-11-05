package com.muazdev.hijricalendar.consumercheck.core

import com.abdulrahman_b.hijrahdatetime.HijrahDate
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import com.muazdev.hijricalendar.core.CalendarDay
import com.muazdev.hijricalendar.core.CalendarEvent
import com.muazdev.hijricalendar.core.CalendarMonth
import com.muazdev.hijricalendar.core.HijriCalendarState
import com.muazdev.hijricalendar.core.HijriEvent
import com.muazdev.hijricalendar.core.HijriMonthLengths
import com.muazdev.hijricalendar.core.HijriMonthOverrides
import com.muazdev.hijricalendar.core.ObservedHijriDate
import com.muazdev.hijricalendar.core.PakistanEvent
import com.muazdev.hijricalendar.core.PakistanHijriCalendar
import com.muazdev.hijricalendar.core.PakistanHijriDate
import com.muazdev.hijricalendar.core.WeekDay
import kotlinx.datetime.LocalDate

/**
 * Consumer-resolution gate for `hijri-calendar-core`.
 *
 * This file exists to be compiled, not run. It references the public API of calendar-core so that
 * a scope mistake on any transitive dependency is caught here rather than in a consumer's build.
 */
@Suppress("unused")
object CoreConsumerCheck {

    fun stateApi() {
        val state = HijriCalendarState(
            initialMonth = HijrahYearMonth(1447, 9),
        )
        val month: CalendarMonth = state.calendarMonth
        val selected: HijrahDate? = state.selectedDate
        val weekStart: WeekDay = state.firstDayOfWeek
        val overrides: HijriMonthLengths = state.monthLengths
        state.pakistanDates
    }

    fun dayApi(day: CalendarDay) {
        val hijrah: HijrahDate? = day.hijrahDate
        val pakistan: PakistanHijriDate? = day.pakistanDate
        val observed: ObservedHijriDate? = day.observedDate
        val local: LocalDate = day.localDate
        val isToday: Boolean = day.isToday
        val isSelected: Boolean = day.isSelected
        val isCurrentMonth: Boolean = day.isCurrentMonth
        val isWeekend: Boolean = day.isWeekend
        val event: HijriEvent? = day.event
        val pakistanEvent: PakistanEvent? = day.pakistanEvent
        val notable: List<CalendarEvent> = day.notableEvents
        val hasNotable: Boolean = day.hasNotableEvent
    }

    fun monthApi(month: CalendarMonth) {
        val year: Int = month.year
        val days: List<CalendarDay> = month.days
        val firstDayOfWeek: WeekDay = month.firstDayOfWeek
        val gregorianRange: String = month.gregorianMonthRange
        val firstDay: LocalDate = month.gregorianFirstDay
        val lastDay: LocalDate = month.gregorianLastDay
    }

    fun overridesApi() {
        HijriMonthOverrides.setMonthLength(1448, 3, 30)
        HijriMonthOverrides.replaceAll(mapOf(Pair(1448, 3) to 30))
    }

    fun pakistanApi() {
        PakistanHijriCalendar.isWarmForCurrentOverrides()
    }
}
