package com.muazdev.hijricalendar.widget.glance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.MetricAffectingSpan
import android.util.TypedValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.isUnspecified
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlin.math.ceil

/**
 * Every text slot on every widget renders through [WidgetText], which is Glance's `Text` except for
 * the one case that cannot be: a slot the bundled Nastaliq face should draw.
 *
 * ## Why a rasteriser exists at all
 *
 * [HijriWidgetFonts]' KDoc is the long version. The short one: Glance's only font API is
 * `FontFamily(name)`, which becomes a `TypefaceSpan` resolved by the **launcher**, against the
 * *system* font map. A font bundled in this library is app-private and simply is not in that map, so
 * naming it silently falls back to the system face. The workaround is not a font API at all — draw
 * the text here, in this app's process, where the bundled font *is* visible, and send the launcher a
 * picture.
 *
 * That is the whole mechanism: `Typeface` from `res/font` → `StaticLayout` → `Bitmap` →
 * `Image(ImageProvider(bitmap))`.
 *
 * ## What pays for it
 *
 * A bitmap is not selectable, not re-laid-out, and not free: it is drawn on every composition that
 * changes text, size, colour or the available width, which is why the result is `remember`ed against
 * exactly those. It also loses the platform's own line-breaking, so [WidgetText] only takes this
 * path for a **single line** — every content slot on these widgets is one line, and the one wrapping
 * slot, [WidgetLocalization.ChromeLabels.monthUnavailable], stays on Glance's `Text`.
 *
 * `maxLines` therefore has **no default**, deliberately: the raster path is the only path that draws
 * the bundled face, so a slot that forgets to state its line count must not compile rather than
 * silently render in the system face. That quiet fallback is exactly how the grid's weekday row came
 * to render Nastaliq-less while every other Urdu slot on the widget drew the bundled face.
 */
@Composable
internal fun WidgetText(
    text: String,
    modifier: GlanceModifier = GlanceModifier,
    style: TextStyle? = null,
    maxLines: Int,
    maxWidthDp: Int? = null,
    /**
     * Whether the raster drops the empty band **above** its ink, i.e. whether the slot's height is its
     * ink plus the face's descent rather than the face's whole line box. See [trimScriptLeading]: the
     * grid's chrome opts in, the fitted typographies do not.
     */
    trimScriptLeading: Boolean = false,
    /**
     * Whether the raster is cropped to its ink **on all four sides**, and therefore whether the slot's
     * height is the ink itself rather than the face's line box.
     *
     * One caller: [WeekdayBand], whose whole height is its word's ink (see [WeekdayBandFit]). It is
     * also the one slot that must rasterise **whatever** the bundled face is set to, so `cropToInk`
     * *forces* the raster path and picks the face the host asked for — with `bundledUrduFont` off that
     * is the system face, laid out and drawn by the same `StaticLayout` code. Cropping is the only way
     * a band can look tight: Glance's padding is non-negative and its `TextStyle` has no `lineHeight`,
     * so the empty band above and below a glyph cannot be reclaimed by any layout nudge.
     *
     * Single-line only, like the rest of the raster path: a crop of a wrapped run would keep one ink
     * box of two. Every caller states `maxLines = 1`, and a slot that wraps never sets this.
     */
    cropToInk: Boolean = false,
) {
    val fonts = HijriWidgetFonts.default
    val resolved = style ?: TextStyle()
    // `null` covers both "no size stated" and `TextUnit.Unspecified`, so the raster path has one size
    // test to make and the compiler sees the non-null receiver the `.value` read below needs.
    val fontSize = resolved.fontSize?.takeUnless { it.isUnspecified }
    val bundledFace = fonts.drawsAsScript(text, maxLines)
    // A slot rasterises for one of two independent reasons: the host asked for the bundled face, or the
    // slot needs a crop only a bitmap can give it. The two flags are folded into the one value the
    // rasteriser and [inkCropRect] both read, so there is no way for the drawing and the height the
    // layout reserved to disagree about which crop ran.
    val crop = when {
        cropToInk -> ScriptCrop.INK
        trimScriptLeading -> ScriptCrop.TRIM_LEADING
        else -> ScriptCrop.BOX
    }
    val rasterises = bundledFace || crop != ScriptCrop.BOX
    if (fontSize == null || !rasterises) {
        Text(text = text, modifier = modifier, style = resolved, maxLines = maxLines)
        return
    }

    val context = LocalContext.current
    val metrics = context.resources.displayMetrics
    val colorArgb = resolved.color.getColor(context).toArgb()
    val availableDp = maxWidthDp?.toFloat() ?: LocalSize.current.width.value
    val run = ScriptRun(
        text = text,
        sizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, fontSize.value, metrics),
        colorArgb = colorArgb,
        bold = resolved.fontWeight == FontWeight.Bold,
        maxWidthPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, availableDp, metrics)
            .coerceAtLeast(1f),
        crop = crop,
        bundledFace = bundledFace,
    )

    val bitmap = remember(run) { rasterizeScriptText(context, run) }

    // The caller's modifier goes straight on the `Image`, with Glance's default `Fit` — and that is
    // safe, which is worth spelling out because it is not obvious. `glance_image_fit.xml` is a
    // `wrap_content`/`wrap_content` `ImageView` with `fitCenter`, and `defaultWeight()` is a
    // `WidthModifier(Dimension.Expand)`, not `Wrap`, so `translateEmittableImage` does *not* set
    // `adjustViewBounds` — the view's height stays the drawable's intrinsic height and `fitCenter`
    // resolves to `min(cellWidth / bitmapWidth, 1)`. The raster is the line's *ink* (see
    // [rasterizeScriptText]), so it can be a fraction of an em wider than the budget the run was
    // shrunk to, and where it is, `fitCenter` scales it down rather than cropping it — which is the
    // right failure. A forced *height* is the one thing that would break this: none of these slots
    // sets one, and none should.
    Image(
        provider = ImageProvider(bitmap),
        // The bitmap carries no text as far as the platform is concerned, so without this the widget
        // would be silent to a screen reader over its whole Urdu surface.
        contentDescription = text,
        modifier = modifier,
    )
}

