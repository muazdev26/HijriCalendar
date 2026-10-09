package com.muazdev.hijricalendar.widget.glance

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
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
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.muazdev.hijricalendar.widgetdata.WidgetLanguage
import com.muazdev.hijricalendar.widgetdata.WidgetLocalization

/**
 * A fixed 1x1 tile showing today's Hijri date as a weekday name over a big day figure over the
 * localized month name. Has no settings screen and no per-widget state: it renders with the
 * family options mirror ([HijriWidgetConfig.loadFamily]) written by the calendar-grid
 * widget's configuration screen, and is swept on every family refresh alongside the Today strip.
 * Tapping anywhere opens the app.
 */
public class HijriDateWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Same one-shot peek as the rest of the family: port legacy config (seeding the family
        // mirror once) and warm up the Pakistan century table if the family now opts into it.
        HijriWidgetConfig.migrateLegacyIfNeeded(context, id)
        val options = HijriWidgetConfig.loadFamily(context)
        if (options.source.pakistan) {
            PakistanWarmUp.ensureWarm()
        }
        val colors = WidgetColors.DEFAULT
        val openAction = actionStartActivity(openAppIntent(context))

        provideContent {
            val today = HijriWidgetRenderCache.today(
                glanceId = id.toString(),
                options = options,
                todayEpochDay = HijriWidgetRefreshScheduler.todayEpochDay(),
            )
            DateTileRoot(
                dayText = today?.hijriDayText,
                monthText = today?.hijriMonthName,
                weekdayText = today?.weekdayName,
                colors = colors,
                openAction = openAction,
                language = options.language,
                gregorian = false,
            )
        }
    }

    /**
     * Real picker preview for Android 15+: today's Hijri date in the family's current options,
     * non-interactive. [HijriWidgetPreviewPublisher] publishes the result.
     */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val options = HijriWidgetConfig.loadFamily(context)
        if (options.source.pakistan) {
            PakistanWarmUp.ensureWarm()
        }
        val colors = WidgetColors.DEFAULT
        provideContent {
            val data = buildRenderData(context, options, viewedMonth = null)
            DateTileRoot(
                dayText = data.todayHijri?.hijriDayText,
                monthText = data.todayHijri?.hijriMonthName,
                weekdayText = data.todayHijri?.weekdayName,
                colors = colors,
                openAction = null,
                language = options.language,
                gregorian = false,
            )
        }
    }
}

/**
 * A fixed 1x1 tile showing today's Gregorian date in the same three-line layout as
 * [HijriDateWidget] — weekday name, day figure, month name. Same family-options-mirror rendering
 * and the same tap-to-open-app behaviour.
 */
public class GregorianDateWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        HijriWidgetConfig.migrateLegacyIfNeeded(context, id)
        val options = HijriWidgetConfig.loadFamily(context)
        if (options.source.pakistan) {
            PakistanWarmUp.ensureWarm()
        }
        val colors = WidgetColors.DEFAULT
        val openAction = actionStartActivity(openAppIntent(context))

        provideContent {
            val today = HijriWidgetRenderCache.today(
                glanceId = id.toString(),
                options = options,
                todayEpochDay = HijriWidgetRefreshScheduler.todayEpochDay(),
            )
            DateTileRoot(
                dayText = today?.gregorianDayText,
                monthText = today?.gregorianMonthName,
                weekdayText = today?.weekdayName,
                colors = colors,
                openAction = openAction,
                language = options.language,
                gregorian = true,
            )
        }
    }

    /**
     * Real picker preview for Android 15+: today's Gregorian date in the family's current options,
     * non-interactive. [HijriWidgetPreviewPublisher] publishes the result.
     */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val options = HijriWidgetConfig.loadFamily(context)
        if (options.source.pakistan) {
            PakistanWarmUp.ensureWarm()
        }
        val colors = WidgetColors.DEFAULT
        provideContent {
            val data = buildRenderData(context, options, viewedMonth = null)
            DateTileRoot(
                dayText = data.todayHijri?.gregorianDayText,
                monthText = data.todayHijri?.gregorianMonthName,
                weekdayText = data.todayHijri?.weekdayName,
                colors = colors,
                openAction = null,
                language = options.language,
                gregorian = true,
            )
        }
    }
}

