package com.muazdev.hijricalendar.widget.glance

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
 *     actually ensures the grid rolls over at midnight. [armMidnightAlarm] is invoked centrally
 *     on app start, on boot, and again by the midnight receiver after it fires, so the alarm
 *     keeps re-arming without depending on the process staying alive between midnights.
 *
 * The alarm is exact via `USE_EXACT_ALARM` (auto-granted to calendar apps). On devices where
 * exact alarms are unavailable the call degrades to a regular `set()`, keeping the widget usable
 * albeit not guaranteed at the exact boundary.
 */
public object HijriWidgetRefreshScheduler {

    private const val WORK_NAME = "hijri_widget_daily_refresh"
    private const val ALARM_REQUEST_CODE = 4_101

    /**
     * `RTC_WAKEUP`, not `RTC` (WG-08).
     *
     * `RTC` fires only while the device is **awake**. A phone at local midnight is normally asleep,
     * so a `RTC` midnight alarm does not fire at midnight — it fires at the next unlock, which is
     * the "widget says yesterday when I pick up my phone in the morning" bug. This is also why the
     * library declares `USE_EXACT_ALARM`: the permission exists for alarms that wake the device,
     * and a rollover that cannot wake anything would not justify it.
     *
     * Both arms use it. An inexact `RTC_WAKEUP` still wakes the device, which is the property that
     * matters; exactness is a refinement on top.
     */
    private const val ALARM_TYPE = AlarmManager.RTC_WAKEUP

    /**
     * When the alarm was last armed, so the receiver can report how long it actually took to fire.
     *
     * This is what turns WG-08 from "the widget said yesterday" into a number. A device that fired
     * the alarm at the armed time needs no investigation; one that fired hours later did not wake at
     * midnight, and the log says so directly.
     */
    @Volatile
    private var armedAtMillis: Long = 0L

    /** Milliseconds between the last [armMidnightAlarm] and now; [Long.MIN_VALUE] if never armed. */
    internal fun lastArmedAtMillis(): Long {
        val armedAt = armedAtMillis
        return if (armedAt == 0L) Long.MIN_VALUE else System.currentTimeMillis() - armedAt
    }

    public fun schedule(context: Context) {
        enqueuePeriodic(context)
        armMidnightAlarm(context)
        // Regenerate the Android 15+ picker previews on first launch / every app start (cheap:
        // guarded to at most once per local day), so a fresh install immediately shows real
        // previews in the widget picker.
        HijriWidgetPreviewPublisher.publishIfDueAsync(context)
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
    public fun armMidnightAlarm(context: Context) {
        // Seam for the WG-07 test: the receiver's guarantee is "the next alarm exists even when the
        // render throws", and that cannot be asserted against a real AlarmManager in a JVM unit test.
        armAction(context)
    }

    /** The real [armMidnightAlarm], behind a seam. `internal` so only this module can replace it. */
    internal var armAction: (Context) -> Unit = ::armMidnightAlarmForReal

    private fun armMidnightAlarmForReal(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            Intent(context, HijriWidgetMidnightAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val triggerAt = nextLocalMidnightMillis()
        armedAtMillis = System.currentTimeMillis()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            HijriWidgetRefreshLog.d(
                "alarm",
                "arm INEXACT wake-up (canScheduleExactAlarms=false) at $triggerAt",
            )
            // `setAndAllowWhileIdle` rather than `set`: it survives Doze without consuming the
            // exact-alarm budget, which is the whole point of degrading in the first place.
            alarmManager.setAndAllowWhileIdle(ALARM_TYPE, triggerAt, pendingIntent)
        } else {
            HijriWidgetRefreshLog.d("alarm", "arm exact wake-up at $triggerAt")
            alarmManager.setExactAndAllowWhileIdle(ALARM_TYPE, triggerAt, pendingIntent)
        }
    }

    private fun nextLocalMidnightMillis(): Long {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        return now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    public fun todayEpochDay(): Long = LocalDate.now().toEpochDay()
}
