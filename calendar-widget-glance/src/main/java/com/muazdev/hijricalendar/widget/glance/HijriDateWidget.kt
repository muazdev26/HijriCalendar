package com.muazdev.hijricalendar.widget.glance

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
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

    @OptIn(ExperimentalGlanceApi::class)
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Same one-shot peek as the rest of the family: port legacy config (seeding the family
        // mirror once) and warm up the Pakistan century table if the family now opts into it.
        HijriWidgetConfig.migrateLegacyIfNeeded(context, id)
        val options = HijriWidgetConfig.loadFamily(context)
        if (options.source.pakistan) {
            PakistanWarmUp.ensureWarm()
        }
        val colors = WidgetColors.from(context)
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
        val colors = WidgetColors.from(context)
        provideContent {
            val data = buildRenderData(context, options, viewedMonth = null)
            DateTileRoot(
                dayText = data.todayHijri?.hijriDayText,
                monthText = data.todayHijri?.hijriMonthName,
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
        val colors = WidgetColors.from(context)
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
        val colors = WidgetColors.from(context)
        provideContent {
            val data = buildRenderData(context, options, viewedMonth = null)
            DateTileRoot(
                dayText = data.todayHijri?.gregorianDayText,
                monthText = data.todayHijri?.gregorianMonthName,
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
     * The localized weekday name, on its own line at the top.
     *
     * **Same size as the month name and bold, deliberately.** It was 10sp Medium in
     * `widget_text_secondary` and read as a caption rather than as part of the date — the weekday is
     * half the answer to "what day is it?", and it was the weakest line on the tile. It now matches
     * [monthSize] in size and emphasis so the two names read as a pair bracketing the day figure.
     */
    val weekdaySize = 11.sp

    /** The day figure — the thing the tile exists to show. */
    val daySize = 26.sp

    /** The month name, on its own line at the bottom. */
    val monthSize = 11.sp

    /** Vertical padding, leaving the rest of the height to the three lines. */
    const val PADDING_DP = 2

    /** The widget-info's declared minimum tile height. Read by the test that records the budget. */
    const val TILE_MIN_HEIGHT_DP = 40
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
    weekdayText: String?,
    colors: WidgetColors,
    openAction: Action?,
    language: WidgetLanguage,
) {
    val modifier = GlanceModifier.fillMaxSize().background(colors.background)
    val clickableModifier = if (openAction != null) modifier.clickable(openAction) else modifier

    Column(
        modifier = clickableModifier.padding(
            horizontal = 6.dp,
            vertical = DateTileTypography.PADDING_DP.dp,
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
                    color = ColorProvider(colors.secondaryText),
                    fontSize = DateTileTypography.monthSize,
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
                color = ColorProvider(colors.primaryText),
                fontSize = DateTileTypography.weekdaySize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
        Text(
            text = dayText,
            style = TextStyle(
                color = ColorProvider(colors.accent),
                fontSize = DateTileTypography.daySize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
        Text(
            text = monthText.orEmpty(),
            modifier = GlanceModifier.padding(top = 1.dp),
            style = TextStyle(
                color = ColorProvider(colors.primaryText),
                fontSize = DateTileTypography.monthSize,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
    }
}
