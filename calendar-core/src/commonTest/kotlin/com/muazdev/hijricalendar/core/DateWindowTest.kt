package com.muazdev.hijricalendar.core

import com.abdulrahman_b.hijrahdatetime.HijrahDate
import com.abdulrahman_b.hijrahdatetime.toLocalDate
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * Tests for the `minDate`/`maxDate` selection window across all three grid spaces.
 *
 * The bug these exist for: the Pakistan and observed builders hardcoded `isDisabled = false`,
 * so `minDate`/`maxDate` were silently **no-ops** outside plain Umm al-Qura mode. Nothing about
 * the public API hinted at that — the bounds were accepted, stored, honoured by navigation, and
 * then ignored on 42 of every 42 cells in the two other modes.
 *
 * The bounds stay `HijrahDate` for API compatibility, but they are resolved into real-world
 * Gregorian days (`DateWindow`) and compared against each cell's own real-world day, because a
 * Pakistan cell or an observed cell with a day beyond its month's Umm al-Qura length has no
 * Umm al-Qura coordinate to compare against.
 */
class DateWindowTest {

    private val year = 1447
    private val month = 1

    /** A window covering days 20..25 of the month, in each mode's own Hijri coordinates. */
    private val minDate = HijrahDate(year, month, 20)
    private val maxDate = HijrahDate(year, month, 25)

    @AfterTest
    fun tearDown() {
        HijriMonthOverrides.clearAll()
    }

    // ── Umm al-Qura (the mode that already worked) ───────────────────────

    @Test
    fun ummAlQura_disablesCellsOutsideTheWindow() {
        val grid = grid(pakistan = false, overrides = HijriMonthLengths())

        assertTrue(
            grid.days.any { it.isCurrentMonth && it.isDisabled },
            "a bounded calendar must have disabled cells",
        )
        grid.days.filter { !it.isDisabled }.forEach { cell ->
            assertTrue(
                cell.localDate in realWorldWindow(),
                "enabled cell ${cell.hijrahDate} falls on ${cell.localDate}, outside $minDate..$maxDate",
            )
        }
    }

    // ── Pakistan (bounds used to be ignored entirely) ────────────────────

    @Test
    fun pakistan_disablesCellsOutsideTheWindow() {
        val grid = grid(pakistan = true, overrides = HijriMonthLengths())

        assertTrue(
            grid.days.any { it.isCurrentMonth && it.isDisabled },
            "minDate/maxDate were ignored on the Pakistan path — every cell used to be enabled",
        )
        grid.days.filter { !it.isDisabled }.forEach { cell ->
            assertTrue(
                cell.localDate in realWorldWindow(),
                "enabled cell ${cell.pakistanDate} falls on ${cell.localDate}, outside $minDate..$maxDate",
            )
        }
    }

    // ── Observed / override-shifted Umm al-Qura (also ignored) ──────────

    @Test
    fun observed_disablesCellsOutsideTheWindow() {
        val overrides = HijriMonthLengths()
        overrides.setMonthLength(1446, 12, 30)
        val grid = grid(pakistan = false, overrides = overrides)

        assertTrue(
            grid.days.any { it.isCurrentMonth && it.isDisabled },
            "minDate/maxDate were ignored on the observed path — every cell used to be enabled",
        )
        grid.days.filter { !it.isDisabled }.forEach { cell ->
            assertTrue(
                cell.localDate in realWorldWindow(),
                "enabled cell ${cell.observedDate} falls on ${cell.localDate}, outside $minDate..$maxDate",
            )
        }
    }

    @Test
    fun anObservedDayBeyondTheCalculatedMonthLengthIsStillBounded() {
        // Force 1447-1 to 30 days. Day 30 has no Umm al-Qura coordinate in a 29-day month, which
        // is exactly the case a HijrahDate-vs-HijrahDate comparison cannot express.
        val overrides = HijriMonthLengths()
        overrides.setMonthLength(1447, 1, 30)
        val grid = grid(pakistan = false, overrides = overrides)

        val day30 = grid.days.firstOrNull { it.observedDate?.day == 30 }
        assertNotNull(day30, "expected a forced 30th of 1447-01 to be rendered")
        assertEquals(
            realWorldWindow().contains(day30.localDate),
            !day30.isDisabled,
            "the forced 30th must be bounded like any other cell",
        )
    }

    // ── The window means the same thing in every mode ────────────────────

