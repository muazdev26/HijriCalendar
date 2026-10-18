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
) {
    val fonts = HijriWidgetFonts.default
    val resolved = style ?: TextStyle()
    val fontSize = resolved.fontSize
    // The size check sits here rather than in [drawsAsScript] so the `fontSize == null` result is
    // visible to the compiler and the `.value` read below is on a non-null receiver.
    if (fontSize == null || fontSize.isUnspecified || !fonts.drawsAsScript(text, maxLines)) {
        Text(text = text, modifier = modifier, style = resolved, maxLines = maxLines)
        return
    }

    val context = LocalContext.current
    val metrics = context.resources.displayMetrics
    val colorArgb = resolved.color.getColor(context).toArgb()
    val sizePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, fontSize.value, metrics)
    val availableDp = maxWidthDp?.toFloat() ?: LocalSize.current.width.value
    val maxWidthPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, availableDp, metrics)
        .coerceAtLeast(1f)

    val bitmap = remember(text, colorArgb, sizePx, resolved.fontWeight, maxWidthPx) {
        rasterizeScriptText(
            context = context,
            text = text,
            sizePx = sizePx,
            colorArgb = colorArgb,
            bold = resolved.fontWeight == FontWeight.Bold,
            maxWidthPx = maxWidthPx,
        )
    }

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
 * pathological budget from shrinking a word into an illegible smudge; past it, ellipsis is the honest
 * answer, because a trimmed word still says which word it was.
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
 * [MIN_RASTER_SCALE]), so a slot narrower than its text is shrunk or, at the floor, trimmed rather
 * than allowed to overflow into whatever is beside it.
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
 * [inkColumnRange]. The crop is horizontal only: a bitmap's *height* is what puts a row of labels on
 * one baseline, so it stays the line box however little ink the string carries.
 */
private fun rasterizeScriptText(
    context: Context,
    text: String,
    sizePx: Float,
    colorArgb: Int,
    bold: Boolean,
    maxWidthPx: Float,
): Bitmap {
    val metrics = context.resources.displayMetrics
    val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
        this.textSize = sizePx
        this.color = colorArgb
        typeface = scriptTypeface(context)
        // Nastaliq ships no bold face — it is a single-weight script — so a bold slot synthesises the
        // weight rather than falling back to regular, which is what `Typeface.create(_, BOLD)` would
        // silently do.
        isFakeBoldText = bold
    }

    // `true` measurement, before any ellipsis: `measureText` reports the run's natural width, so the
    // scale factor is exact rather than a guess refined by rebuilding layouts in a loop. It measures
    // the whole string in the bundled face, which is the wide case — a caption that fits in
    // Nastaliq fits with the figures in the narrower system face too.
    val naturalWidth = paint.measureText(text)
    if (naturalWidth > maxWidthPx) {
        paint.textSize = (sizePx * (maxWidthPx / naturalWidth)).coerceAtLeast(sizePx * MIN_RASTER_SCALE)
    }

    val body = SpannableStringBuilder(text)
    val systemFace = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
    for (range in text.systemFaceRuns()) {
        body.setSpan(
            SystemFaceSpan(systemFace, fakeBold = false),
            range.first,
            range.last + 1,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }

    val layout = StaticLayout.Builder
        .obtain(body, 0, body.length, paint, maxWidthPx.toInt().coerceAtLeast(1))
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(1f, 0f)
        .setIncludePad(false)
        .setMaxLines(1)
        .setEllipsize(TextUtils.TruncateAt.END)
        .build()

    val left = layout.getLineLeft(0)
    val advanceWidth = ceil(layout.getLineRight(0) - left).toInt().coerceAtLeast(1)
    val height = layout.height.coerceAtLeast(1)

    // See the KDoc: one em of slack either side, then crop to what painted. The height is the full
    // line box in both bitmap and result, so a row of labels keeps one baseline.
    val margin = ceil(sizePx).toInt().coerceAtLeast(1)
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
    // load could) falls back to the advance box rather than to a 0-wide bitmap.
    val ink = inkColumnRange(scratch.width, scratch.height, pixels)
        ?: return Bitmap.createBitmap(scratch, margin, 0, advanceWidth, height)
    return Bitmap.createBitmap(scratch, ink.first, 0, ink.last - ink.first + 1, height)
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
