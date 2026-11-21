package com.muazdev.hijricalendar.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesiMonthNamesTest {

    @Test
    fun englishNames_haveTwelveEntries() {
        assertEquals(12, DesiMonthNames.english.size)
    }

    @Test
    fun gurmukhiNames_haveTwelveEntries() {
        assertEquals(12, DesiMonthNames.gurmukhi.size)
    }

    @Test
    fun urduNames_haveTwelveEntries() {
        assertEquals(12, DesiMonthNames.urdu.size)
    }

    @Test
    fun hindiNames_haveTwelveEntries() {
        assertEquals(12, DesiMonthNames.hindi.size)
    }

    @Test
    fun gurmukhiNames_containNoLatinCharacters() {
        for (name in DesiMonthNames.gurmukhi) {
            assertTrue(
                name.none { it in 'a'..'z' || it in 'A'..'Z' },
                "Gurmukhi name '$name' contains Latin characters",
            )
        }
    }

    @Test
    fun urduNames_containNoLatinCharacters() {
        for (name in DesiMonthNames.urdu) {
            assertTrue(
                name.none { it in 'a'..'z' || it in 'A'..'Z' },
                "Urdu name '$name' contains Latin characters",
            )
        }
    }

    @Test
    fun hindiNames_containNoLatinCharacters() {
        for (name in DesiMonthNames.hindi) {
            assertTrue(
                name.none { it in 'a'..'z' || it in 'A'..'Z' },
                "Hindi name '$name' contains Latin characters",
            )
        }
    }

    @Test
    fun englishName_lookupMatchesList() {
        for (i in 1..12) {
            assertEquals(DesiMonthNames.english[i - 1], DesiMonthNames.englishName(i))
        }
    }

    @Test
    fun gurmukhiName_lookupMatchesList() {
        for (i in 1..12) {
            assertEquals(DesiMonthNames.gurmukhi[i - 1], DesiMonthNames.gurmukhiName(i))
        }
    }

    @Test
    fun urduName_lookupMatchesList() {
        for (i in 1..12) {
            assertEquals(DesiMonthNames.urdu[i - 1], DesiMonthNames.urduName(i))
        }
    }

    @Test
    fun hindiName_lookupMatchesList() {
        for (i in 1..12) {
            assertEquals(DesiMonthNames.hindi[i - 1], DesiMonthNames.hindiName(i))
        }
    }

    @Test
    fun firstMonth_isChetInEveryScript() {
        assertEquals("Chet", DesiMonthNames.englishName(1))
        assertEquals("ਚੇਤ", DesiMonthNames.gurmukhiName(1))
        assertEquals("چیت", DesiMonthNames.urduName(1))
        assertEquals("चेत", DesiMonthNames.hindiName(1))
    }

    @Test
    fun lastMonth_isPhagunInEveryScript() {
        assertEquals("Phagun", DesiMonthNames.englishName(12))
        assertEquals("ਫੱਗਣ", DesiMonthNames.gurmukhiName(12))
        assertEquals("پھگݨ", DesiMonthNames.urduName(12))
        assertEquals("फग्गन", DesiMonthNames.hindiName(12))
    }

    /**
     * The Devanagari table is a transliteration of the Desi names, not the Hindu Bikrami month
     * set. Those are a different naming for a calendar whose months start on different days, so a
     * table that drifted toward them would silently mislabel every date — which is exactly the mistake
     * a "add the Hindi names" change invites.
     */
    @Test
    fun hindiNames_areNotTheBikramiOnes() {
        val bikrami = setOf(
            "चैत्र", "वैशाख", "ज्येष्ठ", "आषाढ़", "श्रावण", "भाद्रपद",
            "आश्विन", "कार्तिक", "मार्गशीर्ष", "पौष", "माघ", "फाल्गुन",
        )
        val overlapping = DesiMonthNames.hindi.filter { it in bikrami }
        // `माघ` is spelled the same in both calendars, so one overlap is expected; the point of the
        // test is that the table is not wholesale the Bikrami set.
        assertTrue(overlapping.size <= 1, "Hindi month names look like the Bikrami set: $overlapping")
    }
}