    @Test
    fun theWindowSelectsTheSameRealWorldDaysInEveryMode() {
        // Different spaces put the "1 of the month" on different real-world days, so the number of
        // selectable cells legitimately varies. The *set of real-world days* under the window must
        // not, because that is the single ordering all three spaces agree on.
        val overrides = HijriMonthLengths().apply { setMonthLength(1446, 12, 30) }
        val enabled: List<List<LocalDate>> = listOf(
            grid(pakistan = false, overrides = HijriMonthLengths()),
            grid(pakistan = true, overrides = HijriMonthLengths()),
            grid(pakistan = false, overrides = overrides),
        ).map { built: CalendarMonth ->
            built.days.filter { !it.isDisabled }.map { it.localDate }.distinct().sorted()
        }

        assertTrue(enabled[0].isNotEmpty(), "sanity: the window must admit some cells")
        // Every mode's enabled days are a subset of the same real-world interval, and each is
        // non-empty: a mode that ignored the bounds would return *all* 42 grid days.
        enabled.forEach { days: List<LocalDate> ->
            assertTrue(days.isNotEmpty())
            assertTrue(days.all { it in realWorldWindow() })
            assertTrue(days.size < 42, "bounds appear to be ignored: $days of 42 cells enabled")
        }
    }

    // ── Unbounded calendars must stay unbounded ──────────────────────────

    @Test
    fun anUnboundedWindowEnablesEveryCellInEveryMode() {
        val overrides = HijriMonthLengths().apply { setMonthLength(1446, 12, 30) }
        listOf(
            grid(pakistan = false, overrides = HijriMonthLengths(), min = null, max = null),
            grid(pakistan = true, overrides = HijriMonthLengths(), min = null, max = null),
            grid(pakistan = false, overrides = overrides, min = null, max = null),
        ).forEach { month ->
            val currentMonthCells = month.days.filter { it.isCurrentMonth }
            assertTrue(currentMonthCells.isNotEmpty())
            assertTrue(
                currentMonthCells.none { it.isDisabled },
                "an unbounded calendar must not disable anything: ${currentMonthCells.filter { it.isDisabled }}",
            )
        }
    }

    @Test
    fun adjustmentDaysShiftsTheWindowWithTheGrid() {
        // With adjustmentDays != 0 the cells move but the bounds must move with them, otherwise a
        // shifted grid would compare against unshifted bounds and disable the wrong end.
        val shifted = HijrahYearMonth(year, month).toCalendarMonth(
            minDate = minDate,
            maxDate = maxDate,
            adjustmentDays = 2,
        )
        val window = realWorldWindow(adjustmentDays = 2)

        shifted.days.filter { !it.isDisabled }.forEach { cell ->
            assertTrue(
                cell.localDate in window,
                "enabled cell ${cell.hijrahDate} falls on ${cell.localDate}, outside the shifted window",
            )
        }
    }

    // ── Selection must honour the window too, not just the grid ──────────

    @Test
    fun selectionOutsideTheWindowIsRejectedInEveryMode() {
        val state = HijriCalendarState(
            initialMonth = HijrahYearMonth(year, month),
            minDate = minDate,
            maxDate = maxDate,
        )

        val earlyUaq = HijrahDate(year, month, 5)
        state.selectDate(earlyUaq)
        assertEquals(null, state.selectedDate, "an out-of-window Umm al-Qura day must be rejected")

        val inWindow = minDate
        state.selectDate(inWindow)
        assertEquals(inWindow, state.selectedDate, "an in-window day must be accepted")

        val earlyPakistan = PakistanHijriDate(year, month, 5)
        state.selectPakistanDate(earlyPakistan)
        assertEquals(
            null,
            state.selectedPakistanDate,
            "an out-of-window Pakistan day must be rejected and leave the selection alone",
        )

        val inWindowPakistan = PakistanHijriDate(year, month, 20)
        state.selectPakistanDate(inWindowPakistan)
        assertEquals(
            inWindowPakistan,
            state.selectedPakistanDate,
            "an in-window Pakistan day must be accepted",
        )

        val earlyObserved = ObservedHijriDate(year, month, 5, observedMonthLength(year, month))
        state.selectObservedDate(earlyObserved)
        assertEquals(
            null,
            state.selectedObservedDate,
            "an out-of-window observed day must be rejected and leave the selection alone",
        )

        val inWindowObserved = ObservedHijriDate(year, month, 20, observedMonthLength(year, month))
        state.selectObservedDate(inWindowObserved)
        assertEquals(
            inWindowObserved,
            state.selectedObservedDate,
            "an in-window observed day must be accepted",
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun observedMonthLength(y: Int, m: Int): Int =
        ObservedHijriCalendar.defaultLength(y, m)

    private fun realWorldWindow(adjustmentDays: Int = 0): ClosedRange<LocalDate> {
        val from = minDate.toLocalDate().minus(adjustmentDays, DateTimeUnit.DAY)
        val to = maxDate.toLocalDate().minus(adjustmentDays, DateTimeUnit.DAY)
        return from..to
    }

    private fun grid(
        pakistan: Boolean,
        overrides: HijriMonthLengths,
        min: HijrahDate? = minDate,
        max: HijrahDate? = maxDate,
    ): CalendarMonth = HijrahYearMonth(year, month).toCalendarMonth(
        firstDayOfWeek = WeekDay.DEFAULT_FIRST_DAY,
        minDate = min,
        maxDate = max,
        pakistan = pakistan,
        overrides = overrides,
    )
}
