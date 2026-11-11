package com.muazdev.hijricalendar.widget.glance

import com.muazdev.hijricalendar.core.HijriEventLanguage
import com.muazdev.hijricalendar.core.HijriEvents
import com.muazdev.hijricalendar.core.PakistanEvents
import com.muazdev.hijricalendar.widgetdata.HijriDayWidgetData
import com.muazdev.hijricalendar.widgetdata.HijriMonthWidgetData
import com.muazdev.hijricalendar.widgetdata.WidgetLanguage
import com.muazdev.hijricalendar.widgetdata.WidgetLocalization
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
 * The grid footer: `footerEventName` names **only the selected day**, and `footerLine` turns that into the
 * line the widget draws — the in-app event bar's three-way answer.
 *
 * The name used to fall back to a "nearest upcoming observance" while nothing was tapped, so the line
 * always said something. On a widget the footer sits directly under a grid whose selection moves with
 * every tap, and that fallback made paging months look like selecting a date: the line changed while the
 * selection had not. The filled cells are the month's calendar; the line names what was touched.
 *
 * What is pinned for the **name** is the guard on the selection, because that is where the plausible
 * implementations differ: a selection in the displayed month names its cell's mark, a selection in
 * another month names nothing, a selected day with no mark names nothing, and no selection names nothing.
 * What is pinned for the **line** is the resting state, which is where the widget disagreed with the app:
 * a month with marks asks for a tap, a month without them says there is nothing, and a tapped ordinary day
 * says so rather than describing the month.
 *
 * **Two calendar spaces, two tables.** A Pakistan-mode grid can fill a cell for a Gregorian-fixed
 * national day (Independence Day, Defence Day), which the Hijri table cannot name at all. The fixtures
 * that pin the Hijri rule therefore name [WidgetSource.CALCULATION] explicitly — a bare `WidgetOptions`
 * defaults to Pakistan, and the national days would then answer every question about which Hijri
 * observance was chosen.
 */
class WidgetEventFooterTest {

    /** Hijri observances only: the space every "which one" assertion below is about. */
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

    private fun inMonthDays(monthData: HijriMonthWidgetData) =
        monthData.days.filter { it.isCurrentMonth }

    /** The in-month cell carrying [hijriDay], which the selection addresses by number. */
    private fun day(monthData: HijriMonthWidgetData, hijriDay: Int): HijriDayWidgetData =
        assertNotNull(
            inMonthDays(monthData).firstOrNull { it.hijriDay == hijriDay },
            "month ${monthData.hijriMonth} has no day $hijriDay",
        )

    private fun selectionFor(monthData: HijriMonthWidgetData, hijriDay: Int) =
        HijriDaySelection(monthData.hijriYear, monthData.hijriMonth, hijriDay)

    // ── nothing selected: silence, not the month's next event ─────────────

    /**
     * The headline change: a month holding three observances names **none** of them until a day is
     * selected. This is the assertion that fails if the "nearest upcoming" fallback comes back.
     */
    @Test
    fun anUnselectedMonthNamesNothing() {
        val muharram = month(1)

        assertTrue(
            "Muharram must hold observances for this test to mean anything",
            muharram.days.any { it.isCurrentMonth && it.hasEvent },
        )
        assertNull(footerEventName(muharram, selection = null, WidgetLanguage.ENGLISH))
    }

    /**
     * A selection left behind by paging away is treated as no selection.
     *
     * The viewed month is persisted per widget, so this is the ordinary case after a month step, not an
     * edge one: the footer must describe the month on screen, exactly as `isSelectedCell` does for the
     * cells.
     */
    @Test
    fun aSelectionInAnotherMonthNamesNothing() {
        val muharram = month(1)
        val safarSelection = HijriDaySelection(1447, 2, 14)

        assertNull(footerEventName(muharram, safarSelection, WidgetLanguage.ENGLISH))
    }

