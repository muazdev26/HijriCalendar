package com.muazdev.hijricalendar.shared

import com.muazdev.hijricalendar.ui.DateDisplayMode

/**
 * What a day cell shows.
 *
 * One choice, not a stack of toggles: every option draws **at most two** dates, so a cell never
 * ends up with three lines fighting for the same 48dp. The Desi calendar is a peer of Hijri
 * and Gregorian here, not a layer on top of them — that is what used to produce the three-line cell.
 */
enum class CalendarView {
    HIJRI,
    GREGORIAN,
    NANAKSHAHI,
    HIJRI_AND_GREGORIAN,
    GREGORIAN_AND_NANAKSHAHI,
    HIJRI_AND_NANAKSHAHI,
    ;

    /** The library's own mode for the cell's primary figure. */
    val dateDisplayMode: DateDisplayMode
        get() = when (this) {
            HIJRI, NANAKSHAHI, HIJRI_AND_NANAKSHAHI -> DateDisplayMode.HIJRI_ONLY
            GREGORIAN, GREGORIAN_AND_NANAKSHAHI -> DateDisplayMode.GREGORIAN_ONLY
            HIJRI_AND_GREGORIAN -> DateDisplayMode.BOTH
        }

    /** Whether the Desi line is drawn at all. */
    val desiEnabled: Boolean
        get() = this in setOf(NANAKSHAHI, GREGORIAN_AND_NANAKSHAHI, HIJRI_AND_NANAKSHAHI)

    /** Whether the Desi line replaces the primary figure (single-calendar mode). */
    val desiReplaces: Boolean
        get() = this == NANAKSHAHI

    /** Map the legacy toggles onto a single view, for migration of old persisted state. */
    companion object {
        fun from(
            dateDisplayMode: DateDisplayMode,
            desiEnabled: Boolean,
            placement: DesiCellPlacement,
        ): CalendarView = when {
            !desiEnabled -> when (dateDisplayMode) {
                DateDisplayMode.HIJRI_ONLY -> HIJRI
                DateDisplayMode.GREGORIAN_ONLY -> GREGORIAN
                DateDisplayMode.BOTH -> HIJRI_AND_GREGORIAN
            }
            placement == DesiCellPlacement.INSTEAD_OF_DATE -> NANAKSHAHI
            else -> when (dateDisplayMode) {
                DateDisplayMode.HIJRI_ONLY -> HIJRI_AND_NANAKSHAHI
                DateDisplayMode.GREGORIAN_ONLY -> GREGORIAN_AND_NANAKSHAHI
                // Never stack three dates in one cell.
                DateDisplayMode.BOTH -> HIJRI_AND_GREGORIAN
            }
        }
    }

    val label: String
        get() = when (this) {
            HIJRI -> "Hijri"
            GREGORIAN -> "Gregorian"
            NANAKSHAHI -> "Desi"
            HIJRI_AND_GREGORIAN -> "Hijri + Gregorian"
            GREGORIAN_AND_NANAKSHAHI -> "Gregorian + Desi"
            HIJRI_AND_NANAKSHAHI -> "Hijri + Desi"
        }
}
