package com.muazdev.hijricalendar.widget.glance

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.PreviewSizeMode
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.muazdev.hijricalendar.widgetdata.TodayHijriWidgetData
import com.muazdev.hijricalendar.widgetdata.WidgetLanguage
import com.muazdev.hijricalendar.widgetdata.WidgetLocalization

/**
 * The **compact** dual-date tile: the weekday in the family accent band, then — centred, one under
 * another — the Hijri month name, a Gregorian row with the day and the month pushed to opposite ends,
 * the Hijri day as the tile's largest figure, and the weekday again as the last line.
 *
 * ```
 * ┌──────────────────┐
 * │     WEDNESDAY     │  weekday, in the family accent band
 * ├──────────────────┤
 * │       جمادى       │  Hijri month name — the month only, never the year
 * │  21 ······· أكتوبر│  Gregorian day and month, spread to the two ends
 * │        ٩         │  the Hijri day — the largest line in the tile
 * │      الأربعاء     │  the weekday, again, as the closing line
 * └──────────────────┘
 * ```
 *
 * ## Why the dates are stacked rather than side by side
 *
 * The previous revision put the Gregorian and Hijri halves in two weighted columns, each a figure over
 * a short month name. That is the denser arrangement, but it makes the reader compare two equal halves
 * to work out which is which. This revision answers a glance in one pass: the Hijri month titles the
 * body, the Gregorian date qualifies it, the Hijri figure is the one large number, and the weekday
 * closes the tile. The Hijri figure keeps the accent colour, as it does on every widget in the family.
 *
 * ## "The size of an app icon" — a fixed square inside the cell
 *
 * Android does not expose sub-cell widgets: a host grants a **whole number of cells**, and the
 * smallest an `AppWidgetProviderInfo` can ask for is one. What this tile therefore does is draw
 * **itself** at a fixed [CompactDateTileTypography.DRAWN_EDGE_DP] square and centre that square in
 * whatever cell it was granted, rather than filling the cell. A placed widget is a launcher cell wide
 * (~85dp on a phone), so the tile inside it reads at roughly one and a half app icons — and, unlike a
 * cell-filling design, it renders the *same* size on every host instead of growing with the grid.
 *
 * The one exception is a cell **smaller** than the square: the drawn edge then shrinks to the cell
 * (see [CompactDateTileTypography.drawnEdgeDp]), because a fixed 72dp box inside a 40dp grant would be
 * clipped rather than scaled.
 *
 * > **Four lines in a 72dp square is the tightest stack in the family.** The band plus three body lines
 * > leaves ~17dp per line, so the captions render around 7sp at the drawn edge and the figure around
 * > 16sp. That is what the geometry allows, not a tuning oversight: the fit rule below shrinks the
 * > whole stack together rather than letting any one line clip, and `logcat -s HijriWidgetRefresh`
 * > prints the sizes it settled on (`compact-tile-sizes`). The band is the one line that does *not* pay
 * > a line box for its height — it costs its ink ([WeekdayBandFit]) — which is what makes it the same
 * > legible word here as on the three larger tiles, and it is the only line whose height is *measured*
 * > per word ([rememberWeekdayInkEm]) rather than assumed, because on this tile that height competes
 * > directly with the two captions a reader is trying to read.
 *
 * The provider info asks for `minWidth/minHeight = 40dp`, `targetCellWidth/Height = 1`, and
 * `resizeMode="none"`, so the host picks exactly one cell and does not offer a resize that would turn
 * this into a second copy of the dual tile.
 *
 * No settings screen and no per-widget state: like the other tiles it follows the family options mirror
 * ([HijriWidgetConfig.loadFamily]). Tapping anywhere opens the app.
 */
public class HijriCompactDateWidget : GlanceAppWidget() {

