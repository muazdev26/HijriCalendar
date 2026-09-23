package com.muazdev.hijricalendar.widget.glance

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
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
import com.muazdev.hijricalendar.widget.glance.R

/**
 * A fixed 1x1 tile showing today's Hijri date as a big day figure with the localized month name
 * underneath and nothing else. Has no settings screen and no per-widget state: it renders with
 * the family options mirror ([HijriWidgetConfig.loadFamily]) written by the calendar-grid
 * widget's configuration screen, and is swept on every family refresh alongside the Today strip.
 * Tapping anywhere opens the app.
 */
class HijriDateWidget : GlanceAppWidget() {

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
                colors = colors,
                openAction = openAction,
            )
        }
    }
}

/**
 * A fixed 1x1 tile showing today's Gregorian date as a big day figure with the localized month
 * name underneath and nothing else, mirroring [HijriDateWidget] on the Gregorian side. Same
 * family-options-mirror rendering and the same tap-to-open-app behaviour.
 */
class GregorianDateWidget : GlanceAppWidget() {

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
                colors = colors,
                openAction = openAction,
            )
        }
    }
}

/**
 * The shared 1x1 tile layout: a big centred day figure with the month name underneath, sized to
 * fit the fixed one-cell size with no scroll or ellipsis. Tapping anywhere opens the app.
 */
@Composable
internal fun DateTileRoot(
    dayText: String?,
    monthText: String?,
    colors: WidgetColors,
    openAction: Action?,
) {
    val modifier = GlanceModifier.fillMaxSize().background(colors.background)
    val clickableModifier = if (openAction != null) modifier.clickable(openAction) else modifier

    Column(
        modifier = clickableModifier.padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        if (dayText == null) {
            Text(
                text = LocalContext.current.getString(R.string.hijri_widget_unavailable),
                style = TextStyle(
                    color = ColorProvider(colors.secondaryText),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                ),
            )
            return@Column
        }
        Text(
            text = dayText,
            style = TextStyle(
                color = ColorProvider(colors.accent),
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
        Text(
            text = monthText.orEmpty(),
            modifier = GlanceModifier.padding(top = 2.dp),
            style = TextStyle(
                color = ColorProvider(colors.primaryText),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
    }
}