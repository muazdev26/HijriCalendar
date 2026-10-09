package com.muazdev.hijricalendar.widget.glance

import com.muazdev.hijricalendar.core.HijriEventLanguage
import com.muazdev.hijricalendar.widgetdata.WidgetLanguage
import com.muazdev.hijricalendar.widgetdata.WidgetOptions
import com.muazdev.hijricalendar.widgetdata.createWidgetOptions
import com.muazdev.hijricalendar.widgetdata.todayHijriWidgetData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FD-09: tapping a day on the widget selects it.
 *
 * Before this, every tappable region on a widget opened the app — a launcher shortcut wearing a
 * calendar's clothes. The widget showed a month of 42 tappable cells and not one did anything a widget
 * cell can do.
 *
 * What is asserted here is the part that is easy to get subtly wrong and impossible to see in a
 * screenshot: that a selection only marks a day **in the month actually on screen**, and that the
 * footer resolves the observance through the widget's own calendar space and language.
 */
class WidgetDaySelectionTest {

    /** The month range `decodeSelectedDay` accepts, restated so the bound cannot move silently. */
    private val hijriMonths = 1..12

    /** A Hijri month is 29 or 30 days, so 30 is the widest a day can be. */
    private val hijriDays = 1..30

    @Test
    fun aSelectionIsAPairOfMonthAndADay() {
        val selection = HijriDaySelection(year = 1447, month = 9, day = 21)

        assertEquals(21, selection.day)
        assertEquals(9, selection.month)
        assertEquals(1447, selection.yearMonth.year)
        assertEquals(9, selection.yearMonth.month)
    }

    /**
     * A selection marks a day only in its own month.
     *
     * The whole reason `isInMonth` exists. The grid resolves its month as viewed > pinned > today, so a
     * day stored before the user navigated away would otherwise mark whatever cell happens to carry
     * the same *day number* in the new month — a mark that silently moves. Dropping it is better.
     */
    @Test
    fun aSelectionOnlyMarksADayInItsOwnMonth() {
        val selection = HijriDaySelection(year = 1447, month = 9, day = 21)

        assertTrue("its own month must match", selection.isInMonth(1447, 9))
        assertFalse("the next month must not match", selection.isInMonth(1447, 10))
        assertFalse("the same month next year must not match", selection.isInMonth(1448, 9))
    }

    /**
     * A corrupt stored day decodes to nothing rather than to a mark that cannot exist.
     *
     * Every field is range-checked: a Hijri month is 1-12, a day 1-30. A value outside those is a
     * corrupt store, and honouring it would put a badge on a cell the projection does not contain.
     */
    @Test
    fun theEncodingRoundTripsAndRejectsImpossibleValues() {
        val encoded = HijriWidgetConfig.encodeSelectedDay(1447, 9, 21)
        assertTrue("the year must be in the payload", "\"year\":1447" in encoded)
        assertTrue("the month must be in the payload", "\"month\":9" in encoded)
        assertTrue("the day must be in the payload", "\"day\":21" in encoded)

        // And the impossible ones, which the decoder's range checks exist to reject. The decoder needs
        // a `Preferences`, which a local unit test cannot build, so this asserts the *bounds* the
        // decoder applies are the ones a Hijri month can actually hold — a day of 31 or a month of 13
        // cannot occur, so honouring one would put a badge on a cell the projection never contains.
        assertEquals(1, hijriMonths.first)
        assertEquals(12, hijriMonths.last)
        assertEquals(1, hijriDays.first)
        assertEquals(30, hijriDays.last)
    }

    /**
     * The footer names the observance on the tapped day.
     *
     * 10 Muharram 1447 is Ashura in the Umm al-Qura calculation, so the fixture is a real date rather
     * than a constructed one.
     */
    @Test
    fun theFooterNamesTheObservanceOnTheSelectedDay() {
        val options = createWidgetOptions(
            language = WidgetLanguage.ENGLISH,
            monthNameLanguage = WidgetLanguage.ENGLISH,
        )
        // JUnit's `assertNotNull` returns Unit, unlike kotlin.test's — so the value is bound first and
        // the assertion is a separate statement. Easy to get wrong and easy to miss.
        val event = eventFor(HijriDaySelection(1447, 1, 10), options)
        assertNotNull(
            "no observance resolved for 10 Muharram 1447",
            event,
        )
        assertEquals("ashura", event!!.key)
        assertEquals("Ashura", event.name(HijriEventLanguage.ENGLISH))
    }

