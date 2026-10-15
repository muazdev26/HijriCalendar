package com.muazdev.hijricalendar.widget.glance

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
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
 * A resizable tile showing today's Hijri date as a weekday name over a big day figure over the
 * localized month name. Has no settings screen and no per-widget state: it renders with the
 * family options mirror ([HijriWidgetConfig.loadFamily]) written by the calendar-grid
 * widget's configuration screen, and is swept on every family refresh alongside the Today strip.
 * Tapping anywhere opens the app.
 *
 * Placed as a 1x1, but **resizable on both axes** (point 7): the type is derived from the cell the
 * launcher actually granted, so a 2x1 or 2x2 placement renders larger text rather than the same
 * three lines inside more empty space. [sizeMode] is what makes that work — see the override.
 */
public class HijriDateWidget : GlanceAppWidget() {

    /**
     * `SizeMode.Exact`, not `Single`.
     *
     * `Single` hands the composition `LocalSize` = the provider's declared minimum
     * (`AppWidgetUtils.kt` → `SizeMode.Single -> listOf(minSize)`), *not* the size the launcher
     * granted — and a declared minimum is a floor, so [DateTileTypography.sizesFor] would be
     * computing from 40dp no matter how far the user stretched the tile. Under `Single` this tile
     * could only ever render one size, which is the whole of what point 7 asks it not to do.
     *
     * `Exact` composes once per size in the host's options bundle, so a resize re-runs the
     * composition with the new size and the type grows with it. It is safe for this tile in a way
     * it would not be for the grid widget: the tile's only interaction is one
     * `actionStartActivity`, which is a `PendingIntent` attached to the `RemoteViews` — a tap fires
     * the one on the displayed view, so multiple size compositions cannot double-launch the app.
     *
     * `Exact` is not a `PreviewSizeMode`, so the picker preview is declared separately:
     * `DateTileTypography.REFERENCE_CELL`, the cell the sizes are tuned against, rather than the
     * bare 40dp minimum the picker would otherwise compose at.
     */
    override val sizeMode: SizeMode = SizeMode.Exact

