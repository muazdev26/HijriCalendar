package com.muazdev.hijricalendar.widget.glance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fires from the exact AlarmManager alarm at local midnight to roll the grid over.
 *
 * There is no intent-filter on this receiver; it is only reachable via the alarm
 * [PendingIntent] created by [HijriWidgetRefreshScheduler.armMidnightAlarm], which runs on app
 * start and after boot. If the app is on screen at midnight the render is skipped but recorded
 * as pending, so [HijriWidgetRefresher] applies it as soon as the app leaves the foreground.
 * The alarm is re-armed here too, so a skipped rollover still schedules the next boundary.
 */
internal class HijriWidgetMidnightAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        HijriWidgetRefreshLog.d("midnight", "alarm fired")
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                HijriWidgetRefresher.refreshAll(context, "midnight")
                HijriWidgetRefreshScheduler.armMidnightAlarm(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
