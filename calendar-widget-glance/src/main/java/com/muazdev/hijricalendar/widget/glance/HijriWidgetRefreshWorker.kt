package com.muazdev.hijricalendar.widget.glance

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Daily best-effort backstop for the widget family. Re-renders every widget and makes sure the
 * exact midnight alarm is re-armed (in case it was lost by a force-stop or a third-party managing
 * alarms). The boundary-accurate refresh itself is the AlarmManager alarm.
 */
internal class HijriWidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        HijriWidgetRefreshScheduler.armMidnightAlarm(context)
        HijriWidgetRefresher.refreshAll(context, "daily-worker")
        return Result.success()
    }
}
