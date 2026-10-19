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
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
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
            // The 1x1 tile's caption is one line at 40dp, where the full month name clips. `Oct` and
            // `Rabi' I` are what the projection built for exactly this slot (see
            // TodayHijriWidgetData.gregorianMonthShortName / hijriMonthShortName) — English month
            // names only: Urdu month names are never shortened, so an Urdu tile renders in full.
            DateTileRoot(
                dayText = today?.hijriDayText,
                monthText = today?.hijriMonthShortName,
                weekdayText = today?.weekdayTileName,
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
                monthText = data.todayHijri?.hijriMonthShortName,
                weekdayText = data.todayHijri?.weekdayTileName,
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
                monthText = today?.gregorianMonthShortName,
                weekdayText = today?.weekdayTileName,
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
                monthText = data.todayHijri?.gregorianMonthShortName,
                weekdayText = data.todayHijri?.weekdayTileName,
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
 * (16/30 → 22/44) so a stretched tile has somewhere to grow.
 *
 * **Raised again, 22/44 → 32/64, because the first raise was still inside the range a launcher hands
 * out.** Measured on a Pixel 6a with the bundled Nastaliq face, a 3-column tile reached `MAX_SCALE`
 * (1.69) on both axes, and every further resize — a tile grown to four rows kept the *same* 140×56 px
 * weekday and 118×85 px figure — changed nothing. That is the "the weekday never grows when I resize
 * it" report; it was this constant, not the renderer. 32/64 puts the ceiling at a ~189dp-wide /
 * ~248dp-tall cell, i.e. a ~3×3 tile, so the whole range a 1x1 is actually resized into scales, and
 * only a tile far larger than its own reading distance saturates. Growing the ceiling cannot clip: the
 * *height* axis already bounds the scale at exactly the height the stack asks for, so a raised ceiling
 * only lets a cell that is genuinely bigger render genuinely bigger.
 *
 * **Every line is now derived from one scale** (see [widgetTypeScale]) rather than from per-line
 * width and height rules: the caption is the scale times [DateTileTypography.weekdaySizeAtReference]
 * and the figure is exactly twice it, so a cell that grows tall but not wide (or the reverse) moves
 * all three lines together and cannot push one off the bottom. The band the weekday sits in is part
 * of that stack, which is why [DateTileTypography.REFERENCE_STACK_HEIGHT_DP] counts its padding.
 */
internal data class DateTileSizes(
    val captionSize: TextUnit,
    val daySize: TextUnit,
) {
    /**
     * The height these three lines plus the weekday band ask for, in dp: both captions at
     * [scriptRatio], the figure at [DIGIT_LINE_RATIO], and the band's own padding. What a caller
     * asserts against the height the launcher granted.
     */
    fun requestedHeightDp(scriptRatio: Float = TEXT_LINE_RATIO): Float =
        captionSize.value * scriptRatio +
            daySize.value * DIGIT_LINE_RATIO +
            captionSize.value * scriptRatio +
            WidgetBand.TOP_PADDING_DP + WidgetBand.BOTTOM_PADDING_DP
}

internal object DateTileTypography {

    const val WEEKDAY_REFERENCE_WIDTH_DP: Float = 72f
    const val REFERENCE_HEIGHT_DP: Float = 72f

    /**
     * The cell the sizes are tuned against: a 1x1 on a phone launcher. Used as the picker's preview
     * size, because a preview composed at the bare 40dp minimum would advertise three unreadable
     * lines as if they were the tile.
     */
    val REFERENCE_CELL = DpSize(WEEKDAY_REFERENCE_WIDTH_DP.dp, REFERENCE_HEIGHT_DP.dp)

    val weekdaySizeAtReference = 13.sp

    /** The caption's ceiling at a large cell. The figure is twice it; see [sizesFor]. */
    val weekdaySizeCeiling = 32.sp

    val daySize = 26.sp

    /**
     * The figure's ceiling at a large cell, and the constant `MAX_SCALE` is read off.
     *
     * Kept at exactly twice [weekdaySizeCeiling] (and so exactly `MAX_SCALE` times [daySize]), because
     * that ratio *is* the single scale: `sizesFor` multiplies both lines by one factor, so the two
     * ceilings are one number stated twice. Move one without the other and the weekday and the figure
     * stop being the same multiple of their reference sizes.
     *
     * Raised 44 → 64 with [weekdaySizeCeiling]; see [DateTileSizes]' KDoc for the device measurement
     * that forced it.
     */
    val daySizeCeiling = 64.sp

    const val PADDING_DP = 6

    const val TILE_MIN_HEIGHT_DP = 40
    const val TILE_MIN_WIDTH_DP = 40

    /** The figure is always exactly twice the caption, so a single ceiling governs both. */
    private val MAX_SCALE: Float = daySizeCeiling.value / daySize.value

    /**
     * The height the three lines plus the weekday band ask for at scale 1 — the yardstick
     * [widgetTypeScale] measures the granted cell against. The two captions are script text and the
     * figure is digits, so they are counted at [scriptRatio] and [DIGIT_LINE_RATIO] respectively.
     */
    fun referenceTextHeightDp(scriptRatio: Float): Float =
        weekdaySizeAtReference.value * scriptRatio +
            daySize.value * DIGIT_LINE_RATIO +
            weekdaySizeAtReference.value * scriptRatio

    /** [referenceTextHeightDp] at the system script ratio; the static mirror and the tests read this. */
    val REFERENCE_TEXT_HEIGHT_DP: Float = referenceTextHeightDp(TEXT_LINE_RATIO)

    /** The non-scaling part of the stack: the band's own padding. */
    val FIXED_HEIGHT_DP: Float = (WidgetBand.TOP_PADDING_DP + WidgetBand.BOTTOM_PADDING_DP).toFloat()

    /** The whole stack at scale 1 — the lines plus the chrome; what [requestedHeightDp] returns there. */
    val REFERENCE_STACK_HEIGHT_DP: Float = REFERENCE_TEXT_HEIGHT_DP + FIXED_HEIGHT_DP

    /**
     * Both axes, because this tile is resizable on both: see `DateTileRoot`.
     *
     * [scriptRatio] is the line-box ratio the widget's script lines will actually render at — see
     * [HijriWidgetFonts.scriptLineRatio]. It is a parameter rather than a constant because the
     * bundled Nastaliq face needs a taller box than the system fallback, and a stack measured with
     * the wrong one overflows the cell it was supposed to fit.
     */
    fun sizesFor(
        availableWidthDp: Float,
        availableHeightDp: Float,
        scriptRatio: Float = TEXT_LINE_RATIO,
    ): DateTileSizes = sizesAtScale(
        widgetTypeScale(
            availableWidthDp = availableWidthDp,
            referenceWidthDp = WEEKDAY_REFERENCE_WIDTH_DP,
            availableHeightDp = availableHeightDp,
            fixedHeightDp = FIXED_HEIGHT_DP,
            referenceTextHeightDp = referenceTextHeightDp(scriptRatio),
            maxScale = MAX_SCALE,
        ),
    )

    /**
     * Width alone. This is the projection the Android 12-14 `previewLayout` mirror is held to, and
     * the one the static mirror can state: a launch-time preview has no granted height of its own.
     * The live tile goes through the two-axis overload.
     */
    fun sizesFor(availableWidthDp: Float): DateTileSizes = sizesAtScale(
        (availableWidthDp / WEEKDAY_REFERENCE_WIDTH_DP).coerceIn(MIN_TYPE_SCALE, MAX_SCALE),
    )

    /** The caption size at [availableWidth]; the preview mirror and the tests read this. */
    fun weekdaySizeFor(availableWidth: Float): TextUnit = sizesFor(availableWidth).captionSize

    fun weekdaySizeFor(availableWidth: Dp): TextUnit = weekdaySizeFor(availableWidth.value)

    private fun sizesAtScale(scale: Float): DateTileSizes = DateTileSizes(
        captionSize = (weekdaySizeAtReference.value * scale).sp,
        daySize = (daySize.value * scale).sp,
    )
}

/**
 * The shared 1x1 tile layout: the localized weekday name on top, a big centred day figure in the
 * middle, the month name at the bottom. Tapping anywhere opens the app.
 *
 * The weekday name comes from `TodayHijriWidgetData.weekdayTileName`, the projection's **tile-only**
 * weekday name, which the shared projection populates from the widget's own [WidgetLanguage] and which
 * iOS has always rendered — the Android tiles and the strip were the only places it was dropped. So it is
 * localized per widget, not per device (WG-12), and this ticket adds no field to the options schema. It is
 * the tile variant rather than `weekdayName` because a 1x1 spells Urdu Monday its own way
 * (`سوموار`) — the projection owns that choice, so this composable just prints the field.
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

    // One scale from both granted axes, so every line grows and shrinks together; see
    // [DateTileTypography]. The script ratio is the widget's own, because the bundled Nastaliq face
    // measures taller than the system fallback the default assumes.
    val availableWidth = cell.width - DateTileTypography.PADDING_DP.dp * 2
    val sizes = DateTileTypography.sizesFor(
        availableWidthDp = availableWidth.value,
        availableHeightDp = cell.height.value,
        scriptRatio = fonts.scriptLineRatio(),
    )

    Column(
        modifier = clickableModifier,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        if (dayText == null) {
            Box(
                modifier = GlanceModifier.defaultWeight()
                    .padding(horizontal = DateTileTypography.PADDING_DP.dp),
                contentAlignment = Alignment.Center,
            ) {
                WidgetText(
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
            }
            return@Column
        }
        // The weekday name, in the same accent band the dual-date tile and the Today strip draw it
        // in. Flush to the tile's edges — the band's own horizontal padding is the clear space its
        // text needs — so the three tiles are the same object whether they carry one date or two.
        WeekdayBand(
            text = weekdayText.orEmpty(),
            fontSize = sizes.captionSize,
            colors = colors,
            fontFamily = fonts.weekday.toGlanceFontFamily(),
        )
        // The figure and the month name centre in whatever the band left; the band is flush to the
        // top, so this is the only weighted slot. The horizontal padding is the tile's clear space,
        // kept here rather than on the root because the band must stay flush to the tile's edges.
        Box(
            modifier = GlanceModifier.defaultWeight()
                .padding(horizontal = DateTileTypography.PADDING_DP.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                WidgetText(
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
                    // The Hijri tile shows a Hijri month name, the Gregorian one a Gregorian one; the
                    // caller says which by naming the field it passes in.
                    fontFamily = fonts.forMonthTitle(gregorian).toGlanceFontFamily(),
                    color = colors.primaryText,
                )
            }
        }
    }
}

/**
 * The tile's month line, centred and bold.
 *
 * This was the weekday line too until the weekday moved into an accent band; the band is now
 * [WeekdayBand] and this is the one line under the figure. It stays a function so the tile has a
 * single place that says what its caption looks like.
 */
@Composable
private fun CaptionBand(
    text: String,
    fontSize: TextUnit,
    fontFamily: FontFamily?,
    color: ColorProvider,
) {
    WidgetText(
        text = text,
        style = TextStyle(
            // `primaryText`, not `secondaryText`: the month name is part of the date, not a caption
            // under it, and the secondary tone is what made it read as one. Bold for the same reason
            // — a glance is most often after *which month*.
            color = color,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            fontFamily = fontFamily,
            textAlign = TextAlign.Center,
        ),
        maxLines = 1,
    )
}
