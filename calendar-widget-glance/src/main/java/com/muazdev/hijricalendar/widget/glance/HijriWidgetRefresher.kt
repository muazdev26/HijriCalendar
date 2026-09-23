package com.muazdev.hijricalendar.widget.glance

import android.content.Context
import androidx.glance.GlanceId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The single entry point for every widget re-render, so the things that used to lose refreshes
 * are decided in one place and logged:
 *
 *  - the foreground gate (skip first, mark pending, catch up when the app leaves the foreground),
 *  - the same-day dedupe marker (busywork guard for background triggers), and
 *  - explicit user actions ([bypassGate]) which land even while the app is visible.
 *
 * The two bypass flags are deliberately independent:
 *
 *  - [bypassGate] — user-initiated work (settings, "Refresh now") that must apply on touch.
 *  - [bypassDedupe] — content changed independently of the day marker (locale/timezone/clock,
 *    on-widget source toggle) so a render is due even though today's marker is already set.
 *
 * A skipped-but-pending render overrides the dedupe marker, so a midnight rollover missed while
 * the app was on screen is applied at the next background opportunity instead of staying stale
 * for the rest of the day.
 *
 * Public so a host app's own settings screen can push a re-render after [HijriWidgetConfig.save]:
 * use [refreshInstanceAsync] to update exactly the widget being configured, or [refreshAllAsync]
 * for a family-wide "Refresh now" that bypasses both the foreground gate and the day marker.
 */
object HijriWidgetRefresher {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Short grace period before a catch-up after the app leaves the foreground. A configuration
     * change transiently stops the old activity before the new one starts; without the delay that
     * would look like "app backgrounded" and fire a render mid-rotation. [refreshAll] re-checks the
     * gate, so if the app came back the catch-up just records itself as pending instead.
     */
    private const val CATCH_UP_DELAY_MS = 1_500L

    /**
     * Re-renders the whole widget family.
     *
     * @return true when a render was actually performed.
     */
    suspend fun refreshAll(
        context: Context,
        reason: String,
        bypassGate: Boolean = false,
        bypassDedupe: Boolean = false,
    ): Boolean {
        val todayEpochDay = HijriWidgetRefreshScheduler.todayEpochDay()
        val lastUpdated = HijriWidgetConfig.lastUpdatedEpochDay(context)
        val foreground = HijriWidgetRefreshGate.isAppForeground()
        val fresh = HijriWidgetConfig.isFreshFor(context, todayEpochDay)
        val pending = HijriWidgetConfig.isRefreshPending(context)

        if (foreground && !bypassGate) {
            HijriWidgetConfig.markRefreshPending(context, true)
            HijriWidgetRefreshLog.d(
                reason,
                "skip: app foreground; marked pending (fresh=$fresh, wasPending=$pending)",
            )
            return false
        }
        if (fresh && !bypassDedupe && !pending) {
            HijriWidgetRefreshLog.d(reason, "skip: already refreshed for epochDay=$todayEpochDay")
            return false
        }

        HijriWidgetRefreshLog.d(
            reason,
            "render: epochDay=$todayEpochDay lastUpdated=$lastUpdated (foreground=$foreground, " +
                "fresh=$fresh, pending=$pending, bypassGate=$bypassGate, bypassDedupe=$bypassDedupe)",
        )
        HijriWidgetRenderQueue.renderAll(context)
        HijriWidgetConfig.markUpdatedNow(context, todayEpochDay)
        HijriWidgetConfig.markRefreshPending(context, false)
        HijriWidgetRefreshLog.d(reason, "render: done; cleared pending")
        return true
    }

    /** Fire-and-forget [refreshAll] for callers without a coroutine scope. */
    fun refreshAllAsync(
        context: Context,
        reason: String,
        bypassGate: Boolean = false,
        bypassDedupe: Boolean = false,
    ) {
        scope.launch { refreshAll(context, reason, bypassGate, bypassDedupe) }
    }

    /**
     * Re-renders a single widget instance. Used by user-initiated settings changes so the update
     * touches exactly the widget being configured and never hits the foreground gate.
     */
    suspend fun refreshInstance(context: Context, glanceId: GlanceId?, reason: String) {
        if (glanceId == null) {
            HijriWidgetRefreshLog.d(reason, "instance id unavailable; falling back to all widgets")
            refreshAll(context, reason, bypassGate = true, bypassDedupe = true)
            return
        }
        HijriWidgetRefreshLog.d(reason, "render instance: $glanceId")
        HijriWidgetRenderQueue.render(context, glanceId)
    }

    /** Fire-and-forget [refreshInstance]. */
    fun refreshInstanceAsync(context: Context, glanceId: GlanceId?, reason: String) {
        scope.launch { refreshInstance(context, glanceId, reason) }
    }

    /**
     * Debounced catch-up for when the app leaves the foreground: waits [CATCH_UP_DELAY_MS] and
     * then runs a gate-aware [refreshAll], so renders skipped while the app was visible (and a
     * missed midnight rollover in particular) are applied shortly after.
     */
    fun scheduleCatchUp(context: Context, reason: String) {
        scope.launch {
            delay(CATCH_UP_DELAY_MS)
            refreshAll(context, reason)
        }
    }
}
