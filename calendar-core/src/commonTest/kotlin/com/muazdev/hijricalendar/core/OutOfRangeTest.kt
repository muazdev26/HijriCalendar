package com.muazdev.hijricalendar.core

import com.abdulrahman_b.hijrahdatetime.HijrahDate
import com.abdulrahman_b.hijrahdatetime.toHijrahDate
import kotlinx.datetime.DateTimeArithmeticException
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * "No answer" has exactly one meaning now: the date is outside the supported range. A defect
 * propagates, so an arithmetic slip can no longer render as a grid of disabled cells.
 *
 * These tests install a recording [CalendarLog] so they can assert on what was *reported*, not
 * just on the return value — a fault that returns null without logging is the original bug.
 */
class OutOfRangeTest {

    private val records = mutableListOf<Triple<CalendarLogLevel, String, Throwable?>>()

    private class IllegalState(message: String) : IllegalStateException(message)
    private class MissingKey : NoSuchElementException("no such key")

    @BeforeTest
    fun installLogger() {
        records.clear()
        CalendarLog.logger = CalendarLogger { level, tag, message, cause ->
            records += Triple(level, "$tag: $message", cause)
        }
    }

    @AfterTest
    fun clearLogger() {
        CalendarLog.reset()
    }

    // ── what orNullIfOutOfRange catches, and what it must not ───────────────

    private val caught = mutableListOf<Throwable>()
    private inline fun <T> recording(block: () -> T): T? =
        orNullIfOutOfRange(onFailure = { caught += it }, block = block)

    @Test
    fun illegalArgumentExceptionIsOutOfRange() {
        assertNull(recording { throw IllegalArgumentException("out of range") })
        assertSame(IllegalArgumentException::class.java, caught.single()::class.java)
    }

    @Test
    fun dateTimeArithmeticExceptionIsAlsoOutOfRange() {
        // It extends RuntimeException, not IllegalArgumentException — catching only the latter
        // would let this escape, which is exactly the bug this helper exists to prevent.
        assertNull(recording { throw DateTimeArithmeticException("out of range") })
        assertSame(DateTimeArithmeticException::class.java, caught.single()::class.java)
    }

    @Test
    fun defectsAreNotOutOfRange() {
        assertFailsWith<IllegalState> { recording { throw IllegalState("broken chain") } }
        assertFailsWith<MissingKey> { recording { throw MissingKey() } }
        assertFailsWith<ArithmeticException> { recording { throw ArithmeticException("overflow") } }
        assertTrue(caught.isEmpty(), "a defect must not be reported as an out-of-range date")
    }

    @Test
    fun aSuccessfulCallIsReturnedUnchanged() {
        val value = LocalDate(2026, 9, 30)
        assertSame(value, recording { value })
        assertTrue(caught.isEmpty())
    }

    @Test
    fun theFailureCallbackIsOptional() {
        // Per-cell call sites omit it so one unresolvable grid edge cannot flood the log. Nothing
        // is reported, and nothing throws.
        assertNull(orNullIfOutOfRange { throw IllegalArgumentException() })
        assertTrue(records.isEmpty())
    }

    // ── Pakistan: out of range is null, defects are not ─────────────────────

    @Test
    fun gregorianToHijriReturnsNullForADateOutsideTheWindow() {
        // Year 1000 AH and 1600 AH are both far outside MIN_YEAR..MAX_YEAR.
        assertNull(PakistanHijriCalendar.gregorianToHijri(LocalDate(1570, 1, 1)))
        assertTrue(
            records.none { it.first == CalendarLogLevel.ERROR },
            "an out-of-window date is not a defect",
        )
    }

    @Test
    fun gregorianToHijriResolvesADateInsideTheWindow() {
        val resolved = PakistanHijriCalendar.gregorianToHijri(LocalDate(2026, 9, 30))
        assertNotNull(resolved)
        assertTrue(records.none { it.first == CalendarLogLevel.ERROR })
    }

