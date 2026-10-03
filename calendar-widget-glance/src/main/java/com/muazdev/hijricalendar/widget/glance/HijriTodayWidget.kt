package com.muazdev.hijricalendar.widget.glance

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
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
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.muazdev.hijricalendar.widgetdata.TodayHijriWidgetData
import com.muazdev.hijricalendar.widgetdata.WidgetLanguage
import com.muazdev.hijricalendar.widgetdata.WidgetLocalization

/**
 * Resizable "today" strip widget: full screen width, one text-height row, showing today's Hijri
 * day, month and year above the corresponding Gregorian date. It is resizable
 * (`resizeMode="horizontal|vertical"` in its provider info) down to its natural text bounds, so
 * the text is never clipped or hidden at the minimum size.
 *
 * Unlike the calendar grid it has no per-widget settings screen: it follows the family options
 * mirror ([HijriWidgetConfig.loadFamily]) written by every configurations-screen save on the grid
 * widget, so language, numerals, Hijri source and moon-sighting adjustment always match the
 * family's latest choices. Tapping anywhere opens the app, like the grid widget.
 */
public class HijriTodayWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    /**
     * Picker previews are composed at a realistic placed strip size (one screen row) instead of
     * the widget's 40dp minimum, so the two date halves actually fit side by side.
     */
    override val previewSizeMode: PreviewSizeMode = SizeMode.Responsive(
        setOf(DpSize(144.dp, 40.dp)),
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
        val colors = WidgetColors.DEFAULT
        val openAction = actionStartActivity(openAppIntent(context))

        provideContent {
            val options = HijriWidgetConfig.loadFamily(context)
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
        val colors = WidgetColors.DEFAULT
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
 * The strip's layout: the Gregorian and Hijri dates side by side on one line, each a bold day
 * figure with the month + year underneath. The Hijri side always sits on the right (the
 * Gregorian on the left), regardless of the widget's reading direction: the children are ordered
 * by [layoutRtl] so platform RTL mirroring lands and keeps the Hijri half on the right.
 */
@Composable
internal fun HijriTodayRoot(
    today: TodayHijriWidgetData?,
    colors: WidgetColors,
    openAction: Action?,
    layoutRtl: Boolean,
    language: WidgetLanguage,
) {
    val modifier = GlanceModifier.fillMaxSize().background(colors.background)
    val clickableModifier = if (openAction != null) modifier.clickable(openAction) else modifier

    Row(
        modifier = clickableModifier.padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
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
            return@Row
        }
        val hijri = @Composable {
            DateSide(
                dayText = today.hijriDayText,
                // Era-appended by the projection (FD-05); a renderer must not format the era
                // itself, because the marker follows the widget's language, not the device (WG-12).
                caption = "${today.hijriMonthName} ${today.hijriYearText}",
                dayColor = colors.accent,
                captionColor = colors.primaryText,
            )
        }
        val gregorian = @Composable {
            DateSide(
                dayText = today.gregorianDayText,
                caption = "${today.gregorianMonthName} ${today.gregorianYearText}",
                dayColor = colors.primaryText,
                captionColor = colors.secondaryText,
            )
        }
        // Order so the Hijri side lands on the right in either reading direction.
        if (layoutRtl) {
            hijri()
            gregorian()
        } else {
            gregorian()
            hijri()
        }
    }
}

/**
 * One half of the strip: a bold day figure with the month + year line underneath. Weighted, so
 * the two halves split the width and sit visibly at the strip's two ends.
 */
@Composable
private fun RowScope.DateSide(
    dayText: String,
    caption: String,
    dayColor: ColorProvider,
    captionColor: ColorProvider,
) {
    Column(
        modifier = GlanceModifier.defaultWeight(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        Text(
            text = dayText,
            style = TextStyle(
                color = dayColor,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
        Text(
            text = caption,
            modifier = GlanceModifier.padding(top = 1.dp),
            style = TextStyle(
                color = captionColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
    }
}