    /** A selected day whose cell carries no mark names nothing, rather than borrowing a neighbour's. */
    @Test
    fun aSelectedDayWithoutAMarkNamesNothing() {
        val muharram = month(1)
        val ordinaryDay = inMonthDays(muharram).first { !it.hasEvent }

        assertNull(
            footerEventName(
                muharram,
                selectionFor(muharram, ordinaryDay.hijriDay),
                WidgetLanguage.ENGLISH,
            ),
        )
    }

    // ── a selected day: its own mark ─────────────────────────────────────

    /** Selecting Ashura names Ashura — and not the month's first event, which is what a fallback did. */
    @Test
    fun theSelectedObservanceIsNamed() {
        val muharram = month(1)

        assertEquals(
            "Ashura",
            footerEventName(muharram, selectionFor(muharram, 10), WidgetLanguage.ENGLISH),
        )
        assertEquals(
            "the month's first observance must not answer for a different day",
            "Islamic New Year",
            footerEventName(muharram, selectionFor(muharram, 1), WidgetLanguage.ENGLISH),
        )
    }

    /**
     * The name follows the **widget's** language, not the device's.
     *
     * One Urdu widget is designed to sit beside an English one on a phone with no Urdu locale, and an
     * English footer under an Urdu grid is the most obvious possible failure of that.
     */
    @Test
    fun theNameFollowsTheWidgetsLanguage() {
        val muharram = month(1)
        val selection = selectionFor(muharram, 10)

        val urdu = assertNotNull(footerEventName(muharram, selection, WidgetLanguage.URDU))
        val english = assertNotNull(footerEventName(muharram, selection, WidgetLanguage.ENGLISH))

        assertTrue("the Urdu name should be in the Urdu script, got '$urdu'", urdu.any { it.code > 0x7F })
        assertEquals("Ashura", english)
    }

    /**
     * The footer's name follows the **event language**, not the widget's own language.
     *
     * The option exists precisely so a widget can name an observance in English under an Urdu grid, or
     * the reverse. This asserts that the value the caller resolves — `options.effectiveEventLanguage`
     * — is what reaches the name: that link is the one part of the chain a unit test can reach, because
     * the composable that passes it is not callable off-device.
     */
    @Test
    fun anIndependentEventLanguageOverridesTheWidgetsLanguage() {
        val muharram = month(1)
        val selection = selectionFor(muharram, 10)
        val urduGrid = WidgetOptions(language = WidgetLanguage.URDU)

        assertEquals(
            "an Urdu widget with no event-language option names the event in Urdu",
            HijriEvents.all.first { it.key == "ashura" }.name(HijriEventLanguage.URDU),
            footerEventName(muharram, selection, urduGrid.effectiveEventLanguage),
        )

        assertEquals(
            "the same Urdu widget with the event language set to English names it in English",
            "Ashura",
            footerEventName(
                muharram,
                selection,
                urduGrid.copy(eventLanguage = WidgetLanguage.ENGLISH).effectiveEventLanguage,
            ),
        )
    }

