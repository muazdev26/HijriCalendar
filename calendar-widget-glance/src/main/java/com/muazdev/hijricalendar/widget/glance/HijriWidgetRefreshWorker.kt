package com.muazdev.hijricalendar.widget.glance

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Daily best-effort backstop for the widget family. Re-renders every widget and makes sure the
 * exact midnight alarm is re-armed (in case it was lost by a force-stop or a third-party managing
 * alarms). The boundary-accurate refresh itself is the AlarmManager alarm.
 *
 * The re-arm is placement-gated (WG-18): if the worker outlives the removal of the last widget —
 * it is cancelled by `onDisabled`, but a process kill can race that — it must not put the alarm
 * back for a widget that is no longer on screen.
 */
internal class HijriWidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        HijriWidgetRefreshScheduler.armMidnightAlarmIfPlaced(context)
        HijriWidgetRefresher.refreshAll(context, "daily-worker")
        return Result.success()
    }
}
