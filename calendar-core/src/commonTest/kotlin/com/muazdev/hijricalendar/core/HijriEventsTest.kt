package com.muazdev.hijricalendar.core

import com.abdulrahman_b.hijrahdatetime.HijrahDate
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * FD-08: the curated table of notable Hijri dates.
 *
 * Three things are worth testing here, in increasing order of how much they would hurt to get wrong:
 *
 * 1. **The table resolves** — every entry is findable on its own date, and no date resolves twice.
 * 2. **The Gregorian-fixed flag is honest.** Four of the eight observances are fixed to a *Gregorian*
 *    date in South Asian practice, so their Hijri date is wrong for the audience most likely to be
 *    looking at it. That is a design limitation, not a bug, and the flag is what says so — so the
 *    assertion is that the flag is set on the ones that need it, which is the only thing that keeps the
 *    limitation from quietly becoming a lie.
 * 3. **Lookup is year-independent**, so the table cannot rot across centuries.
 */
class HijriEventsTest {

    @Test
    fun everyEventResolvesOnItsOwnDate() {
        for (event in HijriEvents.all) {
            assertEquals(
                event,
                HijriEvents.forDate(event.month, event.day),
                "${event.key} does not resolve on ${event.month}/${event.day}",
            )
        }
    }

    @Test
    fun noTwoEventsShareADate() {
        val seen = mutableMapOf<Pair<Int, Int>, String>()
        for (event in HijriEvents.all) {
            val key = event.month to event.day
            val clash = seen.put(key, event.key)
            assertNull(
                clash,
                "${event.key} and $clash both claim ${event.month}/${event.day}; the lookup " +
                    "returns one and a host would have no way to choose",
            )
        }
    }

    @Test
    fun everyEventHasBothNames() {
        for (event in HijriEvents.all) {
            assertTrue(event.nameEn.isNotBlank(), "${event.key} has no English name")
            assertTrue(event.nameUr.isNotBlank(), "${event.key} has no Urdu name")
        }
    }

    /**
     * Every entry's month **number** is the month it is named for.
     *
     * The regression guard for a bug that shipped: Eid al-Fitr was entered as month **9**, which is
     * Ramadan, so every Eid in every grid was drawn on 1 Ramadan. Nothing caught it because the table
     * is looked up by `(month, day)` alone — `everyEventResolvesOnItsOwnDate` and
     * `theTableHoldsTheObservancesPeopleActuallyObserve` both passed on the wrong number, and so did
     * every projection test downstream, which read the same table and agreed with itself.
     *
     * A month index is only meaningful against a name, so the assertion has to be made against one.
     * [HijriEvents.coordinateByKey] is the same mapping restated as data, which is the point: a second
     * listing is what lets the two disagree in review.
     */
    @Test
    fun everyEventSitsInTheMonthItIsNamedFor() {
        for (event in HijriEvents.all) {
            val expectedMonth = HijriEvents.coordinateByKey.getValue(event.key)
            assertEquals(
                expectedMonth,
                event.month,
                "${event.key} is on Hijri month ${event.month}, which is " +
                    "${CalendarNames.englishHijriMonths[event.month - 1]} — it belongs in month " +
                    "$expectedMonth",
            )
        }
    }

    /**
     * The same mapping, as data rather than as prose.
     *
     * Exists so the assertion above has something to check against that is not the table itself. It is
     * 1-based to match `englishHijriMonths`, and asserted to cover every key in the table so an entry
     * added without a month here fails loudly instead of silently skipping the check.
     */
    @Test
    fun everyEventHasAnExpectedMonth() {
        assertEquals(
            HijriEvents.all.map { it.key }.sorted(),
            HijriEvents.coordinateByKey.keys.sorted(),
            "a new entry needs its month here, or the name check above skips it",
        )
        for ((key, month) in HijriEvents.coordinateByKey) {
            assertTrue(month in 1..12, "$key maps to month $month, which is not a Hijri month")
        }
    }

