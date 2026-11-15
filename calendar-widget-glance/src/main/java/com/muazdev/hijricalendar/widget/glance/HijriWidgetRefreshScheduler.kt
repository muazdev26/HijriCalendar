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
 * Owns the three refresh mechanisms that keep the widget family day-accurate:
 *
 *  1. A periodic [WorkManager] backstop (daily, best-effort).
 *  2. An exact `AlarmManager` alarm for the next local midnight. This is the mechanism that
 *     actually ensures the grid rolls over at midnight. [schedule] is invoked centrally on app
 *     start and on boot, and the midnight receiver re-arms after it fires, so the alarm keeps
 *     re-arming without depending on the process staying alive between midnights.
 *  3. A repeating `AlarmManager` heartbeat every [HEARTBEAT_INTERVAL_MS]. A one-shot alarm has one
 *     chance per day, so a single lost arm — an OEM "battery saver" clearing alarms, a revoked
 *     exact-alarm grant, a crash between the fire and the re-arm — costs a full day of stale
 *     dates. The heartbeat is the second, independent chance. It is armed only by [reconcile]
 *     (never by the host-sweep repair), because re-arming a repeating alarm restarts its phase.
 *
 * On top of that, [repairMidnightAlarmFromHostUpdate] makes the host's own `updatePeriodMillis`
 * sweep a repair path: it is an alarm the *launcher* owns, so it is the one trigger that keeps
 * firing on a device whose OEM power manager clears everything the app scheduled.
 *
 * **All three mechanisms exist only while the user has a widget placed**. [schedule] therefore
 * *reconciles* rather than arms unconditionally: with at least one instance bound it enqueues the
 * worker, arms the midnight alarm and starts the heartbeat, and with none it cancels all three. A
 * user who installed the library but never placed a widget — or who has just removed the last one —
 * is not left with a pending exact alarm or a repeating wake-up, which Android shows in the status
 * bar and lists under Settings > Alarms & reminders. The receivers call the same reconciliation from
 * `onEnabled`/`onDisabled`, so placement is followed in both directions without depending on the app
 * process being restarted. [armMidnightAlarm] remains the raw primitive for a caller that has
 * already established a widget exists; everything driven by an unconditional lifecycle event goes
 * through [reconcile].
 *
 * The alarm is exact via `USE_EXACT_ALARM` (auto-granted to calendar apps on Android 13+) and
 * `SCHEDULE_EXACT_ALARM` (the Android 12 mechanism). Exact-alarm access is not guaranteed on a
 * device: a user can revoke it under Settings, and from Android 14 it is not pre-granted to every
 * app. [armMidnightAlarmForReal] therefore asks the platform `canScheduleExactAlarms()` first and
 * degrades to `setAndAllowWhileIdle()` when the answer is no, so the widget still rolls over —
 * just not guaranteed at the exact boundary. [AlarmKind] is the decision as a pure function, and
 * `HijriWidgetRefreshSchedulerTest` drives the real arm path through [armerAction].
 */
public object HijriWidgetRefreshScheduler {

    private const val WORK_NAME = "hijri_widget_daily_refresh"
    internal const val ALARM_REQUEST_CODE = 4_101

    /**
     * Request code of the repeating heartbeat, deliberately different from [ALARM_REQUEST_CODE].
     *
     * `PendingIntent` identity is (request code, `Intent` filter), and the two alarms are the same
     * broadcast to the same receiver. Sharing a request code would make the heartbeat and the
     * midnight boundary two names for one alarm: arming either would silently replace the other.
     */
    internal const val HEARTBEAT_REQUEST_CODE = 4_102

    /**
     * How often the heartbeat repeats: six hours.
     *
     * The trade-off is stale-ness against wake-ups. A one-shot daily alarm answers "nothing ran at
     * midnight" with a whole day of a wrong date; four wake-ups a day bound that to about six hours
     * (plus whatever Doze defers — a repeating alarm is not `allowWhileIdle`). It is also the same
     * order as the 30-minute `updatePeriodMillis` sweeps the library already asks the launcher for,
     * so it does not change the battery profile of the family.
     */
    internal const val HEARTBEAT_INTERVAL_MS: Long = 6L * 60 * 60 * 1000

    /**
     * Action on the heartbeat's [Intent], so the receiver can tell which of its two alarms fired and
     * label the rollover in [HijriWidgetRefreshLog] accordingly.
     *
     * The midnight boundary's intent deliberately has **no** action: its shape is frozen, because a
     * `PendingIntent` is matched by `filterEquals` and changing the shape would leave an alarm armed
     * by an older build uncancellable.
     */
    internal const val HEARTBEAT_ACTION: String = "com.muazdev.hijricalendar.widget.HEARTBEAT"

