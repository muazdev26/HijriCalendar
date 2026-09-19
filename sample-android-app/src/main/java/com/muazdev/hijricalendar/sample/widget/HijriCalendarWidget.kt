package com.muazdev.hijricalendar.sample.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.ColorRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.muazdev.hijricalendar.sample.MainActivity
import com.muazdev.hijricalendar.sample.R
import com.muazdev.hijricalendar.widgetdata.HijriDayWidgetData
import com.muazdev.hijricalendar.widgetdata.HijriMonthWidgetData
import com.muazdev.hijricalendar.widgetdata.NumeralStyle
import com.muazdev.hijricalendar.widgetdata.TodayHijriWidgetData
import com.muazdev.hijricalendar.widgetdata.buildHijriMonthWidgetData
import com.muazdev.hijricalendar.widgetdata.todayHijriWidgetData

internal const val HIJRI_DEEP_LINK_TODAY = "hijricalendar://today"

/**
 * The Hijri home-screen widget family.
 *
 * Large sizes render the full 42-day Hijri month grid of the currently observed month (or the
 * config-pinned month); compact sizes render a "today" card. All rendering data comes from the
 * `calendar-widget-data` projection, so the grid lines up exactly with the in-app calendar.
 */
internal class HijriCalendarWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(104.dp, 110.dp), // compact today card
            DpSize(200.dp, 160.dp), // narrow medium
            DpSize(260.dp, 280.dp), // wide/large grid
        ),
    )

    @OptIn(ExperimentalGlanceApi::class)
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val options = HijriWidgetConfig.load(context, id.toString())
        val todayEpochDay = HijriWidgetRefreshScheduler.todayEpochDay()
        val todayHijri = todayHijriWidgetData(todayEpochDay, options.adjustmentDays)
        val monthData = buildMonthData(options, todayHijri)
        val colors = WidgetColors.from(context)
        val openAction = actionStartActivity(openAppIntent(context))

        provideContent {
            HijriWidgetRoot(
                monthData = monthData,
                todayHijri = todayHijri,
                todayEpochDay = todayEpochDay,
                colors = colors,
                openAction = openAction,
            )
        }
    }

    private fun buildMonthData(
        options: HijriWidgetConfig.WidgetOptions,
        todayHijri: TodayHijriWidgetData?,
    ): HijriMonthWidgetData? {
        val year = options.pinnedYear ?: todayHijri?.hijriYear ?: return null
        val month = options.pinnedMonth ?: todayHijri?.hijriMonth ?: return null
        return buildHijriMonthWidgetData(
            hijriYear = year,
            hijriMonth = month,
            adjustmentDays = options.adjustmentDays,
            firstDayOfWeekIndex = options.firstDayOfWeekIndex,
            numeralStyle = options.numeralStyle,
        )
    }

    private fun openAppIntent(context: Context): Intent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(HIJRI_DEEP_LINK_TODAY))
        intent.setClass(context, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        return intent
    }
}

/**
 * Day/night aware widget colors, resolved from resources once per render so Glance views are
 * correct whether the widget is drawn in light or dark mode.
 */
internal class WidgetColors(
    val background: Color,
    val accent: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val todayBackground: Color,
    val todayText: Color,
) {
    companion object {
        fun from(context: Context) = WidgetColors(
            background = context.widgetColor(R.color.widget_background),
            accent = context.widgetColor(R.color.widget_accent),
            primaryText = context.widgetColor(R.color.widget_text_primary),
            secondaryText = context.widgetColor(R.color.widget_text_secondary),
            todayBackground = context.widgetColor(R.color.widget_today_background),
            todayText = context.widgetColor(R.color.widget_text_on_today),
        )
    }
}

private fun Context.widgetColor(@ColorRes resId: Int): Color = Color(getColor(resId))

@Composable
private fun HijriWidgetRoot(
    monthData: HijriMonthWidgetData?,
    todayHijri: TodayHijriWidgetData?,
    todayEpochDay: Long,
    colors: WidgetColors,
    openAction: Action,
) {
    val size = LocalSize.current
    val useCompact = size.width < 180.dp || size.height < 200.dp

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.background)
            .clickable(openAction)
            .padding(10.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        if (useCompact || monthData == null) {
            TodayCard(today = todayHijri, colors = colors)
        } else {
            MonthGrid(month = monthData, todayEpochDay = todayEpochDay, colors = colors)
        }
    }
}

@Composable
private fun TodayCard(today: TodayHijriWidgetData?, colors: WidgetColors) {
    if (today == null) {
        Text(
            text = LocalContext.current.getString(R.string.hijri_widget_unavailable),
            style = TextStyle(color = ColorProvider(colors.secondaryText), fontSize = 12.sp),
        )
        return
    }
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        Text(
            text = "${today.hijriDay}",
            style = TextStyle(
                color = ColorProvider(colors.accent),
                fontSize = 46.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            ),
        )
        Text(
            text = "${today.hijriMonthName} ${today.hijriYear}",
            style = TextStyle(
                color = ColorProvider(colors.primaryText),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
        Text(
            text = today.gregorianDate,
            style = TextStyle(color = ColorProvider(colors.secondaryText), fontSize = 11.sp, textAlign = TextAlign.Center),
            maxLines = 1,
        )
    }
}

@Composable
private fun MonthGrid(
    month: HijriMonthWidgetData,
    todayEpochDay: Long,
    colors: WidgetColors,
) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        // Header: month name + year, and the Gregorian range line underneath.
        Column(
            modifier = GlanceModifier.fillMaxWidth().padding(bottom = 4.dp),
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        ) {
            Text(
                text = "${month.hijriMonthName} ${month.hijriYear}",
                style = TextStyle(
                    color = ColorProvider(colors.accent),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
            )
            Text(
                text = month.gregorianRange,
                style = TextStyle(color = ColorProvider(colors.secondaryText), fontSize = 10.sp, textAlign = TextAlign.Center),
                maxLines = 1,
            )
        }

        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            month.weekdayHeaders.forEach { name ->
                Text(
                    text = name,
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(
                        color = ColorProvider(colors.secondaryText),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }

        month.days.chunked(7).forEach { week ->
            Row(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                week.forEach { cell ->
                    DayCell(cell = cell, todayEpochDay = todayEpochDay, colors = colors)
                }
            }
        }
    }
}

@Composable
private fun RowScope.DayCell(
    cell: HijriDayWidgetData,
    todayEpochDay: Long,
    colors: WidgetColors,
) {
    val isToday = cell.gregorianEpochDay == todayEpochDay
    val isFaded = !cell.isCurrentMonth || cell.isWeekend
    val base = GlanceModifier
        .defaultWeight()
        .fillMaxHeight()
        .padding(horizontal = 1.dp, vertical = 1.dp)

    Box(
        modifier = if (isToday) {
            base.background(colors.todayBackground).cornerRadius(18.dp)
        } else {
            base
        },
    ) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        ) {
            Text(
                text = cell.dayText,
                style = TextStyle(
                    color = ColorProvider(
                        when {
                            isToday -> colors.todayText
                            isFaded -> colors.secondaryText
                            else -> colors.primaryText
                        },
                    ),
                    fontSize = 13.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
            Text(
                text = cell.gregorianDayText,
                style = TextStyle(
                    color = ColorProvider(if (isToday) colors.todayText.copy(alpha = 0.8f) else colors.secondaryText),
                    fontSize = 8.sp,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
        }
    }
}