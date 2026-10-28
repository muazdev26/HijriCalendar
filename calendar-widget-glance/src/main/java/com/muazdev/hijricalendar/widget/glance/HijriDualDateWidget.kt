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
 * ## Four zones, one scale
 *
 * The tile stacks a header band, a sub-row, the day figure and the month name. Laid out as fixed line
 * boxes those four ask for more height than a 1x1 cell provides, and the obvious ways out are both
 * wrong: raising `minHeight` to fit (which makes the widget two cells tall before Android 12), or
 * shrinking every size until the Hijri day stops being the dominant figure.
 *
 * What the tile does instead is **one scale, from both granted axes** ([widgetTypeScale]), exactly as
 * the sibling tiles and the Today strip do. Every zone is `reference * scale`, so the four lines
 * always fit the granted height and none can be pushed off the bottom.
 *
 * ```
 * ┌────────────────────────┐
 * │  WEEKDAY BAND         │  the day name, on the family accent
 * ├────────────────────────┤
 * │       HIJRI DAY        │  the dominant figure
 * │    Hijri month name    │  in full
 * │  Greg month - Greg day │  one compact centred phrase
 * └────────────────────────┘
 * ```
 *
 * ## The two models that came before this one, and why they failed
 *
 * 1. Every zone sized as a **fraction of the granted height**, taken from the reference tear-off
 *    page's proportions. Laid out as a table that reads like a considered design; rendered on a real
 *    72dp cell it produced a **6sp weekday**, smaller than the icon next to it.
 * 2. The remedy — a **width-only** rule with a floor, clipping whatever did not fit — fixed the
 *    unreadable-weekday case and reintroduced the failure the family is now arranged to prevent: on a
 *    short cell some lines grew (or held) while others were clipped, which is the report this change
 *    answers.
 *
 * The scale rule is the smallest of the two axes' ratios, so a cell that is wide and short is bounded
 * by its height and a cell that is tall and narrow by its width; [MIN_TYPE_SCALE] is a legibility
 * floor that the declared 40dp minimum never reaches, so every size a launcher may grant fits.
 *
 * ## Why [daySizeFloor] still exists
 *
 * It bounds the **width** rule ([daySizeFor]) that a preview with no granted height uses, so a
 * preview never shows a smaller figure than a reference-width tile. It is deliberately *not* applied
 * inside [sizesFor]: applying it is what clipped the month line, and a complete tile the launcher
 * scaled down is the behaviour point 7 round 2 asked for.
 */
internal object DualDateTileTypography {

    /**
     * The widget-info's declared minimum tile height, in dp. Read by the test that records the budget,
     * and the smallest height [sizesFor] is ever asked about.
     */
    const val TILE_MIN_HEIGHT_DP = 40

    /**
     * The declared minimum tile width — and the narrowest a sub-row or weekday name can be drawn.
     */
    const val TILE_MIN_WIDTH_DP = 40

    /** The text width the sizes are tuned against: the sibling tiles' own reference. */
    const val TILE_REFERENCE_TEXT_WIDTH_DP: Float = DateTileTypography.WEEKDAY_REFERENCE_WIDTH_DP

    /**
     * The cell height a width-only measurement is taken at: the tile's preview cell, i.e.
     * [DUAL_DATE_TILE_PREVIEW_SIZE]. Only [verticalBudgetDp] with a width alone reads it; the static
     * preview mirror sizes on width, exactly as the sibling tiles' mirrors do, because a launched
     * preview has no granted height to state.
     */
    const val REFERENCE_HEIGHT_DP: Float = 84f

    /** Horizontal clear space. The band itself stays flush to the tile's edges. */
    const val HORIZONTAL_PADDING_DP = WidgetBand.HORIZONTAL_PADDING_DP

    /** Top padding inside the band to prevent glyph clipping (e.g., Arabic/Urdu ascenders). */
    const val BAND_TOP_PADDING_DP = WidgetBand.TOP_PADDING_DP

    const val BAND_PADDING_DP = WidgetBand.BOTTOM_PADDING_DP

    /**
     * The gap between the tile's body lines, in dp.
     */
    val lineGap = 1.dp

    /**
     * The separator between the Gregorian month name and day, in this tile's own typography.
     */
    const val DATE_SEPARATOR = " - "

