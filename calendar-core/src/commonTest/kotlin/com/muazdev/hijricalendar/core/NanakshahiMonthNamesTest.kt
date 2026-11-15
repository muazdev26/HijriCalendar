package com.muazdev.hijricalendar.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NanakshahiMonthNamesTest {

    @Test
    fun englishNames_haveTwelveEntries() {
        assertEquals(12, NanakshahiMonthNames.english.size)
    }

    @Test
    fun gurmukhiNames_haveTwelveEntries() {
        assertEquals(12, NanakshahiMonthNames.gurmukhi.size)
    }

    @Test
    fun gurmukhiNames_containNoLatinCharacters() {
        for (name in NanakshahiMonthNames.gurmukhi) {
            assertTrue(
                name.none { it in 'a'..'z' || it in 'A'..'Z' },
                "Gurmukhi name '$name' contains Latin characters",
            )
        }
    }

    @Test
    fun englishName_lookupMatchesList() {
        for (i in 1..12) {
            assertEquals(NanakshahiMonthNames.english[i - 1], NanakshahiMonthNames.englishName(i))
        }
    }

    @Test
    fun gurmukhiName_lookupMatchesList() {
        for (i in 1..12) {
            assertEquals(NanakshahiMonthNames.gurmukhi[i - 1], NanakshahiMonthNames.gurmukhiName(i))
        }
    }

    @Test
    fun firstMonth_isChet() {
        assertEquals("Chet", NanakshahiMonthNames.englishName(1))
        assertEquals("ਚੇਤ", NanakshahiMonthNames.gurmukhiName(1))
    }

    @Test
    fun lastMonth_isPhagun() {
        assertEquals("Phagun", NanakshahiMonthNames.englishName(12))
        assertEquals("ਫੱਗਣ", NanakshahiMonthNames.gurmukhiName(12))
    }
}