/**
 * The tile's three type sizes, as data so the Android 12-14 `previewLayout` mirror can be held to
 * them.
 *
 * Three lines: **weekday name, day figure, month name**, each on its own row. Merging the weekday
 * and the month onto one caption line was tried and is wrong — it reads as a single run of small
 * text with a number under it, and the weekday name stops being the answer to "what day is it?"
 * that the whole change exists to give.
 *
 * The vertical budget is what forces these numbers. The widget-info declares a 40dp minimum, and
 * three lines of type cannot fit 40dp at any size worth reading — so these are sized for the cell a
 * launcher actually grants (a 1x1 is typically well over 40dp tall) rather than for the declared
 * floor, and the day figure is smaller than it used to be so that all three lines fit together.
 * `DateTilePreviewLayoutTest` records the arithmetic rather than asserting a fit that 40dp cannot
 * deliver.
 */
internal object DateTileTypography {

    /**
     * The localized weekday name, on its own line at the top, sized to the width the launcher gave.
     *
     * **Same treatment as the month name and bold, deliberately.** It shipped as 10sp Medium in
     * `widget_text_secondary` and read as a caption rather than as part of the date — the weekday is
     * half the answer to "what day is it?", and it was the weakest line on the tile.
     *
     * Sized by [weekdaySizeFor] rather than fixed, because the tile's width is the one dimension the
     * host actually varies: `SizeMode.Single` means there is a single bucket, but launchers disagree
     * on how wide a 1x1 is by tens of dp, and a name that reads comfortably in one reads as a caption
     * in another. See [weekdaySizeFor] for the rule and its limits.
     *
     * **The gap under this line is not padding and cannot be removed here.** Nothing pads it; the
     * Column's only vertical padding is [VERTICAL_PADDING_DP] around all three lines. What shows as
     * a gap is line-box leading: Glance gives each `Text` a box of the font's natural
     * ascent-plus-descent (roughly `1.17x` the size), and the 26sp day figure below carries a lot of
     * empty box above its digit. Squeezing it would need a `lineHeight` on the `TextStyle`, and
     * **Glance 1.2.0 has no such parameter** — `androidx.glance.text.TextStyle` exposes only color,
     * fontSize, fontWeight, fontStyle, textAlign, textDecoration and fontFamily. Confirmed 2026-10-06:
     * an explicit `lineHeight` fails to compile against the pinned Glance. The remaining levers are
     * the three sizes here, and shrinking the day figure to buy vertical room is a worse trade than
     * the gap.
     */
    fun weekdaySizeFor(availableWidth: Dp): TextUnit = weekdaySizeFor(availableWidth.value)

    /**
     * The weekday font size for [availableWidth], clamped to
     * [[weekdaySizeFloor], [weekdaySizeCeiling]].
     *
     * **This one rule now sizes both caption lines** — the weekday on top and the month name at the
     * bottom. They were separate functions with separate bounds (32sp/14–34 against 28sp/10–30) until
     * the year came off the bottom line. That change is what allowed them to merge: the reason for the
     * month line's tighter ceiling was that it was *twice as long* than a weekday name, carrying a
     * month **and** an era'd year — `محرم ١٤٤٨ ھ` against `جمعرات`. With the year gone it is `محرم`,
     * the same class of string as the weekday above it, so a separate rule bought nothing and only
     * created a second set of numbers to keep in step.
     *
     * Merging makes them equal at *every* width, not merely at the reference width, which is the
     * stronger version of the FD-01 styling the separate rule was trying to approximate.
     *
     * Linear in width about [WEEKDAY_REFERENCE_WIDTH_DP], so a wider tile gets proportionally larger
     * text and a narrower one proportionally smaller text. Three deliberate bounds:
     *
     * - **A floor, and it is the important one.** At the 40dp minimum the tile-info declares there
     *   are only about 28dp of text width, which works out below [WEEKDAY_REFERENCE_WIDTH_DP] and lands
     *   on the floor. `Thursday` is the longest string on the tile — twice the width of `جمعرات`'s
     *   glyph run in most fonts — so an unbounded scale would render it as a clipped stub on a
     *   launcher that grants a tight cell, which is worse than a small one.
     * - **A ceiling**, because the text still has to leave room for the day figure between the two
     *   caption lines. A tile is not a banner.
     * - **Monotonicity** is the property the tests pin: never smaller as the tile gets wider.
     *
     * Pure and Dp-free so it can be asserted without a `Context` or a composition.
     */
    fun weekdaySizeFor(availableWidth: Float): TextUnit {
        val scaled = weekdaySizeAtReference.value * availableWidth / WEEKDAY_REFERENCE_WIDTH_DP
        return scaled.coerceIn(weekdaySizeFloor.value, weekdaySizeCeiling.value).sp
    }

