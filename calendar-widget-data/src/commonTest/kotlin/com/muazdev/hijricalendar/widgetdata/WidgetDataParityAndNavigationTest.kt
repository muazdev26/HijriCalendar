package com.muazdev.hijricalendar.widgetdata

import com.abdulrahman_b.hijrahdatetime.toHijrahDate
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import com.muazdev.hijricalendar.core.CalendarMonth
import com.muazdev.hijricalendar.core.HijriMonthLengths
import com.muazdev.hijricalendar.core.PakistanHijriCalendar
import com.muazdev.hijricalendar.core.UrduCalendarNames
import com.muazdev.hijricalendar.core.WeekDay
import com.muazdev.hijricalendar.core.toCalendarMonth
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the "shared projection supports the full app calendar" behaviours: Pakistan
 * source parity with the in-app grid, configurable weekend sets, a fully localized today
 * card and the range-safe month-offset helper.
 *
 * **Which override table these tests exercise.** Every case here except
 * [month_gridMatchesInAppCalendarCellForCell_underAScopedOverrideTable] runs against the
 * **process-wide default** (`HijriMonthOverrides.current`), because they omit the `overrides`
 * argument. That is the same trap UI-01 documents for `calendar-ui`: omitting an argument whose
 * default is the global tests the global and looks like it tests the parameter. The scoped case is
 * therefore a separate, explicitly-named test rather than a parameterised one — a reader can tell
 * from the name which promise each test is making.
 *
 * WD-01 was invisible to this file for exactly that reason: every test here used the global, so no
 * test could have failed while the widget read the global and the app read something else.
 */
class WidgetDataParityAndNavigationTest {

    private fun todayLocalEpochDay(): Long {
        val now = kotlin.time.Clock.System.now()
        return now.toLocalDateTime(TimeZone.currentSystemDefault()).date.toEpochDays()
    }

    private fun arabicIndic(value: Int): String =
        value.toString().map { "٠١٢٣٤٥٦٧٨٩"[it.code - '0'.code] }.joinToString("")

    // ── Pakistan flag: cell-for-cell parity with the in-app calendar ─────────

