package com.muazdev.hijricalendar.widget.glance

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** Pairs [HijriTodayWidget] with the app widget host. */
public class HijriTodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HijriTodayWidget()

    // Placement hooks (WG-18): arm the shared midnight alarm/backstop on the first instance, and
    // reconcile on removal so the alarm does not outlive the last widget of the family.
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        HijriWidgetRefreshScheduler.reconcile(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        HijriWidgetRefreshScheduler.reconcile(context)
    }

    // Same foreground gate as the grid widget: a host update must not compose on the main thread
    // while the app is on screen; record it as pending so [HijriWidgetRefresher] catches up.
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        if (HijriWidgetRefreshGate.isAppForeground()) {
            HijriWidgetConfig.markRefreshPending(context, true)
            HijriWidgetRefreshLog.d("host-update-today", "skip: app foreground; marked pending")
            return
        }
        HijriWidgetRefreshLog.d("host-update-today", "render ids=${appWidgetIds.toList()}")
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        if (HijriWidgetRefreshGate.isAppForeground()) {
            HijriWidgetConfig.markRefreshPending(context, true)
            HijriWidgetRefreshLog.d("host-options-today", "skip: app foreground; marked pending")
            return
        }
        HijriWidgetRefreshLog.d("host-options-today", "render id=$appWidgetId")
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }
}
