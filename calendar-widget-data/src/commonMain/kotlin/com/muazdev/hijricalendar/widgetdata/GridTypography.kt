package com.muazdev.hijricalendar.widgetdata

/**
 * The shared sizing rule for a Hijri month grid's two lines of text (FD-04).
 *
 * ## Why this is a function and not two constants
 *
 * A grid cell has a known size at render time — Glance reports it through `LocalSize`, Compose measures
 * it — but the type was fixed: **13sp** for the Hijri figure and **8sp** for the Gregorian day
 * underneath, at every widget size. So a user who resized the widget larger got more padding around the
 * same 8sp glyph.
 *
 * That 8sp is the actual defect. It is below the legibility floor for a secondary line, and against the
 * light background it is close to the minimum contrast ratio for small UI text. A user with a visual
 * impairment reads nothing there at all.
 *
 * ## The floor, and why the Gregorian line is only *partly* proportional
 *
 * Glance `Text` **clips rather than reflows**. A cell that has run out of vertical room loses the bottom
 * line first, and a clipped Gregorian day reads as `14` cut to `1` — a **wrong date**, which is worse
 * than a small one. So [gregorianSize] scales with the cell but is floored at [gregorianFloorSp], and
 * [fitsInCellHeight] is what a renderer checks before committing to the numbers.
 *
 * ## Plain `Float`s, in dp and sp
 *
 * Deliberately not `Dp`/`TextUnit`: `calendar-widget-data` has no Compose dependency, and adding one
 * for two data points would invert the module layering this whole rule exists to respect. Each renderer
 * converts at its own call site (`.dp`, `.sp`), which it has to do anyway.
 */
public object GridTypography {

    /** The smallest cell the rule is defined for, in dp. Matches a compact grid widget's cell. */
    public const val MINIMUM_CELL_SIZE_DP: Float = 26f

    /** The cell the sizes are calibrated at, in dp — roughly a `260×280` grid widget. */
    public const val REFERENCE_CELL_SIZE_DP: Float = 38f

    /**
     * The Hijri day figure's size, in sp, for a [cellSizeDp] cell.
     *
     * Scaled, so a wider widget gets a proportionally larger figure — which is the whole point of the
     * ticket. Bounded, because the figure is one of two lines in a cell that also has to fit its month
     * row underneath.
     */
    public fun hijriSizeSp(cellSizeDp: Float): Float =
        scale(13f, cellSizeDp, HIJRI_FLOOR_SP, HIJRI_CEILING_SP)

    /**
     * The Gregorian day figure's size, in sp, for a [cellSizeDp] cell.
     *
     * **Never below [GREGORIAN_FLOOR_SP]**, whatever the cell. This is the assertion that matters most in
     * the file: the old fixed 8sp is why the ticket exists, and a purely proportional rule would take it
     * *lower* on a small widget, which is the direction that made it illegible.
     */
    public fun gregorianSizeSp(cellSizeDp: Float): Float =
        scale(8.5f, cellSizeDp, GREGORIAN_FLOOR_SP, GREGORIAN_CEILING_SP)

    /** Never below this, however small the cell. The value the old fixed 8sp failed to be. */
    public const val GREGORIAN_FLOOR_SP: Float = 9f

    /** The Gregorian line's ceiling; it is the bottom line, so it is the one that gets clipped. */
    public const val GREGORIAN_CEILING_SP: Float = 11f

    /** The Hijri figure's bounds. */
    public const val HIJRI_FLOOR_SP: Float = 13f
    public const val HIJRI_CEILING_SP: Float = 17f

    /**
     * The share of a cell's height the two lines together may occupy.
     *
     * Not a font size: the check is that the **sum** fits, because Glance clips the bottom line and a
     * clipped Gregorian digit reads as a different date.
     *
     * 1.15 is a text line's rendered height as a multiple of its font size, once the font's own ascent
     * and descent are counted — the reason a `fontSize` alone never adds up to the space it occupies.
     */
    public const val LINE_HEIGHT_RATIO: Float = 1.15f

    /**
     * Whether [hijriSizeSp] and [gregorianSizeSp] together fit a cell of [cellSizeDp] dp.
     *
     * A renderer can check this and fall back rather than clip. It is exposed because the numbers alone
     * cannot answer it: the sum depends on the cell, and the failure mode is a wrong date rather than a
     * missing one.
     */
    public fun fitsInCellHeight(cellSizeDp: Float): Boolean =
        (hijriSizeSp(cellSizeDp) + gregorianSizeSp(cellSizeDp)) * LINE_HEIGHT_RATIO <= cellSizeDp

    private fun scale(atReference: Float, cellSizeDp: Float, floor: Float, ceiling: Float): Float =
        (atReference * cellSizeDp / REFERENCE_CELL_SIZE_DP).coerceIn(floor, ceiling)
}
