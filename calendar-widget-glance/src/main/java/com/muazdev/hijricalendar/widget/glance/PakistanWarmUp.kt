package com.muazdev.hijricalendar.widget.glance

import com.muazdev.hijricalendar.core.HijriMonthOverrides
import com.muazdev.hijricalendar.core.PakistanHijriCalendar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.TimeSource

/**
 * Single-flight coordinator for the `calendar-core` Pakistan (Ruet-e-Hilal) century-table
 * build, shared by every caller that can be the first to touch Pakistan-mode date math:
 * app startup, in-app navigation, widget action callbacks, background widget refreshes and
 * the settings preview.
 *
 * The build itself is owned by [PakistanHijriCalendar.prewarm]; this object only makes sure
 * that when the table is cold, exactly one build runs and everyone else suspends on the same
 * [Deferred] instead of starting their own uncoordinated build on whatever thread they happen
 * to be on. Callers that arrive after the table is warm for the current
 * [HijriMonthOverrides] revision return without touching a thread pool.
 */
object PakistanWarmUp {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val mutex = Mutex()
    private var warmUpJob: Deferred<Unit>? = null

    /**
     * Test hook: replaces the actual century-table build so a test can count how many times the
     * real [PakistanHijriCalendar.prewarm] would run. Defaults to the real build.
     */
    internal var buildAction: () -> Unit = { PakistanHijriCalendar.prewarm() }

    /**
     * Suspends until the Pakistan century table is built for the current overrides revision.
     * Idempotent: joins an in-flight build or returns immediately when the table is already
     * warm. Safe to call from anywhere (main thread included).
     */
    suspend fun ensureWarm() {
        if (PakistanHijriCalendar.isWarmForCurrentOverrides()) return

        val watch = TimeSource.Monotonic.markNow()
        val job = mutex.withLock {
            // Re-check inside the lock: whoever held the mutex before us may have finished
            // (or started and finished) a build while we waited.
            if (PakistanHijriCalendar.isWarmForCurrentOverrides()) {
                warmUpJob = null
                return
            }
            // A completed job is a stale reference for an older overrides revision (the
            // table is not warm for the current one, so it is exactly the "revision
            // changed" case) — replace it rather than awaiting a finished build.
            warmUpJob?.takeUnless { it.isCompleted }
                ?: scope.async { buildAction() }.also { warmUpJob = it }
        }
        job.await()
        HijriWidgetRefreshLog.d(
            "warmup",
            "warm in ${watch.elapsedNow().inWholeMilliseconds}ms " +
                "(revision ${HijriMonthOverrides.currentRevision})",
        )
    }
}