    /**
     * `SizeMode.Exact`, not `Single`.
     *
     * `Single` hands the composition the provider's declared minimum (40dp), not the cell the launcher
     * granted. The tile draws at a fixed edge (see [CompactDateTileTypography.DRAWN_EDGE_DP]) and only
     * needs the granted size to know whether the cell is **smaller** than that edge, so `Single` would
     * also work — but `Exact` keeps the composition honest about the host it is on, and it is safe here
     * because the tile's only interaction is one `actionStartActivity`, a `PendingIntent` on the
     * `RemoteViews`; a tap fires the one attached to the displayed view, so per-size compositions
     * cannot double-launch the app.
     */
    override val sizeMode: SizeMode = SizeMode.Exact

    /**
     * The picker preview composes at the tile's drawn edge rather than the bare 40dp minimum, so the
     * picker shows the square a placed widget actually paints.
     */
    override val previewSizeMode: PreviewSizeMode = SizeMode.Responsive(
        setOf(COMPACT_DATE_TILE_PREVIEW_SIZE),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        HijriWidgetConfig.migrateLegacyIfNeeded(context, id)
        val peeked = HijriWidgetConfig.load(context, id)
        if (peeked.source.pakistan) {
            PakistanWarmUp.ensureWarm()
        }
        val openAction = actionStartActivity(openAppIntent(context))

        provideContent {
            val prefs = currentState<Preferences>()
            val rawOptions = HijriWidgetConfig.decodeOptions(prefs)
            val options = rawOptions ?: HijriWidgetConfig.loadFamily(context)
            val optionsFrom = if (rawOptions != null) "perWidget" else "family"
            HijriWidgetRefreshLog.d(
                "render:tile",
                "type=CompactDate overrides=${options.monthLengthOverrides.size} " +
                    "language=${options.language} optionsFrom=$optionsFrom",
            )
            val colors = WidgetColors.forTheme(options.theme)
            val today = HijriWidgetRenderCache.today(
                glanceId = id.toString(),
                options = options,
                todayEpochDay = HijriWidgetRefreshScheduler.todayEpochDay(),
            )
            CompactDateTileRoot(
                today = today,
                colors = colors,
                openAction = openAction,
                deviceRtl = computeDeviceLayoutRtl(context),
                language = options.language,
            )
        }
    }

    /**
     * Real picker preview for Android 15+: today's two dates in the family's current options,
     * non-interactive. [HijriWidgetPreviewPublisher] publishes the result.
     */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val options = HijriWidgetConfig.loadFamily(context)
        if (options.source.pakistan) {
            PakistanWarmUp.ensureWarm()
        }
        HijriWidgetRefreshLog.d(
            "render:tile",
            "type=CompactDate overrides=${options.monthLengthOverrides.size} " +
                "language=${options.language} optionsFrom=family",
        )
        val colors = WidgetColors.forTheme(options.theme)
        provideContent {
            val data = buildRenderData(
                context,
                options,
                viewedMonth = null,
                glanceId = "preview",
                optionsFrom = "family",
            )
            CompactDateTileRoot(
                today = data.todayHijri,
                colors = colors,
                openAction = null,
                deviceRtl = computeDeviceLayoutRtl(context),
                language = options.language,
            )
        }
    }
}

