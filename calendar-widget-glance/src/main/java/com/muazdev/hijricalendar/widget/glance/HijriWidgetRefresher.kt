package com.muazdev.hijricalendar.widget.glance

import android.content.Context
import androidx.glance.GlanceId
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
 * All three entry points dispatch through [HijriWidgetScope] rather than an ad-hoc scope, so a
 * throwable from any of them is logged instead of taking down the host process at, say, midnight
 * (WG-07).
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
 * **The invariant the dedupe depends on: `markUpdatedNow` is called if and only if a Glance render
 * for this epoch day was actually requested.** Stated here because this is where the dedupe reads
 * it. It is easy to break — a render request that returns normally has not necessarily rendered
 * anything; [HijriWidgetRenderQueue] coalesces a request into an in-flight render and returns. Mark
 * on that path and the marker is a lie, and every later background trigger for the rest of the day
 * skips with a log line that reads like correct behaviour.
 *
 * Public so a host app's own settings screen can push a re-render after [HijriWidgetConfig.save]:
 * use [refreshInstanceAsync] to update exactly the widget being configured, or [refreshAllAsync]
 * for a family-wide "Refresh now" that bypasses both the foreground gate and the day marker.
 */
public object HijriWidgetRefresher {

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
     * @return true when a Glance render was actually requested. A request that was *coalesced* into
     *   an already-in-flight render returns false: the work will still happen when that holder
     *   drains, but nothing has been pushed yet, so this is not a "rendered" as far as any caller
     *   should record.
     */
    public suspend fun refreshAll(
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
        when (HijriWidgetRenderQueue.renderAll(context)) {
            HijriWidgetRenderQueue.RenderOutcome.Coalesced -> {
                // Deliberately leaves `markUpdatedNow` alone. A render is in flight and will drain
                // our sweep, so the work is not lost — but the marker means "the display now shows
                // today", and nothing has been pushed yet. Writing it here is what turned a narrow
                // lost race into a sticky, self-concealing one: every background trigger for the
                // rest of the epoch day would then read "already refreshed" and skip, and that log
                // line looks like correct behaviour. Leaving the marker alone costs at most one
                // redundant render; writing it wrongly costs a day of stale widgets.
                HijriWidgetRefreshLog.d(
                    reason,
                    "coalesced into an in-flight render; marker left unchanged",
                )
                return false
            }

            HijriWidgetRenderQueue.RenderOutcome.Rendered -> Unit
        }
        HijriWidgetConfig.markUpdatedNow(context, todayEpochDay)
        HijriWidgetConfig.markRefreshPending(context, false)
        // Best-effort: regenerate the Android 15+ picker previews so the picker always reflects
        // today's date and the family's current options (once-daily, rate-limit guarded).
        HijriWidgetPreviewPublisher.publishIfDue(context)
        HijriWidgetRefreshLog.d(reason, "render: done; cleared pending")
        return true
    }

    /** Fire-and-forget [refreshAll] for callers without a coroutine scope. */
    public fun refreshAllAsync(
        context: Context,
        reason: String,
        bypassGate: Boolean = false,
        bypassDedupe: Boolean = false,
    ) {
        HijriWidgetScope.launch(context, reason) {
            refreshAll(it, reason, bypassGate, bypassDedupe)
        }
    }

    /**
     * Re-renders a single widget instance. Used by user-initiated settings changes so the update
     * touches exactly the widget being configured and never hits the foreground gate.
     *
     * No day marker is written here, so a coalesced request is harmless — but the outcome is still
     * logged, because "the user tapped apply and nothing was pushed yet" is worth being able to
     * distinguish from "it rendered". The return type stays `Unit`: changing it would alter the
     * published JVM signature of a public function in the one module with no ABI gate.
     */
    public suspend fun refreshInstance(context: Context, glanceId: GlanceId?, reason: String) {
        if (glanceId == null) {
            HijriWidgetRefreshLog.d(reason, "instance id unavailable; falling back to all widgets")
            refreshAll(context, reason, bypassGate = true, bypassDedupe = true)
            return
        }
        HijriWidgetRefreshLog.d(reason, "render instance: $glanceId")
        when (HijriWidgetRenderQueue.render(context, glanceId)) {
            HijriWidgetRenderQueue.RenderOutcome.Coalesced ->
                HijriWidgetRefreshLog.d(reason, "instance render coalesced into an in-flight render")
            HijriWidgetRenderQueue.RenderOutcome.Rendered ->
                HijriWidgetRefreshLog.d(reason, "instance render done")
        }
    }

    /** Fire-and-forget [refreshInstance]. */
    public fun refreshInstanceAsync(context: Context, glanceId: GlanceId?, reason: String) {
        HijriWidgetScope.launch(context, reason) { refreshInstance(it, glanceId, reason) }
    }

    /**
     * Debounced catch-up for when the app leaves the foreground: waits [CATCH_UP_DELAY_MS] and
     * then runs a gate-aware [refreshAll], so renders skipped while the app was visible (and a
     * missed midnight rollover in particular) are applied shortly after.
     */
    public fun scheduleCatchUp(context: Context, reason: String) {
        HijriWidgetScope.launch(context, reason) {
            delay(CATCH_UP_DELAY_MS)
            refreshAll(it, reason)
        }
    }
}
