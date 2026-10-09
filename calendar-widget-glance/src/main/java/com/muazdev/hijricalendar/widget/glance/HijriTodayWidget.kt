package com.muazdev.hijricalendar.widget.glance

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ExperimentalGlanceApi
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
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.muazdev.hijricalendar.widgetdata.TodayHijriWidgetData
import com.muazdev.hijricalendar.widgetdata.WidgetLanguage
import com.muazdev.hijricalendar.widgetdata.WidgetLocalization

/**
 * Resizable "today" strip widget: full screen width, showing the weekday name above today's Hijri
 * and Gregorian dates side by side, separated by a hairline. It is resizable
 * (`resizeMode="horizontal|vertical"` in its provider info) down to its natural text bounds, so
 * the text is never clipped or hidden at the minimum size — which is why the widget-info's declared
 * minimum is [TodayStripTypography.STRIP_MIN_HEIGHT_DP] and not the 40dp it used to declare: that
 * figure does not hold three lines of type.
 *
 * Unlike the calendar grid it has no per-widget settings screen: it follows the family options
 * mirror ([HijriWidgetConfig.loadFamily]) written by every configurations-screen save on the grid
 * widget, so language, numerals, Hijri source and moon-sighting adjustment always match the
 * family's latest choices. Tapping anywhere opens the app, like the grid widget.
 */
public class HijriTodayWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    /**
     * Picker previews are composed at a realistic placed strip size instead of the widget's own
     * minimum, so the weekday line and both date halves actually fit without clipping.
     */
    override val previewSizeMode: PreviewSizeMode = SizeMode.Responsive(
        setOf(DpSize(144.dp, TodayStripTypography.STRIP_MIN_HEIGHT_DP.dp)),
    )

    @OptIn(ExperimentalGlanceApi::class)
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // One-shot peek: port any legacy flat-file config (which seeds the family mirror once) and
        // warm up the Pakistan century table if needed. The options themselves come from the
        // process-global family mirror below — the strip has no settings screen of its own and
        // follows whatever the family's most recent configure-screen save configured.
        HijriWidgetConfig.migrateLegacyIfNeeded(context, id)
        val peeked = HijriWidgetConfig.loadFamily(context)
        if (peeked.source.pakistan) {
            PakistanWarmUp.ensureWarm()
        }
        val openAction = actionStartActivity(openAppIntent(context))

        provideContent {
            val options = HijriWidgetConfig.loadFamily(context)
            // Resolved from the options read here rather than from the peek above, so a theme change
            // repaints the strip instead of waiting for something else to invalidate it.
            val colors = WidgetColors.forTheme(options.theme)
            val data = HijriWidgetRenderCache.today(
                glanceId = id.toString(),
                options = options,
                todayEpochDay = HijriWidgetRefreshScheduler.todayEpochDay(),
            )
            HijriTodayRoot(
                today = data,
                colors = colors,
                openAction = openAction,
                layoutRtl = computeLayoutRtl(context, options.language),
                language = options.language,
            )
        }
    }

    /**
     * Real picker preview for Android 15+: today's Hijri + Gregorian dates in the family's current
     * options, non-interactive. [HijriWidgetPreviewPublisher] publishes the result.
     */
    @OptIn(ExperimentalGlanceApi::class)
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val options = HijriWidgetConfig.loadFamily(context)
        if (options.source.pakistan) {
            PakistanWarmUp.ensureWarm()
        }
        val colors = WidgetColors.forTheme(options.theme)
        provideContent {
            val data = buildRenderData(context, options, viewedMonth = null)
            HijriTodayRoot(
                today = data.todayHijri,
                colors = colors,
                openAction = null,
                layoutRtl = data.layoutRtl,
                language = options.language,
            )
        }
    }
}

/**
 * The strip's type sizes and vertical budget, as data so the Android 12-14 `previewLayout` mirror
 * and its test can be held to them — the same arrangement as [DateTileTypography].
 *
 * **Three lines, not two.** The weekday name goes above the pair of dates rather than into either
 * caption, for the reason [DateTileRoot] gives for the tiles: merged into a caption it stops being the
 * answer to "what day is it?" and becomes small print beside a month name. It is centred across the
 * whole strip, **not repeated on each side** — both halves are the same physical day, so a second copy
 * would say nothing the first did not.
 *
 * [daySize] is unchanged at 26sp: the day figure is what the strip exists to show, and the third line
 * is paid for in height (see [STRIP_MIN_HEIGHT_DP]) rather than by shrinking it.
 */