/**
 * The compact tile's type sizes, its drawn edge and vertical budget, as data so the Android 12-14
 * `previewLayout` mirror can be held to the same numbers the live composition uses.
 *
 * ## One scale over the drawn edge
 *
 * The tile is a stack of **four** lines — the band's weekday, the Gregorian row, the Hijri day and the
 * Hijri month — and the failure this family keeps answering is a resize that grows some lines while
 * leaving others behind. Every size here is therefore `reference * scale`, where the scale comes from
 * [widgetTypeScale] — the smaller of the width and height axes — so the tile either fits whole or
 * shrinks whole.
 *
 * Three sizes, not four: the two body captions (both Gregorian halves share one, and the Hijri month
 * uses it too) use [captionSizeAtReference], the figure has its own [daySize], and the band's word has
 * its own [weekdaySizeAtReference] — a share of the figure, so it stays proportional *and* legible
 * without inheriting a caption's small size. It is the same size within a scale, so the stack stays
 * predictable: a line growing on its own is still the per-line rule this family removed.
 *
 * ## The tile draws at a fixed 72dp, not at the cell
 *
 * The tile is centred in its cell at [DRAWN_EDGE_DP] (see [drawnEdgeDp]), so the scale is 1 on every
 * host that grants at least that much: a wider 1x1 does **not** grow the type, it only leaves more
 * clear space around the square. The one place the scale moves is a grant *below* the edge — the 40dp
 * declared minimum — where the whole stack shrinks together rather than being clipped.
 *
 * 72dp is therefore both the drawn edge and the sizing reference, and the reference sizes are chosen so
 * that the stack fills it at [TEXT_LINE_RATIO]: 10sp × 1 (the band's **worst-case** ink) + 2 × 9sp × 1.7
 * + 20sp × 1.3 + 5dp ≈ 72dp. A real weekday measures less than that worst case — 0.77em for `بدھ`
 * against the 2.1em reserved — and [rememberWeekdayInkEm] hands the difference to the body, so a placed
 * tile renders its captions *above* the reference scale instead of leaving a gap under the band. Sizing
 * the reference at the 40dp floor instead would make every real placement render well above scale 1 and
 * clamp at the ceiling, which is how the sibling tiles once ended up unable to grow.
 * The cap is also why a 1x1 on a five-column Pixel launcher — a 62x88dp cell — draws 62x62dp: the
 * square is never larger than the cell, and what is left of the cell stays transparent.
 *
 * ## Why the captions are measured with the ink ratio, not the declared line box
 *
 * The two body captions set `trimScriptLeading`, which drops the empty band above a raster's ink — see
 * [HijriWidgetFonts.scriptInkBandRatio]. Measuring them with [NASTALIQ_LINE_RATIO] (2.5 em) while they
 * paint ~2.1 em would under-fill the tile by ~16%; measuring them with the ink ratio is what a stack
 * this tight can afford. The day figure is digits, so it keeps [DIGIT_LINE_RATIO]. The band goes one
 * step further and costs its ink exactly, because neither a line box nor a trimmed box is the right
 * height for a single word: [WeekdayBandFit].
 */
internal object CompactDateTileTypography {

    /** The declared minimum, read by the test that records the budget. */
    const val TILE_MIN_HEIGHT_DP = 40
    const val TILE_MIN_WIDTH_DP = 40

    /** The cell the sizes are tuned against — a normal 1x1 on a phone launcher. */
    const val REFERENCE_WIDTH_DP: Float = 72f
    const val REFERENCE_HEIGHT_DP: Float = 72f

    /**
     * The edge the tile paints itself at, in dp — a fixed square centred in the granted cell.
     *
     * It is also the size the type is tuned against, and the picker's preview size, because both are
     * meant to show what a placed widget draws.
     */
    const val DRAWN_EDGE_DP: Float = REFERENCE_WIDTH_DP

    val REFERENCE_CELL: DpSize = DpSize(REFERENCE_WIDTH_DP.dp, REFERENCE_HEIGHT_DP.dp)

    /**
     * The square the tile actually paints, given the cell the host granted.
     *
     * [DRAWN_EDGE_DP] normally, and the cell's smaller side when the host granted less than that — a
     * fixed 72dp box inside a 40dp grant would be clipped rather than scaled, and a clipped month
     * caption is the failure the whole family is arranged to avoid. An unspecified axis (Glance's
     * `Dp.Unspecified`, a `NaN` dp) is ignored rather than compared, because a `minOf` against `NaN`
     * answers `NaN` and the tile would draw at no size at all.
     */
    fun drawnEdgeDp(grantedWidthDp: Float, grantedHeightDp: Float): Float {
        var edge = DRAWN_EDGE_DP
        if (!grantedWidthDp.isNaN()) edge = minOf(edge, grantedWidthDp)
        if (!grantedHeightDp.isNaN()) edge = minOf(edge, grantedHeightDp)
        return edge.coerceAtLeast(1f)
    }

