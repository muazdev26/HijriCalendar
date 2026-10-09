package com.muazdev.hijricalendar.widgetdata

import com.muazdev.hijricalendar.core.CalendarNames
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * FD-06: the widget's header and the in-app calendar's header must be held to **one** answer.
 *
 * This defect was never "the widget could not name two months". It was that the widget named one and
 * the app named two, for roughly half the calendar — so a user who checked one against the other
 * concluded one of them was broken, and both were, in different ways. The fix removes the truncated
 * string; this test is what stops it coming back, by deriving the expected value from the *month* and
 * asserting both surfaces produce it.
 *
 * The expectation is not copied from either implementation. It is computed from the month's own first
 * and last day, so a change to either surface that stays self-consistent still fails here.
 */
class GregorianHeaderAgreementTest {

    /**
     * Both halves of the header agree, for every month of eleven years.
     *
     * A Hijri month is 29 or 30 days and a Gregorian month is 28-31, so a month that starts in one
     * Gregorian month and ends in another is the common case, not an edge case. A single spot-checked
     * month would have missed the whole defect.
     */
    @Test
    fun theWidgetHeaderSpansEveryMonthsGregorianExtent() {
        var straddling = 0

        for (year in 1440..1450) {
            for (month in 1..12) {
                val data = assertNotNull(
                    buildHijriMonthWidgetData(
                        hijriYear = year,
                        hijriMonth = month,
                        adjustmentDays = 0,
                        localizedGregorianMonthNames = CalendarNames.englishGregorianMonths,
                    ),
                    "no projection for $year-$month",
                )
                val own = data.days.filter { it.isCurrentMonth }
                val first = LocalDate.fromEpochDays(own.first().gregorianEpochDay)
                val last = LocalDate.fromEpochDays(own.last().gregorianEpochDay)

                assertEquals(
                    expectedRange(first, last, CalendarNames.englishGregorianMonths),
                    data.gregorianMonthTitle,
                    "$year-$month",
                )
                if (first.month != last.month) straddling++
            }
        }

        assertTrue(
            straddling > 100,
            "only $straddling of 132 months straddled two Gregorian ones; the fixture months are not " +
                "exercising the case this fix is about",
        )
    }

    /**
     * A month that fits inside one Gregorian month is unchanged.
     *
     * The half of the change that must not regress: `"September 2026"`, never
     * `"September - September 2026"`. A range formatter with no single-month case would render the
     * month name twice on roughly a third of the calendar.
     */
    @Test
    fun aMonthInsideOneGregorianMonthNamesItOnce() {
        var single = 0
        for (year in 1440..1450) {
            for (month in 1..12) {
                val data = assertNotNull(
                    buildHijriMonthWidgetData(hijriYear = year, hijriMonth = month, adjustmentDays = 0),
                )
                val own = data.days.filter { it.isCurrentMonth }
                val first = LocalDate.fromEpochDays(own.first().gregorianEpochDay)
                val last = LocalDate.fromEpochDays(own.last().gregorianEpochDay)
                if (first.month == last.month && first.year == last.year) {
                    single++
                    assertEquals(
                        "${CalendarNames.englishGregorianMonths[first.month.ordinal]} ${first.year}",
                        data.gregorianMonthTitle,
                        "$year-$month must name the Gregorian month once",
                    )
                }
            }
        }
        assertTrue(single > 0, "no month fitted inside one Gregorian month; fixture is not exercising it")
    }

    /**
     * A month crossing a year boundary names both years.
     *
     * The third case, and the one that silently dropped a year when the title was built from
     * `gregorianFirst` alone: December and January are different *years*, so a single-year formatter
     * would have labelled a month that spans 2026 and 2027 as being in one of them.
     */
    @Test
    fun aMonthCrossingAYearBoundaryNamesBothYears() {
        var found = false
        for (year in 1440..1450) {
            for (month in 1..12) {
                val data = assertNotNull(
                    buildHijriMonthWidgetData(hijriYear = year, hijriMonth = month, adjustmentDays = 0),
                )
                val own = data.days.filter { it.isCurrentMonth }
                val first = LocalDate.fromEpochDays(own.first().gregorianEpochDay)
                val last = LocalDate.fromEpochDays(own.last().gregorianEpochDay)
                if (first.year != last.year) {
                    found = true
                    assertTrue(
                        data.gregorianMonthTitle.contains(first.year.toString()) &&
                            data.gregorianMonthTitle.contains(last.year.toString()),
                        "$year-$month spans ${first.year}..${last.year} but the header reads " +
                            "'${data.gregorianMonthTitle}'",
                    )
                }
            }
        }
        assertTrue(found, "no month crossed a Gregorian year boundary in the fixture range")
    }

