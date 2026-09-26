package com.muazdev.hijricalendar.widget.glance

import androidx.datastore.preferences.core.mutablePreferencesOf
import com.muazdev.hijricalendar.core.WeekDay
import com.muazdev.hijricalendar.widgetdata.NumeralStyle
import com.muazdev.hijricalendar.widgetdata.WidgetLanguage
import com.muazdev.hijricalendar.widgetdata.WidgetOptions
import com.muazdev.hijricalendar.widgetdata.WidgetOptionsJson
import com.muazdev.hijricalendar.widgetdata.WidgetSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        val options = WidgetOptions(
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
            options.effectiveMonthNameLanguage.ordinal,
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
        assertEquals(WidgetLanguage.ENGLISH, restored.effectiveMonthNameLanguage)
    }

    @Test
    fun decodeOptionsJson_readsTheSharedNamedFormat() {
        // Written by `WidgetOptionsJson`, i.e. by every other native renderer too.
        val options = WidgetOptions(
            adjustmentDays = -2,
            numeralStyle = NumeralStyle.WESTERN,
            firstDayOfWeekIndex = 6,
            pinnedYear = 1448,
            pinnedMonth = 3,
            source = WidgetSource.PAKISTAN,
            language = WidgetLanguage.ENGLISH,
            monthNameLanguage = WidgetLanguage.URDU,
        )
        val encoded = WidgetOptionsJson.encode(options)
        assertEquals(options, HijriWidgetConfig.decodeOptionsJson(encoded))
    }

    @Test
    fun decodeOptionsJson_stillReadsThePreSharedOrdinalFormat() {
        // Options stored by an earlier version encoded the same fields as enum *ordinals*. Dropping
        // this reader would silently reset every already-placed widget to the defaults on upgrade,
        // so it is pinned here rather than left to a manual test.
        val legacy = """
            {"adjustmentDays":1,"numeralStyle":0,"firstDayOfWeekIndex":6,"pinnedYear":1448,
             "pinnedMonth":3,"source":1,"language":1,"monthNameLanguage":0}
        """.trimIndent()
        val options = requireNotNull(HijriWidgetConfig.decodeOptionsJson(legacy))
        assertEquals(1, options.adjustmentDays)
        assertEquals(NumeralStyle.WESTERN, options.numeralStyle)
        assertEquals(6, options.firstDayOfWeekIndex)
        assertEquals(1448, options.pinnedYear)
        assertEquals(3, options.pinnedMonth)
        assertEquals(WidgetSource.PAKISTAN, options.source)
        assertEquals(WidgetLanguage.ENGLISH, options.language)
        assertEquals(WidgetLanguage.URDU, options.effectiveMonthNameLanguage)
    }

    @Test
    fun decodeOptionsJson_legacyReaderSeedsNumeralsFromTheLanguage() {
        // A widget that predates the numeral-style option stored no value for it, so the shared
        // decoder must fall back to the language's default rather than the field default.
        val legacy = """{"language":0}"""
        val urdu = requireNotNull(HijriWidgetConfig.decodeOptionsJson(legacy))
        assertEquals(NumeralStyle.ARABIC_INDIC, urdu.numeralStyle)

        val english = requireNotNull(HijriWidgetConfig.decodeOptionsJson("""{"language":1}"""))
        assertEquals(NumeralStyle.WESTERN, english.numeralStyle)
    }

    @Test
    fun decodeOptionsJson_ignoresFieldsFromANewerLibrary() {
        // The widget can outlive an app update, so an unknown key must not break the render.
        val future = """{"language":"ENGLISH","somethingAddedLater":true}"""
        val options = requireNotNull(HijriWidgetConfig.decodeOptionsJson(future))
        assertEquals(WidgetLanguage.ENGLISH, options.language)
    }

    @Test
    fun decodeOptionsJson_returnsNullForUnusableInput() {
        // Absent/malformed values return null so the caller can choose its own fallback, unlike the
        // shared decoder which substitutes the defaults.
        assertNull(HijriWidgetConfig.decodeOptionsJson(""))
        assertNull(HijriWidgetConfig.decodeOptionsJson("not json"))
    }

    @Test
    fun encodeViewedRoundTripsThroughDecodeViewed() {
        // The viewed month is a separate key from the options so a settings-screen save cannot
        // clobber navigation state; both halves of that pair are covered here. It used to be
        // encoded with `org.json`, which is stubbed in local unit tests, so this path was only ever
        // reachable on a device.
        val prefs = mutablePreferencesOf(
            HijriWidgetConfig.VIEWED_KEY to HijriWidgetConfig.encodeViewed(1448, 3),
        )
        assertEquals(1448 to 3, HijriWidgetConfig.decodeViewed(prefs))
    }

    @Test
    fun decodeViewed_returnsNullWhenAbsentOrIncomplete() {
        assertNull(HijriWidgetConfig.decodeViewed(mutablePreferencesOf()))
        // A half-written value must not resolve to a bogus month.
        assertNull(
            HijriWidgetConfig.decodeViewed(
                mutablePreferencesOf(HijriWidgetConfig.VIEWED_KEY to """{"year":1448}"""),
            ),
        )
    }
}