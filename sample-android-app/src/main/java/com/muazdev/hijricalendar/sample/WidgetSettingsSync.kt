package com.muazdev.hijricalendar.sample

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.muazdev.hijricalendar.core.HijriMonthLengths
import com.muazdev.hijricalendar.widget.glance.GregorianDateWidgetReceiver
import com.muazdev.hijricalendar.widget.glance.HijriCalendarWidgetReceiver
import com.muazdev.hijricalendar.widget.glance.HijriDateWidgetReceiver
import com.muazdev.hijricalendar.widget.glance.HijriDualDateWidgetReceiver
import com.muazdev.hijricalendar.widget.glance.HijriTodayWidgetReceiver
import com.muazdev.hijricalendar.widget.glance.HijriWidgetConfig
import com.muazdev.hijricalendar.widget.glance.HijriWidgetRefreshLog
import com.muazdev.hijricalendar.widget.glance.HijriWidgetRefresher
import com.muazdev.hijricalendar.widgetdata.WidgetSource
import com.muazdev.hijricalendar.widgetdata.monthLengthsToMap
import kotlinx.coroutines.runBlocking

internal class WidgetSettingsSync(private val context: Context) {

    private val receivers = listOf(
        HijriCalendarWidgetReceiver::class.java,
        HijriTodayWidgetReceiver::class.java,
        HijriDateWidgetReceiver::class.java,
        GregorianDateWidgetReceiver::class.java,
        HijriDualDateWidgetReceiver::class.java,
    )

    fun sync(adjustmentDays: Int, pakistanDates: Boolean, monthLengths: HijriMonthLengths) {
        runBlocking {
            val overrideTable = monthLengthsToMap(monthLengths)
            val source = if (pakistanDates) WidgetSource.PAKISTAN else WidgetSource.CALCULATION

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val gridAppWidgetIds = appWidgetManager?.getAppWidgetIds(ComponentName(context, HijriCalendarWidgetReceiver::class.java)) ?: intArrayOf()
            var synced = 0

            HijriWidgetRefreshLog.d(
                "sync:start",
                "inAppOverrides=${overrideTable.size} adjustmentDays=$adjustmentDays pakistan=$pakistanDates grids=${gridAppWidgetIds.size}",
            )

            val currentFamily = HijriWidgetConfig.loadFamily(context)
            val updatedFamily = currentFamily.copy(
                adjustmentDays = adjustmentDays,
                source = source,
                monthLengthOverrides = overrideTable,
            )
            HijriWidgetConfig.saveFamilyOptions(context, updatedFamily, caller = "sync")

            if (appWidgetManager != null) {
                for (receiver in receivers) {
                    val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, receiver))
                    for (appWidgetId in ids) {
                        val glanceId = runCatching { GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId) }.getOrNull()
                            ?: continue
                        runCatching {
                            // Skip widgets with no stored options: they follow the family mirror
                            // (already updated above). Writing them a copy would pin them.
                            val own = HijriWidgetConfig.loadOwn(context, glanceId) ?: return@runCatching
                            HijriWidgetConfig.saveWidgetOptions(
                                context = context,
                                glanceId = glanceId,
                                options = own.copy(
                                    adjustmentDays = adjustmentDays,
                                    source = source,
                                    monthLengthOverrides = overrideTable,
                                ),
                                caller = "sync",
                            )
                            synced++
                        }
                    }
                }
            }

            HijriWidgetRefreshLog.d(
                "sync:end",
                "familyLanguage=${updatedFamily.language} familyOverrides=${updatedFamily.monthLengthOverrides.size} synced=$synced",
            )

            HijriWidgetRefresher.refreshAllAsync(
                context = context,
                reason = "in-app settings",
                bypassGate = true,
                bypassDedupe = true,
            )
            kotlinx.coroutines.delay(250)
        }
    }
}