    /**
     * The names follow the widget's month-name language, not its layout language.
     *
     * A widget can lay out RTL while writing month names in English, and vice versa. The extent is a
     * property of the month; only the *names* in it are localizable.
     */
    @Test
    fun theNamesFollowTheMonthNameLanguage() {
        val englishNames = WidgetOptions(
            language = WidgetLanguage.URDU,
            monthNameLanguage = WidgetLanguage.ENGLISH,
            weekStart = WeekStart.SATURDAY,
        )
        val urduNames = englishNames.copy(monthNameLanguage = WidgetLanguage.URDU)

        val withEnglish = assertNotNull(buildHijriMonthWidgetData(1447, 11, englishNames))
        val withUrdu = assertNotNull(buildHijriMonthWidgetData(1447, 11, urduNames))

        assertTrue(
            withEnglish.gregorianMonthTitle.all { it.code < 0x80 },
            "an English-named widget must write an English extent, got " +
                "'${withEnglish.gregorianMonthTitle}'",
        )
        assertTrue(
            withUrdu.gregorianMonthTitle.any { it.code > 0x7F },
            "an Urdu-named widget must write an Urdu extent, got '${withUrdu.gregorianMonthTitle}'",
        )
    }

    /**
     * The extent is derived from the month, so a pinned or navigated month shows its own range.
     *
     * Worth pinning because the string is now built from `gregorianLast` as well as `gregorianFirst`,
     * and a bug there would show a range that disagrees with the cells underneath it rather than
     * failing loudly.
     */
    @Test
    fun theExtentMatchesTheCellsActuallyPainted() {
        val data = assertNotNull(buildHijriMonthWidgetData(1447, 11, adjustmentDays = 0))
        val own = data.days.filter { it.isCurrentMonth }
        val first = LocalDate.fromEpochDays(own.first().gregorianEpochDay)
        val last = LocalDate.fromEpochDays(own.last().gregorianEpochDay)

        assertTrue(
            data.gregorianMonthTitle.contains(CalendarNames.englishGregorianMonths[first.month.ordinal]),
            "the header omits the first painted month's name",
        )
        if (first.month != last.month) {
            assertTrue(
                data.gregorianMonthTitle.contains(CalendarNames.englishGregorianMonths[last.month.ordinal]),
                "the header omits the last painted month's name, which is the half that was missing",
            )
        }
    }

    /**
     * The widget's header agrees with the in-app calendar's for the same month.
     *
     * The whole point of the ticket. `calendar-ui`'s `defaultGregorianMonthRangeLabel` is the
     * reference answer — the app was always right — and it is reproduced here rather than imported,
     * because `calendar-widget-data` cannot depend on `calendar-ui`. Copying it in is deliberate: a
     * shared helper would create a dependency in the wrong direction to save three lines, and the two
     * need to stay independently readable so a change to either is visible in the diff of both.
     */
    @Test
    fun theWidgetAndTheInAppHeaderAgreeForTheSameMonth() {
        for (year in 1440..1450) {
            for (month in 1..12) {
                val data = assertNotNull(
                    buildHijriMonthWidgetData(
                        hijriYear = year,
                        hijriMonth = month,
                        adjustmentDays = 0,
                        localizedGregorianMonthNames = CalendarNames.englishGregorianMonths,
                    ),
                )
                val own = data.days.filter { it.isCurrentMonth }
                val first = LocalDate.fromEpochDays(own.first().gregorianEpochDay)
                val last = LocalDate.fromEpochDays(own.last().gregorianEpochDay)

                assertEquals(
                    inAppGregorianRangeLabel(first, last),
                    data.gregorianMonthTitle,
                    "$year-$month: the widget and the app disagree about the same month",
                )
            }
        }
    }

    /**
     * A verbatim copy of `calendar-ui`'s `defaultGregorianMonthRangeLabel`, which is what the
     * in-app header renders. Kept as a copy, not a delegation, and asserted here — the in-app suite
     * pins the same three cases from its own side, so a change to either has to be made twice on
     * purpose.
     */
    private fun inAppGregorianRangeLabel(first: LocalDate, last: LocalDate): String = when {
        first.month == last.month && first.year == last.year ->
            "${CalendarNames.englishGregorianMonths[first.month.ordinal]} ${first.year}"
        first.year == last.year ->
            "${CalendarNames.englishGregorianMonths[first.month.ordinal]} - " +
                "${CalendarNames.englishGregorianMonths[last.month.ordinal]} ${first.year}"
        else ->
            "${CalendarNames.englishGregorianMonths[first.month.ordinal]} ${first.year} - " +
                "${CalendarNames.englishGregorianMonths[last.month.ordinal]} ${last.year}"
    }

    private fun expectedRange(
        first: LocalDate,
        last: LocalDate,
        names: List<String>,
    ): String = when {
        first.month == last.month && first.year == last.year ->
            "${names[first.month.ordinal]} ${first.year}"
        first.year == last.year ->
            "${names[first.month.ordinal]} - ${names[last.month.ordinal]} ${first.year}"
        else ->
            "${names[first.month.ordinal]} ${first.year} - " +
                "${names[last.month.ordinal]} ${last.year}"
    }
}