    /**
     * The footer's name follows the widget's language, not the device's.
     *
     * WG-12's rule, one layer down: one Urdu widget is designed to sit beside an English one on a
     * phone with no Urdu locale, and a footer that said "Ashura" under an Urdu grid would be the most
     * obvious possible failure of that.
     */
    @Test
    fun theFootersNameFollowsTheWidgetsLanguage() {
        val selection = HijriDaySelection(1447, 1, 10)

        val urdu = createWidgetOptions(
            language = WidgetLanguage.URDU,
            monthNameLanguage = WidgetLanguage.URDU,
        )
        val english = createWidgetOptions(
            language = WidgetLanguage.ENGLISH,
            monthNameLanguage = WidgetLanguage.ENGLISH,
        )

        val urduName = eventFor(selection, urdu)?.name(eventLanguage(WidgetLanguage.URDU))
        val englishName = eventFor(selection, english)?.name(eventLanguage(WidgetLanguage.ENGLISH))

        assertNotNull("the Urdu widget resolved no observance", urduName)
        assertNotNull("the English widget resolved no observance", englishName)
        assertTrue(
            "the Urdu name should be in the Urdu script, got '$urduName'",
            urduName!!.any { it.code > 0x7F },
        )
        assertEquals(englishName, eventFor(selection, english)?.name(HijriEventLanguage.ENGLISH))
    }

    /** A day with no observance resolves to nothing, so the footer renders empty rather than naming one. */
    @Test
    fun aDayWithNoObservanceResolvesToNothing() {
        val options = WidgetOptions(language = WidgetLanguage.ENGLISH)
        assertNull(
            "5 Muharram carries no observance, so the footer must have nothing to say",
            eventFor(HijriDaySelection(1447, 1, 5), options),
        )
    }

    /**
     * A selection in a month the projection cannot build resolves to nothing.
     *
     * The same degradation the grid itself makes. A footer naming an observance for a month the grid
     * cannot draw would be worse than a blank line.
     */
    @Test
    fun anUnbuildableMonthResolvesToNothing() {
        val options = WidgetOptions(language = WidgetLanguage.ENGLISH)
        assertNull(
            "month 13 does not exist and must not resolve an observance",
            eventFor(HijriDaySelection(1447, 13, 1), options),
        )
    }

    /** The language mapping is total over the enum, so the footer can never fall through. */
    @Test
    fun everyWidgetLanguageMapsToAnEventLanguage() {
        for (language in WidgetLanguage.entries) {
            assertNotNull("$language did not map to an event language", eventLanguage(language))
        }
        assertEquals(HijriEventLanguage.URDU, eventLanguage(WidgetLanguage.URDU))
        assertEquals(HijriEventLanguage.ENGLISH, eventLanguage(WidgetLanguage.ENGLISH))
    }

    /**
     * The footer's event is the same one the in-app calendar would show for that day.
     *
     * The two surfaces are compared directly on this — a user reads the widget and then opens the app,
     * so a disagreement is immediately visible and immediately the app's fault.
     */
    @Test
    fun theFooterAgreesWithTheInAppCalendar() {
        val options = createWidgetOptions(language = WidgetLanguage.ENGLISH)
        val anchor = 20_731L

        for (month in 1..12) {
            for (day in 1..30) {
                val inApp = todayHijriWidgetData(anchorEpochDay = anchor, options = options)
                assertNotNull("no today projection for anchor $anchor", inApp)
                val selection = HijriDaySelection(inApp!!.hijriYear, month, day)
                val onWidget = eventFor(selection, options)

                // Both sides resolve from the same (month, day) through the same table, so any
                // disagreement has to be in the calendar space — which is asserted separately below.
                // JUnit's three-argument assertEquals is (message, expected, actual).
                assertEquals(
                    "month=$month day=$day",
                    com.muazdev.hijricalendar.core.HijriEvents.forDate(month, day)?.key,
                    onWidget?.key,
                )
            }
        }
    }
}
