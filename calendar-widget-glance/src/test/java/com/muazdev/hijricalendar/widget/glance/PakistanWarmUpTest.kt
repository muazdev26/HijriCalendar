package com.muazdev.hijricalendar.widget.glance

import com.muazdev.hijricalendar.core.HijriMonthLengths
import com.muazdev.hijricalendar.core.HijriMonthOverrides
import com.muazdev.hijricalendar.core.PakistanHijriCalendar
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Guards the single-flight warm-up that keeps on-widget Pakistan next/prev from paying for (or
 * racing) the first century-table build. Bumping [HijriMonthOverrides] leaves the table cold for
 * the new revision, which makes the "just restarted the process" state reproducible in-process
 * without restarting.
 */
class PakistanWarmUpTest {

    private val testOverrides = HijriMonthLengths()

    @Test
    fun concurrentCallersShareOneBuild() = runBlocking {
        forceColdTable()
        val buildCount = AtomicInteger(0)
        val original = PakistanWarmUp.buildAction
        PakistanWarmUp.buildAction = { overrides ->
            buildCount.incrementAndGet()
            original(overrides)
        }
        try {
            coroutineScope {
                (1..5).map { async { PakistanWarmUp.ensureWarm(testOverrides) } }.awaitAll()
            }
            assertEquals(1, buildCount.get())
            assertTrue(PakistanHijriCalendar.isWarmForCurrentOverrides(testOverrides))
        } finally {
            PakistanWarmUp.buildAction = original
        }
    }

    @Test
    fun warmTableReturnsWithoutBuilding() = runBlocking {
        forceColdTable()
        PakistanWarmUp.ensureWarm(testOverrides)
        val buildCount = AtomicInteger(0)
        val original = PakistanWarmUp.buildAction
        PakistanWarmUp.buildAction = { overrides ->
            buildCount.incrementAndGet()
            original(overrides)
        }
        try {
            PakistanWarmUp.ensureWarm(testOverrides)
            assertEquals(0, buildCount.get())
        } finally {
            PakistanWarmUp.buildAction = original
        }
    }

    /**
     * Revisions change on any effective override mutation, so set-then-clear leaves the table
     * (and any warm-up job) stale for the current revision — the same cold state a fresh process
     * starts with, but achievable in-process.
     */
    private fun forceColdTable() {
        HijriMonthOverrides.setMonthLength(1450, 4, 29)
        HijriMonthOverrides.clearMonthLength(1450, 4)
        assertFalse(PakistanHijriCalendar.isWarmForCurrentOverrides(testOverrides))
    }
}
