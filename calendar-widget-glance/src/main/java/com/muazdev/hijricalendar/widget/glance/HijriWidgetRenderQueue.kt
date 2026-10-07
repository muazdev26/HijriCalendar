package com.muazdev.hijricalendar.widget.glance

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.updateAll
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Orders every widget render in the family and coalesces bursts of taps into a single render of
 * the final state. Every render pass — instance or sweep — also updates every other widget class
 * ([HijriTodayWidget], [HijriDateWidget], [GregorianDateWidget], [HijriDualDateWidget]): they have no
 * settings screen of their own and render the family options mirror, so any render triggered by a
 * settings apply must re-read it too. (There is no on-widget source toggle: the source is chosen only from the host's
 * settings screen — WD-10c.)
 *
 * Why this exists: Glance's `update()` is fire-and-forget — it only enqueues an `UpdateGlanceState`
 * event on the widget session and returns (GlanceAppWidget.update -> Session.sendEvent on an
 * UNLIMITED channel). The recomposition and the `AppWidgetManager.updateAppWidget` push happen
 * later on the session worker, where the Recomposer coalesces multiple invalidation bursts into
 * one composition. So two back-to-back `update()` calls for the same `glanceId` (a rapid next/prev
 * double-tap) are not guaranteed to land in call order: the display can settle on the first tap's
 * month even though both callbacks persisted the right months. A plain mutex over `update()` does
 * not help, because it only orders the *enqueue*, not the async composition/push it triggers.
 *
 * The fix is to make the coalescing happen here instead: if a render is already in flight, a new
 * request does not call `update()` at all — it appends to the work queue and returns immediately.
 * The caller that is draining works through the backlog, and each drained render reads config *at
 * render time*. Navigation persists the viewed month before calling [render], so the drained render
 * sees the newest month: a double-tap becomes one `update()` of the second month, which Glance
 * renders unambiguously. A full sweep ([Pending.Sweep]) subsumes a pending instance render (it
 * re-renders every instance anyway) and vice versa, so neither is ever lost.
 *
 * **Why a queue + drainer flag rather than a mutex + pending slot.** The previous design held a
 * `Mutex` and kept at most one pending request, re-reading the slot after unlocking. That has a
 * window no amount of re-checking fully closes: a request arriving *after* the drain read the slot
 * as empty but *before* the lock was released found the lock held, recorded itself, and returned —
 * leaving nothing holding the lock to drain it. The intent then sat unrendered until some unrelated
 * future request happened to win the lock, which for a navigation tap means the widget shows the
 * previous month (WG-13).
 *
 * Here the queue and the "someone is draining" flag are the only state, and the drainer hands the
 * role back with a **release-then-recheck** loop: it clears [draining] and *then* looks at the
 * queue. Every interleaving resolves to exactly one drainer:
 *
 *  - request arrives before the drainer's last `poll` — the drainer picks it up;
 *  - it arrives after the drainer cleared the flag — its own `compareAndSet` wins and it drains;
 *  - it arrives after the clear but the requester won the `compareAndSet` first — the drainer's own
 *    `compareAndSet` fails and it returns, leaving the queue to the new owner.
 *
 * The queue is unbounded rather than a single slot, so a burst of taps cannot overwrite an earlier
 * intent; [Pending.Sweep] is still collapsed against the backlog by [drain] so the common cases stay
 * at one render.
 *
 * It complements the reactive state read inside `provideGlance`: this queue orders and coalesces
 * *updates* end to end, while the render cache (`HijriWidgetRenderCache`) guards shared projections
 * with an internal monitor across instances and the settings live preview, which does not go
 * through this queue.
 *
 * **What a request returns matters beyond the request itself.** Both entry points report a
 * [RenderOutcome] because coalescing is silent by design, and a caller that writes a *dedupe
 * marker* needs to know whether anything was pushed to the display. See
 * [HijriWidgetRefresher.refreshAll].
 */
internal object HijriWidgetRenderQueue {

    /**
     * What a render request actually did, so a caller can distinguish "I asked Glance to render"
     * from "someone else is already draining and I appended to the queue".
     *
     * The distinction is not cosmetic. A *render* genuinely does not need the caller to know — the
     * drainer will pick the work up, so it still happens. A *marker* does:
     * `HijriWidgetConfig.markUpdatedNow` means "the display now shows today", and writing it after a
     * coalesced no-op is a lie the same-day dedupe then believes for the rest of the day (WG-02).
     */
    internal sealed interface RenderOutcome {
        /** A Glance `update`/`updateAll` was called by this call's own drain. */
        data object Rendered : RenderOutcome