    /**
     * WD-11: the regression guard for WD-01, and the only test in the module that pins the
     * `overrides` parameter at all.
     *
     * WD-01 was that the projection read `HijriMonthOverrides.current` while the in-app calendar
     * could be driven from a scoped `HijriMonthLengths`, so two calendars configured differently
     * disagreed with no error anywhere. The existing parity test could not have caught it: it omits
     * the argument, so it compares global-with-global.
     *
     * This test therefore constructs a **scoped** table — deliberately different from the process
     * default — forces a month to a length the calculated table does not have, and asserts the two
     * builders agree cell for cell. It also asserts that the grid *changed*, because the failure
     * mode is a test that agrees because both sides ignored the table: two identical reads of the
     * global would pass a pure-equality assertion.
     *
     * Run in both source modes, because Pakistan folds overrides into `lengthOfMonth` while
     * non-Pakistan shifts the grid onto the observed calendar — the parameter is threaded through
     * two different code paths, and only the shared one is covered by the assertion.
     */
    @Test
    fun month_gridMatchesInAppCalendarCellForCell_underAScopedOverrideTable() {
        val year = 1447
        val month = 9
        // Force the month to the length it does *not* already have, rather than hard-coding "30".
        // The calculated length moves as the tables are corrected, and a hard-coded value that
        // quietly became a no-op would make every assertion below pass while testing nothing —
        // which is the same silent-pass failure this test exists to rule out.
        val calculated = assertNotNull(
            buildHijriMonthWidgetData(year, month, 0),
            "default grid $year-$month",
        ).days.count { it.isCurrentMonth }
        val forced = if (calculated == 29) 30 else 29
        val scoped = HijriMonthLengths(mapOf(year to month to forced))

        for (pakistan in listOf(false, true)) {
            val widgetWithTable = assertNotNull(
                buildHijriMonthWidgetData(year, month, 0, pakistan = pakistan, overrides = scoped),
                "widget grid $year-$month pakistan=$pakistan scoped",
            )
            val coreWithTable = assertNotNull(
                HijrahYearMonth(year, month).toCalendarMonth(pakistan = pakistan, overrides = scoped),
                "in-app grid $year-$month pakistan=$pakistan scoped",
            )
            val label = "$year-$month pakistan=$pakistan"

            assertEquals(
                coreWithTable.days.size,
                widgetWithTable.days.size,
                "grid length $label",
            )
            widgetWithTable.days.forEachIndexed { index, w ->
                val cell = coreWithTable.days[index]
                assertEquals(cell.dayOfMonth, w.hijriDay, "hijriDay cell $index of $label")
                assertEquals(
                    cell.localDate.toEpochDays(),
                    w.gregorianEpochDay,
                    "gregorianEpochDay cell $index of $label",
                )
            }

            // The scoped table must actually have been read, or the equality above is two reads of
            // the same global.
            //
            // Compared on `hijriDay`/`isCurrentMonth` rather than on the Gregorian epoch days, and
            // that is not a stylistic choice. In observed (non-Pakistan) mode a 42-cell window is
            // anchored to the month's *start*, so shortening the month by a day leaves every epoch
            // day in the list unchanged — the 30th is dropped and the next month's 1st slides into
            // the same slot, carrying the same date. The grid demonstrably changed (one fewer
            // `isCurrentMonth` cell) while every date stayed put. An assertion on epoch days alone
            // would have passed here without the override having been read at all, which is exactly
            // the silent pass this test is meant to rule out. Pakistan mode does shift the dates,
            // so a date-based probe would work there and fail to generalise — hence the projected
            // identity, which differs in both.
            val defaultGrid = assertNotNull(
                buildHijriMonthWidgetData(year, month, 0, pakistan = pakistan),
                "widget grid $year-$month pakistan=$pakistan default",
            )
            fun identity(grid: HijriMonthWidgetData) =
                grid.days.map { it.hijriDay to it.isCurrentMonth }

            assertNotEquals(
                identity(defaultGrid),
                identity(widgetWithTable),
                "the scoped override did not move the widget grid $label",
            )

            // And the in-app side must agree with the *default* widget, i.e. the two sides are
            // genuinely reading different tables rather than both ignoring the parameter.
            val coreDefault = assertNotNull(
                HijrahYearMonth(year, month).toCalendarMonth(pakistan = pakistan),
                "in-app grid $year-$month pakistan=$pakistan default",
            )
            coreDefault.days.forEachIndexed { index, cell ->
                assertEquals(
                    cell.localDate.toEpochDays(),
                    defaultGrid.days[index].gregorianEpochDay,
                    "default gregorianEpochDay cell $index of $label",
                )
            }
        }
    }

    @Test
    fun month_gridMatchesInAppCalendarCellForCell() {
        val todayEpoch = todayLocalEpochDay()
        for ((year, monthNumber) in listOf(1447 to 9, 1448 to 3, 1448 to 4)) {
            for (pakistan in listOf(false, true)) {
                val widget = assertNotNull(
                    buildHijriMonthWidgetData(year, monthNumber, 0, pakistan = pakistan),
                    "widget grid $year-$monthNumber pakistan=$pakistan",
                )
                val core = HijrahYearMonth(year, monthNumber).toCalendarMonth(pakistan = pakistan)
                assertEquals(CalendarMonth.TOTAL_DAYS, widget.days.size)

                widget.days.forEachIndexed { index, w ->
                    val cell = core.days[index]
                    val label = "cell $index of $year-$monthNumber pakistan=$pakistan"
                    assertEquals(cell.dayOfMonth, w.hijriDay, "hijriDay $label")
                    assertEquals(cell.isCurrentMonth, w.isCurrentMonth, "isCurrentMonth $label")
                    assertEquals(cell.isWeekend, w.isWeekend, "isWeekend $label")
                    assertEquals(
                        cell.localDate.toEpochDays(),
                        w.gregorianEpochDay,
                        "gregorianEpochDay $label",
                    )
                }

                // "Today" highlight: any cell the in-app calendar marks as today must lie
                // on the same real-world Gregorian day the widget renderers use as today.
                core.days.forEachIndexed { index, cell ->
                    if (cell.isToday) {
                        val todayLabel = "today cell $index of $year-$monthNumber pakistan=$pakistan"
                        assertEquals(todayEpoch, cell.localDate.toEpochDays(), "in-app $todayLabel")
                        assertEquals(todayEpoch, widget.days[index].gregorianEpochDay, "widget $todayLabel")
                    }
                }
            }
        }
    }

