package com.muazdev.hijricalendar.core

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies that consecutive Pakistan fixes form a continuous chain:
 * each fix's start date plus its length equals the next fix's start date.
 *
 * This catches data errors where a fix's length does not match the gap to the next fix.
 * Fixes with status "pending-length" are skipped since their length is not yet known.
 */
class PakistanFixChainTest {

    @Test
    fun consecutiveFixesFormContinuousChain() {
        val sortedKeys = PAKISTAN_FIXES.keys.toList().sortedWith(compareBy({ it.first }, { it.second }))
        val fixes = sortedKeys.map { PAKISTAN_FIXES[it]!! }

        for (index in 0 until fixes.size - 1) {
            val current = fixes[index]
            val next = fixes[index + 1]

            // Check if these fixes are for consecutive Hijri months
            val currentHijriProleptic = sortedKeys[index].first * 12 + sortedKeys[index].second
            val nextHijriProleptic = sortedKeys[index + 1].first * 12 + sortedKeys[index + 1].second

            if (nextHijriProleptic == currentHijriProleptic + 1) {
                val expectedNextStart =
                    current.firstGregorian.plus(current.length.toLong(), DateTimeUnit.DAY)
                assertEquals(
                    next.firstGregorian,
                    expectedNextStart,
                    "Fix chain broken: ${sortedKeys[index]} start " +
                        "($current.firstGregorian) + ${current.length} days = " +
                        "$expectedNextStart, but ${sortedKeys[index + 1]} starts at " +
                        "${next.firstGregorian}",
                )
            }
        }
    }

    @Test
    fun confirmed1448FixesHaveExpectedDates() {
        // 1448-04 (Rabi al-Thani) starts 2026-09-14 with length 29
        val rabiThani = PAKISTAN_FIXES[1448 to 4]
        assertTrue(rabiThani != null, "1448-04 fix should exist")
        assertEquals(LocalDate(2026, 9, 14), rabiThani!!.firstGregorian)
        assertEquals(29, rabiThani.length)

        // 1448-05 (Jumada al-Ula) starts 2026-10-13 with length 30
        val jumadaUla = PAKISTAN_FIXES[1448 to 5]
        assertTrue(jumadaUla != null, "1448-05 fix should exist")
        assertEquals(LocalDate(2026, 10, 13), jumadaUla!!.firstGregorian)
        assertEquals(30, jumadaUla.length)

        // Verify chain: 1448-04 start + length = 1448-05 start
        assertEquals(
            jumadaUla.firstGregorian,
            rabiThani.firstGregorian.plus(rabiThani.length.toLong(), DateTimeUnit.DAY),
            "1448-04 start + length should equal 1448-05 start",
        )
    }
}
