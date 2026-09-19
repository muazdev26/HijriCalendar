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
 * Fires from the exact AlarmManager alarm at local midnight to roll the grid over.
 *
 * There is no intent-filter on this receiver; it is only reachable via the alarm
 * [PendingIntent] created by [HijriWidgetRefreshScheduler.armMidnightAlarm], which runs on app
 * start and after boot. Consistency rule: this receiver only *consumes* the tick and never
 * re-arms; if the alarm feels stale (e.g. the process was force-stopped and restarted by the
 * periodic worker), the worker re-arms it.
 */
internal class HijriWidgetMidnightAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                if (!HijriWidgetRefreshGate.isAppForeground()) {
                    HijriCalendarWidget().updateAll(context)
                    HijriWidgetConfig.markUpdatedNow(context, HijriWidgetRefreshScheduler.todayEpochDay())
                }
                HijriWidgetRefreshScheduler.armMidnightAlarm(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}