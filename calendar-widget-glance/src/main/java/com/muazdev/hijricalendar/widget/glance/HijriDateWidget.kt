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
 * The 1x1 tile's two type sizes, as data so the Android 12-14 `previewLayout` mirror can be held to
 * them, and so the fit can be asserted instead of eyeballed.
 *
 * These are *not* proportional and must not become so. The tile is a fixed 40dp
 * (`resizeMode="none"` in both tile widget-infos), so there is exactly one size to fit, and the sum
 * of the two lines plus [DateTileTypography.PADDING_DP] of vertical padding is what determines
 * whether Glance clips. Glance clips rather than reflowing, and a clipped bottom line reads as a
 * missing date rather than as a layout failure — which is why the budget is asserted by a test
 * rather than left to a reviewer's eye.
 *
 * The tile was 34sp + 12sp = 46sp of text in a 40dp box before this ticket, i.e. it already
 * overflowed and relied on the launcher happening to give more than the declared minimum.
 */
internal object DateTileTypography {
    /**
     * The caption line: the localized weekday name over the month name, set as one line.
     *
     * **Why weekday and month share a line.** A third text line does not fit. 40dp less 3dp of
     * padding is 37dp; at 1.2 line height that is ~30sp of type in total, so three lines would each
     * get ~10sp and the day figure would stop being the thing you glance at.
     */
    val captionSize = 10.sp

    /**
     * The day figure — the thing the tile exists to show.
     *
     * This is a third of the 34sp it used to be, and that is arithmetic rather than taste: the old
     * two-line layout declared 34 + 12 + 12dp of padding = 58dp of content inside a 40dp box and
     * relied on the launcher happening to hand over more than the widget-info's own minimum. The
     * tile now fits the size it promises, which is the first time it has.
     */
    val daySize = 17.sp

    /** Vertical padding, leaving the rest of the 40dp to the two lines. */
    const val PADDING_DP = 3

    /** The widget-info's declared minimum tile height. Kept here so the fit test can read it. */
    const val TILE_MIN_HEIGHT_DP = 40
}

/**
 * The shared 1x1 tile layout: the localized weekday name and month name over a big centred day
 * figure. Tapping anywhere opens the app.
 *
 * The weekday name comes from `TodayHijriWidgetData.weekdayName`, which the shared projection has
 * always populated from the widget's own [WidgetLanguage] and which iOS has always rendered — the
 * four Android widgets were the only place it was dropped. So it is localized per widget, not per
 * device (WG-12), and this ticket adds no field to the options schema.
 *
 * The caption line is hidden when there is no readable date, in which case the tile says so via
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
                    fontSize = DateTileTypography.captionSize,
                    textAlign = TextAlign.Center,
                ),
            )
            return@Column
        }
        val month = monthText.orEmpty()
        val weekday = weekdayText.orEmpty()
        if (month.isNotEmpty() || weekday.isNotEmpty()) {
            Text(
                text = listOf(weekday, month).filter { it.isNotEmpty() }.joinToString(CAPTION_SEPARATOR),
                style = TextStyle(
                    color = ColorProvider(colors.secondaryText),
                    fontSize = DateTileTypography.captionSize,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
        }
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
    }
}

/**
 * Joins the caption's two halves. A middot with thin spaces either side reads as a separator in
 * both scripts and needs no localization, which matters because this string is assembled in the
 * Glance module where `WidgetLocalization` is the only localization seam (WG-12).
 */
private const val CAPTION_SEPARATOR = " \u00B7 "
