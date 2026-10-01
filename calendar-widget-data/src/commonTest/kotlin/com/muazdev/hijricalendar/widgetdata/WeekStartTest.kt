package com.muazdev.hijricalendar.widgetdata

import com.muazdev.hijricalendar.core.CalendarNames
import com.muazdev.hijricalendar.core.WeekDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * WD-05: the first day of week was persisted as a bare ordinal into [WeekDay] — another module's
 * enum, in a published ABI — while every other enum in the shared schema was stored by name.
 *
 * `WeekDay`'s own KDoc says its declaration order is persisted and that inserting an entry would
 * silently reinterpret every stored value. That is exactly the hazard `WidgetOptionsJson` exists to
 * remove, so a future `WeekDay` entry would have re-aligned every placed widget's header row with no
 * compile error, no test failure and no log line.
 */
class WeekStartTest {

    /**
     * The mapping the derived index accessor depends on. If this fails, every stored widget that
     * still carries the legacy field changes meaning — which is precisely what this ticket exists to
     * make impossible going forward, and what makes the legacy read worth keeping correct.
     */
    @Test
    fun weekStartOrderMatchesWeekDayOrder() {
        for (index in WeekDay.entries.indices) {
            assertEquals(
                index,
                WeekStart.fromIndex(index).dayOfWeek.index,
                "WeekStart entry $index must map to WeekDay index $index",
            )
            assertEquals(WeekStart.fromIndex(index), WeekStart.of(WeekDay.entries[index]))
        }
        assertEquals(WeekStart.entries.size, WeekDay.entries.size)
    }

    @Test
    fun defaultTracksTheCoreDefault() {
        // `WidgetOptions.DEFAULTS` hardcodes a value; if `WeekDay.DEFAULT_FIRST_DAY` ever moves, the
        // fresh-widget default must move with it rather than silently disagreeing.
        assertEquals(WeekDay.DEFAULT_FIRST_DAY, WeekStart.DEFAULT.dayOfWeek)
        assertEquals(
            WeekDay.DEFAULT_FIRST_DAY.index,
            WidgetOptions.DEFAULTS.effectiveWeekStart.dayOfWeek.index,
        )
    }

    @Test
    fun fromIndexClampsOutOfRangeRatherThanThrowing() {
        assertEquals(WeekStart.DEFAULT, WeekStart.fromIndex(-1))
        assertEquals(WeekStart.DEFAULT, WeekStart.fromIndex(7))
        assertEquals(WeekStart.DEFAULT, WeekStart.fromIndex(Int.MAX_VALUE))
    }

    // ── the migration ────────────────────────────────────────────────────────

    @Test
    fun aStoredLegacyIndexStillMeansTheSameDay() {
        val stored = assertNotNull(
            WidgetOptionsJson.decodeOrNull("""{"firstDayOfWeekIndex":6}"""),
        )
        assertEquals(WeekStart.FRIDAY, stored.effectiveWeekStart)
        assertEquals(6, stored.firstDayOfWeekIndexValue)
    }

    @Test
    fun aCurrentBlobIsReadFromTheNamedField() {
        val stored = assertNotNull(WidgetOptionsJson.decodeOrNull("""{"weekStart":"MONDAY"}"""))
        assertEquals(WeekStart.MONDAY, stored.effectiveWeekStart)
        assertEquals(WeekDay.MONDAY.index, stored.firstDayOfWeekIndexValue)
    }

    @Test
    fun aLegacyIndexWinsOverADefaultNamedField() {
        // The shape a pre-`WeekStart` blob decodes to: no `weekStart`, so the field default, plus the
        // index the user actually chose. The index must win, or every placed widget resets.
        val stored = assertNotNull(
            WidgetOptionsJson.decodeOrNull(
                """{"adjustmentDays":-2,"firstDayOfWeekIndex":3,"language":"ENGLISH"}""",
            ),
        )
        assertEquals(WeekStart.TUESDAY, stored.effectiveWeekStart)
        assertEquals(-2, stored.adjustmentDays, "the other fields must survive alongside it")
    }

    @Test
    fun theDefaultIndexDoesNotOverrideAnExplicitlyChosenDefaultDay() {
        // A current write leaves the legacy field at the default, so choosing the default day
        // explicitly must not be read as "no choice".
        val stored = assertNotNull(
            WidgetOptionsJson.decodeOrNull("""{"weekStart":"SATURDAY"}"""),
        )
        assertEquals(WeekStart.SATURDAY, stored.effectiveWeekStart)
    }

    @Test
    fun anOutOfRangeStoredIndexDegradesToTheDefault() {
        val stored = assertNotNull(
            WidgetOptionsJson.decodeOrNull("""{"firstDayOfWeekIndex":99}"""),
        )
        assertEquals(WeekStart.DEFAULT, stored.effectiveWeekStart)
    }

    @Test
    fun theLegacyIndexIsNeverWrittenBack() {
        // The migration is read-only. A new write must emit `weekStart` and must not resurrect the
        // ordinal — which is what a field-based approach did, because `@EncodeDefault(NEVER)` does
        // not suppress a field whose default is a non-constant expression.
        val encoded = WidgetOptionsJson.encode(WidgetOptions(weekStart = WeekStart.FRIDAY))
        assertTrue("\"weekStart\":\"FRIDAY\"" in encoded, "expected the named field, got: $encoded")
        assertTrue(
            "firstDayOfWeekIndex" !in encoded,
            "the legacy ordinal must not be written again, got: $encoded",
        )
    }

    @Test
    fun reSavingALegacyBlobUpgradesIt() {
        val legacy = assertNotNull(
            WidgetOptionsJson.decodeOrNull("""{"firstDayOfWeekIndex":6,"language":"ENGLISH"}"""),
        )
        val upgraded = WidgetOptionsJson.decodeOrNull(WidgetOptionsJson.encode(legacy))
        assertEquals(WeekStart.FRIDAY, assertNotNull(upgraded).effectiveWeekStart)
        assertTrue(
            "\"weekStart\"" in WidgetOptionsJson.encode(legacy),
            "the rewritten blob must carry the named field",
        )
    }

    @Test
    fun theProjectionHonoursTheStoredWeekStart() {
        val friday = assertNotNull(
            buildHijriMonthWidgetData(
                hijriYear = 1448,
                hijriMonth = 3,
                adjustmentDays = 0,
                weekStart = WeekStart.FRIDAY,
            ),
        )
        val saturday = assertNotNull(
            buildHijriMonthWidgetData(
                hijriYear = 1448,
                hijriMonth = 3,
                adjustmentDays = 0,
                weekStart = WeekStart.SATURDAY,
            ),
        )
        // Friday-first rotates the header row by five: Fri, Sat, Sun, Mon, Tue, Wed, Thu.
        // Friday-first rotates the header row by six: Fri, Sat, Sun, Mon, Tue, Wed, Thu.
        assertEquals(
            CalendarNames.englishWeekdayShortNames.drop(6) + CalendarNames.englishWeekdayShortNames.take(6),
            friday.weekdayHeaders,
        )
        assertEquals(CalendarNames.englishWeekdayShortNames, saturday.weekdayHeaders)
    }
}
