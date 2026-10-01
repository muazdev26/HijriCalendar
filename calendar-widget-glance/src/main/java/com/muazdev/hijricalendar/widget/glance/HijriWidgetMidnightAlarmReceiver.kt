package com.muazdev.hijricalendar.widget.glance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fires from the exact AlarmManager alarm at local midnight to roll the grid over.
 *
 * There is no intent-filter on this receiver; it is only reachable via the alarm
 * [PendingIntent] created by [HijriWidgetRefreshScheduler.armMidnightAlarm], which runs on app
 * start and after boot. If the app is on screen at midnight the render is skipped but recorded
 * as pending, so [HijriWidgetRefresher] applies it as soon as the app leaves the foreground.
 *
 * **The re-arm is a guarantee, not a happy-path step** (WG-07). It runs in a `finally`, so the next
 * midnight boundary exists even when the render above throws — and it used to be the second
 * statement rather than a `finally`, which meant a single `DataStore` I/O failure at 00:00 could
 * leave the alarm permanently unarmed. From then on the only recovery was
 * [HijriWidgetRefreshWorker]'s next daily run, so the widget could show yesterday's date for up to a
 * day, with no log line to explain it. Both the render and the re-arm are logged at error level on
 * failure, which is what makes such a report diagnosable at all.
 *
 * Work runs on [HijriWidgetScope] (application context, logging handler) rather than an ad-hoc
 * scope, because this is the one place in the module that calls fallible library code with no
 * `catch` anywhere in its path — a throwable in a bare `SupervisorJob` reaches Android's default
 * uncaught-exception handler, which kills the process.
 *
 * [rollOver] is separate from [onReceive] so the guarantee can be tested: `goAsync()` needs a live
 * receiver, but the interesting behaviour — *the alarm is re-armed even when the render throws* —
 * is entirely in [rollOver].
 */
internal class HijriWidgetMidnightAlarmReceiver : BroadcastReceiver() {

    // `TooGenericExceptionCaught` is suppressed on [rollOver] rather than in detekt's config
    // because the broad catch IS the fix: this must survive anything the refresh pipeline throws,
    // at midnight, with the re-arm still guaranteed. Naming the specific types would mean guessing
    // at what `refreshAll`'s callees can throw, which is exactly the enumeration that goes stale.
    @Suppress("TooGenericExceptionCaught")
    internal suspend fun rollOver(context: Context, finish: () -> Unit) {
        try {
            refreshAction(context, "midnight")
        } catch (throwable: Exception) {
            // Must not escape: this would be a process death at 00:00. `Exception` rather than
            // `Throwable` because an `Error` (OOM, for instance) is not something to continue from
            // — but `finally` still runs for it, so the alarm is re-armed either way.
            HijriWidgetRefreshLog.e("midnight", "refresh failed: ${throwable.message}", throwable)
        } finally {
            // Always re-arm. `runCatching` because this is the recovery path and must not itself be
            // the thing that throws (a `SecurityException` from the platform, for instance).
            runCatching { HijriWidgetRefreshScheduler.armMidnightAlarm(context) }
                .onFailure {
                    HijriWidgetRefreshLog.e("midnight", "re-arm failed: ${it.message}", it)
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
        val deltaMs = System.currentTimeMillis() - HijriWidgetRefreshScheduler.lastArmedAtMillis()
        HijriWidgetRefreshLog.d("midnight", "alarm fired, $deltaMs ms after arming")
        HijriWidgetScope.launch(context, "midnight") {
            rollOver(it) { pendingResult.finish() }
        }
    }
}