    /**
     * `RTC_WAKEUP`, not `RTC`.
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
    internal const val ALARM_TYPE = AlarmManager.RTC_WAKEUP

    /**
     * When the alarm was first armed **for the upcoming midnight**, so the receiver can report how
     * long it actually took to fire.
     *
     * This is what turns "the widget said yesterday" into a number. A device that fired
     * the alarm at the armed time needs no investigation; one that fired hours later did not wake at
     * midnight, and the log says so directly.
     *
     * **Stamped per boundary, not per arm.** [repairMidnightAlarmFromHostUpdate] re-arms on every
     * host sweep, so a plain "last arm" timestamp would read "fired 12 minutes after arming" on
     * every device and say nothing. The stamp therefore moves only when the *target* changes — see
     * [restampArmedAt] — which keeps the measurement "time since we could first have armed this
     * midnight", whether or not a sweep has re-armed it since.
     */
    @Volatile
    private var armedAtMillis: Long = 0L

    /** The boundary [armedAtMillis] describes; `0` while nothing has been armed. */
    @Volatile
    private var armedForTriggerAtMillis: Long = 0L

    /** Milliseconds since the alarm was first armed for the next midnight; `-1` if never armed. */
    internal fun lastArmedAtMillis(): Long {
        val armedAt = armedAtMillis
        return if (armedAt == 0L) -1L else System.currentTimeMillis() - armedAt
    }

    public fun schedule(context: Context) {
        // Reconcile first, so the alarm/worker reflect whether a widget is actually placed, then
        // regenerate the Android 15+ picker previews on first launch / every app start (cheap:
        // guarded to at most once per local day), so a fresh install immediately shows real
        // previews in the widget picker. Previews are shown in the picker whether or not an
        // instance is placed, so that part is deliberately NOT gated.
        reconcile(context)
        HijriWidgetPreviewPublisher.publishIfDueAsync(context)
    }

    /**
     * Brings the daily backstop, the midnight alarm and the repeating heartbeat in line with widget
     * placement. Idempotent, so it is safe from every trigger: app start, boot, a time/locale change,
     * a widget being added (`onEnabled`) and the last one being removed (`onDisabled`).
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
                "widget placed; daily worker + midnight alarm + heartbeat active"
            } else {
                "no widget placed; daily worker + midnight alarm + heartbeat cancelled"
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
     * Repairs the midnight alarm from the host's own periodic sweep.
     *
     * `android:updatePeriodMillis` is not our alarm — the launcher/`AppWidgetService` owns it and
     * calls the receiver every 30 minutes for as long as the provider is bound. That makes it the
     * most resilient trigger the family has on a device whose OEM power manager clears what the app
     * itself scheduled (Transsion/XOS "Power Marathon" and friends): when it still delivers, a lost
     * midnight alarm is restored within the half hour instead of waiting for the user to open the
     * app and reach [schedule].
     *
     * Costs one exact-alarm re-arm per sweep for the one provider whose `onUpdate` runs, and is
     * idempotent: the trigger time is recomputed from the clock, so re-arming mid-day still targets
     * the same upcoming midnight.
     *
     * [instanceCount] is the ids the host just sent. Zero means the provider is unbound — a sweep
     * racing widget removal — and arming there would resurrect the alarm [deactivateAction] exists
     * to drop.
     */
    internal fun repairMidnightAlarmFromHostUpdate(context: Context, instanceCount: Int) {
        if (instanceCount <= 0) {
            HijriWidgetRefreshLog.d("host-update", "skip repair: no instances in this sweep")
            return
        }
        armMidnightAlarm(context)
        HijriWidgetRefreshLog.d("host-update", "re-armed midnight alarm from the host sweep")
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
        // Only here, never from the host-sweep repair: re-arming a repeating alarm restarts its
        // phase, so a 30-minute sweep would push a 6-hour heartbeat out forever and it would never
        // fire. Reconciling is the rare event (app start, boot, placement change), and a lost
        // heartbeat is harmless while a sweep keeps the one-shot alive anyway.
        armHeartbeatAlarm(context)
    }

