package com.muazdev.hijricalendar.widget.glance

import com.muazdev.hijricalendar.core.HijriEventLanguage
import com.muazdev.hijricalendar.core.HijriEvents
import com.muazdev.hijricalendar.core.PakistanEvents
import com.muazdev.hijricalendar.widgetdata.HijriMonthWidgetData
import com.muazdev.hijricalendar.widgetdata.WidgetLanguage
import com.muazdev.hijricalendar.widgetdata.WidgetOptions
import com.muazdev.hijricalendar.widgetdata.WidgetSource
import com.muazdev.hijricalendar.widgetdata.buildHijriMonthWidgetData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
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
 *
 * **Two calendar spaces, two tables.** A Pakistan-mode grid can fill a cell for a Gregorian-fixed
 * national day (Independence Day, Defence Day), which the Hijri table cannot name at all. The fixtures
 * that pin the Hijri *selection* rule therefore name [WidgetSource.CALCULATION] explicitly — a bare
 * `WidgetOptions` defaults to Pakistan, and the national days would then answer every question about
 * which Hijri observance was chosen.
 */
class WidgetEventFooterTest {

    /** Hijri observances only: the space every "which one is nearest" assertion below is about. */
    private val options = WidgetOptions(
        language = WidgetLanguage.ENGLISH,
        source = WidgetSource.CALCULATION,
    )

    /** The national days a Pakistan-mode grid also fills. */
    private val pakistanOptions = WidgetOptions(
        language = WidgetLanguage.ENGLISH,
        source = WidgetSource.PAKISTAN,
    )

    // kotlin.test's, not JUnit's: JUnit's `assertNotNull` returns `Unit`, so it cannot back a
    // helper whose whole job is to hand the projection on (or fail with the month in the message).
    private fun month(
        hijriMonth: Int,
        options: WidgetOptions = this.options,
        hijriYear: Int = 1447,
    ): HijriMonthWidgetData = assertNotNull(
        buildHijriMonthWidgetData(hijriYear, hijriMonth, options),
        "no projection for Hijri month $hijriYear-$hijriMonth",
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

    /**
     * A month with no observance at all has nothing to name — and `nextEventName` says so by returning
     * `null` rather than a placeholder.
     *
     * The placeholder belongs at the call site, not here: `MonthGrid` renders
     * `WidgetLocalization.ChromeLabels.noEvent(language)` for a `null`, so the *footer* is never empty
     * (FD-08 reserves the line for every month so the layout does not move under a finger, and an empty
     * line reads as a footer that failed to load), while this function stays a pure lookup. Asserting
     * `null` here and the label there keeps the one fact in one place.
     */
    @Test
    fun aMonthWithNoObservanceNamesNothing() {
        val safar = month(2)
        assertTrue(
            "Safar must contain no Hijri observance for this test to mean anything",
            safar.days.none { it.isCurrentMonth && it.hasEvent },
        )
        assertNull(nextEventName(safar, dayOffset(safar, 0), WidgetLanguage.ENGLISH))
    }

    /**
     * The same month in **Pakistan mode** names its national day — the one mark the Hijri table is
     * silent on.
     *
     * Safar 1447 straddles 14 August, so the grid fills a cell no `HijriEvents` lookup can account for.
     * A footer that resolved only through the Hijri table would leave a visibly filled cell unnamed,
     * which is exactly the failure the fallback exists to prevent — and it is why the projection
     * carries an `eventKey` rather than a `(month, day)` the renderer re-derives.
     */
    @Test
    fun aNationalDayIsNamedWhenItIsTheMonthsOnlyMark() {
        val safar = month(2, pakistanOptions)
        val independenceDay = assertNotNull(
            inMonthDays(safar).firstOrNull { cell ->
                val gregorian = LocalDate.ofEpochDay(cell.gregorianEpochDay)
                gregorian.monthValue == 8 && gregorian.dayOfMonth == 14
            },
            "Safar 1447 must contain 14 August for this fixture to mean anything",
        )

        assertEquals(
            "Independence Day is a Gregorian fact with no Hijri coordinate",
            "Independence Day",
            nextEventName(safar, independenceDay.gregorianEpochDay, WidgetLanguage.ENGLISH),
        )
        assertEquals(
            "and the name follows the widget's language, not the device's",
            PakistanEvents.all.first { it.key == "independence_day" }
                .name(HijriEventLanguage.URDU),
            nextEventName(safar, independenceDay.gregorianEpochDay, WidgetLanguage.URDU),
        )
    }

    /**
     * Every national day is named on its own day in Pakistan mode — and none of them leaks into
     * Calculation mode.
     *
     * Swept over the whole table rather than one fixture: [PakistanEvents] is Gregorian-fixed, so the
     * answer depends only on the cell's real-world date, and the sweep is what catches a cell whose
     * **Pakistan** day number differs from Umm al-Qura's around a re-sync fix. The Calculation half is
     * the other direction of the same rule: a national day belongs to the Ruet-e-Hilal calendar the user
     * chose, so a widget that did not choose it must not name it.
     */
    @Test
    fun everyNationalDayIsNamedOnItsOwnDayAndOnlyInPakistanMode() {
        // 1447 and 1448 together span all of 2026, so every entry in the table is reachable once.
        val pakistanMonths = listOf(1447, 1448).flatMap { year ->
            (1..12).map { month(it, pakistanOptions, year) }
        }
        val calculationMonths = listOf(1447, 1448).flatMap { year ->
            (1..12).map { month(it, options, year) }
        }

        for (nationalDay in PakistanEvents.all) {
            val date = LocalDate.of(2026, nationalDay.month, nationalDay.day)
            val pakistanMonth = assertNotNull(
                pakistanMonths.firstOrNull { data ->
                    inMonthDays(data).any { it.gregorianEpochDay == date.toEpochDay() }
                },
                "no Pakistan-mode projection contains ${nationalDay.key} on $date",
            )

            assertEquals(
                "${nationalDay.key} on $date",
                nationalDay.name(HijriEventLanguage.ENGLISH),
                nextEventName(pakistanMonth, date.toEpochDay(), WidgetLanguage.ENGLISH),
            )

            val calculationMonth = assertNotNull(
                calculationMonths.firstOrNull { data ->
                    inMonthDays(data).any { it.gregorianEpochDay == date.toEpochDay() }
                },
                "no Calculation-mode projection contains $date",
            )
            assertTrue(
                "a Calculation-mode grid must not name ${nationalDay.key}; got " +
                    "'${nextEventName(calculationMonth, date.toEpochDay(), WidgetLanguage.ENGLISH)}'",
                nextEventName(calculationMonth, date.toEpochDay(), WidgetLanguage.ENGLISH) !=
                    nationalDay.name(HijriEventLanguage.ENGLISH),
            )
        }
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
            urdu.any { it.code > 0x7F },
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
            val expected = HijriEvents.forDate(hijriMonth, nearest.hijriDay)
                ?.name(HijriEventLanguage.ENGLISH)
            assertEquals("month $hijriMonth", expected, named)
        }
    }
}