/**
 * Whether a slot should be drawn as a bitmap by the bundled face rather than by Glance's `Text`.
 *
 * Two ways out, both cheap and both deliberate: a slot that is not single-line (the rasteriser cannot
 * wrap), and a slot with no Arabic *letters* — English chrome, a Gregorian month, and, importantly, a
 * figures-only slot whichever numeral style is chosen. Digits keep the system face, which is the right
 * face for them and leaves the grid's width-derived cell geometry exactly where it was; see
 * [hasArabicLetters], which is where that decision is made. The third condition — a specified,
 * non-`Unspecified` size — is checked at the call site, where the compiler can see it.
 *
 * The `maxLines == 1` conjunct is why [WidgetText] gives `maxLines` no default: the line count is not
 * a layout nicety here, it is half of whether the bundled face runs at all.
 */
internal fun HijriWidgetFonts.drawsAsScript(text: String, maxLines: Int): Boolean =
    bundledUrduFont && maxLines == 1 && text.hasArabicLetters()

/**
 * `true` when [this] carries Arabic-script **letters**, the trigger for the bundled face.
 *
 * Letters, not digits, and that is the point of the function existing rather than a range check
 * inline. An Arabic-Indic figure (`٤`, `۴`) is Arabic script by codepoint but is a *figure*: routing it
 * here would grow every day cell's content box from the digits' ~1.3em to Nastaliq's measured 2.5em,
 * in the one widget whose type is derived from width and whose rows are not fitted to their contents.
 * A digits-only string is left on Glance's `Text` for that reason, and the grid's geometry with it.
 */
internal fun String.hasArabicLetters(): Boolean = any { it.isArabicScriptLetter() }

/**
 * `true` when [this] is an Arabic-script **letter**, the only character kind the bundled face draws.
 *
 * The mirror of [hasArabicLetters]: an Arabic-Indic figure is Arabic by codepoint but is a *figure*,
 * so it is `false` here and lands in [systemFaceRuns] — which is what keeps the year out of a mixed
 * caption while the words around it stay Nastaliq.
 */
internal fun Char.isArabicScriptLetter(): Boolean {
    val code = this.code
    return when (code) {
        in 0x0600..0x06FF -> code !in 0x0660..0x0669 && code !in 0x06F0..0x06F9
        in 0x0750..0x077F -> true
        in 0x08A0..0x08FF -> true
        in 0xFB50..0xFDFF -> true
        in 0xFE70..0xFEFF -> true
        else -> false
    }
}