    /** What "no widget is placed" releases. Behind a seam for the reason in [reconcile]. */
    internal var deactivateAction: (Context) -> Unit = { context ->
        cancelPeriodic(context)
        cancelAlarms(context)
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
     * Cancels both alarms — the exact midnight boundary and the repeating heartbeat — once the last
     * widget is gone. The [PendingIntent]s are looked up with `FLAG_NO_CREATE` so a device that never
     * armed one is not made to create it just to cancel it; an app with no widget is the common case
     * for this path.
     *
     * Both, not just the boundary: the heartbeat is a `RTC_WAKEUP` alarm, so leaving it behind would
     * keep waking the device every [HEARTBEAT_INTERVAL_MS] for an app with nothing on a home screen —
     * exactly the status-bar-indicator complaint placement gating exists to answer.
     */
    private fun cancelAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val alarms = listOf(
            ALARM_REQUEST_CODE to midnightAlarmIntent(context),
            HEARTBEAT_REQUEST_CODE to heartbeatAlarmIntent(context),
        )
        for ((requestCode, intent) in alarms) {
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ) ?: continue
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    /**
     * Re-arms the exact midnight alarm (idempotent: a new `RTC_WAKEUP` alarm replaces the previous
     * one). This is the raw primitive — it does **not** check whether a widget is placed, because
     * every caller inside this module has already established that. A host that arms it
     * directly is responsible for the same check; the normal entry point is [schedule], which
     * reconciles placement for you.
     */
    public fun armMidnightAlarm(context: Context) {
        // Seam for the receiver's guarantee test: "the next alarm exists even when the
        // render throws", and that cannot be asserted against a real AlarmManager in a JVM unit test.
        armAction(context)
    }

    /** The real [armMidnightAlarm], behind a seam. `internal` so only this module can replace it. */
    internal var armAction: (Context) -> Unit = ::armMidnightAlarmForReal

    /**
     * The platform armer, behind a seam so the exact/inexact branch in
     * [armMidnightAlarmForReal] can be asserted on the JVM. See [MidnightAlarmArmer].
     */
    internal var armerAction: (Context) -> MidnightAlarmArmer = ::PlatformAlarmArmer

    internal fun armMidnightAlarmForReal(context: Context) {
        val armer = armerAction(context)
        val triggerAt = nextLocalMidnightMillis()
        restampArmedAt(triggerAt)

        when (alarmKindFor(armer.apiLevel(), armer.canScheduleExactAlarms())) {
            AlarmKind.INEXACT -> {
                HijriWidgetRefreshLog.d(
                    "alarm",
                    "arm INEXACT wake-up (canScheduleExactAlarms=false) at $triggerAt",
                )
                // `setAndAllowWhileIdle` rather than `set`: it survives Doze without consuming the
                // exact-alarm budget, which is the whole point of degrading in the first place.
                armer.armInexact(triggerAt)
            }
            AlarmKind.EXACT -> {
                HijriWidgetRefreshLog.d("alarm", "arm exact wake-up at $triggerAt")
                armer.armExact(triggerAt)
            }
        }
    }

    /**
     * Moves the "armed at" stamp onto [triggerAt], but only when the boundary actually changed.
     *
     * Called on every arm, including the host-sweep repair, so the stamp describes *the first arm for
     * this midnight* rather than the most recent re-arm. That is the number the receiver's
     * "fired N ms after arming" log is worth reading for; refreshing it every 30 minutes would make
     * every device look punctual.
     */
    private fun restampArmedAt(triggerAt: Long) {
        if (triggerAt == armedForTriggerAtMillis) return
        armedForTriggerAtMillis = triggerAt
        armedAtMillis = System.currentTimeMillis()
    }

    /**
     * Arms the repeating heartbeat (idempotent in the sense that a new repeating alarm replaces the
     * previous one — but re-arming restarts its phase, see [activateAction]).
     *
     * It wakes the process through the same [HijriWidgetMidnightAlarmReceiver] as the midnight
     * boundary and performs the same rollover, so there is no second code path to keep in step.
     *
     * No seam of its own: unlike [armAction], the only thing worth asserting is that a heartbeat was
     * armed and that it did not touch the boundary, and [armerAction] already exposes both.
     */
    internal fun armHeartbeatAlarm(context: Context) {
        val triggerAt = System.currentTimeMillis() + HEARTBEAT_INTERVAL_MS
        armerAction(context).armHeartbeat(triggerAt)
        HijriWidgetRefreshLog.d(
            "heartbeat",
            "arm repeating wake-up every ${HEARTBEAT_INTERVAL_MS / 60_000} min from $triggerAt",
        )
    }

    private fun nextLocalMidnightMillis(): Long {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        return now.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }

    public fun todayEpochDay(): Long = LocalDate.now().toEpochDay()
}

/**
 * Which `AlarmManager` call the midnight alarm uses.
 *
 * Exactness is a refinement on top of [HijriWidgetRefreshScheduler]'s `RTC_WAKEUP` type — an
 * inexact alarm still wakes the device, it just may land later than 00:00. The distinction is a
 * named type so the fallback can be asserted without an `AlarmManager`.
 */
internal enum class AlarmKind {
    /** `setExactAndAllowWhileIdle` — fires at the local-midnight boundary. */
    EXACT,

