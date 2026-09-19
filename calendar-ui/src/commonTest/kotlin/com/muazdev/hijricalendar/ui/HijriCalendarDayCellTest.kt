package com.muazdev.hijricalendar.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class HijriCalendarDayCellTest {

    private val arabicIndic = listOf(
        '\u0660', '\u0661', '\u0662', '\u0663', '\u0664',
        '\u0665', '\u0666', '\u0667', '\u0668', '\u0669',
    )

    // ── toArabicIndicNumerals ───────────────────────────────────────────

    @Test
    fun singleDigits_mapToArabicIndic() {
        (0..9).forEach { digit ->
            assertEquals(
                arabicIndic[digit].toString(),
                digit.toArabicIndicNumerals(),
                "digit $digit",
            )
        }
    }

    @Test
    fun multiDigitNumbers_mapEachPosition() {
        assertEquals("٣٠", 30.toArabicIndicNumerals())
        assertEquals("١٥", 15.toArabicIndicNumerals())
        assertEquals("١٤٤٨", 1448.toArabicIndicNumerals())
        assertEquals("١٠٢", 102.toArabicIndicNumerals())
    }

    @Test
    fun negativeNumbers_preserveMinusSign() {
        assertEquals("-١", (-1).toArabicIndicNumerals())
        assertEquals("-٣٠", (-30).toArabicIndicNumerals())
    }

    @Test
    fun zero_mapsToArabicIndicZero() {
        assertEquals(arabicIndic[0].toString(), 0.toArabicIndicNumerals())
    }

    @Test
    fun maxDayValue_30renders() {
        assertEquals("٣٠", 30.toArabicIndicNumerals())
    }
}