    /**
     * The width at which [weekdaySizeFor] returns [weekdaySizeAtReference].
     *
     * Roughly a 1x1 cell on a current Android launcher. It is a design constant, not a measurement —
     * there is no way to ask a launcher what it will grant — and the floor exists so being wrong in
     * either direction is survivable.
     */
    const val WEEKDAY_REFERENCE_WIDTH_DP: Float = 72f

/** The weekday size at [WEEKDAY_REFERENCE_WIDTH_DP]. */
    val weekdaySizeAtReference = 32.sp

    /** Never smaller than this, however narrow the tile. */
    val weekdaySizeFloor = 14.sp

    /**
     * Never larger than this, however wide.
     *
     * Set above [weekdaySizeAtReference] on purpose: when it sat *below* it, the clamp made this
     * line a constant — every width past ~38dp returned the ceiling and the scale did nothing. The
     * ceiling's job is to stop a name crowding out the day figure, not to pre-empt the reference.
     */
    val weekdaySizeCeiling = 34.sp

    /** Horizontal padding on each side; the text width is the tile's width less twice this. */
    const val PADDING_DP = 6

    /**
     * The day figure — the thing the tile exists to show. Fixed: it is not the length-sensitive one,
     * and it is not width-scaled.
     */
    val daySize = 36.sp

    /**
     * Vertical padding inside the tile's own edges.
     *
     * **Back to 2dp.** This was raised to 6dp to match [PADDING_DP], which was wrong twice over: the
     * column is centre-aligned, so symmetric padding cannot move anything — it only shrinks the content
     * box — and on a short cell the extra 8dp came straight out of the bottom line. That is what sliced
     * the month name in half, which was reported as the padding change being "very bad".
     *
     * A band-based layout that sized each line from its own share of the granted height was tried as the
     * way to remove the clipping for good. It was reverted: holding the captions to a quarter of the cell
     * cost ~10sp of their size, and it made the width scaling inert at any typical cell height. This is
     * the simplest thing that rendered correctly, and the vertical budget it implies is recorded in
     * `DateTilePreviewLayoutTest` rather than left to be discovered on a device.
     */
    const val VERTICAL_PADDING_DP = 2

    /** The widget-info's declared minimum tile height. Read by the test that records the budget. */
    const val TILE_MIN_HEIGHT_DP = 40

    /**
     * The widget-info's declared minimum tile width — and therefore the width the weekday name has at
     * its smallest, which is what puts [weekdaySizeFloor] to work.
     */
    const val TILE_MIN_WIDTH_DP = 40
}

/**
 * The shared 1x1 tile layout: the localized weekday name on top, a big centred day figure in the
 * middle, the month name at the bottom. Tapping anywhere opens the app.
 *
 * The weekday name comes from `TodayHijriWidgetData.weekdayName`, which the shared projection has
 * always populated from the widget's own [WidgetLanguage] and which iOS has always rendered — the
 * Android tiles and the strip were the only places it was dropped. So it is localized per widget, not per
 * device (WG-12), and this ticket adds no field to the options schema.
 *
 * The weekday line is omitted when there is no readable date, in which case the tile says so via
 * [WidgetLocalization.ChromeLabels.monthUnavailable] instead of showing an empty line above a
 * fallback.
 */
