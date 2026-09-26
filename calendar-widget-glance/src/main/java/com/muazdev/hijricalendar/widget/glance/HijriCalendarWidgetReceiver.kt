package com.muazdev.hijricalendar.widget.glance

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** Pairs [HijriCalendarWidget] with the app widget host. */
class HijriCalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HijriCalendarWidget()

    // Each update launches a Glance session that composes on the main thread on some devices,
    // stealing frames while the user is navigating the calendar. Skip re-renders while the app is
    // visible, but record that one is due so [HijriWidgetRefresher] catches it up once the app
    // leaves the foreground. User-initiated settings changes do not go through here; they render
    // their instance directly.
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        // Picker previews are independent of re-render gating: publishing them is cheap, guarded
        // to once per day, and a host update is the natural moment after a widget is added.
        HijriWidgetPreviewPublisher.publishIfDueAsync(context)
        if (HijriWidgetRefreshGate.isAppForeground()) {
            HijriWidgetConfig.markRefreshPending(context, true)
            HijriWidgetRefreshLog.d("host-update", "skip: app foreground; marked pending")
            return
        }
        HijriWidgetRefreshLog.d("host-update", "render ids=${appWidgetIds.toList()}")
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
            HijriWidgetRefreshLog.d("host-options", "skip: app foreground; marked pending")
            return
        }
        HijriWidgetRefreshLog.d("host-options", "render id=$appWidgetId")
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }
}
