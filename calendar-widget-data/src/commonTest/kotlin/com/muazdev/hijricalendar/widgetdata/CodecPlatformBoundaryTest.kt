package com.muazdev.hijricalendar.widgetdata

import com.abdulrahman_b.hijrahdatetime.toHijrahDate
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * WD-08: the shared JSON codec runs on **two** platforms — Android on the JVM, iOS through the
 * `WidgetCalendar` Kotlin/Native framework — and these cases exist because the JVM tests alone do
 * not cover half the users.
 *
 * `kotlinx.serialization`'s JSON has platform-specific failure surfaces that a JVM test
 * systematically misses. `adjustmentDays: Int` through a K/N writer is where a 32/64-bit width
 * difference would show up; so is number parsing at the `Long`/`Int` boundary, and the fact that the
 * generated serializer is a *different artifact per target*. A divergence here is not a crash — it is
 * a widget that renders the wrong content forever, because `HijriSharedOptions.swift` re-encodes
 * through this codec on every `loadOptions`.
 *
 * These live in `commonTest` on purpose: the value is entirely in *where* they run, so the macOS CI
 * job now runs `:calendar-widget-data:iosSimulatorArm64Test` as well as compiling the target.
 */
class CodecPlatformBoundaryTest {

    @Test
    fun inRangeAdjustmentValuesSurviveTheRoundTrip() {
        // The width hazard: `adjustmentDays` is an `Int` written as a JSON number and read back by
        // whichever platform's parser runs. Every value a user can actually choose, both signs.
        for (adjustment in listOf(-100, -3, -2, -1, 0, 1, 2, 3, 100)) {
            val options = WidgetOptions(adjustmentDays = adjustment)
            assertEquals(
                adjustment,
                WidgetOptionsJson.decode(WidgetOptionsJson.encode(options)).adjustmentDays,
                "adjustmentDays=$adjustment did not survive the round trip",
            )
        }
    }

    @Test
    fun anAbsurdAdjustmentIsClampedAtBothEnds() {
        // Deliberate, and the reason `AdjustmentDaysSerializer` exists. The extremes are *not*
        // preserved: they are clamped, because a value a billion is a corrupt blob and the closest
        // safe reading of it is the limit — see that serializer's KDoc.
        assertEquals(100, WidgetOptionsJson.decode("""{"adjustmentDays":2147483647}""").adjustmentDays)
        assertEquals(
            -100,
            WidgetOptionsJson.decode("""{"adjustmentDays":-2147483648}""").adjustmentDays,
        )
        // And a value constructed in Kotlin is clamped on write too, so the two agree.
        val clamped = WidgetOptions(adjustmentDays = Int.MAX_VALUE)
        assertEquals(100, clamped.adjustmentDays.coerceIn(-100, 100))
        assertEquals(100, WidgetOptionsJson.decode(WidgetOptionsJson.encode(clamped)).adjustmentDays)
    }

    @Test
    fun epochDayArithmeticAgreesWithThePlatformCalendar() {
        // `toEpochDays` / `fromEpochDays` are `kotlinx-datetime`'s, and they are the bridge between
        // every anchor a renderer passes in and every Hijri date this module returns — so a
        // difference between the JVM and K/N implementations here would shift dates by a day or
        // more on one platform only, which is silent.
        // 1970, 2000, 2026 and 2117 — inside the ~1300-1600 AH window the projection supports,
        // because outside it there is legitimately no projection to compare against.
        for (anchor in listOf(1L, 10_957L, 20_731L, 54_000L)) {
            val anchorDate = LocalDate.fromEpochDays(anchor)
            assertEquals(anchor, anchorDate.toEpochDays(), "round trip failed for anchor $anchor")

            val projected = assertNotNull(
                todayHijriWidgetData(anchorEpochDay = anchor, adjustmentDays = 0),
                "no projection for anchor $anchor",
            )
            val expected = anchorDate.toHijrahDate()
            assertEquals(expected.day, projected.hijriDay, "day differs at anchor $anchor")
            assertEquals(expected.month.number, projected.hijriMonth, "month differs at anchor $anchor")
            assertEquals(expected.year, projected.hijriYear, "year differs at anchor $anchor")
        }
    }

