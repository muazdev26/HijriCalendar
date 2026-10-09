package com.muazdev.hijricalendar.widgetdata

import com.muazdev.hijricalendar.core.HijriMonthLengths
import com.muazdev.hijricalendar.core.HijriMonthOverrides
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * WD-01: the projection must render against the month-length table its caller names, not a
 * process-wide mutable it happens to inherit.
 *
 * Before this, none of the four builders forwarded `overrides`, so `toCalendarMonth`,
 * `resolveGregorianMonthRange` and `PakistanHijriCalendar.gregorianToHijri` all silently read
 * `HijriMonthOverrides.current`. A host app that built a scoped table got an in-app calendar that
 * honoured it and a home-screen widget that did not — and the iOS WidgetKit extension, being a
 * separate process with its own pristine copy of the global, could not be configured at all.
 */
class MonthLengthOverridesTest {

    /** A 29/30 pair no Umm al-Qura test in this module relies on; 1448-3 is forced to 30 below. */
    private val scoped = HijriMonthLengths().apply { setMonthLength(1448, 3, 30) }

    private fun projection(
        year: Int,
        month: Int,
        overrides: HijriMonthLengths,
    ) = assertNotNull(
        buildHijriMonthWidgetData(
            hijriYear = year,
            hijriMonth = month,
            adjustmentDays = 0,
            overrides = overrides,
        ),
    )

    /**
     * The epoch days the projection painted as belonging to this month, ascending.
     *
     * Sorted because the default Urdu options render RTL, which reverses each week's cells — the
     * day *set* is what an override changes, and comparing the ordered list would conflate that
     * with a reading-direction change.
     */
    private fun HijriMonthWidgetData.currentMonthDays(): List<Long> =
        days.filter { it.isCurrentMonth }.map { it.gregorianEpochDay }.sorted()

    // ── the projection reads the table it is given ──────────────────────────

    @Test
    fun aScopedTableAddsTheForcedDayToThatMonth() {
        // Umm al-Qura has 1448-3 as a 29-day month, so forcing it to 30 is visible twice over: the
        // forced month paints one more cell, and every later month starts a day later. Both are
        // the in-app calendar's behaviour — the widget grid is supposed to *be* that grid.
        val forced = projection(1448, 3, scoped)
        val plain = projection(1448, 3, HijriMonthLengths())

        assertEquals(29, plain.currentMonthDays().size)
        assertEquals(30, forced.currentMonthDays().size)

        val forcedNext = projection(1448, 4, scoped)
        val plainNext = projection(1448, 4, HijriMonthLengths())
        assertEquals(
            plainNext.currentMonthDays().first() + 1,
            forcedNext.currentMonthDays().first(),
            "a forced 30-day month re-anchors the next one",
        )
        assertEquals(
            plainNext.currentMonthDays().size,
            forcedNext.currentMonthDays().size,
            "1448-4's own length is untouched; only its start moves",
        )
    }

    @Test
    fun todayProjectionHonoursAScopedTableInPakistanMode() {
        // Pakistan mode is where the override has the most leverage: `gregorianToHijri` walks the
        // month table, so a forced length moves which Hijri date an anchor day reports. Pick a
        // month the official fix table does not pin (a fixed month keeps its own length by design
        // and so cannot show the difference).
        val (year, month) = (1401 until 1500)
            .flatMap { y -> (1..12).map { y to it } }
            .first { (y, m) ->
                val tableLength = com.muazdev.hijricalendar.core.PakistanHijriCalendar
                    .defaultLengthOfMonth(y, m)
                val forced = if (tableLength == 29) 30 else 29
                val a = HijriMonthLengths()
                val b = HijriMonthLengths().apply { setMonthLength(y, m, forced) }
                com.muazdev.hijricalendar.core.PakistanHijriCalendar.lengthOfMonth(y, m, a) !=
                    com.muazdev.hijricalendar.core.PakistanHijriCalendar.lengthOfMonth(y, m, b)
            }

        // An anchor inside the forced month, found by asking the table where the month starts.
        val start = com.muazdev.hijricalendar.core.PakistanHijriCalendar
            .hijriToGregorian(year, month, 1, scoped)
        val anchor = start.toEpochDays()

        val forcedDay = assertNotNull(
            todayHijriWidgetData(anchor, adjustmentDays = 0, pakistan = true, overrides = scoped),
        )
        assertEquals(month, forcedDay.hijriMonth)
        assertEquals(year, forcedDay.hijriYear)
    }

    // ── options own the table, so the stored schema is what closes the iOS gap ──

    @Test
    fun emptyOverridesDelegateToTheProcessWideTable() {
        val options = WidgetOptions.DEFAULTS
        assertSame(
            HijriMonthOverrides.current,
            options.overridesTable(),
            "no widget-level overrides must mean the process default, not an empty table",
        )
        assertTrue(options.monthLengthOverrides.isEmpty())
    }