/**
 * The maximal ranges of [this] that are **not** an Arabic-script letter — figures of either numeral
 * style, Latin, punctuation, spaces.
 *
 * These are the runs [rasterizeScriptText] pins to the system typeface. The rule the host asked for
 * is blunt and worth honouring literally: *no digit glyph ever comes from the bundled Urdu face*.
 * A digits-only slot never reached the rasteriser anyway ([hasArabicLetters]), but the year inside
 * `محرم 1448 ھ` did, because one bitmap can only carry one face — so a caption's figures were Nastaliq
 * figures while an identical figure standing alone was not. Pinning the run fixes the inconsistency
 * without touching how the caption is laid out.
 */
internal fun String.systemFaceRuns(): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    var start: Int? = null
    for (index in indices) {
        val letter = this[index].isArabicScriptLetter()
        if (letter && start != null) {
            ranges.add(start until index)
            start = null
        } else if (!letter && start == null) {
            start = index
        }
    }
    if (start != null) {
        ranges.add(start until length)
    }
    return ranges
}

/**
 * The bundled face, or the default one when the resource cannot be read.
 *
 * `Resources.getFont` needs no `androidx.core` dependency and cannot fail for any reason this code
 * should crash on; the R8/AGP link step guarantees the entry is present. Returning `Typeface.DEFAULT`
 * on a `NotFoundException` is still better than a widget that renders nothing at all.
 */
private fun scriptTypeface(context: Context): Typeface =
    runCatching { context.resources.getFont(R.font.noto_nastaliq_urdu) }.getOrNull() ?: Typeface.DEFAULT

/**
 * How far the rasteriser may shrink a run to keep it inside its width budget before it gives up and
 * ellipsizes.
 *
 * The budget exists because the bundled face is **wider** than the system fallback at the same size,
 * and some slots are cells, not full-width lines — the grid's seven weekday labels are the case that
 * forces the issue. A tighter budget than the ukase of a width estimator, and shrinking text is
 * always better than letting two neighbouring weekday names run into each other. The floor stops a
 * pathological budget from shrinking a word into an illegible smudge. Past it the run is drawn at the
 * floor and allowed to overhang the budget: [rasterizeScriptText] lays the line out at its natural
 * width precisely so nothing is trimmed, and the parent's `fitCenter` scales the overhang back down.
 * That is the right failure — a slightly smaller word still says which word it was, while a word with
 * its last letter ellipsized (an Urdu Thursday is `جمعرات`) does not.
 */
private const val MIN_RASTER_SCALE: Float = 0.55f

/**
 * Replaces the bundled face with [typeface] for one run of the text being laid out.
 *
 * A [MetricAffectingSpan] rather than a [android.text.style.TypefaceSpan]: the two-argument
 * `TypefaceSpan(Typeface)` constructor is API 28 and the library's minSdk is 26, while a span of our
 * own needs only the two callbacks the layout already calls for every run — one for measuring the
 * line, one for drawing it. Both must be overridden: measuring decides the line box, and a typeface
 * applied only at draw time would leave digits sized against Nastaliq's metrics they do not use.
 */
private class SystemFaceSpan(
    private val typeface: Typeface,
    private val fakeBold: Boolean,
) : MetricAffectingSpan() {
    override fun updateDrawState(paint: TextPaint) = pin(paint)

    override fun updateMeasureState(paint: TextPaint) = pin(paint)

    private fun pin(paint: TextPaint) {
        paint.typeface = typeface
        // The base paint carries Nastaliq's *synthetic* bold (Nastaliq ships one weight). A run
        // pinned to a real bold face must drop it, or the figures get smeared by both.
        paint.isFakeBoldText = fakeBold
    }
}

