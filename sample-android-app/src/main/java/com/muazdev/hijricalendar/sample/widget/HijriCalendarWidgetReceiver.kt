package com.muazdev.hijricalendar.sample.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** Pairs [HijriCalendarWidget] with the app widget host. */
internal class HijriCalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HijriCalendarWidget()

    // Each update launches a Glance session that composes on the main thread on some devices,
    // stealing frames while the user is navigating the calendar. Skip re-renders while the app is
    // visible; the in-app calendar is the source of truth until the next background update.
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        if (HijriWidgetRefreshGate.isAppForeground()) return
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        if (HijriWidgetRefreshGate.isAppForeground()) return
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }
}