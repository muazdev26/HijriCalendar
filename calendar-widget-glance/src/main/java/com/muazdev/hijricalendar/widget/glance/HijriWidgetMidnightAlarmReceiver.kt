package com.muazdev.hijricalendar.widget.glance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fires from the exact AlarmManager alarm at local midnight to roll the grid over.
 *
 * It also fires from the repeating heartbeat
 * ([HijriWidgetRefreshScheduler.HEARTBEAT_INTERVAL_MS]) — deliberately the same receiver, so the
 * second chance and the boundary go through one rollover path rather than two that can drift. Both
 * alarms are `RTC_WAKEUP`, so this runs with the device asleep and needs the process-death
 * guarantees below.
 *
 * There is no intent-filter on this receiver; it is only reachable via the alarm
 * [PendingIntent] created by [HijriWidgetRefreshScheduler.armMidnightAlarm], which runs on app
 * start and after boot. If the app is on screen at midnight the render is skipped but recorded
 * as pending, so [HijriWidgetRefresher] applies it as soon as the app leaves the foreground.
 *
 * **The re-arm is a guarantee, not a happy-path step**, but a *conditional* one: it runs in
 * a `finally` so the next midnight boundary exists even when the render above throws, and it runs
 * only while a widget is still placed. It used to be the second statement rather than a
 * `finally`, which meant a single `DataStore` I/O failure at 00:00 could leave the alarm permanently
 * unarmed. From then on the only recovery was [HijriWidgetRefreshWorker]'s next daily run, so the
 * widget could show yesterday's date for up to a day, with no log line to explain it. Both the
 * render and the re-arm are logged at error level on failure, which is what makes such a report
 * diagnosable at all.
 *
 * The placement gate matters here because the alarm can fire into the moment just after the user
 * removed the last widget: re-arming unconditionally would resurrect the exact alarm whose removal
 * is the whole point of placement-gated scheduling, and the user would see the status-bar indicator
 * come back. When no widget is left there is nothing to roll over, so the alarm simply stops.
 *
 * Work runs on [HijriWidgetScope] (application context, logging handler) rather than an ad-hoc
 * scope, because this is the one place in the module that calls fallible library code with no
 * `catch` anywhere in its path — a throwable in a bare `SupervisorJob` reaches Android's default
 * uncaught-exception handler, which kills the process.
 *
 * [rollOver] is separate from [onReceive] so the guarantee can be tested: `goAsync()` needs a live
 * receiver, but the interesting behaviour — *the alarm is re-armed even when the render throws* —
 * is entirely in [rollOver].
 *
 * **The two alarms are one code path but two log reasons.** A `[midnight]` line means the exact
 * boundary fired; a `[heartbeat]` line means the six-hourly second chance did. Collapsing both onto
 * `"midnight"` would make a device whose exact alarm never fired indistinguishable from one that
 * simply woke up on schedule, which is the whole point of the diagnostic.
 */
internal class HijriWidgetMidnightAlarmReceiver : BroadcastReceiver() {

    // `TooGenericExceptionCaught` is suppressed on [rollOver] rather than in detekt's config
    // because the broad catch IS the fix: this must survive anything the refresh pipeline throws,
    // at midnight, with the re-arm still guaranteed. Naming the specific types would mean guessing
    // at what `refreshAll`'s callees can throw, which is exactly the enumeration that goes stale.
    @Suppress("TooGenericExceptionCaught")
    internal suspend fun rollOver(context: Context, reason: String, finish: () -> Unit) {
        try {
            refreshAction(context, reason)
        } catch (throwable: Exception) {
            // Must not escape: this would be a process death at 00:00. `Exception` rather than
            // `Throwable` because an `Error` (OOM, for instance) is not something to continue from
            // — but `finally` still runs for it, so the next boundary is armed either way (while a
            // widget is still placed).
            HijriWidgetRefreshLog.e(reason, "refresh failed: ${throwable.message}", throwable)
        } finally {
            // Always attempt the re-arm, gated on a widget still being placed. `runCatching` because
            // this is the recovery path and must not itself be the thing that throws (a
            // `SecurityException` from the platform, for instance).
            runCatching { HijriWidgetRefreshScheduler.armMidnightAlarmIfPlaced(context) }
                .onFailure {
                    HijriWidgetRefreshLog.e(reason, "re-arm failed: ${it.message}", it)
                }
            finish()
        }
    }

    /**
     * The rollover render, behind a seam so the test can inject a failure. Production is
     * [HijriWidgetRefresher.refreshAll], which is the only fallible call in [rollOver].
     */
    internal var refreshAction: suspend (Context, String) -> Unit =
        { ctx, reason -> HijriWidgetRefresher.refreshAll(ctx, reason) }

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val reason = alarmReasonFor(intent.action)
        // Either alarm can wake us — the exact midnight boundary or the repeating heartbeat — so the
        // log says how long it has been since we first armed for *this* boundary. `-1` means this
        // process never armed one (a process that only ever ran because the alarm fired).
        val deltaMs = HijriWidgetRefreshScheduler.lastArmedAtMillis()
        HijriWidgetRefreshLog.d(
            reason,
            if (deltaMs < 0) "alarm fired, no arm recorded" else "alarm fired, $deltaMs ms after arming",
        )
        HijriWidgetScope.launch(context, reason) {
            rollOver(it, reason) { pendingResult.finish() }
        }
    }
}

/**
 * Which alarm delivered a broadcast, as the log reason for the whole rollover.
 *
 * Takes the action rather than the `Intent` so it is assertable on the JVM: every class in the
 * stubbed `android.jar` answers defaults, so a real `Intent`'s action is always `null` in a unit
 * test and the heartbeat branch would be unreachable there.
 *
 * Only the heartbeat's `Intent` carries an action — the boundary's is deliberately frozen with none
 * (see `midnightAlarmIntent`), so the absence of an action is what identifies the boundary.
 */
internal fun alarmReasonFor(action: String?): String =
    if (action == HijriWidgetRefreshScheduler.HEARTBEAT_ACTION) "heartbeat" else "midnight"
