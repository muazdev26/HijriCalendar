package com.muazdev.hijricalendar.sample

import com.muazdev.hijricalendar.core.HijriMonthLengths
import com.muazdev.hijricalendar.widgetdata.WidgetLanguage
import com.muazdev.hijricalendar.widgetdata.WidgetOptions
import com.muazdev.hijricalendar.widgetdata.WidgetSource
import com.muazdev.hijricalendar.widgetdata.monthLengthKey
import com.muazdev.hijricalendar.widgetdata.monthLengthsToMap
import org.junit.Test
import kotlin.test.assertEquals

class WidgetSettingsSyncTest {

    @Test
    fun sync_leavesGridLanguagesUnchangedAndAppliesOverridesToBoth() {
        val grid1 = WidgetOptions(
            language = WidgetLanguage.URDU,
            source = WidgetSource.CALCULATION,
            adjustmentDays = 0,
            monthLengthOverrides = emptyMap(),
        )
        val grid2 = WidgetOptions(
            language = WidgetLanguage.ENGLISH,
            source = WidgetSource.CALCULATION,
            adjustmentDays = 0,
            monthLengthOverrides = emptyMap(),
        )

        val monthLengths = HijriMonthLengths().apply {
            setMonthLength(1448, 3, 30)
        }
        val overrideTable = monthLengthsToMap(monthLengths)
        val adjustmentDays = 2
        val pakistanDates = true
        val expectedSource = WidgetSource.PAKISTAN

        val updatedGrid1 = grid1.copy(
            adjustmentDays = adjustmentDays,
            source = expectedSource,
            monthLengthOverrides = overrideTable,
        )
        val updatedGrid2 = grid2.copy(
            adjustmentDays = adjustmentDays,
            source = expectedSource,
            monthLengthOverrides = overrideTable,
        )

        // Verify Grid 1 language is still URDU
        assertEquals(WidgetLanguage.URDU, updatedGrid1.language)
        assertEquals(2, updatedGrid1.adjustmentDays)
        assertEquals(WidgetSource.PAKISTAN, updatedGrid1.source)
        assertEquals(30, updatedGrid1.monthLengthOverrides[monthLengthKey(1448, 3)])

        // Verify Grid 2 language is still ENGLISH
        assertEquals(WidgetLanguage.ENGLISH, updatedGrid2.language)
        assertEquals(2, updatedGrid2.adjustmentDays)
        assertEquals(WidgetSource.PAKISTAN, updatedGrid2.source)
        assertEquals(30, updatedGrid2.monthLengthOverrides[monthLengthKey(1448, 3)])
    }
}
