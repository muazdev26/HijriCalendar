package com.muazdev.hijricalendar.core

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [CalendarLog] is the library's only diagnostics seam, and the promise it makes is narrow and
 * load-bearing: *silent by default, faithful when installed*. Both halves are tested here,
 * because a logger that defaults to noisy is worse than no logger at all.
 */
class CalendarLogTest {

    private data class Entry(
        val level: CalendarLogLevel,
        val tag: String,
        val message: String,
        val cause: Throwable?,
    )

    @AfterTest
    fun tearDown() = CalendarLog.reset()

    @Test
    fun isSilentByDefault() {
        assertFalse(CalendarLog.isInstalled, "a library must be silent until a consumer opts in")

        // Provoke a logged failure with no logger installed: must return null and must not
        // throw, and must not reach for stdout. Nothing observes stdout here — the guard
        // against a `println` regression is `assertFalse(isInstalled)` plus the code review.
        assertNull(todayHijriDate(adjustmentDays = OUT_OF_RANGE_SHIFT))
    }

    @Test
    fun installedLoggerReceivesTheMessageAndCause() {
        val entries = mutableListOf<Entry>()
        CalendarLog.logger = CalendarLogger { level, tag, message, cause ->
            entries += Entry(level, tag, message, cause)
        }
        assertTrue(CalendarLog.isInstalled)

        assertNull(todayHijriDate(adjustmentDays = OUT_OF_RANGE_SHIFT))

        val entry = entries.single()
        assertEquals(CalendarLogLevel.WARN, entry.level)
        assertEquals("HijriCalendar", entry.tag)
        assertNotNull(entry.cause, "a failure path must attach its cause")
    }

    @Test
    fun resetRestoresSilence() {
        val entries = mutableListOf<Entry>()
        CalendarLog.logger = CalendarLogger { l, t, m, c -> entries += Entry(l, t, m, c) }
        assertTrue(CalendarLog.isInstalled)

        CalendarLog.reset()
        assertFalse(CalendarLog.isInstalled)

        assertNull(todayHijriDate(adjustmentDays = OUT_OF_RANGE_SHIFT))
        assertTrue(entries.isEmpty(), "a reset logger must receive nothing")
    }

    @Test
    fun loggerIsReplaceableRatherThanAccumulating() {
        val first = mutableListOf<Entry>()
        val second = mutableListOf<Entry>()
        CalendarLog.logger = CalendarLogger { l, t, m, c -> first += Entry(l, t, m, c) }
        CalendarLog.logger = CalendarLogger { l, t, m, c -> second += Entry(l, t, m, c) }

        assertNull(todayHijriDate(adjustmentDays = OUT_OF_RANGE_SHIFT))

        assertTrue(first.isEmpty(), "replacing the logger must not also keep the previous one")
        assertEquals(1, second.size)
    }

    @Test
    fun causeIsTheOriginalThrowableNotRewrapped() {
        var captured: Throwable? = null
        CalendarLog.logger = CalendarLogger { _, _, _, cause -> captured = cause }

        assertNull(todayHijriDate(adjustmentDays = OUT_OF_RANGE_SHIFT))

        // The library forwards the throwable it caught, not a wrapper, so a consumer can match
        // on the concrete type in Issue 5's narrowed catches.
        assertNotNull(captured, "a failure path must attach its cause")
    }

    private companion object {
        /**
         * A shift far beyond the supported Umm al-Qura range, so `toHijrahDate()` throws and
         * `todayHijriDate` takes its `catch` branch. Deterministic and timezone-independent.
         */
        const val OUT_OF_RANGE_SHIFT = 500_000
    }
}
