package com.muazdev.hijricalendar.core

import kotlin.concurrent.thread
import kotlin.random.Random
import kotlinx.datetime.number
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * JVM-only smoke test for the lock-free atomic state introduced for issue #4.
 *
 * Media: N writer threads mutate [HijriMonthOverrides] (the global singleton) through
 * every mutation path while M reader threads repeatedly take snapshots and resolve
 * Pakistan month starts. Because readers and writers share the same process-wide
 * singleton, this is the only place a torn/partial state can be observed; the contract we
 * assert is that every read observes *some* complete, validated snapshot — no exceptions,
 * no invalid month lengths, and a monotonically non-decreasing revision.
 */
class ThreadSafetySmokeTest {

    @AfterTest
    fun tearDown() {
        HijriMonthOverrides.clearAll()
    }

    @Test
    fun concurrentOverrideMutation_publishesConsistentSnapshots() {
        HijriMonthOverrides.clearAll()
        val startRevision = HijriMonthOverrides.currentRevision
        val writers = 4
        val readers = 4
        val iterations = 3_000

        val writerThreads = (1..writers).map { id ->
            thread(start = true) {
                val random = Random(id)
                repeat(iterations) {
                    when (random.nextInt(3)) {
                        0 -> HijriMonthOverrides.setMonthLength(
                            1448, random.nextInt(1, 13), if (random.nextBoolean()) 29 else 30,
                        )
                        1 -> HijriMonthOverrides.clearMonthLength(1448, random.nextInt(1, 13))
                        else -> HijriMonthOverrides.replaceAll(
                            mapOf((1448 to random.nextInt(1, 13)) to if (random.nextBoolean()) 29 else 30),
                        )
                    }
                }
            }
        }

        val readerThreads = (1..readers).map { id ->
            thread(start = true) {
                val random = Random(id * 31)
                repeat(iterations) {
                    // Every snapshot must be a complete, validated map.
                    HijriMonthOverrides.all().forEach { (_, length) ->
                        assertTrue(length in 29..30, "reader observed invalid length $length")
                    }
                    // Pakistan lookups exercise the CAS-published month table; a torn
                    // snapshot would surface here as an exception or corrupted anchor.
                    val month = random.nextInt(1, 13)
                    val start = PakistanHijriCalendar.hijriToGregorian(1448, month, 1)
                    assertTrue(start.year in 2025..2028, "unexpected anchor $start")
                    assertTrue(start.month.number in 1..12)
                }
            }
        }

        writerThreads.forEach { it.join() }
        readerThreads.forEach { it.join() }

        assertTrue(
            HijriMonthOverrides.currentRevision >= startRevision,
            "revision must be non-decreasing",
        )
    }
}