    @Test
    fun month_pakistan_reportsRuetElHilalGregorianRange() {
        val data = assertNotNull(
            buildHijriMonthWidgetData(hijriYear = 1448, hijriMonth = 3, adjustmentDays = 0, pakistan = true),
        )
        // 1448-03 starts on 2026-08-15 and is 30 days long -> 15 Aug - 13 Sep 2026.
        val first = PakistanHijriCalendar.hijriToGregorian(1448, 3, 1)
        val last = first.plus(PakistanHijriCalendar.lengthOfMonth(1448, 3) - 1, DateTimeUnit.DAY)
        assertEquals("August - September 2026", data.gregorianRange)
        assertEquals(first, LocalDate(2026, 8, 15))
        assertEquals(last, LocalDate(2026, 9, 13))
    }

    // ── Configurable weekend set ──────────────────────────────────────────────

    @Test
    fun month_weekendSetDefaultIsFridayAndSaturday() {
        val data = assertNotNull(
            buildHijriMonthWidgetData(hijriYear = 1448, hijriMonth = 4, adjustmentDays = 0),
        )
        // Parity with the in-app default weekend set.
        val core = HijrahYearMonth(1448, 4).toCalendarMonth()
        data.days.forEachIndexed { index, w ->
            assertEquals(core.days[index].isWeekend, w.isWeekend, "default weekend cell $index")
        }
        data.days.forEachIndexed { index, w ->
            val weekday = WeekDay.fromDayOfWeek(core.days[index].localDate.dayOfWeek)
            assertEquals(weekday in WeekDay.WEEKEND_DAYS, w.isWeekend, "weekend flag cell $index")
        }
    }

    @Test
    fun month_customWeekendSetShadesExactlyThoseDays() {
        val weekend = setOf(WeekDay.MONDAY, WeekDay.THURSDAY)
        val data = assertNotNull(
            buildHijriMonthWidgetData(
                hijriYear = 1448,
                hijriMonth = 4,
                adjustmentDays = 0,
                weekendDays = weekend,
            ),
        )
        val core = HijrahYearMonth(1448, 4).toCalendarMonth(weekendDays = weekend)
        data.days.forEachIndexed { index, w ->
            assertEquals(core.days[index].isWeekend, w.isWeekend, "parity with core cell $index")
            val weekday = WeekDay.fromDayOfWeek(core.days[index].localDate.dayOfWeek)
            assertEquals(weekday in weekend, w.isWeekend, "weekday in custom set cell $index")
        }
        assertTrue(data.days.any { it.isWeekend })
        assertTrue(data.days.any { !it.isWeekend })
    }

    @Test
    fun month_emptyWeekendSetShadesNothing() {
        val data = assertNotNull(
            buildHijriMonthWidgetData(
                hijriYear = 1448,
                hijriMonth = 4,
                adjustmentDays = 0,
                weekendDays = emptySet(),
            ),
        )
        assertTrue(data.days.all { !it.isWeekend })
    }

    // ── Fully localized (Urdu) today card ─────────────────────────────────────

    @Test
    fun today_fullUrduCard() {
        val anchorEpochDay = LocalDate(2026, 9, 13).toEpochDays()
        val today = assertNotNull(
            todayHijriWidgetData(
                anchorEpochDay = anchorEpochDay,
                adjustmentDays = 0,
                localizedHijriMonthNames = WidgetLocalization.urduHijriMonthNames,
                localizedGregorianMonthNames = WidgetLocalization.urduGregorianMonthNames,
                localizedWeekdayNames = WidgetLocalization.urduWeekdayNames,
                numeralStyle = NumeralStyle.ARABIC_INDIC,
            ),
        )
        val hijri = LocalDate(2026, 9, 13).toHijrahDate()
        assertEquals(UrduCalendarNames.hijriMonths[hijri.month.number - 1], today.hijriMonthName)
        assertEquals(arabicIndic(hijri.day), today.hijriDayText)
        // 2026-09-13 is a Sunday.
        assertEquals(UrduCalendarNames.weekdays.getValue(WeekDay.SUNDAY), today.weekdayName)
        assertEquals("اتوار", today.weekdayName)
        // Day digit follows the numeral style; the Gregorian month is localized.
        assertEquals("${arabicIndic(13)} ستمبر 2026", today.gregorianDate)
    }

