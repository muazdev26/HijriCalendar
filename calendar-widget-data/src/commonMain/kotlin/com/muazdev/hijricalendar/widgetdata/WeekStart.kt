package com.muazdev.hijricalendar.widgetdata

import com.muazdev.hijricalendar.core.WeekDay
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * The serialisable, name-backed form of the widget's first day of week.
 *
 * **Why this exists rather than an `Int`** (WD-05). `firstDayOfWeekIndex` was a bare ordinal into
 * [WeekDay] — *another module's* enum, in a published ABI — persisted in the shared wire format and
 * read back by three platforms. `WeekDay`'s own KDoc warns that its declaration order is persisted
 * and that inserting an entry would silently reinterpret every stored value. That is precisely the
 * hazard `WidgetOptionsJson` was introduced to remove ("enums by name, never by ordinal"), and this
 * one field had reintroduced it. A future `WeekDay` entry for a market-specific week start would
 * have re-aligned every placed widget's header row with no compile error, no test failure and no
 * log line.
 *
 * [WeekDay] is not itself serialisable — it is a core value type whose `index` exists for array
 * rotation — so this mirrors it rather than dragging it into the wire format. Keeping the two in
 * step is [WeekStart]'s companion's job and is pinned by a test.
 *
 * **Declaration order is still load-bearing in exactly one place:** [WidgetOptions]'s deprecated
 * `firstDayOfWeekIndex` accessor, which reports `dayOfWeek.index`. A test pins
 * `entries[i].dayOfWeek.index == i`, so a reorder fails the build rather than a home screen.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
public enum class WeekStart(
    /** The core weekday this corresponds to, for the projection's grid alignment. */
    public val dayOfWeek: WeekDay,
) {
    // `@JsonNames` aliases are permanent, not transitional: a stored widget outlives every release
    // that ever wrote it, so a spelling that was ever emitted must keep decoding forever (WD-06).
    // `SUNDAY_INDEX` is here because that is what the pre-`WeekStart` `firstDayOfWeekIndex` field
    // encoded to when a renderer wrote the value through a name-based helper by mistake.
    @JsonNames("SAT")
    SATURDAY(WeekDay.SATURDAY),

    @JsonNames("SUN")
    SUNDAY(WeekDay.SUNDAY),

    @JsonNames("MON")
    MONDAY(WeekDay.MONDAY),

    @JsonNames("TUE")
    TUESDAY(WeekDay.TUESDAY),

    @JsonNames("WED")
    WEDNESDAY(WeekDay.WEDNESDAY),

    @JsonNames("THU")
    THURSDAY(WeekDay.THURSDAY),

    @JsonNames("FRI")
    FRIDAY(WeekDay.FRIDAY),
    ;

    public companion object {
        /** [WeekDay.DEFAULT_FIRST_DAY] as a [WeekStart]; derived, so a core change moves with it. */
        public val DEFAULT: WeekStart = of(WeekDay.DEFAULT_FIRST_DAY)

        /** The [WeekStart] for [dayOfWeek]. */
        public fun of(dayOfWeek: WeekDay): WeekStart = fromIndex(dayOfWeek.index)

        /** The [WeekStart] at [index], or [DEFAULT] when out of range. */
        public fun fromIndex(index: Int): WeekStart = entries.getOrElse(index) { DEFAULT }
    }
}