@Composable
internal fun DateTileRoot(
    dayText: String?,
    /**
     * The bottom line: the month name alone, e.g. `محرم` / `August`.
     *
     * **No year, and no era marker.** This line used to read `محرم ١٤٤٨ ھ` (FD-05) and was assembled
     * by the caller, which meant the year came from `TodayHijriWidgetData`'s era'd
     * `hijriYearText`. Both halves of that are gone: the projection still carries both, because the
     * grid widget shows them, but a tile has no room for either. A 1x1 is read at a glance from arm's
     * length, where the day figure and the month name are the whole answer and a four-digit year is
     * the part that is almost always the current one anyway.
     *
     * It is the month name and nothing else rather than a date assembled elsewhere, so the tile cannot
     * drift into showing a year again on one of its four render paths.
     */
    monthText: String?,
    weekdayText: String?,
    colors: WidgetColors,
    openAction: Action?,
    language: WidgetLanguage,
    /**
     * Which calendar this tile shows, so the bottom line picks [HijriWidgetFonts.monthTitle] or
     * [HijriWidgetFonts.gregorianTitle]. Both tiles share one composable, so this cannot be inferred
     * from the text.
     */
    gregorian: Boolean,
) {
    val fonts = HijriWidgetFonts.default
    val cell = LocalSize.current
    val modifier = GlanceModifier.fillMaxSize().background(colors.background)
    val clickableModifier = if (openAction != null) modifier.clickable(openAction) else modifier

    // The granted width, so the weekday name can be sized to it. `SizeMode.Single` still reports a
    // real size, and it varies by tens of dp between launchers.
    val availableWidth = cell.width - DateTileTypography.PADDING_DP.dp * 2

    val captionSize = DateTileTypography.weekdaySizeFor(availableWidth)

    Column(
        modifier = clickableModifier.padding(
            horizontal = DateTileTypography.PADDING_DP.dp,
            vertical = DateTileTypography.VERTICAL_PADDING_DP.dp,
        ),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        if (dayText == null) {
            Text(
                // Resolved from the widget's own language, not the device locale (WG-12) — see
                // [WidgetLocalization.ChromeLabels].
                text = WidgetLocalization.ChromeLabels.monthUnavailable(language),
                style = TextStyle(
                    color = colors.secondaryText,
                    fontSize = captionSize,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
            return@Column
        }
        CaptionBand(
            text = weekdayText.orEmpty(),
            fontSize = captionSize,
            fontFamily = fonts.weekday.toGlanceFontFamily(),
            color = colors.primaryText,
        )
        Text(
            text = dayText,
            style = TextStyle(
                color = colors.accent,
                fontSize = DateTileTypography.daySize,
                fontWeight = FontWeight.Bold,
                fontFamily = fonts.dayNumber.toGlanceFontFamily(),
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
        CaptionBand(
            text = monthText.orEmpty(),
            fontSize = captionSize,
            // The Hijri tile shows a Hijri month name, the Gregorian one a Gregorian one; the caller
            // says which by naming the field it passes in.
            fontFamily = fonts.forMonthTitle(gregorian).toGlanceFontFamily(),
            color = colors.primaryText,
        )
    }
}

/**
 * One caption line in its own band, centred.
 *
 * The weekday and the month name are the same line twice over: same band, same size, same weight, same
 * colour, same single-line clamp. They were duplicated inline and that duplication is why they were
 * styled as a pair once and could drift apart later — the weekday went bold and the month stayed
 * Medium for a while. One function makes them unable to.
 */
@Composable
private fun CaptionBand(
    text: String,
    fontSize: TextUnit,
    fontFamily: FontFamily?,
    color: ColorProvider,
) {
    Text(
        text = text,
        style = TextStyle(
            // `primaryText`, not `secondaryText`: the weekday is part of the date, not a caption
            // under it, and the secondary tone is what made it read as one. The month name is bold for
            // the same reason it was raised — a glance is most often after *which month*.
            color = color,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            fontFamily = fontFamily,
            textAlign = TextAlign.Center,
        ),
        maxLines = 1,
    )
}
