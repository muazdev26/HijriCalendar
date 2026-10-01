package com.muazdev.hijricalendar.widgetdata

import com.muazdev.hijricalendar.core.CalendarNames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * WD-04: the projection is defensive almost everywhere and then indexed four caller-supplied
 * localization lists without a bound.
 *
 * `buildHijriMonthWidgetData`'s documented failure mode is `null`, never a throw — and its callers
 * are a Glance composition on a session worker and a WidgetKit timeline callback, where a thrown
 * `IndexOutOfBoundsException` is not recoverable and surfaces to a user only as a blank widget. Four
 * sites read `names[month.ordinal]` on a list the caller supplies, guarded by a KDoc sentence
 * ("must be 12 / 12 / 7 entries") that nothing checked.
 *
 * The interesting assertion here is not that a short list no longer throws. It is that it no longer
 * produces a *partly* localized result either: per-entry `getOrNull` degrades to a header row with
 * some names in one script and the rest in another, which a reader cannot diagnose and which has no
 * error to notice it by.
 */
class ShortLocalizationListTest {

    private val english = CalendarNames.englishGregorianMonths
    private val englishWeekdays = CalendarNames.englishWeekdayShortNames
    private val englishHijriMonths = CalendarNames.englishHijriMonths

    private fun grid(
        localizedHijriMonthNames: List<String>? = null,
        localizedGregorianMonthNames: List<String>? = null,
        localizedWeekdayNames: List<String>? = null,
    ) = assertNotNull(
        buildHijriMonthWidgetData(
            hijriYear = 1448,
            hijriMonth = 3,
            adjustmentDays = 0,
            localizedHijriMonthNames = localizedHijriMonthNames,
            localizedGregorianMonthNames = localizedGregorianMonthNames,
            localizedWeekdayNames = localizedWeekdayNames,
        ),
    )

    /** A grid built with no localization at all — the reference every case below must equal. */
    private val reference = grid()

    // ── a short list must not throw ──────────────────────────────────────────

    @Test
    fun anElevenEntryGregorianMonthListIsIgnored() {
        // The one the ticket calls out: `names[month.ordinal]` on 11 entries, outside every `try`.
        val short = english.dropLast(1)
        val data = grid(localizedGregorianMonthNames = short)
        assertEquals(reference.gregorianMonthTitle, data.gregorianMonthTitle)
        assertEquals(reference.gregorianRange, data.gregorianRange)
    }

    @Test
    fun anEmptyListForEachParameterIsIgnored() {
        val data = grid(
            localizedHijriMonthNames = emptyList(),
            localizedGregorianMonthNames = emptyList(),
            localizedWeekdayNames = emptyList(),
        )
        assertEquals(reference.gregorianMonthTitle, data.gregorianMonthTitle)
        assertEquals(reference.gregorianRange, data.gregorianRange)
        assertEquals(reference.hijriMonthName, data.hijriMonthName)
        assertEquals(reference.weekdayHeaders, data.weekdayHeaders)
    }

    @Test
    fun aShortWeekdayListIsIgnored() {
        // 6 entries, not 7. The `?:` at this site used to localize index 0..5 and fall back on
        // index 6, which is precisely the mixed-script row described in the ticket's "Not covered".
        val data = grid(localizedWeekdayNames = englishWeekdays.dropLast(1))
        assertEquals(englishWeekdays.take(7), data.weekdayHeaders)
        assertTrue(
            data.weekdayHeaders.none { it !in englishWeekdays },
            "no header may come from a list that was rejected",
        )
    }

    @Test
    fun aShortHijriMonthListIsIgnored() {
        val data = grid(localizedHijriMonthNames = englishHijriMonths.dropLast(1))
        assertEquals(reference.hijriMonthName, data.hijriMonthName)
    }

    // ── all-or-nothing, not per-entry ────────────────────────────────────────

    @Test
    fun aSevenEntryWeekdayListYieldsOneScriptNotTwo() {
        val seven = listOf("1", "2", "3", "4", "5", "6", "7")
        val data = grid(localizedWeekdayNames = seven)
        assertEquals(seven, data.weekdayHeaders)
    }

    @Test
    fun aTwelveEntryHijriListIsUsedInFull() {
        val twelve = List(12) { "M$it" }
        val data = grid(localizedHijriMonthNames = twelve)
        assertEquals("M2", data.hijriMonthName, "the supplied list must be honoured when it fits")
    }

    @Test
    fun anOverlongListIsHonouredAndOnlyItsFirstEntriesUsed() {
        // A caller who appends to the built-in list rather than replacing it should still work.
        val padded = english + listOf("spare")
        val data = grid(localizedGregorianMonthNames = padded)
        assertEquals(reference.gregorianMonthTitle, data.gregorianMonthTitle)
        assertEquals(reference.gregorianRange, data.gregorianRange)
    }

    // ── the today card must agree with the grid about a short list ───────────

    @Test
    fun theTodayCardAlsoIgnoresAShortList() {
        val plain = assertNotNull(todayHijriWidgetData(anchorEpochDay = 20_731L, adjustmentDays = 0))
        val withShortLists = assertNotNull(
            todayHijriWidgetData(
                anchorEpochDay = 20_731L,
                adjustmentDays = 0,
                localizedHijriMonthNames = englishHijriMonths.dropLast(1),
                localizedGregorianMonthNames = english.dropLast(1),
                localizedWeekdayNames = englishWeekdays.dropLast(1),
            ),
        )
        assertEquals(plain, withShortLists)
    }

    @Test
    fun theTodayCardStillUsesAFullLocalizedList() {
        val data = assertNotNull(
            todayHijriWidgetData(
                anchorEpochDay = 20_731L,
                adjustmentDays = 0,
                localizedHijriMonthNames = WidgetLocalization.urduHijriMonthNames,
                localizedGregorianMonthNames = WidgetLocalization.urduGregorianMonthNames,
                localizedWeekdayNames = WidgetLocalization.urduWeekdayNames,
            ),
        )
        assertTrue(data.gregorianMonthName in WidgetLocalization.urduGregorianMonthNames)
    }
}