    /** `setAndAllowWhileIdle` — survives Doze, but the platform chooses the moment. */
    INEXACT,
}

/**
 * The fallback rule, as a pure function.
 *
 * `canScheduleExactAlarms()` exists from API 31; below that the permission model does not apply
 * and the alarm is always exact. From 31 on, a revoked or never-granted access means the app must
 * degrade rather than crash — `setExactAndAllowWhileIdle` throws `SecurityException` in that case.
 */
internal fun alarmKindFor(sdkInt: Int, canScheduleExactAlarms: Boolean): AlarmKind =
    if (sdkInt >= Build.VERSION_CODES.S && !canScheduleExactAlarms) {
        AlarmKind.INEXACT
    } else {
        AlarmKind.EXACT
    }

/**
 * The intent behind the midnight-boundary alarm.
 *
 * Its shape is deliberately frozen — no action, no extras — because `PendingIntent` identity is
 * `requestCode` + `filterEquals(intent)`: a builder that started adding either would make an alarm
 * armed by an older build impossible to look up, and therefore impossible to cancel.
 */
internal fun midnightAlarmIntent(context: Context): Intent =
    Intent(context, HijriWidgetMidnightAlarmReceiver::class.java)

/**
 * The heartbeat's intent: the same broadcast, tagged so [HijriWidgetMidnightAlarmReceiver] can log
 * which of its two alarms fired. Safe to shape freely — the heartbeat did not exist before it.
 */
internal fun heartbeatAlarmIntent(context: Context): Intent =
    midnightAlarmIntent(context).setAction(HijriWidgetRefreshScheduler.HEARTBEAT_ACTION)

/**
 * The platform calls the alarm arm makes, behind an interface.
 *
 * `AlarmManager` and `PendingIntent` are stubbed in a local unit test, so the *choice* between the
 * two arming methods cannot be observed through them. [HijriWidgetRefreshScheduler.armerAction]
 * lets a test supply a recording implementation and drive the real
 * `armMidnightAlarmForReal` path.
 */
internal interface MidnightAlarmArmer {
    /**
     * The API level the arming calls run on.
     *
     * Part of the seam, not a convenience: the exact-alarm permission model starts at API 31, and a
     * JVM unit test always reads `SDK_INT == 0`, so a test that wants to exercise the fallback has
     * to be able to say which device it is standing in for.
     */
    fun apiLevel(): Int

    /** Whether the platform will accept an exact alarm right now. Always true below API 31. */
    fun canScheduleExactAlarms(): Boolean

    /** `setExactAndAllowWhileIdle(RTC_WAKEUP, …)`. */
    fun armExact(triggerAtMillis: Long)

    /** `setAndAllowWhileIdle(RTC_WAKEUP, …)`. */
    fun armInexact(triggerAtMillis: Long)

    /**
     * `setInexactRepeating(RTC_WAKEUP, …, HEARTBEAT_INTERVAL_MS, …)` on the heartbeat's own
     * `PendingIntent`, so it does not replace the midnight boundary.
     *
     * Inexact on purpose: a repeating alarm cannot be exact from API 19 on, and the heartbeat is a
     * second chance rather than the boundary itself — spending the exact-alarm grant on it would buy
     * nothing.
     */
    fun armHeartbeat(triggerAtMillis: Long)
}

/** The real [MidnightAlarmArmer]: one `AlarmManager` and the receiver's two `PendingIntent`s. */
internal class PlatformAlarmArmer(context: Context) : MidnightAlarmArmer {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val pendingIntent = PendingIntent.getBroadcast(
        context,
        HijriWidgetRefreshScheduler.ALARM_REQUEST_CODE,
        midnightAlarmIntent(context),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private val heartbeatPendingIntent = PendingIntent.getBroadcast(
        context,
        HijriWidgetRefreshScheduler.HEARTBEAT_REQUEST_CODE,
        heartbeatAlarmIntent(context),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    override fun apiLevel(): Int = Build.VERSION.SDK_INT

    override fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()

    override fun armExact(triggerAtMillis: Long) {
        alarmManager.setExactAndAllowWhileIdle(
            HijriWidgetRefreshScheduler.ALARM_TYPE,
            triggerAtMillis,
            pendingIntent,
        )
    }

    override fun armInexact(triggerAtMillis: Long) {
        alarmManager.setAndAllowWhileIdle(
            HijriWidgetRefreshScheduler.ALARM_TYPE,
            triggerAtMillis,
            pendingIntent,
        )
    }

    override fun armHeartbeat(triggerAtMillis: Long) {
        alarmManager.setInexactRepeating(
            HijriWidgetRefreshScheduler.ALARM_TYPE,
            triggerAtMillis,
            HijriWidgetRefreshScheduler.HEARTBEAT_INTERVAL_MS,
            heartbeatPendingIntent,
        )
    }
}
