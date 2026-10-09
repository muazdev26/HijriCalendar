package com.muazdev.hijricalendar.widget.glance

import com.muazdev.hijricalendar.widgetdata.HijriMonthWidgetData
import com.muazdev.hijricalendar.widgetdata.WidgetLanguage
import com.muazdev.hijricalendar.widgetdata.WidgetOptions
import com.muazdev.hijricalendar.widgetdata.buildHijriMonthWidgetData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.test.assertNotNull

/**
 * FD-08: the grid footer names an observance **without a tap**.
 *
 * The footer used to be laid out only once a day was selected, on the reasoning that a permanently
 * reserved strip is a cost paid for a feature most taps never use. That was the wrong trade once the
 * grid itself began filling observance cells: the fills say *which* days matter, and the line is what
 * turns a mark into a name — but a line that only appears after a tap answers nothing for the user who
 * has not tapped yet, which is the state a widget rests in.
 *
 * What is pinned here is the *choice* of which observance, because that is where the plausible
 * implementations differ: the first in the month, the nearest from today, or nothing.
 */
class WidgetEventFooterTest {

    private val options = WidgetOptions(language = WidgetLanguage.ENGLISH)

    // kotlin.test's, not JUnit's: JUnit's `assertNotNull` returns `Unit`, so it cannot back a
    // helper whose whole job is to hand the projection on (or fail with the month in the message).
    private fun month(hijriMonth: Int): HijriMonthWidgetData = assertNotNull(
        buildHijriMonthWidgetData(1447, hijriMonth, options),
        "no projection for Hijri month $hijriMonth",
    )

    /** The in-month days, which is what the "nearest from today" rule ranges over. */
    private fun inMonthDays(monthData: HijriMonthWidgetData) =
        monthData.days.filter { it.isCurrentMonth }

    /** The epoch day of the [offset]-th in-month day, counting from the 1st. */
    private fun dayOffset(monthData: HijriMonthWidgetData, offset: Int): Long =
        inMonthDays(monthData)[offset].gregorianEpochDay

    /**
     * Before an observance, the footer names the **next** one.
     *
     * Anchored a day before the month begins, so the "nearest from today" rule has an unambiguous answer.
     * 1447 Muharram carries 1/1 (New Year), 1/9 and 1/10 (Ashura and its eve), so the answer is the 1st —
     * and asserting the *name* rather than the day catches a lookup that resolves the wrong entry.
     */
    @Test
    fun beforeAnObservanceTheFooterNamesTheNextOne() {
        val muharram = month(1)

        assertEquals(
            "Islamic New Year",
            nextEventName(muharram, dayOffset(muharram, 0) - 1, WidgetLanguage.ENGLISH),
        )
    }

    /**
     * Mid-month, it names the one still ahead rather than the one already past.
     *
     * The distinction from "the first in the month", and the reason the footer is not simply the head of
     * the table: naming an observance the user has already passed is answering a question nobody asked.
     * Dhu al-Hijjah carries 9/12 (Arafah) and 10/12 (Eid al-Adha), so the days around the pair give an
     * unambiguous answer either way — while still naming the day the user is standing on (see the
     * `>=` rule pinned by `onTheObservanceItselfItNamesThatOne`).
     */
    @Test
    fun midMonthItNamesTheNearestOneStillAhead() {
        val dhuAlHijjah = month(12)
        val onTheNinth = dayOffset(dhuAlHijjah, 8)
        val onTheTenth = onTheNinth + 1

        // Standing on Arafah itself, Arafah is today's observance — the same `>=` rule
        // `onTheObservanceItselfItNamesThatOne` pins for Ashura. An earlier revision of this test
        // asserted Eid here, which asked for `>` on the 9th while the sibling test demanded `>=`
        // on the 10th: no single rule can satisfy both, and `>=` is the one the KDoc promises.
        assertEquals(
            "on the 9th, Arafah is the observance of the day",
            "Day of Arafah",
            nextEventName(dhuAlHijjah, onTheNinth, WidgetLanguage.ENGLISH),
        )
        // The case that actually distinguishes "nearest from today" from "first in the month":
        // once Arafah has passed, naming it again is naming an observance already behind the user.
        assertEquals(
            "on the 10th, the one already passed is not named again",
            "Eid al-Adha",
            nextEventName(dhuAlHijjah, onTheTenth, WidgetLanguage.ENGLISH),
        )
        assertEquals(
            "one day before Arafah, it is the nearest ahead",
            "Day of Arafah",
            nextEventName(dhuAlHijjah, onTheNinth - 1, WidgetLanguage.ENGLISH),
        )
    }

