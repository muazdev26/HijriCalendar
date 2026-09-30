package com.muazdev.hijricalendar.core

import kotlinx.datetime.DayOfWeek

/**
 * A day of the week, ordered Saturday-first to match the Persian/Urdu week convention the
 * calendar renders by default.
 *
 * The **declaration order is a persisted wire format.** [index] is written into Android Glance
 * preferences (`WidgetOptions.firstDayOfWeekIndex`) and into the iOS app-group defaults, and it
 * is read back by [fromIndex]. Reordering or inserting an entry would silently reinterpret every
 * already-placed widget — the exact failure `WidgetOptionsJson` exists to prevent when it encodes
 * every *other* enum by name. `WeekDayOrdinalTest` pins the mapping so a reorder fails the build
 * rather than a user's home screen.
 */
public enum class WeekDay(
    /** Abbreviated label for a weekday header cell, e.g. `"Sat"`. English; localize by supplying
     *  `HijriCalendarLabels.weekdayShortName` or a `localizedWeekdayNames` list. */
    public val shortName: String,
    /** Unabbreviated English name, e.g. `"Saturday"`. Kept for accessibility labels and any
     *  surface too narrow for [shortName]. */
    public val fullName: String,
) {
    SATURDAY(shortName = "Sat", fullName = "Saturday"),
    SUNDAY(shortName = "Sun", fullName = "Sunday"),
    MONDAY(shortName = "Mon", fullName = "Monday"),
    TUESDAY(shortName = "Tue", fullName = "Tuesday"),
    WEDNESDAY(shortName = "Wed", fullName = "Wednesday"),
    THURSDAY(shortName = "Thu", fullName = "Thursday"),
    FRIDAY(shortName = "Fri", fullName = "Friday"),
    ;

    /**
     * Stable 0-based ordinal, used as the on-disk representation of a first-day-of-week
     * preference. Prefer [fromIndex] when reading it back, and never compute it from an unrelated
     * ordering (e.g. `kotlinx.datetime.DayOfWeek.ordinal`, which is Monday-first).
     *
     * The value is part of the persisted widget schema — see the class KDoc.
     */
    public val index: Int get() = ordinal

    public companion object {
        public val DEFAULT_FIRST_DAY: WeekDay = SATURDAY

        /** Default weekend days (Friday + Saturday), used when none are configured. */
        public val WEEKEND_DAYS: Set<WeekDay> = setOf(FRIDAY, SATURDAY)

        public fun fromDayOfWeek(dayOfWeek: DayOfWeek): WeekDay = when (dayOfWeek) {
            DayOfWeek.MONDAY -> MONDAY
            DayOfWeek.TUESDAY -> TUESDAY
            DayOfWeek.WEDNESDAY -> WEDNESDAY
            DayOfWeek.THURSDAY -> THURSDAY
            DayOfWeek.FRIDAY -> FRIDAY
            DayOfWeek.SATURDAY -> SATURDAY
            DayOfWeek.SUNDAY -> SUNDAY
        }

        /**
         * The [WeekDay] whose [index] is [index], or null when out of range.
         *
         * Use this instead of indexing [entries] directly so the intent is explicit and the
         * result is nullable — persisted values can be out of range, and callers have to decide
         * whether to coerce or reject.
         */
        public fun fromIndex(index: Int): WeekDay? = entries.getOrNull(index)
    }
}
