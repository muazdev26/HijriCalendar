package com.muazdev.hijricalendar.sample.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Owns the two refresh mechanisms that keep the widget family day-accurate:
 *
 *  1. A periodic [WorkManager] backstop (daily, best-effort).
 *  2. An exact `AlarmManager` alarm for the next local midnight. This is the mechanism that
 *     actually ensures the grid rolls over at midnight; it is a *self-re-arming* alarm, but all
 *     re-arming is done from [armMidnightAlarm] which is invoked centrally on app start and on
 *     boot. The midnight receiver itself never schedules.
 *
 * The alarm is exact via `USE_EXACT_ALARM` (auto-granted to calendar apps). On devices where
 * exact alarms are unavailable the call degrades to a regular `set()`, keeping the widget usable
 * albeit not guaranteed at the exact boundary.
 */
internal object HijriWidgetRefreshScheduler {

    private const val WORK_NAME = "hijri_widget_daily_refresh"
    private const val ALARM_REQUEST_CODE = 4_101

    fun schedule(context: Context) {
        enqueuePeriodic(context)
        armMidnightAlarm(context)
    }

    private fun enqueuePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<HijriWidgetRefreshWorker>(24, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /**
     * Re-arms the exact midnight alarm (idempotent: a new RTC alarm replaces the previous one).
     * Called on application start and after BOOT_COMPLETED so the alarm survives reboots without
     * the midnight receiver ever needing to re-arm itself.
     */
    fun armMidnightAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            Intent(context, HijriWidgetMidnightAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val triggerAt = nextLocalMidnightMillis()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.set(AlarmManager.RTC, triggerAt, pendingIntent)
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC, triggerAt, pendingIntent)
        }
    }

    private fun nextLocalMidnightMillis(): Long {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        return now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    fun todayEpochDay(): Long = LocalDate.now().toEpochDay()
}