    override val previewSizeMode: PreviewSizeMode = SizeMode.Responsive(
        setOf(DateTileTypography.REFERENCE_CELL),
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
                "type=HijriDate overrides=${options.monthLengthOverrides.size} language=${options.language} optionsFrom=$optionsFrom",
            )
            val colors = WidgetColors.forTheme(options.theme)
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
        HijriWidgetRefreshLog.d(
            "render:tile",
            "type=HijriDate overrides=${options.monthLengthOverrides.size} language=${options.language} optionsFrom=family",
        )
        val colors = WidgetColors.forTheme(options.theme)
        provideContent {
            val data = buildRenderData(context, options, viewedMonth = null, glanceId = "preview", optionsFrom = "family")
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
 * A resizable tile showing today's Gregorian date in the same three-line layout as
 * [HijriDateWidget] — weekday name, day figure, month name. Same family-options-mirror rendering,
 * same tap-to-open-app behaviour, and the same resizable/[SizeMode.Exact] sizing.
 */
public class GregorianDateWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override val previewSizeMode: PreviewSizeMode = SizeMode.Responsive(
        setOf(DateTileTypography.REFERENCE_CELL),
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
                "type=GregorianDate overrides=${options.monthLengthOverrides.size} language=${options.language} optionsFrom=$optionsFrom",
            )
            val colors = WidgetColors.forTheme(options.theme)
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
        HijriWidgetRefreshLog.d(
            "render:tile",
            "type=GregorianDate overrides=${options.monthLengthOverrides.size} language=${options.language} optionsFrom=family",
        )
        val colors = WidgetColors.forTheme(options.theme)
        provideContent {
            val data = buildRenderData(context, options, viewedMonth = null, glanceId = "preview", optionsFrom = "family")
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
 *
 * Point 7 changed *which* cell that is: [HijriDateWidget.sizeMode] is `Exact`, so `LocalSize` is the
 * size the launcher granted rather than the declared minimum, and the ceilings were raised
 * (16/30 → 22/44) so a stretched tile has somewhere to grow. The caption tracks the granted width,
 * the figure stays twice the caption, and both are capped.
 */
internal data class DateTileSizes(
    val captionSize: TextUnit,
    val daySize: TextUnit,
)

internal object DateTileTypography {

    fun weekdaySizeFor(availableWidth: Dp): TextUnit = weekdaySizeFor(availableWidth.value)

    fun weekdaySizeFor(availableWidth: Float): TextUnit {
        val scaled = weekdaySizeAtReference.value * availableWidth / WEEKDAY_REFERENCE_WIDTH_DP
        return scaled.coerceIn(weekdaySizeFloor.value, weekdaySizeCeiling.value).sp
    }

    const val WEEKDAY_REFERENCE_WIDTH_DP: Float = 72f
    const val REFERENCE_HEIGHT_DP: Float = 72f

    /**
     * The cell the sizes are tuned against: a 1x1 on a phone launcher. Used as the picker's preview
     * size, because a preview composed at the bare 40dp minimum would advertise three unreadable
     * lines as if they were the tile.
     */
    val REFERENCE_CELL = DpSize(WEEKDAY_REFERENCE_WIDTH_DP.dp, REFERENCE_HEIGHT_DP.dp)

    /**
     * A `Text` line box as a multiple of its font size. Glance boxes every `Text` at ascent +
     * descent (there is no `lineHeight` on `TextStyle`), so the three lines claim
     * `(caption + day + caption) * LINE_HEIGHT_RATIO` and no layout nudge can reclaim it.
     */
    const val LINE_HEIGHT_RATIO: Float = 1.2f

    val weekdaySizeAtReference = 13.sp
    val weekdaySizeFloor = 10.sp

    /** The caption's ceiling at a large cell. The figure is twice it; see [sizesFor]. */
    val weekdaySizeCeiling = 22.sp

    val daySize = 26.sp
    val daySizeFloor = 18.sp
    val daySizeCeiling = 44.sp

    const val PADDING_DP = 6
    const val VERTICAL_PADDING_DP = 2

    const val TILE_MIN_HEIGHT_DP = 40
    const val TILE_MIN_WIDTH_DP = 40

    fun sizesFor(availableWidthDp: Float, availableHeightDp: Float): DateTileSizes {
        val widthCaption = weekdaySizeFor(availableWidthDp).value
        // Three lines, the figure twice a caption: `caption + 2*caption + caption` = four captions
        // of line box. The old `4.68` under-counted the row count, so a tall cell could be asked
        // for ~3% more height than it had; `4 * LINE_HEIGHT_RATIO` is exactly the three line boxes.
        val heightMaxCaption = availableHeightDp / (4f * LINE_HEIGHT_RATIO)
        val caption = minOf(widthCaption, heightMaxCaption)
            .coerceIn(weekdaySizeFloor.value, weekdaySizeCeiling.value)
            .sp

        val day = (caption.value * 2.0f)
            .coerceIn(daySizeFloor.value, daySizeCeiling.value)
            .sp

        return DateTileSizes(captionSize = caption, daySize = day)
    }
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

    // Sized to both width and height granted by launcher so text fits cleanly without clipping.
    val availableWidth = cell.width - DateTileTypography.PADDING_DP.dp * 2
    val availableHeight = cell.height - DateTileTypography.VERTICAL_PADDING_DP.dp * 2
    val sizes = DateTileTypography.sizesFor(availableWidth.value, availableHeight.value)

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
                    fontSize = sizes.captionSize,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
            return@Column
        }
        CaptionBand(
            text = weekdayText.orEmpty(),
            fontSize = sizes.captionSize,
            fontFamily = fonts.weekday.toGlanceFontFamily(),
            color = colors.primaryText,
        )
        Text(
            text = dayText,
            style = TextStyle(
                color = colors.accent,
                fontSize = sizes.daySize,
                fontWeight = FontWeight.Bold,
                fontFamily = fonts.dayNumber.toGlanceFontFamily(),
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
        CaptionBand(
            text = monthText.orEmpty(),
            fontSize = sizes.captionSize,
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
