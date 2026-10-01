package com.muazdev.hijricalendar.widget.glance

import android.content.Context
import android.content.ContextWrapper
import androidx.glance.GlanceId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The render queue's two load-bearing contracts, both of which were wrong when written:
 *
 *  1. **A coalesced request must not report itself as rendered** (WG-02). `HijriWidgetRefresher`
 *     writes the family's "we already show today" marker from that report, so a request that
 *     returned normally having called no Glance API used to mark the day fresh anyway — after which
 *     every background trigger for the rest of the epoch day skipped, logging a line that reads
 *     like correct behaviour.
 *  2. **A coalesced request must never be lost** (WG-13). Its intent is queued rather than rendered
 *     inline, and the caller that is already draining owes it a render. A design that let a request
 *     land in the gap between "drain found nothing" and "lock released" left it unrendered until
 *     some unrelated future request, which for a navigation tap means the widget shows the
 *     *previous* month.
 *
 * Both are properties of the drainer hand-off, so both are testable here by replacing
 * [HijriWidgetRenderQueue.renderAction] with a recording/blocking stand-in. No `AppWidgetManager`
 * and no device. The production action is restored in a `finally` in every test: the queue is a
 * singleton, so a leaked replacement would silently redirect every other test's renders.
 */
class HijriWidgetRenderQueueTest {

    /**
     * One entry per render pass, as a tag: [SWEEP] for a full sweep, otherwise the instance's
     * identity. A [ConcurrentLinkedQueue] cannot hold the `null` the queue uses for "every
     * instance", so passes are recorded as strings.
     */
    private val rendered = ConcurrentLinkedQueue<String>()

    /** The production action to restore; a field so the `finally` blocks can reach it. */
    private var originalAction: (suspend (Context, GlanceId?) -> Unit)? = null

    private fun record(action: suspend (GlanceId?) -> Unit) = apply {
        originalAction = HijriWidgetRenderQueue.renderAction
        HijriWidgetRenderQueue.renderAction = { _, id -> action(id) }
    }

    private fun restore() {
        originalAction?.let { HijriWidgetRenderQueue.renderAction = it }
        originalAction = null
    }

    private fun GlanceId?.tag(): String = this?.toString() ?: SWEEP

    @Test
    fun aSweepRendersOnceAndReportsRendered() = runBlocking {
        record { rendered.add(it.tag()) }
        try {
            assertEquals(HijriWidgetRenderQueue.RenderOutcome.Rendered, HijriWidgetRenderQueue.renderAll(context))
            assertEquals(listOf(SWEEP), rendered.toList())
        } finally {
            restore()
        }
    }

    @Test
    fun anInstanceRendersOnceAndReportsRendered() = runBlocking {
        record { rendered.add(it.tag()) }
        try {
            assertEquals(HijriWidgetRenderQueue.RenderOutcome.Rendered, HijriWidgetRenderQueue.render(context, id("a")))
            assertEquals(listOf("a"), rendered.toList())
        } finally {
            restore()
        }
    }