    @Test
    fun today_defaultsStayEnglishAndWestern() {
        val anchorEpochDay = LocalDate(2026, 9, 13).toEpochDays()
        val today = assertNotNull(todayHijriWidgetData(anchorEpochDay = anchorEpochDay, adjustmentDays = 0))
        assertEquals("13 September 2026", today.gregorianDate)
        assertEquals(
            LocalDate(2026, 9, 13).toHijrahDate().day.toString(),
            today.hijriDayText,
        )
        assertEquals("Sun", today.weekdayName)
    }

    // ── Month-offset helper ───────────────────────────────────────────────────

    @Test
    fun offsetHijriMonth_stepsAcrossYearBoundary() {
        assertEquals(HijriYearMonth(1448, 1), offsetHijriMonth(1447, 12, 1))
        assertEquals(HijriYearMonth(1447, 12), offsetHijriMonth(1448, 1, -1))
        // Multiple months wrap more than one year.
        assertEquals(HijriYearMonth(1449, 1), offsetHijriMonth(1447, 12, 13))
        assertEquals(HijriYearMonth(1446, 12), offsetHijriMonth(1448, 1, -13))
    }

    @Test
    fun offsetHijriMonth_stepsWithinYear() {
        assertEquals(HijriYearMonth(1447, 9), offsetHijriMonth(1447, 9, 0))
        assertEquals(HijriYearMonth(1447, 11), offsetHijriMonth(1447, 9, 2))
        assertEquals(HijriYearMonth(1447, 7), offsetHijriMonth(1447, 9, -2))
    }

    @Test
    fun offsetHijriMonth_returnsNullOutsideSupportedRange() {
        assertNull(offsetHijriMonth(1300, 1, -1))
        assertNull(offsetHijriMonth(1600, 12, 1))
        assertNull(offsetHijriMonth(2000, 1, 0))
    }

    @Test
    fun offsetHijriMonth_rejectsInvalidMonthsAndYears() {
        assertNull(offsetHijriMonth(1447, 0, 1))
        assertNull(offsetHijriMonth(1447, 13, 1))
        assertNull(offsetHijriMonth(1200, 1, 0))
    }

    // ── Urdu names via the shared projection ──────────────────────────────────

    @Test
    fun month_urduNamesDriveTheWholeGrid() {
        val data = assertNotNull(
            buildHijriMonthWidgetData(
                hijriYear = 1448,
                hijriMonth = 4,
                adjustmentDays = 0,
                pakistan = true,
                localizedHijriMonthNames = WidgetLocalization.urduHijriMonthNames,
                localizedGregorianMonthNames = WidgetLocalization.urduGregorianMonthNames,
                localizedWeekdayNames = WidgetLocalization.urduWeekdayNames,
            ),
        )
        assertEquals(UrduCalendarNames.hijriMonths[3], data.hijriMonthName)
        assertEquals(UrduCalendarNames.weekdayShortNames, data.weekdayHeaders)
        // 1448-04 (Ruet-e-Hilal) spans 14 Sep - 13 Oct 2026.
        assertTrue(data.gregorianRange.contains("ستمبر"), "got ${data.gregorianRange}")
        assertTrue(data.gregorianRange.contains("اکتوبر"), "got ${data.gregorianRange}")
    }

    @Test
    fun widgetLocalization_isTheCoreSingleSource() {
        assertEquals(UrduCalendarNames.hijriMonths, WidgetLocalization.urduHijriMonthNames)
        assertEquals(UrduCalendarNames.gregorianMonths, WidgetLocalization.urduGregorianMonthNames)
        assertEquals(UrduCalendarNames.weekdayShortNames, WidgetLocalization.urduWeekdayNames)
        assertFalse(UrduCalendarNames.hijriMonths.isEmpty())
    }
}