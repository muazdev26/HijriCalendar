package com.muazdev.hijricalendar.widget.glance

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.currentState
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
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
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
 * **Placed as a 1x1, and resizable on both axes since point 7.** The widget-info keeps the same
 * 40dp minimum as the other tiles — `minHeight` is deliberately *not* raised to make the content
 * fit, because on launchers before Android 12 the cell count is derived from
 * `minWidth`/`minHeight`, so a 72dp minimum would make this **two** cells tall there. What the
 * minimum does not do is pin the type: [sizeMode] is `Exact`, so a stretched tile renders the same
 * four zones larger rather than the same four zones inside more empty space — see
 * [DualDateTileTypography].
 *
 * No settings screen and no per-widget state: it follows the family options mirror
 * ([HijriWidgetConfig.loadFamily]) like the other two tiles. Tapping anywhere opens the app.
 */
public class HijriDualDateWidget : GlanceAppWidget() {

    /**
     * `SizeMode.Exact`, so the composition sees the cell the launcher granted — the same reasoning as
     * [HijriDateWidget.sizeMode], and for this tile the same reason it is safe: the only interaction
     * is one `actionStartActivity`, a `PendingIntent` attached to the `RemoteViews`, so composing
     * per size cannot double-launch the app.
     */
    override val sizeMode: SizeMode = SizeMode.Exact