    @Test
    fun aRequestThatFindsTheQueueBusyReportsCoalescedAndRendersNothing() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val passes = AtomicInteger(0)
        record {
            passes.incrementAndGet()
            rendered.add(it.tag())
            entered.complete(Unit)
            release.await() // hold the drainer role
        }
        try {
            val holder = async { HijriWidgetRenderQueue.renderAll(context) }
            withTimeout(TIMEOUT_MS) { entered.await() }

            val coalesced = HijriWidgetRenderQueue.renderAll(context)

            assertEquals(
                HijriWidgetRenderQueue.RenderOutcome.Coalesced,
                coalesced,
                "a request that called no Glance API must not claim it rendered (WG-02)",
            )
            assertEquals(1, passes.get(), "the coalesced request must not have called Glance")

            release.complete(Unit)
            assertEquals(HijriWidgetRenderQueue.RenderOutcome.Rendered, holder.await())
            // The holder owes the coalesced sweep a render, so the backlog is drained by it.
            assertEquals(2, passes.get())
            assertEquals(listOf(SWEEP, SWEEP), rendered.toList())
        } finally {
            release.complete(Unit)
            restore()
        }
    }

    /**
     * WG-13, stated as the property rather than as a schedule: no matter how a burst of requests
     * interleaves with a render in flight, **every** requested instance is eventually rendered and
     * no request is left queued.
     *
     * The old mutex + single-slot design could strand a request that arrived between the drain
     * reading the slot as empty and the lock being released, and the only way that surfaced was a
     * widget stuck on the wrong month with no further trigger. There is no deterministic way to hit
     * that window from a test — which is itself the problem — so this asserts the invariant instead:
     * the release-then-recheck hand-off has to make it hold under contention, not on one schedule.
     */
    @Test
    fun noRequestIsLostUnderContention() = runBlocking {
        val passes = AtomicInteger(0)
        record { passes.incrementAndGet() }
        try {
            // Every instance is requested many times over, from many coroutines, so a large number
            // of requests land while a render is in flight. All of them must be honoured.
            val requested = 40
            coroutineScope {
                (1..requested).map { i ->
                    async { HijriWidgetRenderQueue.render(context, id("w$i")) }
                }.forEach { it.await() }
            }
            assertFalse(
                HijriWidgetRenderQueue.hasPendingForTest,
                "a request was left queued and never drained",
            )
            assertTrue(passes.get() > 0, "nothing rendered at all")
        } finally {
            restore()
        }
    }

    /** Every distinct instance requested under contention is eventually rendered. */
    @Test
    fun everyRequestedInstanceIsEventuallyRendered() = runBlocking {
        val seen = ConcurrentLinkedQueue<String>()
        val requested = 12
        record { seen.add(it.tag()) }
        try {
            coroutineScope {
                (1..requested).map { i ->
                    async { HijriWidgetRenderQueue.render(context, id("w$i")) }
                }.forEach { it.await() }
            }
            val missing = (1..requested).map { "w$it" }.filterNot { seen.contains(it) }
            assertEquals(emptyList(), missing, "these instances were never rendered")
        } finally {
            restore()
        }
    }

    @Test
    fun aQueuedSweepSubsumesAQueuedInstance() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        record {
            if (rendered.isEmpty()) {
                entered.complete(Unit)
                release.await()
            }
            rendered.add(it.tag())
        }
        try {
            val holder = async { HijriWidgetRenderQueue.render(context, id("a")) }
            withTimeout(TIMEOUT_MS) { entered.await() }
            // An instance, then a sweep: the sweep re-renders every instance anyway, so honouring
            // the instance too would be wasted work.
            HijriWidgetRenderQueue.render(context, id("b"))
            HijriWidgetRenderQueue.renderAll(context)
            release.complete(Unit)
            withTimeout(TIMEOUT_MS) { holder.await() }

            assertEquals(
                listOf("a", SWEEP),
                rendered.toList(),
                "a queued sweep must subsume the instance behind it rather than render both",
            )
        } finally {
            release.complete(Unit)
            restore()
        }
    }

    @Test
    fun anInstanceQueuedBehindASweepDoesNotReplaceIt() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        record {
            if (rendered.isEmpty()) {
                entered.complete(Unit)
                release.await()
            }
            rendered.add(it.tag())
        }
        try {
            val holder = async { HijriWidgetRenderQueue.renderAll(context) }
            withTimeout(TIMEOUT_MS) { entered.await() }
            // Sweep first, then an instance: the instance must not displace the sweep, or the grid
            // instances that only the sweep covers would never be re-rendered.
            HijriWidgetRenderQueue.renderAll(context)
            HijriWidgetRenderQueue.render(context, id("b"))
            release.complete(Unit)
            withTimeout(TIMEOUT_MS) { holder.await() }

            assertEquals(
                listOf(SWEEP, SWEEP),
                rendered.toList(),
                "a queued sweep must survive a later instance request",
            )
        } finally {
            release.complete(Unit)
            restore()
        }
    }

    @Test
    fun theQueueIsEmptyBeforeEachTest() {
        // A leftover entry would silently absorb the next test's request and make its assertions
        // pass or fail for entirely the wrong reason.
        assertFalse(
            HijriWidgetRenderQueue.hasPendingForTest,
            "a previous test left a request queued",
        )
    }

    /** Minimal [GlanceId]: the queue only ever carries one opaquely. */
    private class FakeGlanceId(private val tag: String) : GlanceId {
        override fun equals(other: Any?): Boolean = other is FakeGlanceId && other.tag == tag
        override fun hashCode(): Int = tag.hashCode()
        override fun toString(): String = tag
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val SWEEP = "<sweep>"

        fun id(tag: String): GlanceId = FakeGlanceId(tag)

        /**
         * The recording action never dereferences the [Context], so an inert wrapper is enough and
         * the module needs no mocking dependency. `ContextWrapper` is concrete in the stubbed
         * `android.jar` (`Context` itself is abstract there, so a dynamic proxy cannot stand in for
         * it), and `unitTests.isReturnDefaultValues = true` makes an accidental call return a
         * default rather than throw.
         */
        val context: Context = ContextWrapper(null)
    }
}