/**
 * Draws [text] into a bitmap exactly as tall as its line box and no wider than [maxWidthPx].
 *
 * A `StaticLayout` rather than the obvious `Canvas.drawText`. `drawText` places glyphs in **logical**
 * order, which mirrors a script the string stores right-to-left; the layout applies the bidi
 * algorithm and resolves the paragraph direction from the first strong character — which, for every
 * string that reaches here, is an Arabic letter, so the paragraph is RTL and `محرم 1448 ھ` keeps its
 * reading order around the Western figure it contains.
 *
 * **Digits are never drawn with the bundled face.** Before the layout is built, every range from
 * [systemFaceRuns] is pinned to the system typeface with a [SystemFaceSpan], so the figures inside a
 * caption come from the same face as a figure standing alone. Doing that as a span instead of drawing
 * run by run is the whole point of keeping [StaticLayout]: bidi resolution, the ellipsis, and the
 * line box stay with the platform, where they are already correct.
 *
 * The run is measured before it is laid out and scaled down to meet [maxWidthPx] (to
 * [MIN_RASTER_SCALE]). A slot narrower than its text is therefore shrunk, and one the floor cannot
 * shrink far enough is drawn **whole** and left to overhang its budget: the layout is built at the
 * run's natural width so `TruncateAt.END` has nothing to trim, and the parent scales the overhang with
 * `fitCenter`. Trimming a letter off a word is the one outcome this must never produce.
 *
 * ## The bitmap is cropped to the ink, not to the advance box
 *
 * `includePad = false` drops the font's leading, and the height stays the full line box on purpose.
 * The **width** is the part that cannot be taken from the layout: `getLineLeft`/`getLineRight`
 * report the line's *advance*, and a Nastaliq run's ink regularly overhangs it — the `پیر` of a
 * weekday header measures **1.53 em of ink for 0.71 em of advance**, so a bitmap cut to the advance
 * silently loses a third of the word. That is not a hypothetical: it is what "in urdu Monday is not
 * properly displayed" was, and it hit exactly one of the seven weekday names, because every other
 * one measures 0.95-0.97 of its advance.
 *
 * So the line is drawn into a scratch bitmap padded by one em either side (one em is well past any
 * overhang the face produces) and the result is cropped to the columns that actually painted —
 * [inkColumnRange].
 *
 * ## The crop is horizontal always, and vertical when the caller asks
 *
 * The **width** is cropped for every slot. The height is the full line box by default, because a
 * bitmap's declared height is what puts a row of labels on one baseline: every label in a row shares
 * the face's ascent and descent, so equal boxes mean equal baselines. That is the right default for
 * the *fitted* typographies (the 1x1 tiles, the dual tile, the Today strip), whose stacks are sized
 * against `scriptLineRatio()` — the ratio *is* the arithmetic those layouts reserve with.
 *
 * It is the wrong answer for the **grid's chrome**, and [ScriptCrop.TRIM_LEADING] is the way out. The face's
 * declared line box is 2.5 em while a Nastaliq line's ink is usually far shorter — measured on a
 * device, the grid's Hijri title raster carried 28 px of empty band above its ink inside an 85 px box,
 * the Gregorian line 8 px, and the weekday names 12-43 px. Nothing in the grid is fitted to a ratio:
 * the rows are weighted, so every px of that empty band is a px the six day rows do not get, and on a
 * 4-row Urdu widget the chrome was taking 131 dp of 312 dp — 42% — leaving the two-line cells 26.9 dp
 * against the 29.3 dp they need. With the band kept, a Nastaliq grid cannot show a two-line month
 * title, a weekday row, a footer *and* a legible two-line cell at once.
 *
 * So [ScriptCrop.TRIM_LEADING] crops the bitmap's top to the first row that painted and leaves the bottom at the
 * layout's own descent. The bottom is the part that matters: `baseline + descent` is a *constant*
 * offset below the baseline for a given size, so every trimmed slot keeps its baseline at the same
 * distance from its own bottom edge. A row of labels that bottom-aligns therefore still shares one
 * baseline — the alignment moves from `CenterVertically` to `Alignment.Vertical.Bottom` at the call
 * site — while a stack of lines (the two title lines) simply comes together by the leading that was
 * removed. Nothing is clipped: the crop is computed from the pixels, so the ink is kept whatever its
 * extent.
 */