    /**
     * The picker preview composes at the tile's square reference cell rather than the bare 40dp
     * minimum, so the picker shows the four zones at the sizes a placed 1x1 renders them — the same
     * arrangement the sibling tiles use.
     */
    override val previewSizeMode: PreviewSizeMode = SizeMode.Responsive(
        setOf(DUAL_DATE_TILE_PREVIEW_SIZE),
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
                "type=DualDate overrides=${options.monthLengthOverrides.size} language=${options.language} optionsFrom=$optionsFrom",
            )
            val colors = WidgetColors.forTheme(options.theme)
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
     * The preview composes at [previewSizeMode]'s square reference cell rather than the declared
     * 40dp minimum, because a preview at the minimum advertises four unreadable lines as if they
     * were the tile.
     */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val options = HijriWidgetConfig.loadFamily(context)
        if (options.source.pakistan) {
            PakistanWarmUp.ensureWarm()
        }
        HijriWidgetRefreshLog.d(
            "render:tile",
            "type=DualDate overrides=${options.monthLengthOverrides.size} language=${options.language} optionsFrom=family",
        )
        val colors = WidgetColors.forTheme(options.theme)
        provideContent {
            val data = buildRenderData(context, options, viewedMonth = null, glanceId = "preview", optionsFrom = "family")
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
 * ## Four zones, all sized from the granted width
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
 * The model that replaced it is **width-driven, like the sibling tiles, with the day figure scaling
 * with the tile**: the captions are the sibling tiles' own rule
 * (`DateTileTypography.weekdaySizeFor`) dialled back by `CAPTION_RATIO`, and the day figure is
 * [DualDateTileTypography.daySizeFor] — 20sp at the reference width, growing to 40sp. So a 72dp cell
 * gets a ~11sp weekday and a 20sp figure, and a wider cell grows both, the figure faster. That is
 * what keeps the day the tile's loudest zone at every size rather than only at the reference one
 * (`isDayDominant` states the invariant next to the rule that maintains it).
 *
 * The consequence is honest and belongs here rather than in a device bug report: **the four zones
 * claim more height than a small cell has**, so a cell well under a typical 1x1 cannot show them all
 * at full size. Nothing shrinks to fit — FD-10 is explicit that this family does not shrink a tile's
 * type for its cell, and a legible-but-cropped tile beats an illegible-but-complete one. The declared
 * 40dp minimum is in that regime and always has been; [verticalBudgetDp] records the arithmetic and
 * `DualDateTilePreviewLayoutTest` holds the layout to it.
 *
 * ```
 * ┌────────────────────────┐
 * │  HEADER BAND          │  Hijri month, white on accent  (width-scaled)
 * ├────────────────────────┤
 * │  Greg month ·  Greg day│  small row, two ends           (width-scaled)
 * │       HIJRI DAY        │  dominant figure                  (grows most)
 * │        weekday         │                              (width-scaled)
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

    /**
     * The declared minimum tile width — and the narrowest a sub-row or weekday name can be drawn.
     */
    const val TILE_MIN_WIDTH_DP = 40

    const val TILE_REFERENCE_TEXT_WIDTH_DP: Float = DateTileTypography.WEEKDAY_REFERENCE_WIDTH_DP
    const val REFERENCE_HEIGHT_DP: Float = 72f

    /** Horizontal clear space. The band itself stays flush to the tile's edges. */
    const val HORIZONTAL_PADDING_DP = 6

    /** Top padding inside the band to prevent glyph clipping (e.g., Arabic/Urdu ascenders). */
    const val BAND_TOP_PADDING_DP = 2

    const val BAND_PADDING_DP = 3

    /**
     * The gap between the tile's body lines, in dp.
     */
    val lineGap = 1.dp

    /**
     * The separator between the Gregorian month name and day, in this tile's own typography.
     */
    const val DATE_SEPARATOR = " - "

    /**
     * The figure at the reference width — and its floor, since a placed 1x1 must never render
     * *smaller* than the size it has always had (FD-10: this family never shrinks a tile's type to
     * fit its cell). Grows with the granted width up to [daySizeCeiling]; see [daySizeFor].
     */
    val daySize = 20.sp
    val daySizeFloor = daySize

    /**
     * The figure's ceiling at a large cell. Point 7: the figure tracks the granted width instead of
     * being a constant, so the tile's loudest zone is also the one that grows most on a resize.
     */
    val daySizeCeiling = 40.sp

    const val CAPTION_RATIO = 0.85f
    internal const val CAPTION_MAX_SHARE = 0.8f

    val subRowSize = 10.5.sp
    val subRowSizeFloor = DateTileTypography.weekdaySizeFloor
    val subRowSizeCeiling = 16.sp

    val monthSizeFloor = 10.sp
    val monthSizeCeiling = 22.sp

    /**
     * The day figure for a granted width: [daySize] at [TILE_REFERENCE_TEXT_WIDTH_DP], scaling with
     * the tile and clamped to [daySizeFloor]..[daySizeCeiling].
     *
     * Exactly `daySize` at the reference width, which is what keeps a preview and a placed 1x1 in
     * agreement; the captions are then held under the figure the *same* cell renders.
     */
    fun daySizeFor(availableWidthDp: Float): TextUnit =
        (daySize.value * availableWidthDp / TILE_REFERENCE_TEXT_WIDTH_DP)
            .coerceIn(daySizeFloor.value, daySizeCeiling.value)
            .sp

    /**
     * A caption — the band weekday, the Hijri month name — for a granted width, held under
     * `CAPTION_MAX_SHARE` of the day figure **that the same cell will render**, so the two values
     * cannot drift apart as the tile grows.
     */
    private fun captionSizeFor(availableWidth: Float, daySizeSp: Float): TextUnit =
        (DateTileTypography.weekdaySizeFor(availableWidth).value * CAPTION_RATIO)
            .coerceIn(
                monthSizeFloor.value,
                minOf(monthSizeCeiling.value, daySizeSp * CAPTION_MAX_SHARE),
            ).sp

    /** The caption rule at the reference cell, for callers that have only a width. */
    fun monthSizeFor(availableWidth: Float): TextUnit = captionSizeFor(availableWidth, daySize.value)

    fun weekdaySizeFor(availableWidth: Float): TextUnit = captionSizeFor(availableWidth, daySize.value)

    /**
     * The tile's type sizes for a granted width. `grantedHeightDp` is accepted for signature parity
     * with the sibling tiles' `DateTileTypography.sizesFor(width, height)` and unused **on purpose**:
     * FD-10 is explicit that a 1x1 tile must never shrink its type to fit its cell (the family does
     * not), so the sizes derive from the width alone — only the *budget* below is a function of the
     * height, and it compares against that height at the call site rather than shrinking to it.
     */
    @Suppress("UnusedParameter")
    fun sizesFor(availableWidthDp: Float, grantedHeightDp: Float): DualDateTileSizes {
        val day = daySizeFor(availableWidthDp)
        val caption = captionSizeFor(availableWidthDp, day.value)
        val subRow = minOf(caption.value, subRowSizeCeiling.value)
            .coerceIn(subRowSizeFloor.value, subRowSizeCeiling.value)
            .sp

        return DualDateTileSizes(
            monthSize = caption,
            daySize = day,
            subRowSize = subRow,
            weekdaySize = caption,
        )
    }

    fun sizesFor(availableWidthDp: Float): DualDateTileSizes =
        sizesFor(availableWidthDp, REFERENCE_HEIGHT_DP)

    fun verticalBudgetDp(availableWidthDp: Float, grantedHeightDp: Float): Float {
        val sizes = sizesFor(availableWidthDp, grantedHeightDp)
        val sum = sizes.monthSize.value + sizes.daySize.value +
            sizes.subRowSize.value + sizes.weekdaySize.value
        return sum * LINE_HEIGHT_RATIO + BAND_TOP_PADDING_DP + BAND_PADDING_DP + lineGap.value * 2
    }

    fun verticalBudgetDp(availableWidthDp: Float): Float =
        verticalBudgetDp(availableWidthDp, REFERENCE_HEIGHT_DP)

    val RESIDUAL_MARGIN_DP: Float =
        DateTileTypography.weekdaySizeAtReference.value * (LINE_HEIGHT_RATIO - 1f) / 2f
}

/**
 * The four type sizes a dual-date tile renders at, as one value.
 */
internal data class DualDateTileSizes(
    /** The Hijri month name, alone in the band. */
    val monthSize: TextUnit,
    /** The Hijri day figure, in the centre of the tile. */
    val daySize: TextUnit,
    val subRowSize: TextUnit,
    val weekdaySize: TextUnit,
) {
    fun stackedHeightDp(): Float =
        (monthSize.value + daySize.value + subRowSize.value + weekdaySize.value) *
            DualDateTileTypography.LINE_HEIGHT_RATIO
}

/**
 * The tile's layout, top to bottom: the weekday in a filled band, the Hijri day centred, the Hijri month
 * name in full beneath it, then the Gregorian date. Tapping anywhere opens the app.
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
    val availableWidth = cell.width - DualDateTileTypography.HORIZONTAL_PADDING_DP.dp * 2
    val sizes = DualDateTileTypography.sizesFor(availableWidth.value, cell.height.value)

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
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(colors.accent),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = weekdayText,
            modifier = GlanceModifier.padding(
                start = DualDateTileTypography.HORIZONTAL_PADDING_DP.dp,
                end = DualDateTileTypography.HORIZONTAL_PADDING_DP.dp,
                top = DualDateTileTypography.BAND_TOP_PADDING_DP.dp,
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