    /**
     * The figure at the reference width. Grows with the granted cell up to [daySizeCeiling]; see
     * [sizesFor].
     *
     * There is no floor any more. The tile used to hold the figure at 20sp and **clip** the last zone
     * rather than render it smaller, on FD-10's reasoning that a legible-but-cropped tile beats an
     * illegible-but-complete one. A device pass contradicted the premise: on a short cell the *month
     * name* was the line pushed off the bottom, so the tile was not "cropped but legible" — it was
     * missing the month, and the figure above it sat at its full size inside the space the month had
     * needed. The tile now fits **every** zone, scaling the whole stack with the cell; see
     * [widgetTypeScale] and [daySizeFloor] for the width rule that still applies to previews.
     *
     * **Raised 20 → 24 with [CAPTION_RATIO] dropped 0.85 → 0.78**, which is the whole of the
     * "enlarge the figure" change and is deliberately a *rebalance* rather than a size increase. The
     * scalable stack at the reference is `3 · caption · scriptRatio + day · DIGIT_LINE_RATIO`; moving
     * two of its constants in opposite directions keeps that total inside the 40dp floor's budget
     * (it went 108.9 → 107.3dp at [NASTALIQ_LINE_RATIO], so the stack still fits the smallest cell the
     * widget-info permits) while giving the figure a larger *share* of it. Raising [daySize] alone would
     * have grown the request past `(40 − fixed) / MIN_TYPE_SCALE` and clipped the month off the floor
     * cell — the failure the tile already carries two redesigns about.
     */
    val daySize = 24.sp

    /**
     * The floor the *width* rule holds the figure to, so a narrow tile does not render a smaller
     * figure than the reference. Not a floor on the fitted stack: [sizesFor] with a granted height may
     * go below it, which is the whole point of fitting.
     */
    val daySizeFloor = daySize

    /**
     * The figure's ceiling at a large cell. Point 7: the figure tracks the granted cell instead of
     * being a constant, so the tile's loudest zone is also the one that grows most on a resize.
     */
    val daySizeCeiling = 40.sp

    /**
     * The caption at scale 1 as a share of the family's reference weekday. Dialled back from 0.85 when
     * [daySize] went up: the two constants are one budget, and the captions give the figure its extra
     * share rather than the stack growing tall enough to lose the month at 40dp. See [daySize].
     */
    const val CAPTION_RATIO = 0.78f
    internal const val CAPTION_MAX_SHARE = 0.8f

    /**
     * The weekday band's type: the family-wide share of the figure ([WeekdayBandFit]), not the caption
     * scale this tile used to give it.
     *
     * That was the band-legibility defect on this tile: the weekday rendered at `caption × 0.78`, so on
     * a placed cell it was ~8.7sp against a 24sp figure, in a band whose height was that size's whole
     * line box — a short word in a tall accent strip. The band is now as tall as its ink and the word is
     * half the figure, so it reads at ~11.4sp on the same cell and the tile's stack is *shorter* by
     * ~7dp ([referenceTextHeightDp]).
     */
    val weekdaySizeAtReference: TextUnit = WeekdayBandFit.sizeFor(daySize)

    val subRowSize = 10.5.sp
    val subRowSizeCeiling = 16.sp

    val monthSizeCeiling = 22.sp

    /** The caption at scale 1: the sibling tiles' reference weekday, dialled back. */
    val captionAtReference = DateTileTypography.weekdaySizeAtReference * CAPTION_RATIO

    /** The Gregorian date at scale 1. */
    val subRowAtReference = minOf(captionAtReference.value, subRowSizeCeiling.value).sp

    private val MAX_SCALE: Float = daySizeCeiling.value / daySize.value

    /**
     * The scalable part at scale 1: three script lines and one digit line, at [scriptRatio] and
     * [DIGIT_LINE_RATIO] respectively.
     */
    fun referenceTextHeightDp(scriptRatio: Float): Float =
        WeekdayBandFit.inkHeightDp(weekdaySizeAtReference, WeekdayBandFit.inkEmFor(scriptRatio)) +
            daySize.value * DIGIT_LINE_RATIO +
            subRowAtReference.value * scriptRatio +
            captionAtReference.value * scriptRatio

    /** [referenceTextHeightDp] at the system script ratio; the static mirror and the tests read this. */
    val REFERENCE_TEXT_HEIGHT_DP: Float = referenceTextHeightDp(TEXT_LINE_RATIO)

