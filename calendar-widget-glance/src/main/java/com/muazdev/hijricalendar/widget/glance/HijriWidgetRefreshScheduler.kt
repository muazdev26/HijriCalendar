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
 *     actually ensures the grid rolls over at midnight. [schedule] is invoked centrally on app
 *     start and on boot, and the midnight receiver re-arms after it fires, so the alarm keeps
 *     re-arming without depending on the process staying alive between midnights.
 *
 * **Both mechanisms exist only while the user has a widget placed** (WG-18). [schedule] therefore
 * *reconciles* rather than arms unconditionally: with at least one instance bound it enqueues the
 * worker and arms the alarm, and with none it cancels both. A user who installed the library but
 * never placed a widget — or who has just removed the last one — is not left with a pending exact
 * alarm, which Android shows in the status bar and lists under Settings > Alarms & reminders. The
 * receivers call the same reconciliation from `onEnabled`/`onDisabled`, so placement is followed
 * in both directions without depending on the app process being restarted.
 * [armMidnightAlarm] remains the raw primitive for a caller that has already established a widget
 * exists; everything driven by an unconditional lifecycle event goes through [reconcile].
 *
 * The alarm is exact via `USE_EXACT_ALARM` (auto-granted to calendar apps). On devices where
 * exact alarms are unavailable the call degrades to a regular `setAndAllowWhileIdle()`, keeping
 * the widget usable albeit not guaranteed at the exact boundary.
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
        // Reconcile first, so the alarm/worker reflect whether a widget is actually placed, then
        // regenerate the Android 15+ picker previews on first launch / every app start (cheap:
        // guarded to at most once per local day), so a fresh install immediately shows real
        // previews in the widget picker. Previews are shown in the picker whether or not an
        // instance is placed, so that part is deliberately NOT gated (WG-18).
        reconcile(context)
        HijriWidgetPreviewPublisher.publishIfDueAsync(context)
    }

    /**
     * Brings the daily backstop and the midnight alarm in line with widget placement. Idempotent,
     * so it is safe from every trigger: app start, boot, a time/locale change, a widget being added
     * (`onEnabled`) and the last one being removed (`onDisabled`).
     *
     * The two sides are behind seams rather than inlined because `AppWidgetManager`, `WorkManager`
     * and `AlarmManager` are all stubbed in a local unit test, so the *decision* — activate versus
     * deactivate — cannot be asserted through them.
     */
    internal fun reconcile(context: Context) {
        val placed = placedAction(context)
        if (placed) {
            activateAction(context)
        } else {
            deactivateAction(context)
        }
        HijriWidgetRefreshLog.d(
            "schedule",
            if (placed) {
                "widget placed; daily worker + midnight alarm active"
            } else {
                "no widget placed; daily worker + midnight alarm cancelled"
            },
        )
    }

    /**
     * Re-arms the midnight alarm **only while a widget is still placed**.
     *
     * The midnight receiver and the daily worker both want "make sure the next boundary exists",
     * and both can run in the moment just after the last widget was removed but before `onDisabled`
     * reconciled. Re-arming unconditionally there would resurrect the very alarm the placement gate
     * exists to drop, which is why they call this rather than [armMidnightAlarm].
     */
    internal fun armMidnightAlarmIfPlaced(context: Context) {
        if (placedAction(context)) {
            armMidnightAlarm(context)
        } else {
            HijriWidgetRefreshLog.d("alarm", "skip re-arm: no widget placed")
        }
    }

    /**
     * Presence check, behind a seam so [reconcile]'s decision is testable on the JVM. The real
     * query is [HijriWidgetPresence.anyPlaced].
     */
    internal var placedAction: (Context) -> Boolean = HijriWidgetPresence::anyPlaced

    /** What "a widget is placed" schedules. Behind a seam for the reason in [reconcile]. */
    internal var activateAction: (Context) -> Unit = { context ->
        enqueuePeriodic(context)
        armMidnightAlarm(context)
    }

    /** What "no widget is placed" releases. Behind a seam for the reason in [reconcile]. */
    internal var deactivateAction: (Context) -> Unit = { context ->
        cancelPeriodic(context)
        cancelMidnightAlarm(context)
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
     * Cancels the daily backstop once the last widget is gone. The spec is constant, so re-arming
     * later is just [enqueuePeriodic] again; `cancelUniqueWork` is idempotent and cheap when there
     * is nothing to cancel.
     */
    private fun cancelPeriodic(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /**
     * Cancels the exact midnight alarm. The [PendingIntent] is looked up with `FLAG_NO_CREATE` so
     * a device that never armed one is not made to create it just to cancel it — an app with no
     * widget is the common case for this path.
     */
    private fun cancelMidnightAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            Intent(context, HijriWidgetMidnightAlarmReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    /**
     * Re-arms the exact midnight alarm (idempotent: a new `RTC_WAKEUP` alarm replaces the previous
     * one). This is the raw primitive — it does **not** check whether a widget is placed, because
     * every caller inside this module has already established that (WG-18). A host that arms it
     * directly is responsible for the same check; the normal entry point is [schedule], which
     * reconciles placement for you.
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
