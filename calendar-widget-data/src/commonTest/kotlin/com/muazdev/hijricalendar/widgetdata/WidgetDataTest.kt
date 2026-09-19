package com.muazdev.hijricalendar.widgetdata

import com.abdulrahman_b.hijrahdatetime.HijrahDate
import com.abdulrahman_b.hijrahdatetime.toHijrahDate
import com.abdulrahman_b.hijrahdatetime.toLocalDate
import com.abdulrahman_b.hijrahdatetime.yearmonth.HijrahYearMonth
import com.muazdev.hijricalendar.core.WeekDay
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WidgetDataTest {

    private fun month(
        y: Int = 1447,
        m: Int = 9,
        adjustmentDays: Int = 0,
        firstDayOfWeekIndex: Int = WeekDay.DEFAULT_FIRST_DAY.index,
        numeralStyle: NumeralStyle = NumeralStyle.WESTERN,
    ): HijriMonthWidgetData = assertNotNull(
        buildHijriMonthWidgetData(
            hijriYear = y,
            hijriMonth = m,
            adjustmentDays = adjustmentDays,
            firstDayOfWeekIndex = firstDayOfWeekIndex,
            numeralStyle = numeralStyle,
        )
    )

    @Test
    fun grid_hasExactly42Days() {
        assertEquals(42, month().days.size)
    }

    @Test
    fun grid_has7WeekdayHeadersInWeekDayEnumOrder() {
        val headers = month(firstDayOfWeekIndex = WeekDay.SATURDAY.index).weekdayHeaders
        assertEquals(7, headers.size)
        assertEquals(WeekDay.entries.map { it.shortName }, headers)
    }

    @Test
    fun grid_headersFollowConfiguredFirstDay() {
        val headers = month(firstDayOfWeekIndex = WeekDay.SUNDAY.index).weekdayHeaders
        assertEquals(WeekDay.SUNDAY.shortName, headers.first())
        assertEquals(WeekDay.SATURDAY.shortName, headers.last())
    }

    @Test
    fun grid_firstCellAlignsWithConfiguredFirstDay() {
        val data = month(firstDayOfWeekIndex = WeekDay.SUNDAY.index)
        val firstCell = LocalDate.fromEpochDays(data.days.first().gregorianEpochDay)
        assertEquals(WeekDay.SUNDAY, WeekDay.fromDayOfWeek(firstCell.dayOfWeek))
    }

    @Test
    fun grid_cellDaysWalkConsecutiveGregorianDates() {
        val data = month()
        val epochDays = data.days.map { it.gregorianEpochDay }
        assertEquals((0L until 42L).toList(), epochDays.map { it - epochDays.first() })
    }

    @Test
    fun grid_currentMonthDaysWithinRange() {
        val data = month(1447, 11) // Shawwal 1447
        val current = data.days.filter { it.isCurrentMonth }
        assertTrue(current.size in 29..30)
        assertTrue(current.all { it.hijriDay in 1..30 })
    }

    @Test
    fun grid_hijriDayTextMatchesNumber() {
        val data = month()
        data.days.filter { it.isCurrentMonth }.forEach { cell ->
            assertEquals(cell.hijriDay.toString(), cell.dayText)
        }
    }

    @Test
    fun grid_arabicIndicNumerals() {
        val data = month(numeralStyle = NumeralStyle.ARABIC_INDIC)
        val current = data.days.filter { it.isCurrentMonth }
        assertEquals("١", current.first().dayText)
        assertEquals("٢", current.first { it.hijriDay == 2 }.dayText)
    }

    @Test
    fun grid_adjustmentDaysShiftCurrentMonthCells() {
        val unadjusted = month(adjustmentDays = 0)
        val adjusted = month(adjustmentDays = 1)
        assertEquals(unadjusted.days.size, adjusted.days.size)
        unadjusted.days.filter { it.isCurrentMonth }.forEach { cell ->
            val shifted = adjusted.days.single { it.hijriDay == cell.hijriDay && it.isCurrentMonth }
            assertEquals(cell.gregorianEpochDay - 1, shifted.gregorianEpochDay)
        }
    }

    @Test
    fun grid_outOfRangeMonthReturnsNull() {
        assertNull(buildHijriMonthWidgetData(hijriYear = 2000, hijriMonth = 1, adjustmentDays = 0))
    }

    @Test
    fun grid_invalidMonthNumberReturnsNull() {
        assertNull(buildHijriMonthWidgetData(hijriYear = 1447, hijriMonth = 0, adjustmentDays = 0))
        assertNull(buildHijriMonthWidgetData(hijriYear = 1447, hijriMonth = 13, adjustmentDays = 0))
    }

    @Test
    fun grid_localizedNamesAreUsed() {
        val data = month().let {
            buildHijriMonthWidgetData(
                hijriYear = it.hijriYear,
                hijriMonth = it.hijriMonth,
                adjustmentDays = 0,
                localizedHijriMonthNames = listOf("A", "B", "C", "D", "E", "F", "G", "H", "أ", "J", "K", "L"),
                localizedWeekdayNames = listOf("X1", "X2", "X3", "X4", "X5", "X6", "X7"),
            )!!
        }
        assertEquals("أ", data.hijriMonthName)
        assertEquals("X1", data.weekdayHeaders.first())
    }

    @Test
    fun today_allowsMonthRoundTrip() {
        val anchor = LocalDate(2026, 9, 13).toEpochDays()
        val today = assertNotNull(todayHijriWidgetData(anchorEpochDay = anchor, adjustmentDays = 0))
        val reconstructed = HijrahDate(
            today.hijriYear,
            today.hijriMonth,
            today.hijriDay,
        )
        assertEquals(LocalDate(2026, 9, 13), reconstructed.toLocalDate())
    }

    @Test
    fun today_respectsAdjustment() {
        val anchor = LocalDate(2026, 9, 13).toEpochDays()
        val adjusted = assertNotNull(todayHijriWidgetData(anchorEpochDay = anchor, adjustmentDays = 3))
        val expected = LocalDate(2026, 9, 16).toHijrahDate()
        assertEquals(expected.year, adjusted.hijriYear)
        assertEquals(expected.month.number, adjusted.hijriMonth)
        assertEquals(expected.day, adjusted.hijriDay)
    }

    @Test
    fun today_anchorRoundTrip() {
        // Ramadan 1447 should start on the Gregorian day that map() gives back.
        val anchor = HijrahYearMonth(1447, 9).firstDay.toLocalDate().toEpochDays()
        val today = assertNotNull(todayHijriWidgetData(anchorEpochDay = anchor, adjustmentDays = 0))
        assertEquals(1447, today.hijriYear)
        assertEquals(9, today.hijriMonth)
        assertEquals(1, today.hijriDay)
    }

    @Test
    fun today_gregorianDateMatchesAnchor() {
        val anchor = LocalDate(2026, 9, 13).toEpochDays()
        val today = assertNotNull(todayHijriWidgetData(anchorEpochDay = anchor, adjustmentDays = 0))
        assertEquals("13 September 2026", today.gregorianDate)
    }

    @Test
    fun epochDay_equalsKotlinxConvention() {
        // Gregorian anchor round-trips through the same day count used by cells.
        val data = month()
        val middle = data.days[21]
        val local = LocalDate.fromEpochDays(middle.gregorianEpochDay)
        assertEquals(middle.gregorianEpochDay, local.toEpochDays())
    }
}