    /** The non-scaling part of the stack: band padding and the two line gaps. */
    val FIXED_HEIGHT_DP: Float =
        (BAND_TOP_PADDING_DP + BAND_PADDING_DP).toFloat() + lineGap.value * 2

    /**
     * The height the four zones plus the band's padding ask for at scale 1 — the yardstick
     * [widgetTypeScale] measures a granted height against, and the height a preview and a placed tile
     * agree at.
     */
    val REFERENCE_STACK_HEIGHT_DP: Float = REFERENCE_TEXT_HEIGHT_DP + FIXED_HEIGHT_DP

    /**
     * The day figure for a granted width: [daySize] at [TILE_REFERENCE_TEXT_WIDTH_DP], scaling with
     * the tile and clamped to [daySizeFloor]..[daySizeCeiling].
     *
     * This is the **width** rule, the one the Android 12-14 preview mirror states. The live tile goes
     * through [sizesFor], whose granted height may scale the figure below [daySizeFloor] to keep the
     * other three zones on the tile.
     */
    fun daySizeFor(availableWidthDp: Float): TextUnit =
        (daySize.value * availableWidthDp / TILE_REFERENCE_TEXT_WIDTH_DP)
            .coerceIn(daySizeFloor.value, daySizeCeiling.value)
            .sp

    fun monthSizeFor(availableWidthDp: Float): TextUnit = sizesFor(availableWidthDp).monthSize

    fun weekdaySizeFor(availableWidthDp: Float): TextUnit = sizesFor(availableWidthDp).weekdaySize

    /**
     * The tile's type sizes for a granted cell: **one scale from both axes**, the same rule the
     * sibling tiles and the Today strip use ([widgetTypeScale]).
     *
     * The tile used to size on width alone and accept that a fourth zone did not fit a short cell.
     * That is exactly the behaviour point 7 round 2 removed from this family: a resize that grows some
     * lines and hides others. Every line here is `reference * scale`, so the stack always fits the
     * granted height and the tile never drops a zone.
     */
    fun sizesFor(
        availableWidthDp: Float,
        grantedHeightDp: Float,
        scriptRatio: Float = TEXT_LINE_RATIO,
    ): DualDateTileSizes = sizesAtScale(
        widgetTypeScale(
            availableWidthDp = availableWidthDp,
            referenceWidthDp = TILE_REFERENCE_TEXT_WIDTH_DP,
            availableHeightDp = grantedHeightDp,
            fixedHeightDp = FIXED_HEIGHT_DP,
            referenceTextHeightDp = referenceTextHeightDp(scriptRatio),
            maxScale = MAX_SCALE,
        ),
    )

    /**
     * Width alone, at [REFERENCE_HEIGHT_DP]. The projection the Android 12-14 `previewLayout` mirror
     * is held to, and the one it can state: a launched preview has no granted height of its own.
     */
    fun sizesFor(availableWidthDp: Float): DualDateTileSizes = sizesAtScale(
        (availableWidthDp / TILE_REFERENCE_TEXT_WIDTH_DP).coerceIn(MIN_TYPE_SCALE, MAX_SCALE),
    )

    private fun sizesAtScale(scale: Float): DualDateTileSizes {
        val day = (daySize.value * scale).sp
        // No legibility floor: a floor is exactly what made the smallest zone the one that got
        // clipped. The ceilings stay, because the captions must never overtake the figure.
        val caption = (captionAtReference.value * scale)
            .coerceAtMost(monthSizeCeiling.value)
            .sp
        val subRow = (subRowAtReference.value * scale)
            .coerceAtMost(minOf(subRowSizeCeiling.value, caption.value))
            .sp
        return DualDateTileSizes(
            monthSize = caption,
            daySize = day,
            subRowSize = subRow,
            weekdaySize = (weekdaySizeAtReference.value * scale)
                .coerceAtMost(WeekdayBandFit.MAX_SIZE.value)
                .sp,
        )
    }

    fun verticalBudgetDp(
        availableWidthDp: Float,
        grantedHeightDp: Float,
        scriptRatio: Float = TEXT_LINE_RATIO,
    ): Float = sizesFor(availableWidthDp, grantedHeightDp, scriptRatio)
        .requestedHeightDp(scriptRatio)

