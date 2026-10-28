package com.muazdev.hijricalendar.core

import com.abdulrahman_b.hijrahdatetime.toHijrahDate
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The curated table of Pakistani national days.
 *
 * The risks this file answers, in increasing order of how much they would hurt:
 *
 * 1. **The table resolves** — every entry is findable on its own Gregorian date, and no date resolves
 *    twice.
 * 2. **The month is the one the entry is named for.** The bug [HijriEventsTest] records (Eid al-Fitr
 *    entered as month 9) is available here in exactly the same shape: a lookup by `(month, day)` agrees
 *    with itself even when the month is wrong. [PakistanEvents.coordinateByKey] is the second listing
 *    that has to agree.
 * 3. **The gate is real** — these appear in Pakistan mode and nowhere else, which is asserted on real
 *    [CalendarDay]s built by the state rather than on a hand-made fixture.
 */
class PakistanEventsTest {

    @Test
    fun everyEventResolvesOnItsOwnDate() {
        for (event in PakistanEvents.all) {
            assertEquals(
                event,
                PakistanEvents.forDate(event.month, event.day),
                "${event.key} does not resolve on ${event.month}/${event.day}",
            )
        }
    }

    @Test
    fun noTwoEventsShareADate() {
        val seen = mutableMapOf<Pair<Int, Int>, String>()
        for (event in PakistanEvents.all) {
            val clash = seen.put(event.month to event.day, event.key)
            assertNull(
                clash,
                "${event.key} and $clash both claim ${event.month}/${event.day}; the lookup returns " +
                    "one and a host would have no way to choose",
            )
        }
    }

    @Test
    fun everyEventHasBothNames() {
        for (event in PakistanEvents.all) {
            assertTrue(event.nameEn.isNotBlank(), "${event.key} has no English name")
            assertTrue(event.nameUr.isNotBlank(), "${event.key} has no Urdu name")
            assertTrue(
                event.nameUr.any { it.code > 0x7F },
                "${event.key}'s Urdu name is not in the Urdu script",
            )
        }
    }

    /**
     * Every entry's month **number** is the month it is named for.
     *
     * [PakistanEvents.coordinateByKey] restates the same mapping as data so a wrong month has to be
     * written down twice before it can ship — exactly the guard [HijriEventsTest] needed after the Eid
     * bug. The names below are the assertion's anchor: 14 August is Independence Day and August is
     * month 8, not 7.
     */
    @Test
    fun everyEventSitsInTheMonthItIsNamedFor() {
        assertEquals(
            PakistanEvents.all.map { it.key }.sorted(),
            PakistanEvents.coordinateByKey.keys.sorted(),
            "a new entry needs its month here, or the check below skips it",
        )
        for (event in PakistanEvents.all) {
            assertEquals(
                PakistanEvents.coordinateByKey.getValue(event.key),
                event.month,
                "${event.key} is on Gregorian month ${event.month}; the second listing says " +
                    PakistanEvents.coordinateByKey.getValue(event.key),
            )
        }
        for ((key, month) in PakistanEvents.coordinateByKey) {
            assertTrue(month in 1..12, "$key maps to month $month, which is not a Gregorian month")
        }
    }

    /**
     * The anchored pairs, asserted against the names rather than the numbers.
     *
     * A month index is only meaningful against a name, so the four days a user would notice first are
     * named here: 14 August, 6 September, 23 March and 25 December.
     */
    @Test
    fun theTableHoldsTheDaysPakistanObserves() {
        assertEquals(
            listOf(
                "kashmir_solidarity_day",
                "pakistan_day",
                "labour_day",
                "youm_e_takbir",
                "independence_day",
                "defence_day",
                "iqbal_day",
                "quaid_e_azam_day",
            ),
            PakistanEvents.all.map { it.key },
            "the curated table changed in a way a user would see",
        )

        assertEquals("Independence Day", PakistanEvents.forDate(8, 14)?.nameEn)
        assertEquals("Defence Day", PakistanEvents.forDate(9, 6)?.nameEn)
        assertEquals("Pakistan Day", PakistanEvents.forDate(3, 23)?.nameEn)
        assertEquals("Quaid-e-Azam Day", PakistanEvents.forDate(12, 25)?.nameEn)
    }

    @Test
    fun everyEventHasAStableKey() {
        val keys = PakistanEvents.all.map { it.key }
        assertEquals(keys.size, keys.toSet().size, "keys are what a host matches on, so they must be unique")
        for (key in keys) {
            assertTrue(
                key.isNotBlank() && key.all { it.isLowerCase() || it == '_' || it.isDigit() },
                "key '$key' should be a lower_snake_case identifier",
            )
        }
    }

    @Test
    fun lookupIsYearIndependent() {
        val independence = assertNotNull(PakistanEvents.forDate(8, 14))
        assertEquals("independence_day", independence.key)
        assertEquals(
            independence,
            PakistanEvents.forDate(8, 14),
            "the lookup takes no year, so the same pair must always give the same answer",
        )
    }

    @Test
    fun aDayWithNoNationalDayResolvesToNothing() {
        assertNull(PakistanEvents.forDate(8, 15), "the day after Independence Day carries nothing")
        assertNull(PakistanEvents.forDate(4, 1), "April carries no national day")
        assertNull(PakistanEvents.forDate(13, 1), "an out-of-range month must not throw")
        assertNull(PakistanEvents.forDate(0, 1), "and so must month zero")
    }

