package com.muazdev.hijricalendar.widget.glance

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.updateAll
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.sync.Mutex

/**
 * Orders every widget render in the family and coalesces bursts of taps into a single render of
 * the final state. Every render pass — instance or sweep — also updates every other widget class
 * ([HijriTodayWidget], [HijriDateWidget], [GregorianDateWidget]): they have no settings screen of
 * their own and render the family options mirror, so any render triggered by a settings apply or
 * source-pill toggle must re-read it too.
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
 * request does not call `update()` at all — it records a pending request and returns. The in-flight
 * holder drains pending requests after finishing, and each drained render reads config *at render
 * time*. Navigation persists the viewed month before calling [render], so the drained render sees
 * the newest month: a double-tap becomes one `update()` of the second month, which Glance renders
 * unambiguously. A full sweep ([Pending.Sweep]) subsumes a pending instance render (it re-renders
 * every instance anyway) and vice versa, so neither is ever lost by the single pending slot.
 *
 * It complements the reactive state read inside `provideGlance`: this queue orders and coalesces
 * *updates* end to end, while the render cache (`HijriWidgetRenderCache`) guards shared projections
 * with an internal monitor across instances and the settings live preview, which does not go
 * through this queue.
 */
internal object HijriWidgetRenderQueue {

    private val mutex = Mutex()

    private sealed interface Pending {
        /** Re-render this one instance once the current holder finishes. */
        data class Instance(val id: GlanceId) : Pending

        /** Re-run a full sweep; subsumes any pending [Instance]. */
        data object Sweep : Pending
    }

    private val pending = AtomicReference<Pending?>(null)

    /**
     * Renders a single grid instance — plus every mirror-following widget (Today strip and the
     * two 1x1 date tiles), which share the family options mirror — immediately when idle, or as
     * a coalesced follow-up when a render is already in flight (never blocks the tap on the
     * in-flight render).
     */
    suspend fun render(context: Context, glanceId: GlanceId) {
        if (!mutex.tryLock()) {
            request(Pending.Instance(glanceId))
            return
        }
        try {
            renderNow(context, glanceId)
            drain(context)
        } finally {
            mutex.unlock()
        }
    }

    /** Renders every bound instance of both widget classes as one sweep, coalesced the same way. */
    suspend fun renderAll(context: Context) {
        if (!mutex.tryLock()) {
            request(Pending.Sweep)
            return
        }
        try {
            renderNow(context, glanceId = null)
            drain(context)
        } finally {
            mutex.unlock()
        }
    }

    private fun request(next: Pending) {
        // A pending sweep already covers every instance, so keep it over a later instance request.
        pending.updateAndGet { old -> if (old == Pending.Sweep) Pending.Sweep else next }
    }

    private suspend fun drain(context: Context) {
        while (true) {
            when (val next = pending.getAndSet(null)) {
                null -> return
                is Pending.Instance -> renderNow(context, next.id)
                Pending.Sweep -> renderNow(context, glanceId = null)
            }
        }
    }

    /**
     * One render pass under the queue lock: the grid instance (or all grid instances when
     * [glanceId] is null) followed by a sweep of every mirror-following widget (Today strip and
     * the two 1x1 date tiles).
     */
    private suspend fun renderNow(context: Context, glanceId: GlanceId?) {
        if (glanceId == null) {
            HijriCalendarWidget().updateAll(context)
        } else {
            HijriCalendarWidget().update(context, glanceId)
        }
        HijriTodayWidget().updateAll(context)
        HijriDateWidget().updateAll(context)
        GregorianDateWidget().updateAll(context)
    }
}