internal object TodayStripTypography {

    /**
     * The weekday name, centred above both dates.
     *
     * Bold and in the primary text colour, matching the tiles' weekday line rather than this strip's
     * own caption styling — it is part of the date, not a caption under it.
     *
     * **Sized for the emphasis the tiles' line gets, not for the strip's caption.** `FontWeight.Bold`
     * is the heaviest weight Glance has (700 — there is nothing above it), so with the tiles using the
     * same weight the only thing that made their weekday look heavier was that it is far larger. It
     * first shipped a point above the caption and read as the weakest line on a widget whose top line
     * is nothing but the weekday.
     *
     * It is still well under [daySize], so the day figure stays the loudest thing.
     */
    val weekdaySize = 17.sp

    /**
     * The day figure in each half, sized to the height the launcher granted rather than fixed.
     *
     * This is the same move as `DateTileTypography.weekdaySizeFor(availableWidth)`, on the other axis:
     * the strip is vertically resizable, so the height is the one dimension the host genuinely varies,
     * and a fixed figure either wastes a tall placement or overflows a short one. Deriving it from
     * [LocalSize] is what lets the figure **take the available height** — it grows into whatever space
     * the weekday line and the month line have not already claimed, which is exactly the dead leading
     * above the digit that cannot be removed any other way.
     *
     * [grantedHeightDp] is the **whole** widget height the launcher granted, as `LocalSize` reports it;
     * the padding and the two other lines are subtracted here rather than by the caller, so a caller
     * cannot get it wrong by forgetting one of them.
     *
     * Linear in height, then clamped at both ends:
     *
     * - **A floor**, because [DAY_HEIGHT_REFERENCE_DP] is a *minimum*: a launcher is free to grant
     *   less than it (down to `minResizeHeight`), and a figure that kept shrinking below the size the
     *   declared minimum can hold would clip the month line off the bottom — the failure this whole
     *   layout is arranged to avoid.
     * - **A ceiling**, because a strip stretched across four launcher cells should still read as a
     *   strip and not as a clock.
     *
     * Pure and Dp-free so it can be asserted without a `Context` or a composition.
     */
    fun daySizeFor(grantedHeightDp: Float): TextUnit {
        val scaled = ((grantedHeightDp - VERTICAL_PADDING_DP * 2 - nonDayHeightDp()) / LINE_HEIGHT_RATIO)
            .coerceIn(daySizeFloor.value, daySizeCeiling.value)
        return scaled.sp
    }

    /**
     * The height the two non-figure lines ask for: the weekday line, its gap, the month line and its
     * gap. What [daySizeFor] subtracts from the granted height to get the room the figure has left.
     */
    fun nonDayHeightDp(): Float =
        (weekdaySize.value + captionSize.value) * LINE_HEIGHT_RATIO + lineGap.value * 2

    /** Never smaller than this, however short the strip — below it the month line would be clipped. */
    val daySizeFloor = 20.sp

    /** Never larger than this, however tall: a strip should still read as a strip. */
    val daySizeCeiling = 40.sp

    /** The month + year (and era) caption under each day figure — the bottom line of each column. */
    val captionSize = 11.sp

    /** Gap under the weekday line, and under each day figure. */
    val lineGap = 1.dp

    /**
     * The declared minimum height, in dp.
     *
     * **Sized against the height a *placed* strip has, not against whatever would be comfortable.**
     * That is the whole lesson of this layout: an already-placed widget keeps the size the launcher
     * granted it, and raising `minHeight` in the XML does **not** resize it. Growing the type without
     * this figure moving with it is what pushed the month caption off the bottom of a strip whose
     * numbers had been enlarged — the widget stayed its old height, the content outgrew it, and the
     * last line was what got clipped.
     *
     * So the type here is set to fill this, and the figure is the three lines at [LINE_HEIGHT_RATIO]
     * each plus the two gaps and the vertical padding, which `TodayStripPreviewLayoutTest` recomputes
     * from these constants so the widget-info XMLs and the composition cannot drift apart.
     */
    const val STRIP_MIN_HEIGHT_DP = 72