    /** Clear space either side of the body. The band stays flush to the tile's edges. */
    const val HORIZONTAL_PADDING_DP = WidgetBand.HORIZONTAL_PADDING_DP

    /**
     * The size the tile's **two body captions** share: the Gregorian row and the Hijri month name.
     *
     * It used to be three, the weekday band included, and that is the band-legibility report: the
     * band's word rendered at 8sp of a 72dp tile — under a seventh of it — inside a band as tall as
     * that size's line box. The band now has its own size ([weekdaySizeAtReference]) and its own
     * height ([WeekdayBandFit]), so the tile's tightest stack gained a legible weekday *and* gave the
     * body back ~3dp: the band cost `8 × 1.7 + 5 = 18.6dp` of a 72dp square before, and costs
     * `10 × 1 + 5 = 15dp` now, with the word 25% larger.
     *
     * Reference 9sp, up from 8sp: the Gregorian date and the Hijri month are the two lines a reader
     * actually reads, and at a placed 62dp cell they rendered at ~5.7sp under the bundled face — a
     * report described the date as unreadable. What pays for the increase is the band: the compact
     * tile is the one fitted typography whose stack arithmetic sets its scale, and it now reserves the
     * word's **measured** ink ([rememberWeekdayInkEm]) rather than the face's worst case, which hands
     * the body back ~1.3em of height a typical weekday never uses.
     */
    val captionSizeAtReference = 9.sp

    /**
     * The Hijri day figure. The tile's largest line by construction — the reference design asks for
     * exactly that, and [CompactDateTileSizes.isDayDominant] is what pins it.
     */
    val daySize = 20.sp

    /**
     * The weekday band's type: half the figure, by the family-wide rule ([WeekdayBandFit]).
     *
     * The largest single gain in the report's fix, and it comes from the band no longer sharing the
     * caption scale *or* the line box: at the drawn 62dp square of a Pixel 6a the word went from
     * ~6.9sp in an 18.6dp band to ~9.1sp in a ~14dp band.
     */
    val weekdaySizeAtReference: TextUnit = WeekdayBandFit.sizeFor(daySize)

    /** The script lines the body stacks at [captionSizeAtReference]: the Gregorian row, the Hijri month. */
    const val CAPTION_LINES = 2

    val captionSizeCeiling = 12.sp
    val daySizeCeiling = 40.sp

    private val MAX_SCALE: Float = daySizeCeiling.value / daySize.value

    /**
     * The stack's height at scale 1 — the band's ink, the two body captions and the digit line.
     *
     * [bandInkEm] is the weekday word's own ink, in em, from [rememberWeekdayInkEm]; the default is
     * the face's worst case ([WeekdayBandFit.inkEmFor]), which is what a caller with no word to
     * measure — the static mirror, a test, a host that failed to load the face — has to assume.
     *
     * The band's ink is **not** `weekdaySize × scriptRatio`: that is a line box, and a band paints its
     * ink. Reserving the wrong one is what put a gap under the band on a Wednesday, when the word was
     * 0.77em and the face's worst case 2.1em.
     */
    fun referenceTextHeightDp(
        scriptRatio: Float,
        bandInkEm: Float = WeekdayBandFit.inkEmFor(scriptRatio),
    ): Float = WeekdayBandFit.inkHeightDp(weekdaySizeAtReference, bandInkEm) +
        captionSizeAtReference.value * scriptRatio * CAPTION_LINES +
        daySize.value * DIGIT_LINE_RATIO

    /** [referenceTextHeightDp] at the system script ratio and the worst-case band; the tests read this. */
    val REFERENCE_TEXT_HEIGHT_DP: Float = referenceTextHeightDp(TEXT_LINE_RATIO)