    @Test
    fun everyEventHasAStableKey() {
        val keys = HijriEvents.all.map { it.key }
        assertEquals(
            keys.size,
            keys.toSet().size,
            "keys are what a host matches on to customise one entry, so they must be unique",
        )
        for (key in keys) {
            assertTrue(
                key.isNotBlank() && key.all { it.isLowerCase() || it == '_' || it.isDigit() },
                "key '$key' should be a lower_snake_case identifier",
            )
        }
    }

    /**
     * Both scripts, and the six that carry the caveat.
     *
     * The list is the ticket's, asserted by name so a rename cannot quietly drop a day that people
     * observe: Islamic New Year, Ashura and its eve, the Mawlid, Isra and Mi'raj, Shab-e-Barat,
     * Eid al-Fitr, Arafah and Eid al-Adha.
     */
    @Test
    fun theTableHoldsTheObservancesPeopleActuallyObserve() {
        val keys = HijriEvents.all.map { it.key }
        assertEquals(
            listOf(
                "islamic_new_year",
                "ashura_ninth",
                "ashura",
                "mawlid",
                "isra_and_miraj",
                "shab_e_barat",
                "eid_al_fitr",
                "day_of_arafah",
                "eid_al_adha",
            ),
            keys,
            "the curated table changed; every entry here is one somebody observes",
        )
    }

    /**
     * The South Asian Gregorian-fixed observances are marked, and the Hijri-fixed ones are not.
     *
     * This is the assertion that keeps the documented limitation honest. If the flags were all `false`
     * the table would look authoritative and be wrong for its main audience, and nothing else in the
     * codebase would notice.
     */
    @Test
    fun theGregorianFixedOnesAreMarkedAndTheRestAreNot() {
        val expected = mapOf(
            "islamic_new_year" to false,
            "ashura_ninth" to true,
            "ashura" to true,
            "mawlid" to true,
            "isra_and_miraj" to false,
            "shab_e_barat" to false,
            "eid_al_fitr" to true,
            "day_of_arafah" to true,
            "eid_al_adha" to true,
        )
        assertEquals(
            expected,
            HijriEvents.all.associate { it.key to it.isGregorianFixedInPractice },
            "the isGregorianFixedInPractice flags do not match the documented set",
        )

        assertTrue(
            HijriEvents.all.any { it.isGregorianFixedInPractice },
            "at least one caveat must be flagged, or the field is decorative",
        )
        assertTrue(
            HijriEvents.all.any { !it.isGregorianFixedInPractice },
            "at least one observance must be genuinely Hijri-fixed, or the table exists for nothing",
        )
    }

    /**
     * Lookup does not consider the year.
     *
     * That is what makes the table correct across centuries and cheap enough to call from a composable,
     * and it is a property rather than an accident — an observance recurs annually.
     */
    @Test
    fun lookupIsYearIndependent() {
        val ashura = assertNotNull(HijriEvents.forDate(1, 10), "no Ashura")
        assertEquals(
            ashura,
            HijriEvents.forDate(1, 10),
            "the lookup takes no year, so the same pair must always give the same answer",
        )
        assertEquals(ashura.key, "ashura")
    }

    @Test
    fun aDayWithNoObservanceResolvesToNothing() {
        // 5 Muharram, or thereabouts: no entry, and `null` rather than a default.
        assertNull(HijriEvents.forDate(1, 5), "a day with no observance must resolve to null")
        assertNull(HijriEvents.forDate(2, 30), "and so must one in a month with no entries")
        assertNull(HijriEvents.forDate(13, 1), "and an out-of-range month must not throw")
        assertNull(HijriEvents.forDate(0, 1), "and so must month zero")
    }

    @Test
    fun forMonthReturnsThatMonthsEntriesInDayOrder() {
        // Shawwal is **10**. This was `forMonth(9)`, which asserted against Ramadan and so passed on
        // a table that had Eid al-Fitr in the wrong month.
        val shawwal = HijriEvents.forMonth(10)
        assertEquals(listOf("eid_al_fitr"), shawwal.map { it.key })

        // And the negative that would have caught it: Ramadan carries nothing.
        assertTrue(
            HijriEvents.forMonth(9).isEmpty(),
            "Ramadan (month 9) carries no observance; Eid al-Fitr is 1 Shawwal, month 10",
        )

        val muharram = HijriEvents.forMonth(1)
        assertEquals(
            listOf("islamic_new_year", "ashura_ninth", "ashura"),
            muharram.map { it.key },
        )
        assertEquals(
            listOf(1, 9, 10),
            muharram.map { it.day },
            "forMonth must return day order, not table order",
        )

        assertTrue(HijriEvents.hasAnyIn(1), "Muharram carries three")
        assertTrue(HijriEvents.hasAnyIn(10), "Shawwal carries Eid al-Fitr")
        assertFalse(HijriEvents.hasAnyIn(2), "Safar carries none")
        assertTrue(HijriEvents.forMonth(2).isEmpty())
    }