    /**
     * The height at which [daySizeFor] has room for the whole of the three lines — the widget-info's
     * declared minimum. The one dimension the host *cannot* vary downwards past, so it is what a static
     * `previewLayout` mirror has to render, for the same reason the tiles' mirror renders the weekday
     * at `DateTileTypography.WEEKDAY_REFERENCE_WIDTH_DP`.
     */
    val DAY_HEIGHT_REFERENCE_DP: Float = STRIP_MIN_HEIGHT_DP.toFloat()

    /** The floor for a vertical resize — just below the natural height of the three lines. */
    const val STRIP_MIN_RESIZE_HEIGHT_DP = 68

    /** Horizontal padding either side of the whole strip. */
    const val HORIZONTAL_PADDING_DP = 12

    /**
     * Vertical padding above and below the three lines.
     *
     * Down from 4dp, because every dp here comes out of the budget the type has to fit in. The root
     * column is centre-aligned rather than pinned to the top, and that is what lets the leftover
     * height land somewhere it reads as intentional: a top-pinned column renders its content first and
     * shows all the slack in one place — under the day figures, as the dead leading inside their line
     * boxes. Centring splits it between the top and the bottom instead.
     *
     * That leading is not padding and cannot be removed: Glance boxes every `Text` at the font's
     * ascent plus descent, `PaddingModifier` is non-negative, and Glance 1.2's `TextStyle` has no
     * `lineHeight`.
     */
    const val VERTICAL_PADDING_DP = 2

    /**
     * A `Text`'s rendered line box as a multiple of its font size.
     *
     * Glance gives each `Text` a box of the font's natural ascent plus descent; the strip's height
     * arithmetic cannot ignore it. `TodayStripPreviewLayoutTest` uses this to check that the declared
     * minimum actually holds the three lines.
     */
    const val LINE_HEIGHT_RATIO = 1.17f
}

/**
 * The strip's layout: the weekday name centred across the top, then the Gregorian and Hijri dates
 * side by side below it, separated by a vertical hairline. Each half is a bold day figure with the
 * month + year underneath.
 *
 * The Hijri side always sits on the right (the Gregorian on the left), regardless of the widget's
 * reading direction: the children are ordered by [layoutRtl] so platform RTL mirroring lands and keeps
 * the Hijri half on the right. The divider goes between the two halves in both orderings, which is the
 * only placement that is correct in either.
 */