    @Test
    fun optionsCarryTheirOwnTableAndTheProjectionUsesIt() {
        val options = WidgetOptions(
            source = WidgetSource.CALCULATION,
            monthLengthOverrides = mapOf(monthLengthKey(1448, 3) to 30),
        )

        assertEquals(30, options.overridesTable().monthLength(1448, 3))
        assertEquals(
            projection(1448, 3, scoped).currentMonthDays(),
            assertNotNull(
                buildHijriMonthWidgetData(hijriYear = 1448, hijriMonth = 3, options = options),
            ).currentMonthDays(),
            "the options-taking overload must thread the options' own table",
        )
    }

    @Test
    fun aScopedTableReplacesRatherThanMergesWithTheProcessGlobal() {
        // A month the global knows about but this widget's map does not must fall back to the
        // calculation, not silently inherit the global's entry: half-merging two tables is how a
        // widget ends up describing a month neither the app nor the user configured.
        HijriMonthOverrides.setMonthLength(1448, 5, 30)
        try {
            val widgetScoped = WidgetOptions(
                monthLengthOverrides = mapOf(monthLengthKey(1448, 3) to 30),
            ).overridesTable()
            assertEquals(null, widgetScoped.monthLength(1448, 5))
            assertEquals(30, HijriMonthOverrides.current.monthLength(1448, 5))
        } finally {
            HijriMonthOverrides.clearAll()
        }
    }

    // ── encodings ───────────────────────────────────────────────────────────

    @Test
    fun jsonRoundTripsTheOverrideMap() {
        val options = WidgetOptions(
            adjustmentDays = 1,
            monthLengthOverrides = mapOf(
                monthLengthKey(1448, 3) to 30,
                monthLengthKey(1447, 12) to 29,
            ),
        )
        val decoded = assertNotNull(WidgetOptionsJson.decodeOrNull(WidgetOptionsJson.encode(options)))
        assertEquals(options, decoded)
        assertEquals(30, decoded.overridesTable().monthLength(1448, 3))
    }

    @Test
    fun optionsWithoutAnOverrideMapDecodeToNone() {
        val decoded = assertNotNull(
            WidgetOptionsJson.decodeOrNull("""{"adjustmentDays":3,"language":"ENGLISH"}"""),
        )
        assertTrue(decoded.monthLengthOverrides.isEmpty())
        assertSame(HijriMonthOverrides.current, decoded.overridesTable())
    }

    @Test
    fun csvRoundTripsAndIsTheFormTheAndroidSaverCarries() {
        val map = mapOf(monthLengthKey(1448, 3) to 30, monthLengthKey(1447, 12) to 29)
        assertEquals(map, decodeMonthLengthsCsv(encodeMonthLengthsCsv(map)))
        assertEquals(emptyMap(), decodeMonthLengthsCsv(null))
        assertEquals(emptyMap(), decodeMonthLengthsCsv(""))
        assertEquals(emptyMap(), decodeMonthLengthsCsv("   "))
    }

    @Test
    fun createWidgetOptionsTakesTheCsvSoANativeScreenCanPassOne() {
        val options = createWidgetOptions(overridesCsv = "1448-3:30,1447-12:29")
        assertEquals(30, options.monthLengthOverrides[monthLengthKey(1448, 3)])
        assertEquals(29, options.monthLengthOverrides[monthLengthKey(1447, 12)])
        assertEquals(emptyMap(), createWidgetOptions().monthLengthOverrides)
    }

    @Test
    fun malformedOverridesAreDroppedRatherThanThrown() {
        // The stored JSON is user-reachable (a shared app group on iOS), and `HijriMonthLengths`
        // requires 29/30 — so the decode path has to fence, or a hand-edited store turns into a
        // crash inside a Glance composition.
        val table = monthLengthsFrom(
            mapOf(
                "1448-3" to 30,
                "1448-13" to 29, // no such month
                "not-a-key" to 29,
                "" to 30,
                "-" to 29,
                "1448-" to 30,
                "1449-2" to 31, // no such length
                "1449-3" to 0,
            ),
        )
        assertEquals(
            mapOf(HijriYearMonth(1448, 3) to 30),
            table.all().mapKeys { HijriYearMonth(it.key.first, it.key.second) },
        )
        assertEquals(30, table.monthLength(1448, 3))
        assertEquals(null, table.monthLength(1449, 2))
    }

    @Test
    fun monthLengthsToMapIsTheInverseOfTheStoredForm() {
        // A fresh table rather than `scoped`: mutating the shared instance would leak into every
        // other test in this class.
        val two = HijriMonthLengths().apply {
            setMonthLength(1448, 3, 30)
            setMonthLength(1447, 12, 29)
        }
        assertEquals(mapOf("1448-3" to 30, "1447-12" to 29), monthLengthsToMap(two))
        assertEquals(emptyMap(), monthLengthsToMap(HijriMonthLengths()))
        assertEquals(two.all(), monthLengthsFrom(monthLengthsToMap(two)).all())
    }
}
