package com.muazdev.hijricalendar.core

import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The point of making [HijriMonthLengths] instantiable: overrides stop being process state and
 * become a value two calendars can disagree about.
 *
 * These tests deliberately avoid the process-wide [HijriMonthOverrides], so they need no
 * `@AfterTest` cleanup and cannot leak into other tests — which was the other half of the problem.
 */
class HijriMonthLengthsTest {

    // ── independence ────────────────────────────────────────────────────────

    @Test
    fun twoInstancesDoNotShareState() {
        val a = HijriMonthLengths()
        val b = HijriMonthLengths()

        a.setMonthLength(1448, 3, 30)

        assertEquals(30, a.monthLength(1448, 3))
        assertNull(b.monthLength(1448, 3), "b must not see a's write")
        assertTrue(b.all().isEmpty())
    }

    @Test
    fun mutatingAScopedInstanceDoesNotTouchTheProcessDefault() {
        val scoped = HijriMonthLengths()
        scoped.setMonthLength(1448, 3, 30)

        assertNull(HijriMonthOverrides.monthLength(1448, 3), "the global table must be untouched")
    }

    @Test
    fun replaceAllOnlyAffectsItsOwnInstance() {
        val a = HijriMonthLengths()
        val b = HijriMonthLengths()

        a.replaceAll(mapOf((1447 to 9) to 30))

        assertEquals(30, a.monthLength(1447, 9))
        assertNull(b.monthLength(1447, 9))
    }

    // ── two calendars, two answers ──────────────────────────────────────────

    @Test
    fun twoStateHoldersRenderDifferentGridsFromDifferentOverrides() {
        val plain = HijriMonthLengths()
        val forced = HijriMonthLengths().apply { setMonthLength(1448, 3, 30) }

        val month = HijrahYearMonth(1448, 3)
        val plainState = HijriCalendarState(initialMonth = month, monthLengths = plain)
        val forcedState = HijriCalendarState(initialMonth = month, monthLengths = forced)

        assertFalse(
            plainState.calendarMonth.days.any { it.observedDate != null },
            "no overrides means no observed space",
        )
        assertTrue(
            forcedState.calendarMonth.days.any { it.observedDate?.day == 30 },
            "the forced 30th must appear in the second calendar",
        )
        assertEquals(30, forcedState.monthLengthOf(1448, 3))
        assertNull(plainState.monthLengthOf(1448, 3))
    }

    @Test
    fun twoStateHoldersDisagreeAboutTheSameMonthRange() {
        val plain = HijriMonthLengths()
        val forced = HijriMonthLengths().apply { setMonthLength(1448, 3, 30) }

        val plainRange = resolveGregorianMonthRange(1448, 4, overrides = plain)
        val forcedRange = resolveGregorianMonthRange(1448, 4, overrides = forced)

        assertNotEquals(
            plainRange,
            forcedRange,
            "a forced 30-day month re-anchors the next month, but only for its own calendar",
        )
    }

    @Test
    fun thePakistanMonthTableIsScopedPerInstance() {
        // Pick a month with no official fix, so nothing pins its length. A month *in* FIXES keeps
        // its own start no matter what is forced — the fix wins by design — so such a month
        // cannot demonstrate per-instance scoping.
        val (year, month) = (PakistanHijriCalendar.MIN_YEAR + 1 until PakistanHijriCalendar.MAX_YEAR)
            .flatMap { y -> (1..12).map { y to it } }
            .first { (y, m) -> (y to m) !in PakistanHijriCalendar.FIXES }

        val tableLength = PakistanHijriCalendar.defaultLengthOfMonth(year, month)
        val forced = if (tableLength == 29) 30 else 29

        val a = HijriMonthLengths()
        val b = HijriMonthLengths().apply { setMonthLength(year, month, forced) }

        assertEquals(tableLength, PakistanHijriCalendar.lengthOfMonth(year, month, a))
        assertEquals(forced, PakistanHijriCalendar.lengthOfMonth(year, month, b))

        // Whichever month is longer, its extra day is out of range for the shorter table — the
        // range check consults the instance, not the constants.
        val longerDay = maxOf(tableLength, forced)
        val shorterTable = if (forced < tableLength) b else a
        assertFailsWith<IllegalArgumentException> {
            PakistanHijriCalendar.hijriToGregorian(year, month, longerDay, shorterTable)
        }

        // The geometry differs. *Which* end moves depends on where the month sits relative to the
        // first fix: after it, a forced length moves the month's end; before it, the next fix
        // pins the end and the start moves instead. Assert on the whole extent so the test is
        // valid in both regions.
        assertNotEquals(
            resolveGregorianMonthRange(year, month, pakistan = true, overrides = a),
            resolveGregorianMonthRange(year, month, pakistan = true, overrides = b),
            "each instance must resolve this month against its own table",
        )
        assertNotEquals(
            resolveGregorianMonthRange(year, month, pakistan = true, overrides = a).lengthInDays,
            resolveGregorianMonthRange(year, month, pakistan = true, overrides = b).lengthInDays,
        )
    }