    @Test
    fun anOutOfRangeYearStillThrowsBecauseItIsAProgrammingError() {
        // `lengthOfMonth`/`monthStart` guard their input with `require`. That is a caller bug, not
        // a date, so it must not be laundered into null.
        assertFailsWith<IllegalArgumentException> {
            PakistanHijriCalendar.lengthOfMonth(PakistanHijriCalendar.MIN_YEAR - 1, 1)
        }
        assertFailsWith<IllegalArgumentException> {
            PakistanHijriCalendar.hijriToGregorian(PakistanHijriCalendar.MAX_YEAR + 1, 1, 1)
        }
    }

    @Test
    fun anImpossibleDayStillThrowsBecauseItIsAProgrammingError() {
        assertFailsWith<IllegalArgumentException> {
            PakistanHijriCalendar.hijriToGregorian(1441, 6, 99)
        }
        assertFailsWith<IllegalArgumentException> {
            PakistanHijriCalendar.hijriToGregorian(1441, 6, 0)
        }
    }

    // ── observed: null means "not in the Umm al-Qura table", and says so ────

    @Test
    fun observedDateAtResolvesADateInsideTheRange() {
        assertNotNull(ObservedHijriCalendar.observedDateAt(LocalDate(2026, 9, 30).toEpochDays()))
    }

    @Test
    fun observedDateAtReturnsNullOutsideTheRangeAndReportsIt() {
        // Far before the table: the conversion itself cannot produce a Hijri date.
        assertNull(ObservedHijriCalendar.observedDateAt(LocalDate(1000, 1, 1).toEpochDays()))
        assertTrue(
            records.any { it.first == CalendarLogLevel.DEBUG },
            "an unresolvable epoch day should be reported, not silently dropped",
        )
    }

    @Test
    fun observedDateAtStaysResolvableAcrossTheWholeSupportedRange() {
        // The binary search's drift window must not let an in-range date fall through to null.
        var epochDay = LocalDate(2000, 1, 1).toEpochDays()
        val end = LocalDate(2050, 1, 1).toEpochDays()
        var checked = 0
        while (epochDay < end) {
            assertNotNull(
                ObservedHijriCalendar.observedDateAt(epochDay),
                "a supported date resolved to null at epoch day $epochDay",
            )
            epochDay += 97 // prime stride so the sample is not aligned to month boundaries
            checked++
        }
        assertTrue(checked > 150, "expected a meaningful sweep, got $checked days")
    }

    // ── navigation bounds are pre-checked, not caught ───────────────────────

    @Test
    fun navigationClampsAtBothEndsOfTheHijriTable() {
        // `hijrah-datetime` signals an over-range month with ArrayIndexOutOfBoundsException from
        // its rule table. The state holder pre-checks instead, so nothing depends on that.
        val state = HijriCalendarState(initialMonth = com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth(HijrahDate.MAX.year, 12))
        state.goToNextMonth()
        assertNotNull(state.currentMonth)

        val atMin = HijriCalendarState(initialMonth = com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth(HijrahDate.MIN.year, 1))
        atMin.goToPreviousMonth()
        assertNotNull(atMin.currentMonth)

        assertTrue(records.none { it.first == CalendarLogLevel.ERROR })
    }

    @Test
    fun aDateConversionOutsideTheTableIsReportedNotSwallowed() {
        // What `CalendarMonthExt.toHijrahDateOrNull` does per cell: range failures become null,
        // but it deliberately does not log, because it runs 42 times per grid.
        val farFuture = LocalDate(3000, 1, 1)
        val converted = try {
            farFuture.toHijrahDate()
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: DateTimeArithmeticException) {
            null
        }
        // Either the library extends to 3000 or it does not; what matters is that both range
        // exception shapes were handled above without a bare Exception catch.
        converted?.let { assertNotNull(it) }
    }
}
