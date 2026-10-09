package com.muazdev.hijricalendar.widget.glance

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.muazdev.hijricalendar.widgetdata.TodayHijriWidgetData
import com.muazdev.hijricalendar.widgetdata.WidgetLanguage
import com.muazdev.hijricalendar.widgetdata.WidgetLocalization

/**
 * A fixed 1x1 tile showing **today's Hijri and Gregorian dates together**: the weekday in a filled band,
 * the Hijri day as the tile's dominant figure at its centre, the Hijri month name in full beneath it, and
 * the Gregorian date on one compact line at the bottom.
 *
 * It exists because the other two 1x1 tiles each answer half the question — [HijriDateWidget] shows
 * the Hijri date, [GregorianDateWidget] the Gregorian one — and the only widget that showed both was
 * the resizable Today strip, which cannot be placed in a single cell.
 *
 * **Strictly 1x1 and not resizable**, which is the whole constraint on this layout. The widget-info
 * declares the same 40dp minimum as the other tiles and `resizeMode="none"`; `minHeight` is *not*
 * raised to make the content fit, because on launchers before Android 12 the cell count is derived
 * from `minWidth`/`minHeight`, so a 72dp minimum would make this **two** cells tall there. The strip
 * can afford that because it is resizable. A strict 1x1 cannot, so the type is sized to the cell the
 * launcher grants — see [DualDateTileTypography].
 *
 * No settings screen and no per-widget state: it follows the family options mirror
 * ([HijriWidgetConfig.loadFamily]) like the other two tiles. Tapping anywhere opens the app.
 */
