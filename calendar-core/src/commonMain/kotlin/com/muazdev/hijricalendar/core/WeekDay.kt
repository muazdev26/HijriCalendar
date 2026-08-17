package com.muazdev.hijricalendar.core

import kotlinx.datetime.DayOfWeek

enum class WeekDay(
    val shortName: String,
    val fullName: String,
) {
    SATURDAY(shortName = "Sat", fullName = "Saturday"),
    SUNDAY(shortName = "Sun", fullName = "Sunday"),
    MONDAY(shortName = "Mon", fullName = "Monday"),
    TUESDAY(shortName = "Tue", fullName = "Tuesday"),
    WEDNESDAY(shortName = "Wed", fullName = "Wednesday"),
    THURSDAY(shortName = "Thu", fullName = "Thursday"),
    FRIDAY(shortName = "Fri", fullName = "Friday"),
    ;

    val index: Int get() = ordinal

    companion object {
        val DEFAULT_FIRST_DAY: WeekDay = SATURDAY

        fun fromDayOfWeek(dayOfWeek: DayOfWeek): WeekDay = when (dayOfWeek) {
            DayOfWeek.MONDAY -> MONDAY
            DayOfWeek.TUESDAY -> TUESDAY
            DayOfWeek.WEDNESDAY -> WEDNESDAY
            DayOfWeek.THURSDAY -> THURSDAY
            DayOfWeek.FRIDAY -> FRIDAY
            DayOfWeek.SATURDAY -> SATURDAY
            DayOfWeek.SUNDAY -> SUNDAY
        }
    }
}