    @Test
    fun prewarmTracksOnlyTheInstanceItWasCalledWith() {
        val a = HijriMonthLengths()
        val b = HijriMonthLengths()

        PakistanHijriCalendar.prewarm(a)

        assertTrue(PakistanHijriCalendar.isWarmForCurrentOverrides(a))
        assertFalse(
            PakistanHijriCalendar.isWarmForCurrentOverrides(b),
            "warming one table must not mark another as warm",
        )
    }

    @Test
    fun changingAnInstanceInvalidatesOnlyItsOwnWarmTable() {
        val year = 1441
        val month = 6
        val forced = if (PakistanHijriCalendar.defaultLengthOfMonth(year, month) == 29) 30 else 29

        val a = HijriMonthLengths()
        val b = HijriMonthLengths()
        PakistanHijriCalendar.prewarm(a)
        PakistanHijriCalendar.prewarm(b)
        assertTrue(PakistanHijriCalendar.isWarmForCurrentOverrides(a))

        a.setMonthLength(year, month, forced)

        assertFalse(PakistanHijriCalendar.isWarmForCurrentOverrides(a), "a changed, so a must rebuild")
        assertTrue(PakistanHijriCalendar.isWarmForCurrentOverrides(b), "b did not change")
    }

    // ── revision semantics ──────────────────────────────────────────────────

    @Test
    fun revisionOnlyBumpsOnAnEffectiveChange() {
        val lengths = HijriMonthLengths()
        val start = lengths.currentRevision

        lengths.setMonthLength(1448, 3, 30)
        val afterFirstWrite = lengths.currentRevision
        assertTrue(afterFirstWrite > start)

        lengths.setMonthLength(1448, 3, 30) // same value: no-op
        assertEquals(afterFirstWrite, lengths.currentRevision, "a redundant write must not bump")

        lengths.setMonthLength(1448, 3, 29) // real change
        assertTrue(lengths.currentRevision > afterFirstWrite)

        val afterChange = lengths.currentRevision
        lengths.clearMonthLength(1449, 1) // never set: no-op
        assertEquals(afterChange, lengths.currentRevision)

        lengths.clearMonthLength(1448, 3) // real change
        assertTrue(lengths.currentRevision > afterChange)
    }

    @Test
    fun replaceAllWithIdenticalContentIsNotAnEffectiveChange() {
        val lengths = HijriMonthLengths(mapOf((1448 to 3) to 30))
        val revision = lengths.currentRevision

        lengths.replaceAll(mapOf((1448 to 3) to 30))

        assertEquals(revision, lengths.currentRevision)
        assertEquals(30, lengths.monthLength(1448, 3))
    }

    @Test
    fun replaceAllSwapsTheWholeTableAtOnce() {
        val lengths = HijriMonthLengths(mapOf((1448 to 3) to 30, (1447 to 9) to 29))

        lengths.replaceAll(mapOf((1449 to 1) to 30))

        assertEquals(mapOf((1449 to 1) to 30), lengths.all())
        assertNull(lengths.monthLength(1448, 3))
    }

    // ── validation ──────────────────────────────────────────────────────────

    @Test
    fun invalidLengthsAreRejectedAtEveryEntryPoint() {
        val lengths = HijriMonthLengths()

        assertFailsWith<IllegalArgumentException> { lengths.setMonthLength(1448, 3, 28) }
        assertFailsWith<IllegalArgumentException> { lengths.setMonthLength(1448, 3, 31) }
        assertFailsWith<IllegalArgumentException> { lengths.replaceAll(mapOf((1448 to 3) to 0)) }
        assertTrue(lengths.all().isEmpty(), "a rejected write must not land")
    }

    @Test
    fun theConstructorValidatesToo() {
        assertFailsWith<IllegalArgumentException> { HijriMonthLengths(mapOf((1448 to 3) to 45)) }
    }
}