        /**
         * Another caller holds the drainer role, so this request was queued and no Glance call was
         * made here. The drainer will render it.
         */
        data object Coalesced : RenderOutcome
    }

    private sealed interface Pending {
        /** Re-render this one instance. */
        data class Instance(val id: GlanceId) : Pending

        /** Re-run a full sweep; subsumes any queued [Instance]. */
        data object Sweep : Pending
    }

    private val work = ConcurrentLinkedQueue<Pending>()

    /** Exactly one caller may drain [work] at a time. Released before the final emptiness re-check. */
    private val draining = AtomicBoolean(false)

    /**
     * Renders a single grid instance — plus every mirror-following widget (the Today strip and the
     * 1x1 date tiles), which share the family options mirror — immediately when idle, or as a
     * coalesced follow-up when a render is already in flight (never blocks the tap on the in-flight
     * render).
     */
    suspend fun render(context: Context, glanceId: GlanceId): RenderOutcome =
        submit(context, Pending.Instance(glanceId))

    /** Renders every bound instance of both widget classes as one sweep, coalesced the same way. */
    suspend fun renderAll(context: Context): RenderOutcome = submit(context, Pending.Sweep)

    private suspend fun submit(context: Context, request: Pending): RenderOutcome {
        work.add(request)
        if (!draining.compareAndSet(false, true)) {
            return RenderOutcome.Coalesced
        }
        var rendered = false
        // `released` guards the `finally`: once the role has been handed back we must not clear the
        // flag again, because by then a different caller may legitimately own it.
        var released = false
        try {
            while (true) {
                drain(context)
                rendered = true
                // Release, then re-check. See the class KDoc for why this order is what closes the
                // window a mutex + pending slot could not.
                draining.set(false)
                if (work.isEmpty() || !draining.compareAndSet(false, true)) {
                    released = true
                    break
                }
            }
        } finally {
            if (!released) draining.set(false)
        }
        return if (rendered) RenderOutcome.Rendered else RenderOutcome.Coalesced
    }

    /**
     * Renders the backlog, collapsing it to the minimum number of passes.
     *
     * The batch is taken *whole* before anything is rendered, because an instance request that is
     * already sitting behind a queued sweep must be dropped rather than rendered and then
     * immediately superseded by that sweep — the sweep re-renders every instance anyway. Deciding
     * one entry at a time cannot see the sweep that is coming, which is how a double-tap used to
     * cost two full passes instead of one.
     *
     * Anything that arrives *while* this batch is rendering is picked up by the next iteration, so
     * no request is lost to the timing of the scan.
     */
    private suspend fun drain(context: Context) {
        while (true) {
            val batch = buildList {
                while (true) {
                    work.poll()?.let(::add) ?: break
                }
            }
            if (batch.isEmpty()) return
            if (batch.any { it is Pending.Sweep }) {
                renderNow(context, glanceId = null)
            } else {
                batch.filterIsInstance<Pending.Instance>().forEach { renderNow(context, it.id) }
            }
            if (work.isEmpty()) return
        }
    }

    /**
     * One render pass: the grid instance (or all grid instances when [glanceId] is null) followed by
     * a sweep of every mirror-following widget (the Today strip and the 1x1 date tiles).
     *
     * Behind a seam so the coalescing and the drainer hand-off can be exercised without a real
     * `AppWidgetManager`. The default is the production behaviour; only tests replace it, and they
     * restore it in a `finally`.
     */
    private suspend fun renderNow(context: Context, glanceId: GlanceId?) {
        renderAction(context, glanceId)
    }

    internal var renderAction: suspend (Context, GlanceId?) -> Unit = { context, glanceId ->
        if (glanceId == null) {
            HijriCalendarWidget().updateAll(context)
        } else {
            HijriCalendarWidget().update(context, glanceId)
        }
        HijriTodayWidget().updateAll(context)
        HijriDateWidget().updateAll(context)
        GregorianDateWidget().updateAll(context)
        // The dual-date tile is swept here for the same reason as the two 1x1 tiles above: it has no
        // settings screen and renders the family options mirror, so a settings apply must re-read it
        // too. A tile that is placed but never swept is the one failure mode here with no log line —
        // it simply shows the language the user picked two settings screens ago.
        HijriDualDateWidget().updateAll(context)
    }

    /** Whether a request is queued but not yet rendered. For tests, which must not guess. */
    internal val hasPendingForTest: Boolean get() = work.isNotEmpty()
}