    /**
     * A **Pakistan-mode** selection names the national day — the one mark the Hijri table is silent on.
     *
     * Safar 1447 straddles 14 August, so the grid fills a cell no `HijriEvents` lookup can account for.
     * A footer that resolved only through the Hijri table would leave that cell unnamed, which is why
     * the projection carries an `eventKey` rather than a `(month, day)` the renderer re-derives.
     */
    @Test
    fun aNationalDayIsNamedWhenItIsSelected() {
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
            footerEventName(
                safar,
                selectionFor(safar, independenceDay.hijriDay),
                WidgetLanguage.ENGLISH,
            ),
        )
        assertEquals(
            "and the name follows the widget's language, not the device's",
            PakistanEvents.all.first { it.key == "independence_day" }
                .name(HijriEventLanguage.URDU),
            footerEventName(
                safar,
                selectionFor(safar, independenceDay.hijriDay),
                WidgetLanguage.URDU,
            ),
        )
    }

    /**
     * Every national day is named when its own cell is selected — and none of them leaks into
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
            val pakistanCell = inMonthDays(pakistanMonth)
                .first { it.gregorianEpochDay == date.toEpochDay() }

            assertEquals(
                "${nationalDay.key} on $date",
                nationalDay.name(HijriEventLanguage.ENGLISH),
                footerEventName(
                    pakistanMonth,
                    selectionFor(pakistanMonth, pakistanCell.hijriDay),
                    WidgetLanguage.ENGLISH,
                ),
            )

            val calculationMonth = assertNotNull(
                calculationMonths.firstOrNull { data ->
                    inMonthDays(data).any { it.gregorianEpochDay == date.toEpochDay() }
                },
                "no Calculation-mode projection contains $date",
            )
            val calculationCell = inMonthDays(calculationMonth)
                .first { it.gregorianEpochDay == date.toEpochDay() }

            assertTrue(
                "a Calculation-mode grid must not name ${nationalDay.key}; got " +
                    "'${footerEventName(
                        calculationMonth,
                        selectionFor(calculationMonth, calculationCell.hijriDay),
                        WidgetLanguage.ENGLISH,
                    )}'",
                footerEventName(
                    calculationMonth,
                    selectionFor(calculationMonth, calculationCell.hijriDay),
                    WidgetLanguage.ENGLISH,
                ) != nationalDay.name(HijriEventLanguage.ENGLISH),
            )
        }
    }

    /**
     * The named observance is one the grid actually filled.
     *
     * The footer and the cells read the same projection field, so this asserts the wiring rather than
     * the arithmetic — but it is the assertion that fails if the name is ever re-derived from day
     * numbers while the grid keeps using `hasEvent`, which is the divergence this design prevents.
     */
    @Test
    fun theNamedObservanceIsAlwaysAFilledCell() {
        for (hijriMonth in 1..12) {
            val monthData = month(hijriMonth)
            for (cell in inMonthDays(monthData)) {
                val named = footerEventName(
                    monthData,
                    selectionFor(monthData, cell.hijriDay),
                    WidgetLanguage.ENGLISH,
                )
                if (named == null) {
                    assertTrue(
                        "month $hijriMonth day ${cell.hijriDay} is not filled but was not named",
                        !cell.hasEvent,
                    )
                    continue
                }
                assertTrue("month $hijriMonth day ${cell.hijriDay} was named but not filled", cell.hasEvent)
                assertEquals(
                    "month $hijriMonth day ${cell.hijriDay}",
                    eventForKey(cell.eventKey)?.name(HijriEventLanguage.ENGLISH),
                    named,
                )
            }
        }
    }

    /** A day number the projection does not have (the 31st of a 30-day month) names nothing. */
    @Test
    fun aDayNumberTheMonthDoesNotHoldNamesNothing() {
        val muharram = month(1)
        val lastDay = inMonthDays(muharram).maxOf { it.hijriDay }

        assertNull(
            footerEventName(
                muharram,
                HijriDaySelection(1447, 1, lastDay + 1),
                WidgetLanguage.ENGLISH,
            ),
        )
    }

    // ── the whole line: the app's three-way answer ────────────────────────

    /**
     * The resting state **asks for a tap** when the month holds something to find.
     *
     * This is the reported defect: every month with filled cells in the grid directly above it said
     * "No event", which is simply false — and the app's own event bar asks the question instead. The
     * hint is only honest because the same projection says whether there is anything to tap for, which
     * is the next assertion.
     */
    @Test
    fun aMonthWithEventsAsksForATapUntilOneIsSelected() {
        val muharram = month(1)
        assertTrue(
            "Muharram must hold observances for this test to mean anything",
            monthHasAnyEvent(muharram),
        )

        assertEquals(
            WidgetLocalization.ChromeLabels.eventBannerHint(WidgetLanguage.ENGLISH),
            footerLine(muharram, selection = null, WidgetLanguage.ENGLISH, WidgetLanguage.ENGLISH),
        )
        assertEquals(
            "the hint is chrome, so it follows the widget's own language",
            WidgetLocalization.ChromeLabels.eventBannerHint(WidgetLanguage.URDU),
            footerLine(muharram, selection = null, WidgetLanguage.ENGLISH, WidgetLanguage.URDU),
        )
        assertEquals(
            "the English label is what the hint says",
            "Tap a day to see its event",
            WidgetLocalization.ChromeLabels.eventBannerHint(WidgetLanguage.ENGLISH),
        )
    }

    /**
     * A month holding **nothing** says so, rather than asking for a tap that can only disappoint.
     *
     * The two "nothing" sentences are different claims and the month one is the weaker: there is no day
     * in it whose cell carries a mark, so the resting line describes the month.
     */
    @Test
    fun aMonthWithoutEventsSaysThereIsNoEvent() {
        val quietMonth = (1..12)
            .map { month(it) }
            .firstOrNull { !monthHasAnyEvent(it) }

        assertNotNull(
            quietMonth,
            "no eventless Hijri month in 1447, so this assertion cannot be made",
        )
        assertEquals(
            WidgetLocalization.ChromeLabels.noEvent(WidgetLanguage.ENGLISH),
            footerLine(quietMonth, selection = null, WidgetLanguage.ENGLISH, WidgetLanguage.ENGLISH),
        )
        assertEquals(
            "the line is chrome, so it follows the widget's own language",
            WidgetLocalization.ChromeLabels.noEvent(WidgetLanguage.URDU),
            footerLine(quietMonth, selection = null, WidgetLanguage.ENGLISH, WidgetLanguage.URDU),
        )
    }

    /**
     * A tapped day that carries nothing answers **the question the user asked** — "no event on this
     * day", not the month's "no event".
     *
     * The distinction is the reason the footer needs three sentences: the month plainly has filled cells,
     * so saying "No event" over it is the confusion the report described.
     */
    @Test
    fun aTappedOrdinaryDaySaysNoEventOnThisDay() {
        val muharram = month(1)
        val ordinaryDay = inMonthDays(muharram).first { !it.hasEvent }

        assertEquals(
            WidgetLocalization.ChromeLabels.noEventOnDay(WidgetLanguage.ENGLISH),
            footerLine(
                muharram,
                selectionFor(muharram, ordinaryDay.hijriDay),
                WidgetLanguage.ENGLISH,
                WidgetLanguage.ENGLISH,
            ),
        )
        assertEquals(
            "No event on this day",
            WidgetLocalization.ChromeLabels.noEventOnDay(WidgetLanguage.ENGLISH),
        )
    }

    /** A tapped day that carries a mark names it — the case the footer already had, unchanged. */
    @Test
    fun aTappedObservanceIsStillNamed() {
        val muharram = month(1)

        assertEquals(
            "Ashura",
            footerLine(
                muharram,
                selectionFor(muharram, 10),
                WidgetLanguage.ENGLISH,
                WidgetLanguage.ENGLISH,
            ),
        )
    }

    /**
     * A selection left in another month falls back to the month's resting sentence, not to a day answer.
     *
     * The viewed month is persisted per widget, so paging away with a day still selected is ordinary.
     * The footer must describe the month on screen: the tap it asks for is about *this* grid.
     */
    @Test
    fun aSelectionInAnotherMonthFallsBackToTheMonthsLine() {
        val muharram = month(1)
        val safarSelection = HijriDaySelection(1447, 2, 14)

        assertEquals(
            WidgetLocalization.ChromeLabels.eventBannerHint(WidgetLanguage.ENGLISH),
            footerLine(muharram, safarSelection, WidgetLanguage.ENGLISH, WidgetLanguage.ENGLISH),
        )
    }
}