    /** The non-scaling part of the stack: the weekday band's padding. */
    val FIXED_HEIGHT_DP: Float = (WidgetBand.TOP_PADDING_DP + WidgetBand.BOTTOM_PADDING_DP).toFloat()

    /** The whole stack at scale 1, which is what a granted cell is measured against. */
    val REFERENCE_STACK_HEIGHT_DP: Float = REFERENCE_TEXT_HEIGHT_DP + FIXED_HEIGHT_DP

    fun sizesFor(
        availableWidthDp: Float,
        grantedHeightDp: Float,
        scriptRatio: Float = TEXT_LINE_RATIO,
        bandInkEm: Float = WeekdayBandFit.inkEmFor(scriptRatio),
    ): CompactDateTileSizes = sizesAtScale(
        widgetTypeScale(
            availableWidthDp = availableWidthDp,
            referenceWidthDp = REFERENCE_WIDTH_DP,
            availableHeightDp = grantedHeightDp,
            fixedHeightDp = FIXED_HEIGHT_DP,
            referenceTextHeightDp = referenceTextHeightDp(scriptRatio, bandInkEm),
            maxScale = MAX_SCALE,
        ),
        bandInkEm = bandInkEm,
    )

    /**
     * The projection for a caller that has a **drawn edge but no granted height** — the static
     * `previewLayout` mirror and the tests, which state a width and nothing else.
     *
     * Named apart from the two-axis overload deliberately: `sizesFor(62f, 62f)` would otherwise be
     * ambiguous between (width, height) and (width, band ink), and Kotlin resolves that in favour of
     * whichever it likes — silently. A band ink of `62` em is not a value any caller means.
     */
    fun sizesForDrawnEdge(
        availableWidthDp: Float,
        bandInkEm: Float = WeekdayBandFit.inkEmFor(TEXT_LINE_RATIO),
    ): CompactDateTileSizes = sizesAtScale(
        (availableWidthDp / REFERENCE_WIDTH_DP).coerceIn(MIN_TYPE_SCALE, MAX_SCALE),
        bandInkEm = bandInkEm,
    )

    private fun sizesAtScale(scale: Float, bandInkEm: Float): CompactDateTileSizes = CompactDateTileSizes(
        daySize = (daySize.value * scale).sp,
        captionSize = (captionSizeAtReference.value * scale)
            .coerceAtMost(captionSizeCeiling.value)
            .sp,
        weekdaySize = (weekdaySizeAtReference.value * scale)
            .coerceAtMost(WeekdayBandFit.MAX_SIZE.value)
            .sp,
        bandInkEm = bandInkEm,
    )

    fun verticalBudgetDp(
        availableWidthDp: Float,
        grantedHeightDp: Float,
        scriptRatio: Float = TEXT_LINE_RATIO,
        bandInkEm: Float = WeekdayBandFit.inkEmFor(scriptRatio),
    ): Float = sizesFor(availableWidthDp, grantedHeightDp, scriptRatio, bandInkEm)
        .requestedHeightDp(scriptRatio)
}

/** The type sizes a compact dual-date tile renders at, as one value. */
internal data class CompactDateTileSizes(
    /** The Hijri day figure — the tile's largest line. */
    val daySize: TextUnit,
    /** The caption size for the Gregorian row and the Hijri month. */
    val captionSize: TextUnit,
    /** The weekday band's type; see [CompactDateTileTypography.weekdaySizeAtReference]. */
    val weekdaySize: TextUnit = WeekdayBandFit.sizeFor(daySize),
    /**
     * The band's word ink, in em, as the layout reserved it — [weekdayInkEm] of the word actually
     * drawn, or the face's worst case when nothing could be measured.
     *
     * Carried rather than recomputed so [requestedHeightDp] states the same height the typography was
     * fitted to: a value that said "the band is a line box" while the renderer painted an ink crop is
     * exactly the disagreement that produced the gap under the band.
     */
    val bandInkEm: Float = WeekdayBandFit.inkEmFor(TEXT_LINE_RATIO),
) {
    /** The height the stack asks for: the band's ink, the two body captions and the digit line. */
    fun requestedHeightDp(scriptRatio: Float = TEXT_LINE_RATIO): Float =
        WeekdayBandFit.inkHeightDp(weekdaySize, bandInkEm) +
            captionSize.value * scriptRatio * CompactDateTileTypography.CAPTION_LINES +
            daySize.value * DIGIT_LINE_RATIO +
            WidgetBand.TOP_PADDING_DP +
            WidgetBand.BOTTOM_PADDING_DP
}

