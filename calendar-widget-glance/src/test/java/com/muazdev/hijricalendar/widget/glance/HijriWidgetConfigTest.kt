package com.muazdev.hijricalendar.widget.glance

import com.muazdev.hijricalendar.core.WeekDay
import com.muazdev.hijricalendar.widgetdata.NumeralStyle
import com.muazdev.hijricalendar.widgetdata.WidgetLanguage
import com.muazdev.hijricalendar.widgetdata.WidgetSource
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Guards the public [HijriWidgetConfig] surface for host-app settings screens: the Bundle-safe
 * [HijriWidgetConfig.widgetOptionsSaver] must round-trip every option (including the independent
 * month-name language), and stored configs written before the month-name option existed must keep
 * following the widget language rather than flipping scripts on upgrade.
 */
class HijriWidgetConfigTest {

    @Test
    fun widgetOptionsSaver_roundTripsAllFields() {
        val options = HijriWidgetConfig.WidgetOptions(
            adjustmentDays = -1,
            numeralStyle = NumeralStyle.ARABIC_INDIC,
            firstDayOfWeekIndex = WeekDay.DEFAULT_FIRST_DAY.index,
            pinnedYear = 1448,
            pinnedMonth = 3,
            source = WidgetSource.PAKISTAN,
            language = WidgetLanguage.URDU,
            monthNameLanguage = WidgetLanguage.ENGLISH,
        )
        // The exact list the saver writes (field order matters for restore).
        val saved = listOf(
            options.adjustmentDays,
            options.numeralStyle.ordinal,
            options.firstDayOfWeekIndex,
            options.pinnedYear,
            options.pinnedMonth,
            options.source.ordinal,
            options.language.ordinal,
            options.monthNameLanguage.ordinal,
        )
        val restored = requireNotNull(HijriWidgetConfig.widgetOptionsSaver().restore(saved))
        assertEquals(options, restored)
    }

    @Test
    fun widgetOptionsSaver_defaultsNewOptionToLanguage() {
        // A stored list written before the month-name option is a 7-element list; restoring it
        // must still produce a valid result by keeping month names tied to the language slot.
        val legacy = listOf(0, 0, 6, null, null, 0, 1) // adjustment, numerals, friday, no pin, CALCULATION, ENGLISH
        val restored = requireNotNull(HijriWidgetConfig.widgetOptionsSaver().restore(legacy))
        assertEquals(WidgetLanguage.ENGLISH, restored.language)
        assertEquals(WidgetLanguage.ENGLISH, restored.monthNameLanguage)
    }
}