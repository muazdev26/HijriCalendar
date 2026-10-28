package com.muazdev.hijricalendar.widgetdata

import com.muazdev.hijricalendar.core.HijriEvents
import com.muazdev.hijricalendar.core.PakistanEvents
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * FD-08: `HijriDayWidgetData.hasEvent` — the observance flag a renderer fills its grid cell from.
 *
 * Two properties this pins, and the second is the one that could silently rot.
 *
 * **The flag rides the projection.** `CalendarDay.event` has already resolved the calendar space the
 * projection was built in — Pakistan, observed, then Umm al-Qura — so a grid that re-derived it from
 * `hijriDay` alone would mark Ashura on the Umm al-Qura coordinate and disagree with the footer's
 * answer for the same day on a Pakistan-calendar widget.
 *
 * **Adjacent-month cells are marked too, and that is deliberate.** The grid's renderer is what decides
 * to skip a fill on a cell outside the month; the projection does not, because a renderer that painted
 * neighbours is entitled to know they carry observances, and a second platform's renderer must not have
 * to re-derive the rule. Gating here would make the flag mean two things depending on who read it.
 *
 * **And there are two tables behind the flag.** In Pakistan mode a cell can be marked for a *Gregorian*
 * national day instead of a Hijri observance, which is why the projection also carries
 * [HijriDayWidgetData.eventKey]: the flag says "fill this cell", the key says which table answered.
 * The Hijri-table assertions below therefore state Calculation mode explicitly — `WidgetOptions.DEFAULTS`
 * is Pakistan, so leaving them on the default would make them assert against the combined table.
 */
class EventFlagTest {

    /** Pakistan is the widget default, so the Hijri-table cases have to name Calculation mode. */
    private val options = WidgetOptions(source = WidgetSource.CALCULATION)

    @Test
    fun anObservanceDayIsFlagged() {
        // 10 Muharram is Ashura, and the widget's own language decides only the *name* — the flag is
        // language-independent by construction.
        val month = assertNotNull(buildHijriMonthWidgetData(1447, 1, options))
        val ashura = month.days.firstOrNull { it.isCurrentMonth && it.hijriDay == 10 }
        assertTrue(
            assertNotNull(ashura, "no in-month 10 Muharram in the projection").hasEvent,
            "10 Muharram carries an observance, so the cell must be flagged for a fill",
        )
    }

    @Test
    fun anOrdinaryDayIsNotFlagged() {
        // Shawwal, whose only observance is 1/1 — so every day after it is plain.
        val month = assertNotNull(buildHijriMonthWidgetData(1447, 10, options))
        val flagged = month.days.filter { it.isCurrentMonth && it.hasEvent }.map { it.hijriDay }
        assertEquals(
            listOf(1),
            flagged,
            "only 1 Shawwal carries an observance in this table; a flagged ordinary day means the " +
                "lookup is wrong, not that the table grew",
        )
    }

    /**
     * Ramadan is month 9, and the only day it flags is its first.
     *
     * The regression guard for the bug this suite exists beside: the core table had Eid al-Fitr on
     * month 9, so every grid drew Eid on 1 Ramadan. It shipped because the flag is derived *from* that
     * table, so every projection test agreed with itself — this one is the negative the derivation
     * cannot produce on its own. Day 1 is flagged **on purpose**: the month's first day is named after
     * the month, which is what a reader counting to Eid looks for.
     */
    @Test
    fun ramadanFlagsOnlyItsFirstDay() {
        val ramadan = assertNotNull(buildHijriMonthWidgetData(1447, 9, options))
        val flagged = ramadan.days.filter { it.isCurrentMonth && it.hasEvent }.map { it.hijriDay }
        assertEquals(
            listOf(1),
            flagged,
            "only 1 Ramadan carries an observance here; Eid al-Fitr is 1 Shawwal (month 10)",
        )
    }

    /**
     * The flag must agree with the table, for every month of a real year, on both sides of the year.
     *
     * `buildHijriMonthWidgetData` can return `null` for a year Umm al-Qura does not cover, so this
     * walks the table's own coordinates rather than asserting a fixture month — a month with no
     * projection would otherwise make the test vacuous on a silent regression.
     */
    @Test
    fun theFlagAgreesWithTheEventsTable() {
        val expectedByMonth = HijriEvents.all.groupBy { it.month }

        var monthsChecked = 0
        for (month in 1..12) {
            val projection = buildHijriMonthWidgetData(1447, month, options) ?: continue
            monthsChecked++
            val expected = expectedByMonth[month].orEmpty().map { it.day }.toSet()
            val actual = projection.days
                .filter { it.isCurrentMonth && it.hasEvent }
                .map { it.hijriDay }
                .toSet()
            assertEquals(
                expected,
                actual,
                "month $month: the flagged days must be exactly the table's days for that month",
            )
        }
        assertTrue(monthsChecked > 0, "no month of 1447 built, so nothing above was actually checked")
    }

    /** Every flagged in-month day must be a day the table recognises — the converse of the above. */
    @Test
    fun noFlaggedDayIsUnknownToTheTable() {
        val month = assertNotNull(buildHijriMonthWidgetData(1447, 1, options))
        for (cell in month.days.filter { it.isCurrentMonth && it.hasEvent }) {
            assertNotNull(
                HijriEvents.forDate(1, cell.hijriDay),
                "day ${cell.hijriDay} of Muharram is flagged but carries no observance",
            )
        }
    }