private fun rasterizeScriptText(context: Context, run: ScriptRun): Bitmap {
    val metrics = context.resources.displayMetrics
    val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        textSize = run.sizePx
        color = run.colorArgb
        typeface = if (run.bundledFace) {
            scriptTypeface(context)
        } else {
            Typeface.create(Typeface.DEFAULT, if (run.bold) Typeface.BOLD else Typeface.NORMAL)
        }
        // Nastaliq ships no bold face — it is a single-weight script — so a bold slot synthesises the
        // weight rather than falling back to regular, which is what `Typeface.create(_, BOLD)` would
        // silently do. A run in the *system* face has a real bold face to use, so it must not also be
        // smeared by a synthetic one.
        isFakeBoldText = run.bold && run.bundledFace
    }

    // `true` measurement, before any ellipsis: `measureText` reports the run's natural width, so the
    // scale factor is exact rather than a guess refined by rebuilding layouts in a loop. It measures
    // the whole string in the bundled face, which is the wide case — a caption that fits in
    // Nastaliq fits with the figures in the narrower system face too.
    val naturalWidth = paint.measureText(run.text)
    if (naturalWidth > run.maxWidthPx) {
        paint.textSize = (run.sizePx * (run.maxWidthPx / naturalWidth))
            .coerceAtLeast(run.sizePx * MIN_RASTER_SCALE)
    }

    val body = SpannableStringBuilder(run.text)
    val systemFace = if (run.bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
    for (range in run.text.systemFaceRuns()) {
        body.setSpan(
            SystemFaceSpan(systemFace, fakeBold = false),
            range.first,
            range.last + 1,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }

    // Laid out at the run's own natural width, **not** at `maxWidthPx`. `TruncateAt.END` then has
    // nothing to trim, which is deliberate: a run the floor could not shrink to its budget used to be
    // ellipsized right here, and on the bordered grid widget that silently ate the final `ت` of an
    // Urdu Thursday (`جمعرات`) — the one weekday whose ink overhangs its advance far enough to need the
    // floor at all. A run that overhangs is now drawn **whole**, and the parent scales it with
    // `fitCenter` (see [WidgetText]), which shrinks the word rather than amputating a letter from it.
    // `maxOf` over the two measurers because the spanned body pins its digit runs to the *narrower*
    // system face, so the plain `measureText` is the safe upper bound.
    val layoutWidth = ceil(
        maxOf(paint.measureText(run.text), Layout.getDesiredWidth(body, paint)),
    ).toInt().coerceAtLeast(1)

    val layout = StaticLayout.Builder
        .obtain(body, 0, body.length, paint, layoutWidth)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(1f, 0f)
        .setIncludePad(false)
        .setMaxLines(1)
        .setEllipsize(TextUtils.TruncateAt.END)
        .build()

    val left = layout.getLineLeft(0)
    val advanceWidth = ceil(layout.getLineRight(0) - left).toInt().coerceAtLeast(1)
    val height = layout.height.coerceAtLeast(1)

    // See the KDoc: one em of slack either side, then crop to what painted.
    val margin = ceil(run.sizePx).toInt().coerceAtLeast(1)
    val scratch = Bitmap.createBitmap(
        metrics,
        advanceWidth + margin * 2,
        height,
        Bitmap.Config.ARGB_8888,
    )
    Canvas(scratch).apply {
        // The canvas is translated by the line's own left edge, which is not zero once the RTL
        // paragraph is right-aligned inside `maxWidthPx`, plus the scratch bitmap's own slack.
        translate(margin - left, 0f)
        layout.draw(this)
    }

    val pixels = IntArray(scratch.width * scratch.height)
    scratch.getPixels(pixels, 0, scratch.width, 0, 0, scratch.width, scratch.height)
    // A run that painted nothing at all (an empty string cannot reach here, but a font that failed to
    // load could) falls back to the advance box rather than to a 0-sized bitmap.
    val ink = inkCropRect(
        width = scratch.width,
        height = scratch.height,
        pixels = pixels,
        crop = run.crop,
    ) ?: return Bitmap.createBitmap(scratch, margin, 0, advanceWidth, height)
    return Bitmap.createBitmap(scratch, ink.left, ink.top, ink.width, ink.height)
}

/**
 * One rasterised run: the words, how to set them, and the box to keep.
 *
 * A value rather than nine arguments, and not only for the parameter count: it is also the identity of
 * the resulting bitmap, so [WidgetText]'s `remember` is keyed on it directly. Two draws of the same word
 * at the same size, colour, weight, budget and crop share one bitmap, and a change to any of them
 * produces a new one — the property that keeps a rasterised slot from re-drawing on every composition.
 */
internal data class ScriptRun(
    val text: String,
    val sizePx: Float,
    val colorArgb: Int,
    val bold: Boolean,
    val maxWidthPx: Float,
    val crop: ScriptCrop,
    /**
     * Whether the bundled face is the base face. `false` is a slot that rasterises for its **crop**
     * rather than for its typeface — the weekday band, whose height must be its ink — and the system
     * face is then both the right face and the one the host chose.
     */
    val bundledFace: Boolean,
)

/**
 * Which box [rasterizeScriptText] keeps of its scratch bitmap.
 *
 * One value rather than a pair of booleans because the three modes are mutually exclusive and each
 * states a *contract* with the layout that reserved room for the slot: [BOX] is a line in a row,
 * [TRIM_LEADING] is the grid's chrome (height bought back from leading, baseline unmoved) and [INK] is
 * the weekday band (the slot *is* its ink). A bitmap can only satisfy one, and a caller that set two
 * flags would be stating contradictory contracts.
 */
internal enum class ScriptCrop {
    /** The face's whole line box, cropped horizontally to the ink. A row of labels shares one baseline. */
    BOX,

    /** [BOX] with the empty band above the ink dropped; the bottom stays the layout's descent. */
    TRIM_LEADING,

    /** Exactly the ink, on all four sides — [WeekdayBand]'s, whose whole height is its word. */
    INK,
}

/**
 * The box [rasterizeScriptText] keeps of its scratch bitmap, or `null` when nothing painted.
 *
 * Extracted from the rasteriser because the three crop modes are *policy*, not drawing, and this
 * module's unit tests run against the stubbed `android.jar` — a `Bitmap` cannot be built there, so a
 * rule worth pinning has to be reachable as a function over pixels. The modes are documented on
 * [ScriptCrop]; what they share is that the crop is always to the ink **horizontally**
 * ([inkColumnRange] has the overhang story) and differs only in which rows survive.
 */
internal data class InkCrop(val left: Int, val top: Int, val width: Int, val height: Int)

internal fun inkCropRect(
    width: Int,
    height: Int,
    pixels: IntArray,
    crop: ScriptCrop,
): InkCrop? {
    val columns = inkColumnRange(width, height, pixels)
    val rows = inkRowRange(width, height, pixels)
    return if (columns == null || rows == null) null else cropRect(columns, rows, height, crop)
}

/**
 * The one crop each [ScriptCrop] mode makes, given the buffer's ink — a single `InkCrop` per mode
 * rather than a branch per mode, so the three answers can be read against each other.
 *
 * The two vertical edges are chosen independently: the top is the ink or the box's own top, and the
 * bottom is the ink or the box's own bottom. `INK` takes both; `TRIM_LEADING` takes the top only,
 * which is what leaves a trimmed slot's baseline at a constant offset from its own bottom edge.
 */
private fun cropRect(columns: IntRange, rows: IntRange, height: Int, crop: ScriptCrop): InkCrop {
    val top = if (crop == ScriptCrop.BOX) 0 else rows.first
    val bottom = if (crop == ScriptCrop.INK) rows.last else height - 1
    return InkCrop(
        left = columns.first,
        top = top,
        width = columns.last - columns.first + 1,
        height = (bottom - top + 1).coerceAtMost(height - top),
    )
}

/**
 * The columns of a [width]×[height] `pixels` buffer (`Bitmap.getPixels` order, i.e. row-major) that
 * carry at least one non-transparent pixel, or `null` when nothing painted.
 *
 * The seam [rasterizeScriptText]'s crop is built on, extracted so the rule is testable without a
 * `Bitmap` — this module's unit tests run against the stubbed `android.jar` (see the file KDoc). The
 * alpha channel alone decides: an anti-aliased edge pixel is ink, and the colour is irrelevant.
 */
internal fun inkColumnRange(width: Int, height: Int, pixels: IntArray): IntRange? {
    var first = -1
    var last = -1
    for (x in 0 until width) {
        var painted = false
        for (y in 0 until height) {
            if (pixels[y * width + x] ushr 24 != 0) {
                painted = true
                break
            }
        }
        if (painted) {
            if (first < 0) first = x
            last = x
        }
    }
    return if (first < 0) null else first..last
}

/**
 * The rows of a [width]×[height] `pixels` buffer (`Bitmap.getPixels` order, i.e. row-major) that carry
 * at least one non-transparent pixel, or `null` when nothing painted.
 *
 * The same seam as [inkColumnRange] on the other axis, and the same decision rule — alpha alone, so an
 * anti-aliased edge counts and the colour does not. Only `first` is used by [rasterizeScriptText]'s
 * leading trim (the band *above* the ink is what is dropped), but the whole range is returned because
 * "what did this string actually paint" is the fact worth pinning, and a test that could only see the
 * top row would not notice a scan that stopped at the first painted row.
 */
internal fun inkRowRange(width: Int, height: Int, pixels: IntArray): IntRange? {
    var first = -1
    var last = -1
    for (y in 0 until height) {
        var painted = false
        for (x in 0 until width) {
            if (pixels[y * width + x] ushr 24 != 0) {
                painted = true
                break
            }
        }
        if (painted) {
            if (first < 0) first = y
            last = y
        }
    }
    return if (first < 0) null else first..last
}