@Composable
internal fun HijriTodayRoot(
    today: TodayHijriWidgetData?,
    colors: WidgetColors,
    openAction: Action?,
    layoutRtl: Boolean,
    language: WidgetLanguage,
) {
    val fonts = HijriWidgetFonts.default
    val cell = LocalSize.current
    val modifier = GlanceModifier.fillMaxSize().background(colors.background)
    val clickableModifier = if (openAction != null) modifier.clickable(openAction) else modifier

    // The day figures take whatever height the launcher granted, once the weekday line, the month line,
    // the gaps and the padding have had theirs — so a tall placement renders a bigger figure and a short
    // one a smaller figure, and neither clips the month line. See [TodayStripTypography.daySizeFor].
    val daySize = TodayStripTypography.daySizeFor(cell.height.value)

    Column(
        modifier = clickableModifier.padding(
            horizontal = TodayStripTypography.HORIZONTAL_PADDING_DP.dp,
            vertical = TodayStripTypography.VERTICAL_PADDING_DP.dp,
        ),
        // Centred, not top-pinned — see [TodayStripTypography.VERTICAL_PADDING_DP]. A top-pinned column
        // renders its content first and dumps every dp of slack in one place, under the day figures.
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        if (today == null) {
            Text(
                // Resolved from the widget's own language, not the device locale (WG-12) — see
                // [WidgetLocalization.ChromeLabels].
                text = WidgetLocalization.ChromeLabels.monthUnavailable(language),
                style = TextStyle(
                    color = colors.secondaryText,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                ),
            )
            return@Column
        }
        // The weekday both halves share: `TodayHijriWidgetData.weekdayName`, projected in the widget's
        // own language (WG-12) since the projection was written, and rendered by iOS and by every 1x1
        // tile. This strip was the one place it was dropped.
        Text(
            text = today.weekdayName,
            modifier = GlanceModifier.padding(bottom = TodayStripTypography.lineGap),
            style = TextStyle(
                color = colors.primaryText,
                fontSize = TodayStripTypography.weekdaySize,
                fontWeight = FontWeight.Bold,
                fontFamily = fonts.weekday.toGlanceFontFamily(),
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
        Row(
            // `fillMaxWidth` is what makes the two halves *spread*: `defaultWeight` splits whatever
            // width the Row is given, and a Row that sizes to its content in a centred Column would
            // instead leave both dates bunched in the middle with the widget's spare width as margins.
            // Stating it here rather than relying on the weight to stretch the Row makes the halves'
            // share of the width independent of how the platform measures an unconstrained Row.
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            val hijri = @Composable {
                DateSide(
                    dayText = today.hijriDayText,
                    // Era-appended by the projection (FD-05); a renderer must not format the era
                    // itself, because the marker follows the widget's language, not the device (WG-12).
                    caption = "${today.hijriMonthName} ${today.hijriYearText}",
                    daySize = daySize,
                    colors = colors,
                    gregorian = false,
                )
            }
            val gregorian = @Composable {
                DateSide(
                    dayText = today.gregorianDayText,
                    caption = "${today.gregorianMonthName} ${today.gregorianYearText}",
                    daySize = daySize,
                    colors = colors,
                    gregorian = true,
                )
            }
            // Order so the Hijri side lands on the right in either reading direction.
            if (layoutRtl) {
                hijri()
                DateDivider(colors)
                gregorian()
            } else {
                gregorian()
                DateDivider(colors)
                hijri()
            }
        }
    }
}

/**
 * The hairline between the two date halves.
 *
 * `widget_cell_border` is the grid's own divider token (FD-04), reused rather than given a second
 * near-identical colour: it is already a hairline alpha over the surface with a night variant, which
 * is exactly what this needs, and it is a `@ColorRes` `ColorProvider` like every other member of
 * [WidgetColors] so a night-mode switch re-resolves it at bind time rather than restarting the widget
 * (FD-07).
 *
 * Unweighted, so it stays a hairline rather than taking a share of the width from the two dates; the
 * [DIVIDER_GAP_DP] padding either side is what keeps it from touching their text, which matters because
 * each half is only half the strip's width and a long caption can reach all the way to the middle.
 */
@Composable
private fun RowScope.DateDivider(colors: WidgetColors) {
    Box(
        modifier = GlanceModifier
            .padding(horizontal = DIVIDER_GAP_DP.dp)
            .width(1.dp)
            .fillMaxHeight()
            .background(colors.cellBorder),
    ) {}
}

/** Clear space either side of [DateDivider]. */
private const val DIVIDER_GAP_DP = 5

/**
 * One half of the strip: a bold day figure with the month + year line underneath. Weighted, so
 * the two halves split the width and sit visibly at the strip's two ends.
 */
@Composable
private fun RowScope.DateSide(
    dayText: String,
    caption: String,
    /** Derived from the granted height by the caller — see [TodayStripTypography.daySizeFor]. */
    daySize: TextUnit,
    colors: WidgetColors,
    /**
     * Which calendar this half shows. The strip renders both through this one composable, so the caption's
     * font slot cannot be inferred from the text — see [HijriWidgetFonts.forMonthTitle].
     *
     * It now decides the **day figure's** colour too, which is where the halves' remaining distinction
     * lives: the Hijri figure is the accent colour, the Gregorian one primary. Taking the palette rather
     * than a per-side colour is what makes that a rule instead of two call-site arguments that can drift.
     */
    gregorian: Boolean,
) {
    val fonts = HijriWidgetFonts.default
    val dayColor = if (gregorian) colors.primaryText else colors.accent
    Column(
        modifier = GlanceModifier.defaultWeight(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        Text(
            text = dayText,
            style = TextStyle(
                color = dayColor,
                fontSize = daySize,
                fontWeight = FontWeight.Bold,
                fontFamily = fonts.dayNumber.toGlanceFontFamily(),
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
        Text(
            text = caption,
            modifier = GlanceModifier.padding(top = TodayStripTypography.lineGap),
            style = TextStyle(
                color = colors.primaryText,
                fontSize = TodayStripTypography.captionSize,
                fontWeight = FontWeight.Medium,
                fontFamily = fonts.forMonthTitle(gregorian).toGlanceFontFamily(),
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
    }
}
