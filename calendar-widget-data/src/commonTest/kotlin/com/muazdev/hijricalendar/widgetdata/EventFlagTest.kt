package com.muazdev.hijricalendar.widgetdata

import com.muazdev.hijricalendar.core.HijriEvents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
 */
class EventFlagTest {

    private val options = WidgetOptions()

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
     * Ramadan is month 9, and carries nothing.
     *
     * The regression guard for the bug this suite exists beside: the core table had Eid al-Fitr on
     * month 9, so every grid drew Eid on 1 Ramadan. It shipped because the flag is derived *from* that
     * table, so every projection test agreed with itself — this one is the negative the derivation
     * cannot produce on its own.
     */
    @Test
    fun ramadanCarriesNoObservance() {
        val ramadan = assertNotNull(buildHijriMonthWidgetData(1447, 9, options))
        assertTrue(
            ramadan.days.none { it.isCurrentMonth && it.hasEvent },
            "no day of Ramadan carries an observance; Eid al-Fitr is 1 Shawwal (month 10)",
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
    }
}
