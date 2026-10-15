package com.muazdev.hijricalendar.widgetdata

import com.muazdev.hijricalendar.core.CalendarNames
import com.muazdev.hijricalendar.core.HijriMonthLengths
import com.muazdev.hijricalendar.core.HijriMonthOverrides
import com.muazdev.hijricalendar.core.ObservedHijriCalendar
import com.muazdev.hijricalendar.core.PakistanHijriCalendar
import com.muazdev.hijricalendar.core.WeekDay
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The grid's Gregorian labels must be resolved against **the table the grid was built with**, not
 * the process-global one.
 *
 * `PakistanHijriDate` used to derive its `localDate` from `HijriMonthOverrides.current`, so a cell
 * produced under a widget's scoped table was labelled with the global calendar's day. In a
 * widget-only process that global is empty, so the official 30-day fix for 1448-04 was the only
 * input — and a widget configured with a 29-day 1448-04 put every label from 1448-05 onward one day
 * high: its "1 Jumada al-ula" cell read `14` where the in-app calendar read `13`. The label and the
 * cell's own Hijri number disagreed, and neither surface could tell.
 *
 * The reference used here is not either implementation: it is
 * [PakistanHijriCalendar.hijriToGregorian] / [ObservedHijriCalendar.observedToGregorian] evaluated
 * directly against the table under test, which is the definition the grid means to paint. Equality
 * with the cell's stored day is what the fix restores.
 */
class GridLabelsFollowTheirOwnTableTest {

    /**
     * The widget's own table: the official 1448-04 fix is 30 days, shortened to 29. Under it
     * 1448-05-01 is 2026-10-13; under the fix alone it is 2026-10-14.
     */
    private val scoped = HijriMonthLengths().apply { setMonthLength(1448, 4, 29) }

    private val oct13 = LocalDate(2026, 10, 13)

    @AfterTest
    fun tearDown() {
        HijriMonthOverrides.clearAll()
    }

    // ── the calendar's own conversion is the reference ─────────────────────

    @Test
    fun everyPakistanGridLabelMatchesItsOwnTable() {
        // The global is empty, exactly as it is in a widget-only process. A label that agreed with
        // the *global* calendar would be off by one for every month on or after the shortened one.
        HijriMonthOverrides.clearAll()
        for (month in 1..12) {
            val data = assertNotNull(
                buildHijriMonthWidgetData(
                    hijriYear = 1448,
                    hijriMonth = month,
                    adjustmentDays = 0,
                    pakistan = true,
                    overrides = scoped,
                ),
                "no projection for 1448-$month",
            )
            for (cell in data.days.filter { it.isCurrentMonth }) {
                assertEquals(
                    PakistanHijriCalendar.hijriToGregorian(1448, month, cell.hijriDay, scoped).toEpochDays(),
                    cell.gregorianEpochDay,
                    "1448-$month day ${cell.hijriDay} is labelled with the wrong Gregorian day",
                )
            }
        }
    }

    @Test
    fun everyCalculationGridLabelMatchesItsOwnTable() {
        // The override turns this into the observed calendar, whose cells carry their own stored
        // day; this pins that the option path did not regress while the Pakistan one was fixed.
        HijriMonthOverrides.clearAll()
        for (month in 1..12) {
            val data = assertNotNull(
                buildHijriMonthWidgetData(
                    hijriYear = 1448,
                    hijriMonth = month,
                    adjustmentDays = 0,
                    pakistan = false,
                    overrides = scoped,
                ),
                "no projection for 1448-$month",
            )
            for (cell in data.days.filter { it.isCurrentMonth }) {
                assertEquals(
                    ObservedHijriCalendar.observedToGregorian(1448, month, cell.hijriDay, scoped).toEpochDays(),
                    cell.gregorianEpochDay,
                    "1448-$month observed day ${cell.hijriDay} is labelled with the wrong Gregorian day",
                )
            }
        }
    }

    // ── the absolute anchors from the report ───────────────────────────────

    @Test
    fun theShortenedMonthLabelsItsFirstDaysWithTheRealGregorianDates() {
        // Reported symptom, stated absolutely: with 1448-04 forced to 29, 2026-10-13 is
        // 1 Jumada al-ula 1448, so the grid's days 1 / 3 / 5 are 13 / 15 / 17 October — not the
        // 14 / 16 / 18 the empty global produced.
        HijriMonthOverrides.clearAll()
        val data = assertNotNull(
            buildHijriMonthWidgetData(
                hijriYear = 1448,
                hijriMonth = 5,
                adjustmentDays = 0,
                weekStart = WeekStart.SATURDAY,
                pakistan = true,
                overrides = scoped,
            ),
        )

        assertEquals(oct13.toEpochDays(), data.epochOfHijriDay(1))
        assertEquals(LocalDate(2026, 10, 15).toEpochDays(), data.epochOfHijriDay(3))
        assertEquals(LocalDate(2026, 10, 17).toEpochDays(), data.epochOfHijriDay(5))
    }

    @Test
    fun theTodayTileIsUnaffectedByTheGridLabelFix() {
        // The tile resolves its Hijri date from the anchor directly, never from a cell's stored
        // Gregorian day, so the fix must leave it exactly as it was — and its Gregorian half still
        // describes the anchor, not the Hijri month.
        HijriMonthOverrides.clearAll()
        val today = assertNotNull(
            todayHijriWidgetData(
                anchorEpochDay = LocalDate(2026, 10, 15).toEpochDays(),
                adjustmentDays = 0,
                pakistan = true,
                overrides = scoped,
            ),
        )

        assertEquals(3, today.hijriDay)
        assertEquals(5, today.hijriMonth)
        assertEquals(1448, today.hijriYear)
        assertEquals(CalendarNames.englishHijriMonths[4], today.hijriMonthName, "Jumada al-ula")

        assertEquals(15, today.gregorianDay)
        assertEquals(10, today.gregorianMonth)
        assertEquals(2026, today.gregorianYear)
    }

    // ── the label sits in the cell's real weekday column ───────────────────

    @Test
    fun theFirstDaySitsInItsWeekdayColumnUnderTheScopedTable() {
        // 2026-10-13 is a Tuesday; the default week starts on Saturday, so it belongs in column 3.
        // A label one day high would also misalign the weekday, which is the second tell of the bug.
        HijriMonthOverrides.clearAll()
        val data = assertNotNull(
            buildHijriMonthWidgetData(
                hijriYear = 1448,
                hijriMonth = 5,
                adjustmentDays = 0,
                weekStart = WeekStart.SATURDAY,
                pakistan = true,
                overrides = scoped,
            ),
        )

        assertEquals(WeekDay.TUESDAY, WeekDay.fromDayOfWeek(oct13.dayOfWeek))
        assertEquals("Tue", data.weekdayHeaders[3])
        val index = data.days.indexOfFirst { it.gregorianEpochDay == oct13.toEpochDays() }
        assertTrue(index >= 0, "the 1st of the month is missing from its own grid")
        assertEquals(3, index % 7, "1 Jumada al-ula must sit in Tuesday's column")
        assertEquals(1, data.days[index].hijriDay)
    }

    private fun HijriMonthWidgetData.epochOfHijriDay(day: Int): Long =
        days.first { it.isCurrentMonth && it.hijriDay == day }.gregorianEpochDay
}