    fun verticalBudgetDp(availableWidthDp: Float): Float =
        verticalBudgetDp(availableWidthDp, REFERENCE_HEIGHT_DP)

    val RESIDUAL_MARGIN_DP: Float =
        DateTileTypography.weekdaySizeAtReference.value * (TEXT_LINE_RATIO - 1f) / 2f
}

/**
 * The four type sizes a dual-date tile renders at, as one value.
 */
internal data class DualDateTileSizes(
    /** The Hijri month name, in full, under the figure. */
    val monthSize: TextUnit,
    /** The Hijri day figure, in the centre of the tile. */
    val daySize: TextUnit,
    val subRowSize: TextUnit,
    /** The weekday name, alone in the accent band — see [DualDateTileTypography.weekdaySizeAtReference]. */
    val weekdaySize: TextUnit,
) {
    /**
     * The height these four lines plus the band's padding ask for, in dp: each caption at
     * [scriptRatio], the figure at [DIGIT_LINE_RATIO], and the fixed chrome.
     */
    fun requestedHeightDp(scriptRatio: Float = TEXT_LINE_RATIO): Float =
        monthSize.value * scriptRatio +
            daySize.value * DIGIT_LINE_RATIO +
            subRowSize.value * scriptRatio +
            WeekdayBandFit.inkHeightDp(weekdaySize, WeekdayBandFit.inkEmFor(scriptRatio)) +
            DualDateTileTypography.BAND_TOP_PADDING_DP +
            DualDateTileTypography.BAND_PADDING_DP +
            DualDateTileTypography.lineGap.value * 2
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
    val sizes = DualDateTileTypography.sizesFor(
        availableWidthDp = availableWidth.value,
        grantedHeightDp = cell.height.value,
        scriptRatio = fonts.scriptLineRatio(),
    )

    logSizes(availableWidth.value, cell.height.value, sizes)

    val modifier = GlanceModifier.fillMaxSize().background(colors.background)
    val clickableModifier = if (openAction != null) modifier.clickable(openAction) else modifier

    Column(
        modifier = clickableModifier,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        if (today == null) {
            WidgetText(
                // Resolved from the widget's own language, not the device locale (WG-12) — see
                // [WidgetLocalization.ChromeLabels]. This is the whole body of the tile when the date
                // does not resolve, so it is the one case a user most needs to be told about.
                text = WidgetLocalization.ChromeLabels.monthUnavailable(language),
                modifier = GlanceModifier.defaultWeight(),
                // A sentence, not a label: it wraps, and wrapping is the one thing the bundled face's
                // rasteriser cannot do — so this slot stays on Glance's `Text` deliberately.
                maxLines = Int.MAX_VALUE,
                style = TextStyle(
                    color = colors.secondaryText,
                    fontSize = sizes.weekdaySize,
                    textAlign = TextAlign.Center,
                ),
            )
            return@Column
        }

        // The weekday name, in the family's shared accent band ([WeekdayBand]) — the same composable
        // every other widget in the family draws its weekday with. It is one word in every language
        // (at its longest الاربعاء), so it fits a full-width band, and it answers the question a
        // glance asks. The band is sized from [DualDateTileTypography.weekdaySize] rather than the
        // length of the word it holds, so the tile's vertical rhythm does not depend on the day.
        WeekdayBand(
            text = today.weekdayTileName,
            weekdaySize = sizes.weekdaySize,
            colors = colors,
            fontFamily = fonts.weekday.toGlanceFontFamily(),
        )

        // Weighted and centre-aligned: the weight takes the height the band did not, and the centring is
        // what splits the leftover between the two margins. See the KDoc.
        Box(
            modifier = GlanceModifier.defaultWeight(),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                // The figure first and centred: it is the answer this tile exists to give.
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
                // The short month form on a 1x1 tile — `Rabi' I` and `Oct` when the month names
                // render in English. Urdu renders its full name here, because **Urdu month names are
                // never shortened** (the projection decides; the renderer just prints the field). The
                // full name rendered here until this round, on the evidence that the body had room
                // for it — but at one cell it is the line that ellipsizes, and a 1x1 answers "which
                // month?" rather than spelling it out (the same reason the strip drops its era when
                // squeezed).
                WidgetText(
                    text = today.hijriMonthShortName,
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
                    monthText = today.gregorianMonthShortName,
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
                WidgetText(
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
                WidgetText(
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
                WidgetText(
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