    @Test
    fun aGridsEpochDaysConvertBackToTheDatesTheyCameFrom() {
        // The 42 cells are day counts a renderer turns back into dates; a width or sign problem
        // shows up as a grid whose last cell is decades out.
        val month = assertNotNull(
            buildHijriMonthWidgetData(hijriYear = 1448, hijriMonth = 3, adjustmentDays = 0),
        )
        val days = month.days.map { it.gregorianEpochDay }
        assertEquals(days.size, days.toSet().size, "epoch days must be distinct")
        // Each row of seven must be consecutive days.
        days.chunked(7).forEach { week ->
            week.zipWithNext { a, b -> assertEquals(a + 1, b, "a week's days must be consecutive") }
        }
        assertEquals(
            days.size,
            days.count { LocalDate.fromEpochDays(it).toEpochDays() == it },
            "every epoch day must survive the LocalDate round trip",
        )
    }

    @Test
    fun anOutOfRangeNumberIsClampedRatherThanLosingTheWholeBlob() {
        // The failure this found: `coerceInputValues` contains an unknown *enum*, but not a number
        // that overflows the field — so a single absurd value failed the entire decode and every
        // other option was lost with it. `AdjustmentDaysSerializer` clamps instead, and this asserts
        // the *other* fields survive, which is the property that matters.
        val decoded = WidgetOptionsJson.decodeOrNull(
            """{"adjustmentDays":99999999999999,"language":"ENGLISH","source":"PAKISTAN"}""",
        )
        assertNotNull(decoded, "a corrupt number must not discard the readable fields")
        assertEquals(100, decoded.adjustmentDays, "clamped to the limit, not truncated or dropped")
        assertEquals(WidgetLanguage.ENGLISH, decoded.language)
        assertEquals(WidgetSource.PAKISTAN, decoded.source)

        // A non-numeric value in the same field is not a number at all, so it takes the default
        // rather than being clamped to something invented.
        assertEquals(0, WidgetOptionsJson.decode("""{"adjustmentDays":"two"}""").adjustmentDays)

        // An out-of-range *month* is not a Hijri month, so it is not a pin (WD-09).
        assertEquals(
            null,
            WidgetOptionsJson.decodeOrNull("""{"pinnedMonth":13,"pinnedYear":1448}""")?.pinned,
        )
    }

    @Test
    fun theOverrideMapSurvivesWithItsKeysIntact() {
        // Map keys are the riskiest part of a cross-platform round trip: a String-keyed map is
        // written as JSON object keys and read back as a map, and a K/N parser that changed how it
        // handles them would silently drop overrides rather than fail.
        val options = WidgetOptions(
            monthLengthOverrides = mapOf(
                monthLengthKey(1447, 9) to 29,
                monthLengthKey(1448, 12) to 30,
            ),
        )
        val decoded = WidgetOptionsJson.decode(WidgetOptionsJson.encode(options))
        assertEquals(options.monthLengthOverrides, decoded.monthLengthOverrides)
        assertEquals(29, decoded.overridesTable().monthLength(1447, 9))
        assertEquals(30, decoded.overridesTable().monthLength(1448, 12))
    }

    @Test
    fun theStoredSchemaIsAsciiOnlyAndSaysSo() {
        // A finding, recorded rather than assumed: `WidgetOptionsJson` emits **no** non-ASCII, so
        // the escaping path this format shares with iOS is currently unexercised by the schema
        // itself. Localised month and weekday names are *not* stored — they are derived from
        // `language` / `monthNameLanguage` through `WidgetLocalization` — which is why. A future
        // field holding a user-supplied label would be the first to carry non-ASCII, so the decoder
        // is pinned against a blob that does.
        val text = WidgetOptionsJson.encode(WidgetOptions.DEFAULTS)
        assertTrue(
            text.all { it.code in 0x20..0x7E },
            "expected the stored schema to be ASCII-only, got: $text",
        )

        val withUnicode = WidgetOptionsJson.decodeOrNull(
            """{"language":"ENGLISH","note":"\u0631\u0645\u0636\u0627\u0646 \u2014 \\u0022quoted\u0022"}""",
        )
        assertEquals(
            WidgetLanguage.ENGLISH,
            withUnicode?.language,
            "a blob with escaped Unicode and an unknown key must still decode",
        )
    }

    @Test
    fun malformedInputIsStillRejectedTheSameWay() {
        // The property `decodeOptionsJson` in calendar-widget-glance depends on: text that is not
        // this format must come back `null`, on every platform, so the shape-based fence can route
        // it. A K/N parser that accepted, say, a bare number here would break that routing.
        for (text in listOf("", "   ", "not json", "[1,2,3]", "\"a string\"", "12345", "null")) {
            assertNull(WidgetOptionsJson.decodeOrNull(text), "should have rejected: '$text'")
        }
    }
}
