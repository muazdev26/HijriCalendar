package com.muazdev.hijricalendar.sample.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Responds to the system broadcasts that can invalidate the widget family's day/today state:
 * manual clock changes, timezone changes, locale changes and boot completion.
 *
 * Note: `ACTION_DATE_CHANGED` is intentionally NOT listed here - it is not exempt from the
 * API 26+ implicit-broadcast manifest ban, so midnight rollover is instead handled by the
 * exact AlarmManager alarm.
 *
 * On boot the process starts via [BOOT_COMPLETED], so [com.muazdev.hijricalendar.sample.HijriCalendarApp.onCreate]
 * has already re-armed the midnight alarm before this receiver runs; we only need to catch the
 * widget up.
 */
internal class HijriWidgetUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (
            action == Intent.ACTION_TIME_CHANGED ||
            action == Intent.ACTION_TIMEZONE_CHANGED ||
            action == Intent.ACTION_LOCALE_CHANGED ||
            action == Intent.ACTION_BOOT_COMPLETED
        ) {
            HijriWidgetRefreshScheduler.armMidnightAlarm(context)
            refreshNow(context)
        }
    }

    /**
     * Re-renders every widget (skipping when we already refreshed today or when the app is
     * visible) running on a background dispatcher via [goAsync] so the render doesn't block the
     * main broadcast thread.
     */
    private fun refreshNow(context: Context) {
        // The in-app calendar is already the source of truth; skip while it is on screen so the
        // Glance session doesn't steal frames from navigation.
        if (HijriWidgetRefreshGate.isAppForeground()) return
        val todayEpochDay = HijriWidgetRefreshScheduler.todayEpochDay()
        if (HijriWidgetConfig.isFreshFor(context, todayEpochDay)) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                HijriCalendarWidget().updateAll(context)
                HijriWidgetConfig.markUpdatedNow(context, todayEpochDay)
            } finally {
                pendingResult.finish()
            }
        }
    }
}