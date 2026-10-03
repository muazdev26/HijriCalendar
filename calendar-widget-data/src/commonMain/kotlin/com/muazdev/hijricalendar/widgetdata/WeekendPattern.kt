package com.muazdev.hijricalendar.widgetdata

import com.muazdev.hijricalendar.core.WeekDay
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * Which days the widget paints as non-working days — the red ones.
 *
 * ## Why an enum of patterns rather than a set of weekdays
 *
 * The obvious schema for this is `weekendDays: Set<WeekDay>`, and it must not be used. That is the
 * WD-05 hazard the wire format was introduced to remove, arriving again: [WeekDay] is **another
 * module's** enum whose own KDoc warns that its declaration order is persisted. A stored set of
 * ordinals would silently reinterpret itself the day someone inserted a `WeekDay` entry — no compile
 * error, no test failure, no log line, just a home screen showing the wrong days red.
 *
 * A name-backed enum owned by this module cannot have that problem: reordering *these* entries
 * changes nothing, because the wire format stores the name.
 *
 * It is also the right *shape*, not merely the safe one. The realistic conventions are four, and a
 * `Set<WeekDay>` would permit all 127 subsets — including several nobody wants a calendar to shade.
 *
 * ## Why four and not "any two days"
 *
 * Every entry below corresponds to a convention with a real audience:
 *
 * - [FRIDAY_SATURDAY] — Pakistan and the Gulf. Also the library's historical behaviour, and the
 *   reason the report this came from read as a bug: it looks like "two red days" next to a Google
 *   Calendar that shows only Sunday. It was never wrong for the audience it was written for.
 * - [SUNDAY] — most of the Christian world, and what a user comparing against Google Calendar expects.
 * - [FRIDAY_ONLY] — the Levant, and a user who works a Friday week-end.
 * - [NONE] — a secular or business calendar with no religious day off.
 *
 * ## Deliberately not here
 *
 * A Saturday–Sunday weekend. It is the most-requested combination anywhere else, and it is absent
 * because it is `SUNDAY` plus one more value in a schema that has to stay stable — adding it later is
 * additive and needs no migration, so there is no reason to hold the door open for a guess.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
public enum class WeekendPattern {
    /** Pakistan and the Gulf. The library's default and its historical behaviour. */
    @JsonNames("FRIDAY_SAT", "ISLAMIC")
    FRIDAY_SATURDAY,

    /** Most of the Christian world. */
    @JsonNames("SUN")
    SUNDAY,

    /** The Levant, and a user who works a Friday week-end. */
    @JsonNames("FRI")
    FRIDAY_ONLY,

    /** No shaded days. */
    @JsonNames("OFF", "NO_WEEKEND", "DISABLED")
    NONE,
    ;

    /**
     * The core weekdays this pattern paints.
     *
     * This is the single place the mapping exists, so the Android renderer, the in-app calendar and
     * the iOS renderer cannot each hand-roll their own `when`. [WeekDay.WEEKEND_DAYS] is what
     * [FRIDAY_SATURDAY] returns, which is what makes this a faithful description of the behaviour
     * being replaced rather than a reinterpretation of it.
     */
    public fun toWeekDays(): Set<WeekDay> = when (this) {
        FRIDAY_SATURDAY -> WeekDay.WEEKEND_DAYS
        SUNDAY -> setOf(WeekDay.SUNDAY)
        FRIDAY_ONLY -> setOf(WeekDay.FRIDAY)
        NONE -> emptySet()
    }

    /**
     * The pattern for an arbitrary set of weekdays.
     *
     * Returns `null` for a set this enum does not describe — a host that resolved its own set, or one
     * whose configuration came from somewhere else. It deliberately does **not** fall back to
     * [FRIDAY_SATURDAY]: a caller holding an unrecognised set wants to know, and silently shading
     * Friday and Saturday because the caller asked for something else is how a calendar starts lying
     * about which days matter.
     */
    public companion object {
        /** The pattern for [days], or `null` when it is not one of the four. */
        public fun of(days: Set<WeekDay>): WeekendPattern? =
            entries.firstOrNull { it.toWeekDays() == days }
    }
}