    @Test
    fun forMonthReturnsThatMonthsEntriesInDayOrder() {
        assertEquals(
            listOf("labour_day", "youm_e_takbir"),
            PakistanEvents.forMonth(5).map { it.key },
            "May carries two, and forMonth must return day order",
        )
        assertTrue(PakistanEvents.hasAnyIn(8), "August carries Independence Day")
        assertFalse(PakistanEvents.hasAnyIn(4), "April carries none")
        assertTrue(PakistanEvents.forMonth(4).isEmpty())
    }

    @Test
    fun namesSwitchByLanguage() {
        val independence = assertNotNull(PakistanEvents.forDate(8, 14))
        assertEquals(independence.nameEn, independence.name(HijriEventLanguage.ENGLISH))
        assertEquals(independence.nameUr, independence.name(HijriEventLanguage.URDU))
        assertTrue(
            independence.nameEn != independence.nameUr,
            "the two languages must not render the same string",
        )
    }

    /** A blank Urdu name falls back to English rather than rendering nothing. */
    @Test
    fun aBlankUrduNameFallsBackToEnglish() {
        val untranslated = PakistanEvent(
            month = 1,
            day = 1,
            key = "host_supplied",
            nameEn = "New Year",
            nameUr = "  ",
        )
        assertEquals("New Year", untranslated.name(HijriEventLanguage.URDU))
    }

    /** Both implementations are usable as the one type a banner holds. */
    @Test
    fun bothKindsAreTheSameInterface() {
        val events: List<CalendarEvent> = listOf(
            assertNotNull(PakistanEvents.forDate(8, 14)),
            assertNotNull(HijriEvents.forDate(1, 10)),
        )
        assertEquals(listOf("independence_day", "ashura"), events.map { it.key })
        assertEquals(
            listOf("Independence Day", "Ashura"),
            events.map { it.name(HijriEventLanguage.ENGLISH) },
        )
    }

    /**
     * The national day is gated on Pakistan mode — the whole reason this table is not merged into
     * [HijriEvents].
     *
     * Asserted on real cells from [HijriCalendarState], in both modes of the *same* Gregorian day, so a
     * regression that drops the gate shows up as "14 August's fill on the calculated grid".
     */
    @Test
    fun theNationalDayAppearsOnlyInPakistanMode() {
        val target = LocalDate(2026, 8, 14)

        val pakistanCell = monthContaining(target, pakistan = true).days
            .first { it.localDate == target && it.pakistanDate != null }
        assertEquals(
            "independence_day",
            pakistanCell.pakistanEvent?.key,
            "14 August must carry Independence Day in Pakistan mode",
        )
        assertTrue(pakistanCell.hasNotableEvent, "and it must mark the cell")
        assertTrue(
            pakistanCell.notableEvents.any { it.key == "independence_day" },
            "and it must be in the cell's ordered list",
        )

        val calculationCell = monthContaining(target, pakistan = false).days
            .first { it.localDate == target && it.hijrahDate != null }
        assertNull(
            calculationCell.pakistanEvent,
            "Calculation mode must not claim a Pakistani national day",
        )
        assertEquals(
            calculationCell.event?.key,
            calculationCell.notableEvents.singleOrNull()?.key,
            "in Calculation mode the list holds the Hijri observance only (here: none)",
        )
    }

    /**
     * The national day is resolved from the **real-world** Gregorian day, not from the stored Hijri
     * coordinate, so a moon-sighting shift cannot move Independence Day.
     *
     * The shift moves which Hijri date a Gregorian day maps to; the national day is a Gregorian fact and
     * must stay on 14 August.
     */
    @Test
    fun theNationalDayIgnoresTheMoonSightingShift() {
        val target = LocalDate(2026, 8, 14)
        for (adjustment in listOf(-2, 0, 2)) {
            val cell = monthContaining(target, pakistan = true, adjustmentDays = adjustment).days
                .first { it.pakistanDate != null && it.localDate == target }
            assertEquals(
                "independence_day",
                cell.pakistanEvent?.key,
                "an adjustment of $adjustment must not move 14 August",
            )
        }
    }

    /** A day with no national day and no observance carries neither, on a real cell. */
    @Test
    fun anOrdinaryPakistanDayCarriesNothing() {
        val target = LocalDate(2026, 8, 20)
        val cell = monthContaining(target, pakistan = true).days
            .first { it.localDate == target && it.pakistanDate != null }
        assertNull(cell.pakistanEvent, "20 August is not a national day")
        assertTrue(
            cell.notableEvents.none { it is PakistanEvent },
            "and no PakistanEvent may appear in the list",
        )
    }

    /**
     * The state's month holding [date], in the requested mode.
     *
     * Resolved through each mode's own Gregorian→Hijri conversion rather than a hard-coded Hijri month:
     * the two calendars disagree about which month a given Gregorian day falls in, and pinning a number
     * here is how a test starts asserting against the wrong grid.
     */
    private fun monthContaining(
        date: LocalDate,
        pakistan: Boolean,
        adjustmentDays: Int = 0,
    ): CalendarMonth {
        // The state builds Pakistan cells from `gregorian + adjustmentDays`, so the month is the one
        // that shift lands in — the cell's own `localDate` undoes it again.
        val realWorld = date.plus(adjustmentDays, DateTimeUnit.DAY)
        val month = if (pakistan) {
            val pakistanDate = assertNotNull(
                PakistanHijriCalendar.gregorianToHijri(realWorld),
                "the Pakistan calendar could not resolve $realWorld",
            )
            HijrahYearMonth(pakistanDate.year, pakistanDate.month)
        } else {
            val hijri = realWorld.toHijrahDate()
            HijrahYearMonth(hijri.year, hijri.month.number)
        }
        return HijriCalendarState(
            initialMonth = month,
            pakistanDates = pakistan,
            adjustmentDays = adjustmentDays,
        ).calendarMonthFor(month)
    }
}