    /**
     * Today *is* the observance, and is named.
     *
     * `>=` rather than `>` in the rule, so a widget opened on Eid morning says so rather than reaching
     * forward to next year's.
     */
    @Test
    fun onTheObservanceItselfItNamesThatOne() {
        val muharram = month(1)
        val ashura = inMonthDays(muharram).first { it.hijriDay == 10 }.gregorianEpochDay

        assertEquals("Ashura", nextEventName(muharram, ashura, WidgetLanguage.ENGLISH))
    }

    /**
     * After the last one, it names **that** one rather than nothing.
     *
     * The alternative — an empty line on a month that visibly contains a filled cell — reads as a bug
     * rather than as an absence, which is the whole reason this fallback exists.
     */
    @Test
    fun afterTheLastObservanceItNamesTheLastOne() {
        val muharram = month(1)
        val longAfter = dayOffset(muharram, inMonthDays(muharram).lastIndex) + 30

        assertEquals("Ashura", nextEventName(muharram, longAfter, WidgetLanguage.ENGLISH))
    }

    /** A month with no observance at all renders an empty line — nothing to name is not a crash. */
    @Test
    fun aMonthWithNoObservanceNamesNothing() {
        val safar = month(2)
        assertTrue(
            "Safar must contain no observance for this test to mean anything",
            safar.days.none { it.isCurrentMonth && it.hasEvent },
        )
        assertNull(nextEventName(safar, dayOffset(safar, 0), WidgetLanguage.ENGLISH))
    }

    /**
     * The name follows the **widget's** language, not the device's.
     *
     * WG-12's rule at the footer, asserted separately from the selection path because the fallback is a
     * second call site: one Urdu widget is designed to sit beside an English one on a phone with no Urdu
     * locale, and an English footer under an Urdu grid is the most obvious possible failure of that.
     */
    @Test
    fun theNameFollowsTheWidgetsLanguage() {
        val muharram = month(1)
        val today = dayOffset(muharram, 0) - 1

        val urdu = nextEventName(muharram, today, WidgetLanguage.URDU)
        val english = nextEventName(muharram, today, WidgetLanguage.ENGLISH)

        assertNotNull(urdu)
        assertNotNull(english)
        assertTrue(
            "the Urdu name should be in the Urdu script, got '$urdu'",
            urdu!!.any { it.code > 0x7F },
        )
        assertEquals("Islamic New Year", english)
    }

    /**
     * The named observance is one the grid actually filled.
     *
     * The fallback and the cells read the same projection field, so this asserts the wiring rather than
     * the arithmetic — but it is the assertion that fails if the fallback is ever re-derived from day
     * numbers while the grid keeps using `hasEvent`, which is the divergence this design prevents.
     */
    @Test
    fun theNamedObservanceIsAlwaysAFilledCell() {
        val start = dayOffset(month(1), 0) - 40

        for (hijriMonth in 1..12) {
            val monthData = month(hijriMonth)
            val today = start + hijriMonth * 30L
            val named = nextEventName(monthData, today, WidgetLanguage.ENGLISH) ?: continue

            val observances = monthData.days.filter { it.isCurrentMonth && it.hasEvent }
            assertTrue("month $hijriMonth has observances but none were named", observances.isNotEmpty())
            val nearest = observances.firstOrNull { it.gregorianEpochDay >= today } ?: observances.last()
            val expected = com.muazdev.hijricalendar.core.HijriEvents
                .forDate(hijriMonth, nearest.hijriDay)
                ?.name(com.muazdev.hijricalendar.core.HijriEventLanguage.ENGLISH)
            assertEquals("month $hijriMonth", expected, named)
        }
    }
}
