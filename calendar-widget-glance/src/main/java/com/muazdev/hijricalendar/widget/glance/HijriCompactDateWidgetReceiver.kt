package com.muazdev.hijricalendar.widget.glance

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Pairs [HijriCompactDateWidget] with the app widget host.
 *
 * Its own receiver — and so its own picker entry and provider info — for the same reason every widget in
 * this family has one: the picker discovers a widget per `APPWIDGET_UPDATE` receiver carrying the
 * provider meta-data (WG-10).
 *
 * The placement hooks and foreground gate are identical to the rest of the family: the first instance
 * arms the shared midnight alarm ([HijriWidgetRefreshScheduler.reconcile]) and removal reconciles rather
 * than cancels it, because a sibling widget may still be placed; a host update while the app is visible
 * is marked pending instead of composing on the main thread.
 */
public class HijriCompactDateWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HijriCompactDateWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        HijriWidgetRefreshScheduler.reconcile(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        HijriWidgetRefreshScheduler.reconcile(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        if (HijriWidgetRefreshGate.isAppForeground()) {
            HijriWidgetConfig.markRefreshPending(context, true)
            HijriWidgetRefreshLog.d("host-update-compact-date", "skip: app foreground; marked pending")
            return
        }
        HijriWidgetRefreshLog.d("host-update-compact-date", "render ids=${appWidgetIds.toList()}")
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }
}