/**
 * The compact tile's layout, top to bottom: the weekday's accent band, a Gregorian row with the day and
 * the month at opposite ends, the Hijri day as the largest line, and the Hijri month name below it. The
 * weekday appears once, in the band. Tapping anywhere opens the app.
 *
 * ## The Gregorian row reads in the *widget's* direction
 *
 * "Day at one end, month at the other" is a reading-order instruction, so the day takes the reading-start
 * end: the right-hand end for an Urdu widget, the left-hand end for an English one. A Glance `Row` is a
 * plain horizontal `LinearLayout` that the platform mirrors on an RTL **device**, so the emission order
 * has to cancel that mirroring exactly when it is in play — which is the `deviceRtl XOR language.isRtl`
 * decision below, and the reason this differs from [HijriDualDateWidget]'s fixed physical Gregorian
 * order: there the halves are one phrase, here they are two labels at two ends.
 */
@Composable
internal fun CompactDateTileRoot(
    today: TodayHijriWidgetData?,
    colors: WidgetColors,
    openAction: Action?,
    deviceRtl: Boolean,
    language: WidgetLanguage,
) {
    val fonts = HijriWidgetFonts.default
    val cell = LocalSize.current

    // Exactly 72dp square, never larger, centred in whatever cell the host granted — and never bigger
    // than the cell, because a 72dp box inside a 63dp-wide grant would be clipped rather than scaled.
    val edgeDp = CompactDateTileTypography.drawnEdgeDp(cell.width.value, cell.height.value)
    val edge = edgeDp.dp

    val scriptRatio = fonts.scriptInkBandRatio()
    // The band's word is the one thing in this stack whose height cannot be derived from a ratio: a
    // weekday name's ink ranges from 0.77em (`بدھ`) to 1.82em (`منگل`) in the bundled face. Measuring
    // it is what keeps the reserve equal to the paint — reserving the face's worst case for a short
    // word is what left a gap under the band — and what hands the difference to the body, which is
    // where the Gregorian date and the month name gained their size.
    val measuredInkEm = rememberWeekdayInkEm(today?.weekdayTileName.orEmpty(), fonts.weekday)
    val bandInkEm = measuredInkEm.takeIf { it > 0f } ?: WeekdayBandFit.inkEmFor(scriptRatio)
    val sizes = CompactDateTileTypography.sizesFor(
        availableWidthDp = edgeDp,
        grantedHeightDp = edgeDp,
        scriptRatio = scriptRatio,
        bandInkEm = bandInkEm,
    )

    logSizes(edgeDp, scriptRatio, sizes)

    val cellModifier = GlanceModifier.fillMaxSize()
    val clickableCell = if (openAction != null) cellModifier.clickable(openAction) else cellModifier

    Box(modifier = clickableCell, contentAlignment = Alignment.Center) {
        Column(
            modifier = GlanceModifier.size(edge).background(colors.background),
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        ) {
            if (today == null) {
                CompactDateUnavailable(colors, sizes, language, GlanceModifier.defaultWeight())
                return@Column
            }

            WeekdayBand(
                text = today.weekdayTileName,
                weekdaySize = sizes.weekdaySize,
                colors = colors,
                fontFamily = fonts.weekday.toGlanceFontFamily(),
            )

            CompactDateBody(
                today = today,
                colors = colors,
                sizes = sizes,
                deviceRtl = deviceRtl,
                language = language,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }
}

/** The square's message when the projection has nothing for today — the one wrapping slot. */
@Composable
private fun CompactDateUnavailable(
    colors: WidgetColors,
    sizes: CompactDateTileSizes,
    language: WidgetLanguage,
    modifier: GlanceModifier,
) {
    Box(
        modifier = modifier
            .padding(horizontal = CompactDateTileTypography.HORIZONTAL_PADDING_DP.dp),
        contentAlignment = Alignment.Center,
    ) {
        WidgetText(
            // Resolved from the widget's own language, not the device locale (WG-12).
            text = WidgetLocalization.ChromeLabels.monthUnavailable(language),
            style = TextStyle(
                color = colors.secondaryText,
                fontSize = sizes.captionSize,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
    }
}

/**
 * The three body lines, centred in the height the band left — at the drawn edge they fill it exactly
 * (see the typography's reference arithmetic): the Gregorian row, the Hijri day as the
 * largest line, and the Hijri month name.
 *
 * **Centred, not bottom-pinned.** A bottom-pinned body pools every unspent dp above its first line,
 * which is precisely where a reader looks for the date: on a placed 62dp cell that was 9.5dp of space
 * under the band and the Gregorian row sitting well below it. The stack is fitted so that the residue
 * is ~0, and centring splits whatever is left instead of collecting it in one place. The band's own
 * reserve is the other half of the same defect ([rememberWeekdayInkEm]).
 */
@Composable
private fun CompactDateBody(
    today: TodayHijriWidgetData,
    colors: WidgetColors,
    sizes: CompactDateTileSizes,
    deviceRtl: Boolean,
    language: WidgetLanguage,
    modifier: GlanceModifier,
) {
    val fonts = HijriWidgetFonts.default

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = GlanceModifier.fillMaxWidth()
                .padding(horizontal = CompactDateTileTypography.HORIZONTAL_PADDING_DP.dp),
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        ) {
            // 1. The Gregorian date: the day and the month at opposite ends of one line.
            CompactGregorianRow(today, colors, sizes, deviceRtl, language)

            // 2. The Hijri day: the largest line in the tile, and the one figure in the family accent,
            // exactly as on every other widget.
            WidgetText(
                text = today.hijriDayText,
                style = TextStyle(
                    color = colors.accent,
                    fontSize = sizes.daySize,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fonts.dayNumber.toGlanceFontFamily(),
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )

            // 3. The Hijri month name — the month alone, below the Hijri date. The short form is the
            // projection's: it *is* the full name in Urdu and Arabic (those are never shortened) and
            // `Rabi' I` in English, which is what a 72dp line can hold.
            CompactCaption(
                text = today.hijriMonthShortName,
                color = colors.primaryText,
                size = sizes.captionSize,
                family = fonts.monthTitle.toGlanceFontFamily(),
                trimScriptLeading = true,
            )
        }
    }
}

/**
 * The Gregorian date as **one row with two labelled ends**: the day at the reading-start edge, the
 * month at the reading-trailing one.
 *
 * The two halves are split by *kind*, not by importance: the figures in the tile (this day and the
 * Hijri one below it) carry the family accent, and the two month names carry the primary tone — the
 * same rule every widget in the family follows, so one date in two calendars never reads as a title
 * with a footnote under it.
 *
 * Glance's `Row` has no spread arrangement, only a group alignment, so the two ends are made by giving
 * each child half the width and aligning its text to the outer edge. That alignment is **absolute**
 * (`Left`/`Right`, not `Start`/`End`, which resolve against the *device's* direction), so an Urdu
 * widget lays its day on the right even on an English phone; the emission order then cancels the
 * platform's own RTL mirroring. See [CompactDateTileRoot].
 */
@Composable
private fun CompactGregorianRow(
    today: TodayHijriWidgetData,
    colors: WidgetColors,
    sizes: CompactDateTileSizes,
    deviceRtl: Boolean,
    language: WidgetLanguage,
) {
    val fonts = HijriWidgetFonts.default
    val dayAlign = if (language.isRtl) TextAlign.Right else TextAlign.Left
    val monthAlign = if (language.isRtl) TextAlign.Left else TextAlign.Right

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        // Bottom-aligned, not centre-aligned: under the bundled Nastaliq face the month half is a
        // raster whose box ends at `baseline + descent`, while the day half is ordinary `Text` digits.
        // Centring those two boxes floats the digits ~0.6em above the script's baseline, which at this
        // size reads as two unrelated labels on two lines; sharing the box bottom leaves only the two
        // faces' descent difference between their baselines, which is a fraction of a line.
        verticalAlignment = Alignment.Vertical.Bottom,
    ) {
        val day: @Composable RowScope.() -> Unit = {
            CompactCaption(
                text = today.gregorianDayText,
                color = colors.accent,
                size = sizes.captionSize,
                family = fonts.dayNumber.toGlanceFontFamily(),
                modifier = GlanceModifier.defaultWeight(),
                textAlign = dayAlign,
            )
        }
        val month: @Composable RowScope.() -> Unit = {
            CompactCaption(
                text = today.gregorianMonthShortName,
                color = colors.primaryText,
                size = sizes.captionSize,
                family = fonts.gregorianTitle.toGlanceFontFamily(),
                modifier = GlanceModifier.defaultWeight(),
                textAlign = monthAlign,
                trimScriptLeading = true,
            )
        }
        // Emit the day first exactly when the platform will *not* mirror the row, so it always lands on
        // the widget's reading-start end.
        if (deviceRtl == language.isRtl) {
            day()
            month()
        } else {
            month()
            day()
        }
    }
}

/** One single-line caption of the tile, in the one size and weight its script lines share. */
@Composable
private fun CompactCaption(
    text: String,
    color: ColorProvider,
    size: TextUnit,
    family: FontFamily?,
    modifier: GlanceModifier = GlanceModifier,
    textAlign: TextAlign = TextAlign.Center,
    trimScriptLeading: Boolean = false,
) {
    WidgetText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size,
            fontWeight = FontWeight.Bold,
            fontFamily = family,
            textAlign = textAlign,
        ),
        maxLines = 1,
        trimScriptLeading = trimScriptLeading,
    )
}

