package com.muazdev.hijricalendar.widget.glance

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** Pairs [HijriDateWidget] with the app widget host. */
public class HijriDateWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HijriDateWidget()

    // Same foreground gate as the rest of the family: a host update must not compose on the main
    // thread while the app is on screen; record it as pending so [HijriWidgetRefresher] catches up.
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        if (HijriWidgetRefreshGate.isAppForeground()) {
            HijriWidgetConfig.markRefreshPending(context, true)
            HijriWidgetRefreshLog.d("host-update-hijri-date", "skip: app foreground; marked pending")
            return
        }
        HijriWidgetRefreshLog.d("host-update-hijri-date", "render ids=${appWidgetIds.toList()}")
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }
}

/**
 * Pairs [HijriDualDateWidget] with the app widget host.
 *
 * Its own receiver rather than a parameter of an existing one because the picker discovers a widget
 * per `APPWIDGET_UPDATE` receiver carrying the provider meta-data (WG-10) — one receiver means one
 * entry in the picker, and one info XML to override.
 *
 * The foreground gate is the same as the rest of the family, and for the same reason: a host update
 * must not compose on the main thread while the app is on screen, so it records itself as pending
 * instead and [HijriWidgetRefresher] catches up when the last activity stops.
 */
public class HijriDualDateWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HijriDualDateWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        if (HijriWidgetRefreshGate.isAppForeground()) {
            HijriWidgetConfig.markRefreshPending(context, true)
            HijriWidgetRefreshLog.d("host-update-dual-date", "skip: app foreground; marked pending")
            return
        }
        HijriWidgetRefreshLog.d("host-update-dual-date", "render ids=${appWidgetIds.toList()}")
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }
}

/** Pairs [GregorianDateWidget] with the app widget host. */
public class GregorianDateWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GregorianDateWidget()

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        if (HijriWidgetRefreshGate.isAppForeground()) {
            HijriWidgetConfig.markRefreshPending(context, true)
            HijriWidgetRefreshLog.d("host-update-gregorian-date", "skip: app foreground; marked pending")
            return
        }
        HijriWidgetRefreshLog.d("host-update-gregorian-date", "render ids=${appWidgetIds.toList()}")
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }
}
