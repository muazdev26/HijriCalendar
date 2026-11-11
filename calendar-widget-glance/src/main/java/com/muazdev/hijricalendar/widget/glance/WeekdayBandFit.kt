package com.muazdev.hijricalendar.widget.glance

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * How large the weekday name is drawn in the family's accent band, on every widget that has one.
 *
 * ## The defect this answers
 *
 * "The day name when it is `بدھ` is showing very very small, unable to read." The word was legible in
 * size but not in *proportion*: each widget sized it from whatever line it happened to share — a
 * caption scale on the dual and compact tiles, a hand-picked 13 sp on the tiles and the strip — and
 * the band then took the whole font **line box** as its height. A line box is ascent plus descent
 * with most of it empty, so the band stood 1.7 em tall for ~0.75 em of ink: on a placed 1×1 the band
 * was 37% of the tile and the word was 22% of the band. A short word like `بدھ` sat in a third of the
 * tile as a smudge in the middle of a bright accent block.
 *
 * ## The two halves of the rule
 *
 * 1. **The size is a share of the figure beside it**, not of a caption: `weekday = figure × 0.6`.
 *    That is what makes the weekday the same object on all six widgets instead of 0.4 of the figure
 *    here and 0.46 there, and it is why the compact and dual tiles — the two that sized it from a
 *    caption — grew most. It also keeps the day figure the loudest line, which is the family's rule:
 *    the weekday never approaches it.
 * 2. **The band is as tall as the ink, not as tall as the line box**: `band = inkEm × size +
 *    paddings`. The word still paints its own ink, so filling the band costs the *body* nothing —
 *    distinguishing this from the round-3 change on the Today strip, which was the opposite trade
 *    (a smaller weekday to give the month captions their height back). Here the height the band gives
 *    up goes back to the body, and the weekday is larger besides.
 *
 * Both halves are pure functions so a test can state them, and both are read twice: by the fitted
 * typography, which reserves the band's height in its stack arithmetic, and by [WeekdayBand], which
 * draws it. The two cannot disagree because they call the same two functions.
 *
 * ## Why ink em rather than a stated band height
 *
 * [WidgetText] crops a band's word to its ink (see its `cropToInk`), so the band's *painted* height is
 * the word's ink whatever the font's box says. Reserving `inkEm × size` and letting the box overflow
 * into `heightIn`'s slack would be wrong twice: the ink would be clipped on the days whose ink is
 * taller than the reserve, and the stack's arithmetic would be sized against a number the renderer
 * does not use. The reserve is therefore the face's measured ink, and the drawn band is stated from
 * it.
 */
internal object WeekdayBandFit {

    /**
     * The weekday's size as a share of the widget's dominant figure.
     *
     * `0.6` — up from the `0.5` this shipped at, reported as "the day name bar is very small although
     * we have space in every widget". Half the figure was legible in proportion but not in *size*: at
     * a placed 1×1 it rendered ~11sp, and at the compact tile's drawn square ~8sp, while the band
     * above them had the whole width to itself and nothing else competing for it. Three fifths buys
     * the word roughly 20% in every direction on all six widgets and still leaves the figure it
     * belongs to clearly the loudest line — which is the one rule the share exists to protect.
     *
     * The four fitted typographies read this single constant, so the word cannot drift apart across
     * widgets that sit side by side on one home screen, and [WeekdayBandFitTest] holds each of them to
     * it at its own placed cell.
     */
    const val SHARE_OF_FIGURE: Float = 0.6f

    /**
     * The largest the weekday ever renders at, however large the widget.
     *
     * A ceiling at all — rather than only the share — because a 1×1 tile stretched to 3×3 is read at the
     * same distance as one at 1×1, and a weekday the size of a paragraph would compete with the figure
     * it belongs to. Read off the tiles' own ceiling: `SHARE_OF_FIGURE × [DateTileTypography.daySize]`'s
     * ceiling (64sp) is 38.4sp, and this is that rounded down. In practice it never binds a placed
     * widget — the dual and compact tiles' figures stop at 40sp (24sp words) and the Today strip's at
     * 40sp too — so it exists to bound what [sizeFor] may return for a figure larger than any this
     * family draws rather than to clip a real cell.
     */
    val MAX_SIZE: TextUnit = 38.sp

    /**
     * The weekday's size for a figure of [figureSize] — the whole of rule 1.
     */
    fun sizeFor(figureSize: TextUnit): TextUnit =
        (figureSize.value * SHARE_OF_FIGURE).coerceIn(0f, MAX_SIZE.value).sp

    /**
     * The ink em of the face that will draw the band, from the ratio the stack was measured with.
     *
     * The family threads exactly one face-selection value through every fitted typography —
     * `scriptRatio`, from [HijriWidgetFonts.scriptLineRatio] — and its only values are
     * [TEXT_LINE_RATIO] (system), [NASTALIQ_INK_BAND_RATIO] and [NASTALIQ_LINE_RATIO] (bundled). So a
     * threshold at the smaller of Nastaliq's two is a faithful reading of the same decision rather
     * than a second one, and it keeps every existing `sizesFor(..., scriptRatio)` signature intact.
     */
    fun inkEmFor(scriptRatio: Float): Float =
        if (scriptRatio >= NASTALIQ_INK_BAND_RATIO) NASTALIQ_INK_BAND_RATIO else SYSTEM_WEEKDAY_INK_EM

    /** The height of the band's ink for a word at [size], in dp. */
    fun inkHeightDp(size: TextUnit, inkEm: Float): Float = size.value * inkEm

    /**
     * The height the band draws at, in dp — its ink plus the two asymmetric paddings [WidgetBand]
     * carries. This is the number the fitted typographies reserve, so it is also the number
     * `requestedHeightDp` adds back.
     */
    fun bandHeightDp(size: TextUnit, inkEm: Float): Float =
        inkHeightDp(size, inkEm) + WidgetBand.TOP_PADDING_DP + WidgetBand.BOTTOM_PADDING_DP
}