public class HijriDualDateWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Same one-shot peek as the rest of the family: port legacy config (seeding the family mirror
        // once) and warm the Pakistan century table if the family now opts into it.
        HijriWidgetConfig.migrateLegacyIfNeeded(context, id)
        val options = HijriWidgetConfig.loadFamily(context)
        if (options.source.pakistan) {
            PakistanWarmUp.ensureWarm()
        }
        val colors = WidgetColors.forTheme(options.theme)
        val openAction = actionStartActivity(openAppIntent(context))

        provideContent {
            val today = HijriWidgetRenderCache.today(
                glanceId = id.toString(),
                options = options,
                todayEpochDay = HijriWidgetRefreshScheduler.todayEpochDay(),
            )
            DualDateTileRoot(
                today = today,
                colors = colors,
                openAction = openAction,
                deviceRtl = computeDeviceLayoutRtl(context),
                language = options.language,
            )
        }
    }

    /**
     * Real picker preview for Android 15+: today's Hijri + Gregorian date in the family's current
     * options, non-interactive. [HijriWidgetPreviewPublisher] publishes the result.
     *
     * Keeps `SizeMode.Single` as its `previewSizeMode`, i.e. the *real* 40dp minimum, rather than a
     * composed-at-a-realistic-size bucket like the grid and the strip use. This tile declares no
     * `minHeight` it can be stretched against — a 1x1 is a 1x1 — so a preview composed at anything
     * other than the minimum would be showing a size the picker cannot grant, which is exactly the
     * mismatch the responsive preview sizes exist to avoid elsewhere.
     */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val options = HijriWidgetConfig.loadFamily(context)
        if (options.source.pakistan) {
            PakistanWarmUp.ensureWarm()
        }
        val colors = WidgetColors.forTheme(options.theme)
        provideContent {
            val data = buildRenderData(context, options, viewedMonth = null)
            DualDateTileRoot(
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
 * The dual-date tile's type sizes and its vertical budget, as data so the Android 12-14
 * `previewLayout` mirror can be held to them — the same arrangement as [DateTileTypography] and
 * [TodayStripTypography].
 *
 * ## Four zones: fixed labels, flexing day figure
 *
 * The tile stacks a header band, a sub-row, the day figure and the weekday. Laid out as fixed line
 * boxes those four ask for more height than a 1x1 cell provides — and the obvious ways out are both
 * wrong: raising `minHeight` to fit (which makes the widget two cells tall before Android 12), or
 * shrinking every size until the Hijri day stops being the dominant figure.
 *
 * ## The model, and the mistake the first attempt at it made
 *
 * **This is [TodayStripTypography]'s model. The first version here got it wrong, and the way it was
 * wrong is worth recording because it looks right on paper.** It made all four zones **fractions of
 * the granted height**, taken from the reference tear-off page's proportions — band 27%, sub-row 12%,
 * day 31%, weekday 10%. Laid out as a table that reads like a considered design. Rendered on a real
 * 72dp cell it produced a **6sp weekday**: smaller than the icon next to it, and unreadable at
 * arm's length. Every zone shrank together, so a modest cell took the labels down with it.
 *
 * The strip's insight is the opposite, and it is the whole fix: **the small lines are fixed at a size
 * chosen for reading, and only the day figure flexes** into whatever height is left. A 72dp cell then
 * gets a 13sp weekday and a 20sp day figure instead of a 13sp weekday and a 6sp one; a 140dp cell
 * gets the same 13sp weekday and a 40sp figure.
 *
 * The consequence is honest and belongs here rather than in a device bug report: **the three labels
 * claim about 45dp before the day figure gets any**, so a cell well under a typical 1x1 cannot show
 * them at full size. Where they genuinely do not fit, [sizesFor] scales *everything* uniformly so
 * nothing clips — a legible-but-tight tile becomes an illegible-but-complete one, which is the right
 * way round. The declared 40dp minimum is in that regime and always has been; see
 * [theWidgetInfoIsAStrictSingleCell]'s counterpart in the test.
 *
 * ```
 * ┌────────────────────────┐
 * │  HEADER BAND          │  Hijri month, white on accent  (fixed)
 * ├────────────────────────┤
 * │  Greg month ·  Greg day│  small row, two ends           (fixed)
 * │       HIJRI DAY        │  dominant figure          (flexes)
 * │        weekday         │                              (fixed)
 * └────────────────────────┘
 * ```
 * ## The equal-margin requirement, and what this layout can and cannot promise
 *
 * The requirement FD-10 states is that the distance from the tile's top edge to the top of the header
 * band's text, and from the bottom of the weekday's glyphs to the bottom edge, are equal **to within
 * 1dp, for every supported language and numeral style, at every granted size**.
 *
 * **Glance 1.2.0 cannot deliver that, and no arrangement of Glance `Text`s can.** Each `Text` is
 * inflated as a `TextView` whose style sets only `ellipsize`, so the font's ascent-plus-descent
 * leading stays inside its box; `TextStyle` has no `lineHeight` parameter; and `PaddingModifier` is
 * non-negative, so the leading cannot be pushed out either. The only two routes that could measure
 * real glyph bounds are an `AndroidRemoteViews` layout (which removes font padding but keeps the
 * leading, so margins get *closer* to equal rather than provably equal) and a drawn `Bitmap` through
 * `Image` (which can measure, but costs a hand-written accessibility label).
 *
 * **What this layout does instead is make the insets equal at the level it controls, and put a number
 * on the residue.** [DualDateTileRoot] pins the header band flush against a top inset of
 * [EDGE_INSET_DP] and leaves the same [EDGE_INSET_DP] below the weekday, with the slack split evenly
 * between them by a centre-aligned weighted region. So the two insets are the *same constant* by
 * construction, and what remains is the per-line leading inside each `Text` box — which is a property
 * of the font and of whether the particular string has descenders, and which this module cannot
 * measure from a Glance composition.
 *
 * The honest form of the acceptance criterion is therefore: **the insets are equal by construction,
 * and the residual is the leading, bounded by [RESIDUAL_MARGIN_DP].** That is what
 * `DualDateTilePreviewLayoutTest` asserts.
 */
internal object DualDateTileTypography {

    /**
     * A `Text`'s rendered line box as a multiple of its font size.
     *
     * Glance gives each `Text` a box of the font's natural ascent plus descent, and every height
     * here is derived from an allocated share of the cell, so this ratio is what converts a share of
     * the height into a font size. It **understates** the box for Arabic faces, which is the reason
     * the residual margin below is a bound rather than a measurement.
     */
    const val LINE_HEIGHT_RATIO = 1.17f

    /**
     * The widget-info's declared minimum tile height, in dp. Read by the test that records the budget,
     * and the smallest height [sizesFor] is ever asked about.
     */
    const val TILE_MIN_HEIGHT_DP = 40

    /** The declared minimum tile width — and the narrowest a sub-row or weekday name can be drawn. */
    const val TILE_MIN_WIDTH_DP = 40

    /**
     * The height at which the sizes are the ones a 1x1 cell is normally granted, and the size the
     * Android 12-14 `previewLayout` mirror is rendered at.
     *
     * Not a measurement: there is no way to ask a launcher what it will grant, and launchers disagree
     * by tens of dp. It is the smallest of the typical 1x1 grants, chosen so the static mirror shows
     * the **conservative** end of what the live tile renders rather than an optimistic one.
     */
    const val TILE_REFERENCE_TEXT_WIDTH_DP: Float = DateTileTypography.WEEKDAY_REFERENCE_WIDTH_DP

    /** Horizontal clear space. The band itself stays flush to the tile's edges. */
    const val HORIZONTAL_PADDING_DP = 6

    /**
     * The band's extra height, in dp — added **below** its text, never above it.
     *
     * The band is grown by padding under the weekday, not by padding the box symmetrically, and the reason
     * is a reported defect. A line box's leading is asymmetric — more space below the baseline than above
     * it — so *symmetric* padding lands off-centre by the descent, which showed on a device as a stripe of
     * empty accent **above** the text. Padding below only keeps the space above the glyphs exactly as it
     * was and puts the added height where it reads as a header bar rather than as a gap.
     */
    const val BAND_PADDING_DP = 6

    /**
     * The gap between the tile's body lines, in dp.
     *
     * 1dp, matching the Today strip's [TodayStripTypography.lineGap]. Small on purpose: the request this
     * answers was for the Gregorian date to sit "without any large spaces", and a 1x1 has no height to
     * spend on breathing room — every dp here comes out of the ~99dp budget the four lines share.
     */
    val lineGap = 1.dp

    /**
     * The separator between the Gregorian month name and day, in this tile's own typography.
     *
     * **A literal, not a projected string**, and deliberately: it is punctuation rather than text, and a
     * dash reads the same in both supported languages — it does not move to the other side of a date. The
     * grid header already joins its two title halves with a literal `" · "` for the same reason, so this
     * follows existing practice rather than inventing a second one.
     *
     * What *must* come from the projection is the month name, the weekday and the numerals, because those
     * are language- and numeral-specific (WG-12). A separator is neither.
     */
    const val DATE_SEPARATOR = " - "

    // ── The type sizes ────────────────────────────────────────────────────────
    //
    // **These are the sibling tiles' own numbers, not a parallel set.** Every one below is read from
    // [DateTileTypography], the object `HijriDateWidget` and `GregorianDateWidget` already size
    // themselves with, and that is the whole design:
    //
    //   * the day figure is [DateTileTypography.daySize] — **the same 36sp** all three tiles draw, so a
    //     number on this tile is indistinguishable in weight from a number on either other one;
    //   * the header band's month name and the weekday line are both sized by
    //     [DateTileTypography.weekdaySizeFor], the rule the sibling tiles already use for both of their
    //     caption lines.
    //
    // ## The sizing *posture* is shared too, and it is the part that matters
    //
    // The sibling tiles ask for **~107–122dp of line box in a cell whose declared minimum is 40dp**, and
    // they accept clipping below that rather than shrink. [DateTileTypography]'s KDoc records why: a
    // band layout that sized each line from its own share of the granted height was tried, and reverted,
    // because holding the captions to a quarter of the cell cost ~10sp of their size.
    //
    // An earlier version of *this* tile took the opposite posture — it shrank the whole tile to fit
    // whatever it was granted — and that is why it rendered a 6sp weekday and then, when its sizes were
    // raised, a 12.9sp one. Fitting the content is not what this family does, and a four-zone tile has
    // less to give than a three-zone one.
    //
    // So this tile sizes for the cell a launcher actually grants and overflows below that, and
    // `DualDateTilePreviewLayoutTest` records the budget instead of pretending to meet the minimum.

    /**
     * The Hijri day figure, in the **centre** of the tile under the band.
     *
     * 30sp, which is `DateTileTypography.daySize` (36sp) less a sixth. The reduction is what lets the four
     * lines coexist: the sibling tiles carry two caption lines at 32sp against a 36sp figure and already
     * accept clipping on a short cell, while this tile has four lines rather than three.
     *
     * **It is still the dominant figure**, which is the property that matters and the one [isDayDominant]
     * guards: every caption in this layout is [CAPTION_RATIO] of the sibling tiles' own rule against it
     * and clamped below it, so nothing can overtake it at any width.
     */
    val daySize = 22.sp

    /**
     * The scale the captions are drawn at, as a fraction of the sibling tiles' name-sizing rule.
     *
     * 0.6, and it is a **scale** rather than a fixed size so the captions still widen with the tile the
     * way the sibling tiles' do — the rule is borrowed whole and dialled back, not replaced by a constant
     * that would stop responding to the granted width.
     *
     * It is also what keeps the weekday under the band day. Unscaled, the sibling rule returns 32sp at a
     * 72dp text width, which would have made the **weekday larger than the Hijri day** and inverted the
     * tile.
     */
    const val CAPTION_RATIO = 0.7f

    /**
     * The largest a caption may be, as a share of [daySize] — so the figure always dominates.
     *
     * `internal` rather than `private` because the test asserts the clamp's meaning and must not restate
     * the number: a private constant that a test duplicates is a second source of truth, and restating it
     * here is exactly how a caption clamp came to be 0.7 in the test while the tile said 0.8 — which fails
     * as a plausible-looking assertion about a *different* number.
     */
    internal const val CAPTION_MAX_SHARE = 0.8f

    /**
     * The Hijri month name, alone in the band.
     *
     * Borrowed from [DateTileTypography.weekdaySizeFor] like every other name here, then scaled by
     * [CAPTION_RATIO] and clamped between the sibling tiles' caption floor and [CAPTION_MAX_SHARE]
     * of the figure. The two clamps are what keep the hierarchy true at both ends of the size range: a
     * narrow cell would otherwise render the caption too small to read, and a wide one too large to be a
     * caption under a figure.
     */
    fun monthSizeFor(availableWidth: Float): TextUnit = captionSizeFor(availableWidth)

    /**
     * The weekday, last, at the same size as the band's caption.
     *
     * Both are captions on a page whose figure is [daySize], so they share one rule and come out the
     * same size — the same reasoning that gives the sibling tiles two identical caption lines.
     */
    fun weekdaySizeFor(availableWidth: Float): TextUnit = captionSizeFor(availableWidth)

    /**
     * The Gregorian month and day in the sub-row, at opposite ends.
     *
     * A **caption**, and deliberately the smallest line on the tile: the two ends are one date split by
     * the page fold, and the Hijri day in the band is the figure this tile exists to show. A Gregorian day
     * at equal weight would put two dominant numbers on the tile — the failure FD-05's removal of the
     * year already avoided on the other two tiles.
     */
    val subRowSize = 16.sp

    /**
     * The smallest a caption may be at a narrow tile, in sp.
     *
     * [DateTileTypography.weekdaySizeFloor]'s floor, the one the sibling tiles hold their captions to, so
     * no line here can be smaller than the smallest line on any other 1x1 tile in the family.
     */
    val subRowSizeFloor = DateTileTypography.weekdaySizeFloor

    /** The caption rule: the sibling tiles' own, scaled down and clamped. See [CAPTION_RATIO]. */
    private fun captionSizeFor(availableWidth: Float): TextUnit =
        (DateTileTypography.weekdaySizeFor(availableWidth).value * CAPTION_RATIO)
            .coerceIn(
                DateTileTypography.weekdaySizeFloor.value,
                daySize.value * CAPTION_MAX_SHARE,
            ).sp

    /**
     * The four type sizes for a tile [availableWidth] wide.
     *
     * **Width, not height** — because that is the axis the sibling tiles size on, and the axis a fixed 1x1
     * actually varies along. `SizeMode.Single` still reports a real size and launchers disagree on how wide
     * a 1x1 is by tens of dp, so a name sized for one reads as a caption on another. The band day is fixed
     * because it is not the length-sensitive one.
     *
     * No height term at all, deliberately: a four-line tile has no vertical room to spare, so letting the
     * height shrink anything would immediately take size off the text — which is what made two earlier
     * versions of this tile unreadable on a device.
     *
     * Pure and Dp-free so it can be asserted without a `Context` or a composition.
     */
    fun sizesFor(availableWidthDp: Float): DualDateTileSizes {
        val monthSize = monthSizeFor(availableWidthDp)
        return DualDateTileSizes(
            monthSize = monthSize,
            daySize = daySize,
            // Never larger than the caption above it, or the sub-row would out-weigh the month name it
            // belongs with.
            subRowSize = minOf(subRowSize.value, monthSize.value).coerceAtLeast(subRowSizeFloor.value).sp,
            weekdaySize = weekdaySizeFor(availableWidthDp),
        )
    }

    /**
     * The vertical budget these four lines ask for, in dp, at [availableWidthDp].
     *
     * **Recorded rather than met, exactly as [DateTileTypography] records its own.** A four-zone tile
     * needs more height than a three-zone one — this is ~125dp at a 72dp-wide cell against the sibling
     * tiles' ~107dp — so it clips its last line on a cell shorter than that, and the honest thing is to
     * say so here and let the test hold the number, rather than to shrink the type until the number looks
     * acceptable. The alternative was tried and is what made this tile unreadable.
     */
    fun verticalBudgetDp(availableWidthDp: Float): Float {
        val sizes = sizesFor(availableWidthDp)
        val sum = sizes.monthSize.value + sizes.daySize.value +
            sizes.subRowSize.value + sizes.weekdaySize.value
        return sum * LINE_HEIGHT_RATIO + BAND_PADDING_DP
    }

    /**
     * The upper bound on how far a line's glyphs can sit inside its box, in dp.
     *
     * **A bound, not a measurement**, and deliberately stated in the module rather than discovered on
     * a device. It is the largest leading [LINE_HEIGHT_RATIO] allows between the largest label's line
     * box and its glyph run — the header band, since a band puts its line's whole inset on show. That
     * is the whole residue the equal-inset construction leaves, and quoting it as a number is what lets
     * the top and bottom margins be *compared* rather than merely asserted equal in intent.
     *
     * It can be tightened by measuring real glyph bounds, which is what D1's two abandoned routes (an
     * `AndroidRemoteViews` layout, or a drawn `Bitmap`) were for. Neither is taken here.
     */
    val RESIDUAL_MARGIN_DP: Float =
        DateTileTypography.weekdaySizeAtReference.value * (LINE_HEIGHT_RATIO - 1f) / 2f
}

/**
 * The four type sizes a dual-date tile renders at, as one value.
 *
 * A single type rather than four independent parameters because they are not independently choosable:
 * three of them are fixed together and the fourth is derived from the height they leave over, so a
 * caller that took [daySize] alone could pair it with a stale [weekdaySize] and get a tile whose
 * proportions do not match — which is not a failure any single-number assertion would catch.
 */
internal data class DualDateTileSizes(
    /** The Hijri month name, alone in the band. */
    val monthSize: TextUnit,
    /** The Hijri day figure, in the centre of the tile. */
    val daySize: TextUnit,
    val subRowSize: TextUnit,
    val weekdaySize: TextUnit,
) {
    /**
     * The four zones stacked as line boxes, in dp — what [DualDateTileTypography.sizesFor] fits
     * against the granted height, and what the preview-layout test recomputes from the XML.
     */
    fun stackedHeightDp(): Float =
        (monthSize.value + daySize.value + subRowSize.value + weekdaySize.value) *
            DualDateTileTypography.LINE_HEIGHT_RATIO
}

/**
 * The tile's layout, top to bottom: the weekday in a filled band, the Hijri day centred, the Hijri month
 * name in full beneath it, then the Gregorian date. Tapping anywhere opens the app.
 *
 * ```
 * Column
 * ├── Box  band, full width, accent fill: the weekday name
 * └── Box  defaultWeight(), centre-aligned
 *     └── Column
 *         ├── Text Hijri day, accent, bold      <- the figure
 *         ├── Text Hijri month name, full
 *         └── Box  fillMaxWidth, centred: Gregorian month - day
 * ```
 *
 * ## How the margins are shared
 *
 * The band is a fixed height at the top and the region below it is a **weighted, centre-aligned** `Box`, so
 * whatever height the launcher grants beyond the four lines' own boxes is split evenly between the space
 * above the day and the space below the Gregorian date. A top-pinned region would render its content first
 * and pool all the slack underneath, which is the arrangement the strip's KDoc records as making a leftover
 * height read as a mistake.
 *
 * The residue this leaves is the leading inside each `Text`'s line box, which Glance cannot remove — see
 * [DualDateTileTypography] for why, and for what it costs.
 *
 * ## Reading direction
 *
 * The Gregorian date is the tile's only left/right order; see [GregorianDateLine] for why its order keys
 * on the **device** rather than the language.
 */
@Composable
internal fun DualDateTileRoot(
    today: TodayHijriWidgetData?,
    colors: WidgetColors,
    openAction: Action?,
    deviceRtl: Boolean,
    language: WidgetLanguage,
) {
    val fonts = HijriWidgetFonts.default
    val cell = LocalSize.current
    // Width, not height — see [DualDateTileTypography.sizesFor]. The granted width is what a fixed 1x1
    // varies along, and it is the axis the sibling tiles size their names on.
    val availableWidth = cell.width - DualDateTileTypography.HORIZONTAL_PADDING_DP.dp * 2
    val sizes = DualDateTileTypography.sizesFor(availableWidth.value)

    // One line per render, under the family's existing log tag, so the granted height can be read off
    // a device instead of guessed at.
    //
    // This exists because "the text is too small" is otherwise undiagnosable from a screenshot: there
    // is no way to ask a launcher what it grants, the answer differs per launcher, and *every* number
    // in [DualDateTileTypography] is conditional on it. `adb logcat -s HijriWidgetRefresh` reports the
    // height the cell actually gave this tile and the sizes it derived, which is the difference between
    // tuning the numbers and guessing at them.
    //
    // The label-budget line is the one that matters when a size looks wrong: it says whether the
    // labels fitted (budget >= 0) or were scaled down to fit (budget < 0), which is the distinction
    // that is invisible from the outside and easy to get backwards — raising the label sizes *lowers*
    // the headroom, it does not raise it.
    logSizes(availableWidth.value, cell.height.value, sizes)

    val modifier = GlanceModifier.fillMaxSize().background(colors.background)
    val clickableModifier = if (openAction != null) modifier.clickable(openAction) else modifier

    Column(
        modifier = clickableModifier,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        if (today == null) {
            Text(
                // Resolved from the widget's own language, not the device locale (WG-12) — see
                // [WidgetLocalization.ChromeLabels]. This is the whole body of the tile when the date
                // does not resolve, so it is the one case a user most needs to be told about.
                text = WidgetLocalization.ChromeLabels.monthUnavailable(language),
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(
                    color = colors.secondaryText,
                    fontSize = sizes.weekdaySize,
                    textAlign = TextAlign.Center,
                ),
            )
            return@Column
        }

        HeaderBand(
            weekdayText = today.weekdayName,
            sizes = sizes,
            colors = colors,
            fonts = fonts,
        )

        // Weighted and centre-aligned: the weight takes the height the band did not, and the centring is
        // what splits the leftover between the two margins. See the KDoc.
        Box(
            modifier = GlanceModifier.defaultWeight(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                // The figure first and centred: it is the answer this tile exists to give.
                Text(
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
                // The month name in **full** — `ربیع الثانی`, `Jumada al-akhirah` — because it is no
                // longer confined to a narrow band, and the body has room for it.
                Text(
                    text = today.hijriMonthName,
                    modifier = GlanceModifier.padding(top = DualDateTileTypography.lineGap),
                    style = TextStyle(
                        color = colors.primaryText,
                        fontSize = sizes.monthSize,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fonts.monthTitle.toGlanceFontFamily(),
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 1,
                )
                GregorianDateLine(
                    monthText = today.gregorianMonthName,
                    dayText = today.gregorianDayText,
                    sizes = sizes,
                    colors = colors,
                    fonts = fonts,
                    deviceRtl = deviceRtl,
                )
            }
        }
    }
}

/**
 * Reports the granted cell and the sizes derived from it. See the call site for why this is not optional.
 *
 * "The text is too small" is otherwise undiagnosable from a screenshot: there is no way to ask a launcher
 * what it grants, the answer differs per launcher, and *every* number in [DualDateTileTypography] is
 * conditional on it. `adb logcat -s HijriWidgetRefresh` reports the cell the host actually gave this tile,
 * which is the difference between tuning the numbers and guessing at them.
 */
private fun logSizes(availableWidthDp: Float, cellHeightDp: Float, sizes: DualDateTileSizes) {
    HijriWidgetRefreshLog.d(
        "dual-tile-sizes",
        "cell=${cellHeightDp}x${availableWidthDp + DualDateTileTypography.HORIZONTAL_PADDING_DP * 2}dp " +
            "band=${sizes.weekdaySize.value.toInt()}sp day=${sizes.daySize.value.toInt()}sp " +
            "month=${sizes.monthSize.value.toInt()}sp greg=${sizes.subRowSize.value.toInt()}sp " +
            "budget=${DualDateTileTypography.verticalBudgetDp(availableWidthDp).toInt()}dp " +
            "(clips below this height)",
    )
}

/**
 * The filled band across the top of the tile, carrying **the weekday name**.
 *
 * "Wed", "today" — the name of the day, in the widget's own language and numerals
 * ([TodayHijriWidgetData.weekdayName], projected per WG-12).
 *
 * **`widget_accent` on `widget_on_today`, not a colour of its own.** The reference page's header is blue,
 * and giving this tile a blue would look right on one launcher and wrong in every other, in night mode, and
 * against a host that has themed the family. Reusing the family accent is what makes the band re-resolve
 * for night mode like every other surface (FD-07). If the family wants a blue, that is a palette decision
 * for all of it, not a new token here.
 *
 * ## Why the day is *not* in here
 *
 * **The figure belongs in the centre of the tile, which is where the reference puts it and where the eye
 * lands.** An intermediate revision folded the day into the band with the month name under it, on the
 * reasoning that a figure and its month name are one unit and drawing them as one unit costs less height.
 * The height saving was real (~99dp against ~136dp) but it put the tile's one dominant number inside a
 * coloured bar at the top, which reads as a header label rather than as the answer. The day is back in the
 * centre, at the same 30sp.
 *
 * ## Why the band has no padding of its own
 *
 * **The band's height is exactly its text's line box.** An earlier version added 2dp inside it, and
 * because a line box's leading is asymmetric — more below the baseline than above it — *symmetric* padding
 * around it lands off-centre by the descent, which showed as a stripe of empty accent above the month name.
 * The line box already supplies the visual inset; adding to it only adds a gap.
 *
 * The band is sized from [DualDateTileTypography.weekdaySize] rather than from the length of the word it
 * holds: a band that grew and shrank with the name would make the tile's whole vertical rhythm depend on
 * which day it is.
 *
 * **A weekday name is the right thing for a band where a month name was.** It is one word in every
 * language — at its longest `الاربعاء` — so it fits the full width comfortably, and it answers the question
 * a glance actually asks. The Hijri month name, the long one, moved down into the tile's body, which has
 * room to render it **in full** — so the abbreviated form this band originally needed,
 * [TodayHijriWidgetData.hijriMonthShortName], is no longer read here. It stays on the projection for iOS,
 * where the equivalent narrow band still does.
 */
@Composable
private fun HeaderBand(
    weekdayText: String,
    sizes: DualDateTileSizes,
    colors: WidgetColors,
    fonts: HijriWidgetFonts,
) {
    val bandHeight = sizes.weekdaySize.value * DualDateTileTypography.LINE_HEIGHT_RATIO +
        DualDateTileTypography.BAND_PADDING_DP
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(bandHeight.dp)
            .background(colors.accent),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = weekdayText,
            modifier = GlanceModifier.padding(
                start = DualDateTileTypography.HORIZONTAL_PADDING_DP.dp,
                end = DualDateTileTypography.HORIZONTAL_PADDING_DP.dp,
                // Below only — see [BAND_PADDING_DP].
                bottom = DualDateTileTypography.BAND_PADDING_DP.dp,
            ),
            style = TextStyle(
                color = colors.onTodayText,
                fontSize = sizes.weekdaySize,
                fontWeight = FontWeight.Bold,
                fontFamily = fonts.weekday.toGlanceFontFamily(),
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
    }
}

/**
 * The Gregorian date as **one compact phrase, centred** — `سبتمبر ۳۰` / `September 30` — with the **day on
 * the right and the month name on its left**.
 *
 * ## Centred, and the two halves a fixed pair
 *
 * The phrase is one date, not two labels, so it is centred as a unit with its halves **adjacent**. The
 * order inside it is a fixed physical one — month left, day right — rather than a reading-direction one,
 * and those are different requirements that each look like the other:
 *
 * - **Centred** answers "is this one thing?", which is why the halves are adjacent rather than pushed to
 *   opposite ends. An earlier revision weighted one of them, which put the month at one edge and the day at
 *   the other and read as two unrelated labels.
 * - **Fixed order** answers "which side is the number on?", and it is a property of the design, not of the
 *   script. A revision before this one aligned the whole phrase to the reading-start end — right for Urdu,
 *   left for English — which pushed the date to one side of the tile; asking for the day on the right means
 *   the day on the right *within* the date, not the date slid to the end.
 *
 * ## Why the emission order keys on the **device**
 *
 * A Glance `Row` is a plain horizontal `LinearLayout`, which the platform mirrors on an RTL device. To land
 * month-left / day-right **visually** in both, the children are emitted day-first exactly when that mirroring
 * is in play — a question about the *device*, not the language. [computeDeviceLayoutRtl] is therefore the
 * right seam here, and [computeLayoutRtl] would be the category error: that one answers "must the
 * projection pre-reverse to cancel the mirroring" and folds the widget's language in, which would swap the
 * two ends on an Urdu device.
 */
@Composable
private fun GregorianDateLine(
    monthText: String,
    dayText: String,
    sizes: DualDateTileSizes,
    colors: WidgetColors,
    fonts: HijriWidgetFonts,
    deviceRtl: Boolean,
) {
    Box(
        modifier = GlanceModifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = GlanceModifier.padding(
                start = DualDateTileTypography.HORIZONTAL_PADDING_DP.dp,
                end = DualDateTileTypography.HORIZONTAL_PADDING_DP.dp,
                top = DualDateTileTypography.lineGap,
            ),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            val month = @Composable {
                Text(
                    text = monthText,
                    // `primaryText`, not `secondaryText`: the same colour the Hijri month name uses a
                    // line above. The two month names are one date in two calendars, and muting one of
                    // them read as if it were a footnote on the other. The *day figures* and the size
                    // carry the emphasis instead.
                    style = TextStyle(
                        color = colors.primaryText,
                        fontSize = sizes.subRowSize,
                        fontFamily = fonts.gregorianTitle.toGlanceFontFamily(),
                    ),
                    maxLines = 1,
                )
            }
            val day = @Composable {
                Text(
                    text = dayText,
                    style = TextStyle(
                        color = colors.primaryText,
                        fontSize = sizes.subRowSize,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fonts.dayNumber.toGlanceFontFamily(),
                    ),
                    maxLines = 1,
                )
            }
            // `secondaryText`, so the separator stays punctuation: both month names are `primaryText`
            // because they are one date in two calendars, and a dash in that weight would read as a third
            // thing on the tile rather than as the join between two halves of one.
            val separator = @Composable {
                Text(
                    text = DualDateTileTypography.DATE_SEPARATOR,
                    style = TextStyle(
                        color = colors.secondaryText,
                        fontSize = sizes.subRowSize,
                    ),
                    maxLines = 1,
                )
            }
            if (dayFirstForDayOnRight(deviceRtl)) {
                day()
                separator()
                month()
            } else {
                month()
                separator()
                day()
            }
        }
    }
}

/**
 * Whether the date's halves must be emitted **day first** for the day to land on the physical right.
 *
 * A Glance `Row` is a horizontal `LinearLayout` the platform mirrors on an RTL device, so the emission
 * order has to run against that mirroring exactly once — and the widget's language must play no part, since
 * the placement is fixed and a language-driven flag would swap the two ends on an Urdu device.
 *
 * Split out as a named pure function so the rule can be asserted without a `Configuration`; the mirroring
 * itself happens in the host's view hierarchy, which no JVM test can run.
 */
internal fun dayFirstForDayOnRight(deviceRtl: Boolean): Boolean = deviceRtl

/**
 * The tile's preview size hint for the sample app's catalog and settings preview: a square cell.
 *
 * Sized on the tile's *width* budget rather than a round number, because a fixed 1x1 is sized on width:
 * the width below leaves the reference width for text, so the preview renders at exactly the sizes the
 * widget-info mirror shows. A preview that granted less would render smaller names than a placed widget
 * does, and a preview that granted more would show a size the picker cannot give the user.
 */
private val previewSquareDp: Float = DualDateTileTypography.TILE_REFERENCE_TEXT_WIDTH_DP +
    DualDateTileTypography.HORIZONTAL_PADDING_DP * 2

internal val DUAL_DATE_TILE_PREVIEW_SIZE: DpSize = DpSize(previewSquareDp.dp, previewSquareDp.dp)

/**
 * The day figure's emphasis is the point of the tile, so [DualDateTileTypography.sizesFor] must never
 * return a [DualDateTileSizes] where another zone is the largest.
 *
 * Stated here rather than only in the test, so the invariant sits next to the rule that maintains it;
 * `DualDateTilePreviewLayoutTest` sweeps the whole size range against this.
 */
internal fun DualDateTileSizes.isDayDominant(): Boolean =
    daySize.value > monthSize.value &&
        daySize.value > subRowSize.value &&
        daySize.value > weekdaySize.value
