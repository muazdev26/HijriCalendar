package com.muazdev.hijricalendar.widget.glance

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
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
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
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

    @OptIn(ExperimentalGlanceApi::class)
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
                captionText = today?.let { data ->
                    "${data.hijriMonthName} " +
                        WidgetLocalization.ChromeLabels.yearWithEra(
                            data.hijriYear, options.language, gregorian = false,
                        )
                },
                weekdayText = today?.weekdayName,
                colors = colors,
                openAction = openAction,
                language = options.language,
            )
        }
    }

    /**
     * Real picker preview for Android 15+: today's Hijri date in the family's current options,
     * non-interactive. [HijriWidgetPreviewPublisher] publishes the result.
     */
    @OptIn(ExperimentalGlanceApi::class)
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
                captionText = data.todayHijri?.let { today ->
                    "${today.hijriMonthName} " +
                        WidgetLocalization.ChromeLabels.yearWithEra(
                            today.hijriYear, options.language, gregorian = false,
                        )
                },
                weekdayText = data.todayHijri?.weekdayName,
                colors = colors,
                openAction = null,
                language = options.language,
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

    @OptIn(ExperimentalGlanceApi::class)
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
                captionText = today?.let { data ->
                    "${data.gregorianMonthName} " +
                        WidgetLocalization.ChromeLabels.yearWithEra(
                            data.gregorianYear, options.language, gregorian = true,
                        )
                },
                weekdayText = today?.weekdayName,
                colors = colors,
                openAction = openAction,
                language = options.language,
            )
        }
    }

    /**
     * Real picker preview for Android 15+: today's Gregorian date in the family's current options,
     * non-interactive. [HijriWidgetPreviewPublisher] publishes the result.
     */
    @OptIn(ExperimentalGlanceApi::class)
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
                captionText = data.todayHijri?.let { today ->
                    "${today.gregorianMonthName} " +
                        WidgetLocalization.ChromeLabels.yearWithEra(
                            today.gregorianYear, options.language, gregorian = true,
                        )
                },
                weekdayText = data.todayHijri?.weekdayName,
                colors = colors,
                openAction = null,
                language = options.language,
            )
        }
    }
}

/**
 * The 1x1 tile's three type sizes, as data so the Android 12-14 `previewLayout` mirror can be held to
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
     */
    fun weekdaySizeFor(availableWidth: Dp): TextUnit = weekdaySizeFor(availableWidth.value)

    /**
     * The bottom line's size for [availableWidth], which carries a month name **and** an era'd year
     * (FD-05) and is therefore the longest string on the tile.
     *
     * Same shape as [weekdaySizeFor] and the same value at the reference width, so the two caption
     * lines still match — which is the FD-01 styling. Different bounds, because this line is roughly
     * twice as long: it reaches a ceiling sooner and a 15sp "محرم ١٤٤٨ ھ" would clip long before a
     * 15sp "جمعرات" would.
     */
    fun monthLineSizeFor(availableWidth: Dp): TextUnit = monthLineSizeFor(availableWidth.value)

    /** The bottom line's font size for [availableWidth], clamped like [weekdaySizeFor]. */
    fun monthLineSizeFor(availableWidth: Float): TextUnit {
        val scaled = monthLineSizeAtReference.value * availableWidth / WEEKDAY_REFERENCE_WIDTH_DP
        return scaled.coerceIn(monthLineSizeFloor.value, monthLineSizeCeiling.value).sp
    }

    /** The bottom line's size at [WEEKDAY_REFERENCE_WIDTH_DP]. Matches [weekdaySizeAtReference]. */
    val monthLineSizeAtReference = 11.sp

    /** Never smaller than this, however narrow the tile. */
    val monthLineSizeFloor = 9.sp

    /** Never larger than this: this line is roughly twice as long as the weekday's. */
    val monthLineSizeCeiling = 12.sp

    /**
     * The weekday font size for [availableWidth], clamped to
     * [[weekdaySizeFloor], [weekdaySizeCeiling]].
     *
     * Linear in width about [WEEKDAY_REFERENCE_WIDTH_DP], so a wider tile gets a proportionally larger
     * name and a narrower one a proportionally smaller one. Three deliberate bounds:
     *
     * - **A floor, and it is the important one.** At the 40dp minimum the tile-info declares there
     *   are only about 28dp of text width, which works out below [WEEKDAY_REFERENCE_WIDTH_DP] and lands
     *   on the floor. A weekday name is the longest string on the tile — `Thursday` is twice the
     *   width of `جمعرات`'s glyph run in most fonts — so an unbounded scale would render it as a
     *   clipped stub on a launcher that grants a tight cell, which is worse than a small one.
     * - **A ceiling**, because the name still has to leave room for the day figure above it and the
     *   month below. A tile is not a banner.
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
    val weekdaySizeAtReference = 11.sp

    /** Never smaller than this, however narrow the tile. */
    val weekdaySizeFloor = 10.sp

    /** Never larger than this, however wide. */
    val weekdaySizeCeiling = 15.sp

    /** The day figure — the thing the tile exists to show. Fixed: it is not the length-sensitive one. */
    val daySize = 26.sp

    /** Horizontal padding on each side; the text width is the tile's width less twice this. */
    const val PADDING_DP = 6

    /** Vertical padding, leaving the rest of the height to the three lines. */
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
 * four Android widgets were the only place it was dropped. So it is localized per widget, not per
 * device (WG-12), and this ticket adds no field to the options schema.
 *
 * The weekday line is omitted when there is no readable date, in which case the tile says so via
 * [WidgetLocalization.ChromeLabels.monthUnavailable] instead of showing an empty line above a
 * fallback.
 */
@Composable
internal fun DateTileRoot(
    dayText: String?,
    monthText: String?,
    /**
     * The bottom line: a month name with its era'd year, e.g. `محرم ١٤٤٨ ھ` (FD-05).
     *
     * Assembled by the caller rather than here, so the year and its era come from the widget's own
     * language (WG-12) instead of the Glance module guessing at them.
     */
    captionText: String?,
    weekdayText: String?,
    colors: WidgetColors,
    openAction: Action?,
    language: WidgetLanguage,
) {
    val modifier = GlanceModifier.fillMaxSize().background(colors.background)
    val clickableModifier = if (openAction != null) modifier.clickable(openAction) else modifier

    // The granted width, so the weekday name can be sized to it. `SizeMode.Single` still reports a
    // real size, and it varies by tens of dp between launchers.
    val availableWidth = LocalSize.current.width - DateTileTypography.PADDING_DP.dp * 2

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
                    fontSize = DateTileTypography.monthLineSizeFor(availableWidth),
                    textAlign = TextAlign.Center,
                ),
            )
            return@Column
        }
        Text(
            text = weekdayText.orEmpty(),
            style = TextStyle(
                // `primaryText`, not `secondaryText`: the weekday is part of the date, not a caption
                // under it, and the secondary tone is what made it read as one.
                color = colors.primaryText,
                fontSize = DateTileTypography.weekdaySizeFor(availableWidth),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
        Text(
            text = dayText,
            style = TextStyle(
                color = colors.accent,
                fontSize = DateTileTypography.daySize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
        Text(
            text = captionText ?: monthText.orEmpty(),
            modifier = GlanceModifier.padding(top = 1.dp),
            style = TextStyle(
                color = colors.primaryText,
                fontSize = DateTileTypography.monthLineSizeFor(availableWidth),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
    }
}