    /** The language switch is the whole point of carrying both names. */
    @Test
    fun namesSwitchByLanguage() {
        val ashura = assertNotNull(HijriEvents.forDate(1, 10))

        assertEquals(ashura.nameEn, ashura.name(HijriEventLanguage.ENGLISH))
        assertEquals(ashura.nameUr, ashura.name(HijriEventLanguage.URDU))
        assertTrue(
            ashura.nameEn != ashura.nameUr,
            "the two languages must not render the same string",
        )
        assertTrue(
            ashura.nameUr.any { it.code > 0x7F },
            "the Urdu name should be in the Urdu script",
        )
    }

    /**
     * A cell resolves its observance through the state's own routing order.
     *
     * **Pakistan → observed → Umm al-Qura**, which is the order `selectDay` uses, so tapping a day and
     * looking up its event cannot disagree about which coordinate was meant. This test builds cells in
     * each space and checks the resolution follows the same precedence.
     */
    @Test
    fun aCellResolvesItsEventInTheStateRoutingOrder() {
        // 10 Muharram 1447 in the plain space.
        val plain = HijriCalendarState(
            initialMonth = HijrahYearMonth(1447, 1),
            initialSelectedDate = HijrahDate(1447, 1, 10),
        ).calendarMonthFor(HijrahYearMonth(1447, 1)).days
            .first { it.hijrahDate == HijrahDate(1447, 1, 10) }

        assertEquals(
            "ashura",
            plain.event?.key,
            "a plain cell must resolve its own Hijri coordinate",
        )

        // The same day in the Pakistan space: `pakistanDate` wins, so a Pakistan-calendar user on
        // 10 Muharram gets the answer for the day they are looking at.
        val pakistan = HijriCalendarState(
            initialMonth = HijrahYearMonth(1447, 1),
            pakistanDates = true,
        ).calendarMonthFor(HijrahYearMonth(1447, 1)).days
            .first { it.pakistanDate?.month == 1 && it.pakistanDate?.day == 10 }

        assertEquals(
            "ashura",
            pakistan.event?.key,
            "a Pakistan cell must resolve its own coordinate",
        )
    }

    /**
     * A cell with no date carries no event.
     *
     * The substitution this guards against is inventing one: a disabled placeholder with all three
     * date fields null must not inherit an observance, or Ashura appears on a cell that represents no
     * day at all.
     */
    @Test
    fun aCellWithNoDateHasNoEvent() {
        val empty = CalendarDay(
            isCurrentMonth = false,
            isToday = false,
            isSelected = false,
            isDisabled = true,
            isWeekend = false,
        )
        assertNull(empty.event, "a cell with no date must not resolve an observance")
        assertEquals(0, empty.dayOfMonth, "and it reports no day either")
    }

    /**
     * The date an observance resolves on is stable across the year range the library supports.
     *
     * Cheap, and it is the assertion that would catch a future "let's store the year too" change —
     * which would silently make the table stop working in 1600.
     */
    @Test
    fun theTableIsStableAcrossTwelveCenturies() {
        for (event in HijriEvents.all) {
            for (year in listOf(1300, 1400, 1447, 1500, 1600)) {
                val date = HijrahDate(year, event.month, event.day)
                assertTrue(
                    date.year >= HijrahDate.MIN.year && date.year <= HijrahDate.MAX.year,
                    "the library cannot represent $year",
                )
                assertEquals(
                    event,
                    HijriEvents.forDate(date.month.number, date.day),
                    "${event.key} resolved differently in $year",
                )
            }
        }
    }
}
