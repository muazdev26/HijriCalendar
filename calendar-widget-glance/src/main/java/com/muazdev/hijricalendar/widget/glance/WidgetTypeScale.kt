package com.muazdev.hijricalendar.widget.glance

/**
 * The one type-scale rule every widget in the family sizes its lines with, and the two line-box
 * ratios it measures a stack against.
 *
 * ## Why a single scale, and why both axes
 *
 * A widget is resized in two dimensions the design does not control, and the failure reported on a
 * device is specific: some lines grew with the cell, others did not, and the lines that did not were
 * pushed off the bottom. The cause is per-line rules — a caption that tracks width, a figure that
 * tracks height, a band that is fixed — which each saturate at a different cell size. The remedy is
 * the opposite arrangement: **compute one scale from the granted cell, and derive every line from
 * it**. Then no line can outgrow another, and the whole stack either fits or does not scale at all.
 *
 * ## Why [minOf] of the two axes
 *
 * The stack must fit the height *and* every line must fit the width. The smaller of the two ratios is
 * the largest scale at which both hold, so it is the answer. A cell that is wide and short is bounded
 * by its height (the stacked lines must fit between the band and the bottom edge); a cell that is
 * tall and narrow is bounded by its width (the longest caption must not run past the edge).
 *
 * ## The floor and the ceiling
 *
 * [MIN_TYPE_SCALE] is a legibility floor, not a fit one: below it a line is a smudge at any size, so
 * the widget stops shrinking and accepts a little clipping rather than rendering unreadable text. It
 * is deliberately low enough that the declared 40dp minima never reach it, so every size a launcher
 * is allowed to grant still fits. [maxScale] is per-widget, derived from that widget's own figure
 * ceiling so a stretched widget still reads as a widget rather than as a clock.
 *
 * @param availableWidthDp the width left for text, i.e. the granted width less the widget's
 *   horizontal padding.
 * @param referenceWidthDp the text width the sizes are tuned against.
 * @param availableHeightDp the granted height, including the widget's own vertical chrome.
 * @param fixedHeightDp the part of the stack that does **not** scale — band padding and gaps. It is
 *   subtracted from the granted height before the division, because it is added back unscaled; dividing
 *   the whole height by the whole reference stack would make the request `scale * lines + fixed`, which
 *   is *above* the granted height for every cell below the reference and so clips exactly the cell the
 *   fit was meant to help.
 * @param referenceTextHeightDp the height of the scalable lines at scale 1.
 * @param maxScale the widget's own ceiling.
 */
internal fun widgetTypeScale(
    availableWidthDp: Float,
    referenceWidthDp: Float,
    availableHeightDp: Float,
    fixedHeightDp: Float,
    referenceTextHeightDp: Float,
    maxScale: Float,
): Float = minOf(
    availableWidthDp / referenceWidthDp,
    (availableHeightDp - fixedHeightDp) / referenceTextHeightDp,
).coerceIn(MIN_TYPE_SCALE, maxScale)

/**
 * The smallest scale any widget renders at, as a legibility floor rather than a fit bound.
 *
 * The declared minima in this family — 40dp for the 1x1 tiles, 72dp for the Today strip — are all
 * large enough that [widgetTypeScale] never reaches this for a cell a launcher may grant, so it only
 * ever applies to a cell smaller than any supported placement.
 */
internal const val MIN_TYPE_SCALE: Float = 0.3f

/**
 * A `Text`'s rendered line box as a multiple of its font size, for a line carrying **script text** —
 * a weekday name, a month name.
 *
 * Glance gives each `Text` a box of the font's natural ascent plus descent (there is no `lineHeight` on
 * `TextStyle`), and this family is designed to be set in Urdu Nastaliq, whose ascent and descent are
 * far taller than a Latin face's. The number is deliberately above the Latin ratio: sizing a stack
 * against the Latin ratio is what pushed the month caption off the bottom of the Today strip on a
 * Pixel 6a (an 11sp Nastaliq caption measured 31px of ink at 420dpi — see `docs/FEEDBACK-TODOS-2.md`).
 * An over-estimate costs a little empty space; an under-estimate clips a line.
 */
internal const val TEXT_LINE_RATIO: Float = 1.7f

/**
 * A `Text`'s rendered line box as a multiple of its font size, for a line carrying **digits** — a day
 * figure.
 *
 * Digits are drawn in a Latin-numeral face (or the same face's numerals), whose box is close to the
 * font's nominal line height, so modelling one at [TEXT_LINE_RATIO] would reserve a third of a
 * figure's height as empty space and shrink the whole stack to pay for it.
 */
internal const val DIGIT_LINE_RATIO: Float = 1.3f

/**
 * A script line's rendered box when the bundled Nastaliq face draws it, as a multiple of its font
 * size.
 *
 * **Measured from the font, not guessed.** `NotoNastaliqUrdu[wght].ttf` declares
 * `hhea`/`OS/2.sTypo` ascender 1904 and descender −596 over a 1000-unit em, and sets
 * `USE_TYPO_METRICS`, which is the pair Android's `Paint.fontMetrics` reports — so a line box is
 * `(1904 + 596) / 1000 = 2.5` em, not the 1.7 [TEXT_LINE_RATIO] models the *system* Naskh fallback
 * at. Sizing a Nastaliq stack at 1.7 under-reserves its height by ~47% and clips it: the same
 * failure mode, in the same direction, that [TEXT_LINE_RATIO] exists to avoid.
 *
 * So the ratio a widget's stack is measured with depends on which face will draw it — see
 * [scriptLineRatio]. Nastaliq's box is mostly the long descenders and tall ascenders of the script,
 * so the *glyphs* are not 47% bigger; the line simply needs a taller box, which is the same reason
 * Nastaliq is a poor fit for a dense grid.
 */
internal const val NASTALIQ_LINE_RATIO: Float = 2.5f
