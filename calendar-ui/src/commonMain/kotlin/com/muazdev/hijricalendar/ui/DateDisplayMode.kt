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

    /**
     * Both dates in one cell, the [PrimaryDate] on top and the other beneath it.
     */
    BOTH,
}

/**
 * Which calendar's date is the **primary** line when [DateDisplayMode.BOTH] draws two.
 *
 * The primary line is the top one and is drawn **bold**; the other follows beneath it in the dimmer
 * secondary style. This is a *reading* preference, not a calendar one: it changes which date answers
 * "what day is it?" at a glance, never which dates exist or which one a tap selects.
 *
 * [DateDisplayMode.HIJRI_ONLY] and [DateDisplayMode.GREGORIAN_ONLY] draw a single figure, so this
 * value has no effect there — the mode already says which date is primary.
 *
 * Persisted **by name** like [DateDisplayMode]; no ordinal is stored anywhere.
 */
public enum class PrimaryDate {
    /** The Hijri figure on top, bold. The library's historical two-line order. */
    HIJRI,

    /** The Gregorian figure on top, bold, with the Hijri figure beneath it. */
    GREGORIAN,
}