    /**
     * The default is `false`, which is what keeps a hand-constructed cell unchanged.
     *
     * A native renderer that builds a cell itself rather than reading a projection — the Swift grid can
     * — must not have to know about observances to compile or to look right.
     */
    @Test
    fun aHandBuiltCellDefaultsToNoObservance() {
        val cell = HijriDayWidgetData(
            hijriDay = 10,
            dayText = "10",
            gregorianDay = 10,
            gregorianDayText = "10",
            gregorianEpochDay = 0,
            isCurrentMonth = true,
            isWeekend = false,
        )
        assertFalse(cell.hasEvent)
        assertNull(cell.eventKey)
    }

    /**
     * The Gregorian month of a projected cell.
     *
     * `HijriDayWidgetData` carries `gregorianDay` and `gregorianEpochDay`, not a month number — the cell
     * renders a day figure, and the epoch day is what the renderer converts. Resolving the month from
     * the same epoch day keeps this test reading the value the widget actually paints.
     */
    private fun gregorianMonthOf(cell: HijriDayWidgetData): Int =
        LocalDate.fromEpochDays(cell.gregorianEpochDay).month.ordinal + 1

    /**
     * A Pakistani national day marks the cell **in Pakistan mode**, and carries its key.
     *
     * 14 August 2026 is 30 Safar 1448, the last day of that month, so this is a real in-month cell in the
     * Pakistan projection rather than an adjacent-day slot. The key is what the footer names, and it is
     * the only thing that distinguishes a national day from a Hijri observance downstream — the flag
     * alone cannot say which table answered.
     */
    @Test
    fun aPakistanNationalDayIsFlaggedInPakistanMode() {
        val pakistan = WidgetOptions(source = WidgetSource.PAKISTAN)
        val month = assertNotNull(buildHijriMonthWidgetData(1448, 2, pakistan))
        val independence = month.days.firstOrNull {
            it.isCurrentMonth && gregorianMonthOf(it) == 8 && it.gregorianDay == 14
        }
        assertNotNull(independence, "14 August is not in the 1448-02 projection")
        assertTrue(independence.hasEvent, "14 August must be flagged for a fill")
        assertEquals("independence_day", independence.eventKey)
    }

    /**
     * The same Gregorian day in Calculation mode carries no national day.
     *
     * The gate is on the projection builder's calendar space, not on the renderer: a widget in
     * Calculation mode must not paint Independence Day, and a renderer that had to remember the rule
     * would be the second place to get it wrong.
     */
    @Test
    fun theSameDayIsNotFlaggedInCalculationMode() {
        // 14 August 2026 is 30 Safar in the Pakistan calendar and 1 Rabi' I in Umm al-Qura.
        val calculation = WidgetOptions(source = WidgetSource.CALCULATION)
        val month = assertNotNull(buildHijriMonthWidgetData(1448, 3, calculation))
        val august14 = month.days.firstOrNull { gregorianMonthOf(it) == 8 && it.gregorianDay == 14 }
        assertNotNull(august14, "14 August is not in the 1448-03 projection")
        assertNull(
            august14.eventKey,
            "Calculation mode must not carry a Pakistani national day",
        )
        assertFalse(august14.hasEvent, "and it must not flag the cell for one")
    }

    /**
     * A national day outranks a Hijri observance on the same cell, and that precedence is stated once.
     *
     * `notableEvents` puts the national day first, so both the flag and the footer's key inherit it. The
     * search here is for a cell carrying **both** kinds, so the assertion is about the order rather than
     * about a month that happens to hold one national day: if no such cell existed the test would be
     * vacuous, hence the explicit failure.
     */
    @Test
    fun aNationalDayWinsTheCellsSingleKey() {
        val pakistan = WidgetOptions(source = WidgetSource.PAKISTAN)
        // A coincidence between the two tables is a rare alignment (10 Muharram landing on a
        // national day, say), so the search spans a decade of Hijri years rather than one.
        val found = sequence {
            for (year in 1440..1450) {
                for (month in 1..12) {
                    val projection = buildHijriMonthWidgetData(year, month, pakistan) ?: continue
                    for (cell in projection.days) {
                        if (cell.isCurrentMonth) yield(month to cell)
                    }
                }
            }
        }.mapNotNull { (month, cell) ->
            val national = PakistanEvents.forDate(gregorianMonthOf(cell), cell.gregorianDay)
                ?: return@mapNotNull null
            val hijri = HijriEvents.forDate(month, cell.hijriDay) ?: return@mapNotNull null
            Triple(cell, national.key, hijri.key)
        }.firstOrNull()
        val (cell, nationalKey, hijriKey) = assertNotNull(
            found,
            "no in-month cell in 1440..1450 carries both a national day and an observance, so the " +
                "precedence this test exists for is never exercised",
        )
        assertTrue(
            nationalKey != hijriKey,
            "the two tables share a key, which makes this assertion meaningless",
        )
        assertEquals(
            nationalKey,
            cell.eventKey,
            "the key must be the national day's ($nationalKey), not the observance's ($hijriKey)",
        )
    }
}
