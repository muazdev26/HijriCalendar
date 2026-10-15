package com.muazdev.hijricalendar.widget.glance

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** Pairs [HijriCalendarWidget] with the app widget host. */
public class HijriCalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HijriCalendarWidget()

    /**
     * A widget was just placed. Reconcile scheduling here rather than waiting for the next app
     * start: the process is often already alive when a widget is added (the launcher starts only
     * the receiver), so `Application.onCreate` may not run again (WG-18).
     */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        HijriWidgetRefreshScheduler.reconcile(context)
    }

    /**
     * The last grid instance was removed. Reconcile rather than cancel outright: the other four
     * providers share the one alarm and the one worker, and one of them may still be placed.
     */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        HijriWidgetRefreshScheduler.reconcile(context)
    }

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
