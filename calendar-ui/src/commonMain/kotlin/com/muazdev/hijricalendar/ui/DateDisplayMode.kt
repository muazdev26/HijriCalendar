package com.muazdev.hijricalendar.ui

/**
 * How much of a date [HijriCalendar] shows in its day cells.
 *
 * Lives here rather than in `calendar-core` because it is a pure presentation switch: it decides
 * which labels a cell draws, and no core type consumes it. `calendar-core` holds date models and
 * the calendar state, so a rendering option there was a leak of the UI layer into the domain.
 *
 * **Persisted by name, not ordinal.** Both sample platforms store `valueOf(name)`, so reordering
 * or inserting an entry is safe — unlike `WeekDay.index`, which is a stored ordinal in the widget
 * options schema.
 */
public enum class DateDisplayMode {
    /** Hijri date only. */
    HIJRI_ONLY,

    /** Gregorian date only. */
    GREGORIAN_ONLY,

    /** Hijri date as the primary line with the Gregorian date beneath it. */
    BOTH,
}
