package com.muazdev.hijricalendar.shared

/**
 * Where the Desi date sits inside a day cell, once the calendar is switched on.
 *
 * Orthogonal to `DateDisplayMode` on purpose. The mode says *which* dates the cell draws; this says
 * what the second calendar does *to* those dates. One control therefore covers every combination a
 * reader can want — Gregorian + Desi is [UNDER_DATE] in `GREGORIAN_ONLY`, all three dates are
 * [UNDER_DATE] in `BOTH` — instead of a six-way list that would have to be re-cut for every mode the
 * library gains later.
 */
enum class DesiCellPlacement {
    /**
     * Under the date the mode would have drawn: one Desi line beneath Hijri, Gregorian, or the
     * Hijri-over-Gregorian pair.
     */
    UNDER_DATE,

    /**
     * Instead of it: the cell's figure becomes the Desi day and the gloss under it the
     * Desi month, so the grid reads as a Desi month rather than as a Hijri one with an
     * annotation.
     */
    INSTEAD_OF_DATE,
    ;

    /** The chip's label. Short, because three of them share one row. */
    val label: String
        get() = when (this) {
            UNDER_DATE -> "Under the date"
            INSTEAD_OF_DATE -> "Instead of it"
        }
}