/**
 * Reports the edge the tile drew at and the sizes derived from it — the difference between tuning this
 * tile's numbers and guessing at them, because every number above is conditional on a size the launcher
 * never tells the developer directly.
 */
private fun logSizes(edgeDp: Float, scriptRatio: Float, sizes: CompactDateTileSizes) {
    HijriWidgetRefreshLog.d(
        "compact-tile-sizes",
        "tile=${edgeDp.toInt()}dp band=${sizes.weekdaySize.value}sp " +
            "caption=${sizes.captionSize.value}sp day=${sizes.daySize.value}sp " +
            "lineRatio=$scriptRatio bandInk=${sizes.bandInkEm}em " +
            "budget=${sizes.requestedHeightDp(scriptRatio).toInt()}dp",
    )
}

/**
 * The picker/preview square: exactly the edge the tile paints, so a preview shows the square a placed
 * widget draws rather than advertising the cell around it.
 */
internal val COMPACT_DATE_TILE_PREVIEW_SIZE: DpSize =
    DpSize(CompactDateTileTypography.DRAWN_EDGE_DP.dp, CompactDateTileTypography.DRAWN_EDGE_DP.dp)

/**
 * The figure is the point of the tile, so no caption may ever exceed it.
 *
 * Stated next to the sizing rule that maintains it; `CompactDateTilePreviewLayoutTest` sweeps the size
 * range.
 */
internal fun CompactDateTileSizes.isDayDominant(): Boolean = daySize.value > captionSize.value
