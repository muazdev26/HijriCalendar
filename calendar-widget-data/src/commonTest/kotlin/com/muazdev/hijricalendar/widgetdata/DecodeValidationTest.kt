package com.muazdev.hijricalendar.widgetdata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * WD-09 and WD-10: the decode boundary's contract.
 *
 * kotlinx-serialization checks *types*, not values, so every field used to decode cleanly into a
 * `WidgetOptions` that no projection can build — and the failure was silent and *terminal*:
 *
 *  - `{"pinnedMonth":13}` produced `pinnedMonth = 13`, the grid builder returned `null`, and
 *    `HijriWidgetRoot` answered that `null` by falling back to a **compact today card**. So a widget
 *    the user had resized to a four-column month grid quietly showed a small card in a large frame.
 *    That reads as intentional, which is worse than blank: there is nothing to diagnose.
 *  - `{"adjustmentDays":99999999999999}` failed the *whole* decode through the number parser (which
 *    `coerceInputValues` does not contain), taking the language and source down with it.
 *  - An extreme `adjustmentDays` overflowed `LocalDate.plus`, which was outside any `try` in a
 *    function documented to return `null`.
 *  - `formatNumber(-5, ARABIC_INDIC)` indexed its digit table at `'-'.code - '0'.code == -3`.
 *
 * The asymmetry was the giveaway throughout: some fields were coerced and some were not.
 */
class DecodeValidationTest {

    private fun decode(text: String): WidgetOptionsJson.DecodeResult =
        assertNotNull(WidgetOptionsJson.decodeOrReport(text), "should have decoded: $text")

    // ── impossible values are repaired, not accepted ─────────────────────────

    @Test
    fun anImpossibleMonthIsDroppedAndReported() {
        val result = decode("""{"pinnedYear":1448,"pinnedMonth":13}""")
        assertNull(result.options.pinned, "month 13 is not a Hijri month")
        assertTrue(result.wasRepaired)
        assertTrue(
            result.repairedFields.contains("pin") || result.repairedFields.contains("pinnedMonth"),
            "expected a repair report, got ${result.repairedFields}",
        )
        // And the whole point: it now *renders*, showing today, rather than degrading to a card.
        assertNotNull(
            buildHijriMonthWidgetData(hijriYear = 1448, hijriMonth = 9, options = result.options),
        )
    }

    @Test
    fun everyOutOfRangeMonthIsRepaired() {
        for (month in listOf(-4, 0, 1, 12, 13, 99, Int.MIN_VALUE, Int.MAX_VALUE)) {
            val result = decode("""{"pinnedYear":1448,"pinnedMonth":$month}""")
            val expected = if (month in 1..12) HijriYearMonth(1448, month) else null
            assertEquals(
                expected,
                result.options.pinned,
                "month $month should resolve to ${expected ?: "no pin"}",
            )
        }
    }

    @Test
    fun aHalfSetPinIsRepairedToNoPin() {
        val yearOnly = decode("""{"pinnedYear":1448}""")
        assertNull(yearOnly.options.pinned)
        assertTrue(yearOnly.wasRepaired, "a dropped half is a repair and must be reported")

        val monthOnly = decode("""{"pinnedMonth":3}""")
        assertNull(monthOnly.options.pinned)
        assertTrue(monthOnly.wasRepaired)
    }

    @Test
    fun anAbsurdAdjustmentKeepsTheOtherOptions() {
        // The failure this found: `coerceInputValues` contains an unknown enum but NOT a number that
        // overflows, so one corrupt value failed the entire decode and every other field with it.
        val result = decode("""{"adjustmentDays":99999999999999,"language":"ENGLISH"}""")
        assertEquals(WidgetLanguage.ENGLISH, result.options.language, "the readable field must survive")
    }

    @Test
    fun aCleanBlobIsReportedAsUnrepaired() {
        val result = decode(WidgetOptionsJson.encode(WidgetOptions.DEFAULTS))
        assertTrue(
            result.repairedFields.isEmpty(),
            "a blob this module wrote must need no repair, got ${result.repairedFields}",
        )
        assertEquals(WidgetOptions.DEFAULTS, result.options)
    }

    @Test
    fun aValidPinIsLeftAlone() {
        val result = decode("""{"pinnedYear":1448,"pinnedMonth":3}""")
        assertEquals(HijriYearMonth(1448, 3), result.options.pinned)
        assertTrue(!result.wasRepaired, "a valid pin must not be reported as a repair")
    }

    // ── the year is deliberately not range-checked ───────────────────────────

    @Test
    fun anOutOfRangeYearIsKeptBecauseTheValidWindowDependsOnTheSource() {
        // Pakistan mode supports a narrower year window than Umm al-Qura, so a bound written at the
        // decode boundary would be wrong for one of the two modes. The builder's own `null` stands,
        // and it is the same `null` both modes already handle.
        val result = decode("""{"pinnedYear":9999,"pinnedMonth":3}""")
        assertEquals(HijriYearMonth(9999, 3), result.options.pinned, "not the decoder's job")
        assertTrue(!result.wasRepaired)
    }

    // ── WD-10: nothing here may throw ────────────────────────────────────────

    @Test
    fun anExtremeAnchorCannotThrowOutOfTodayProjection() {
        // `LocalDate.plus` throws when the *shifted* date leaves the representable range, and that
        // shift was outside a `try` in a function documented to return `null` (WD-10a).
        for (anchor in listOf(Long.MIN_VALUE, Long.MAX_VALUE, -1L, 0L)) {
            for (adjustment in listOf(Int.MIN_VALUE, Int.MAX_VALUE, 0, 2)) {
                // No assertion on the value: the contract is that it returns rather than throws.
                todayHijriWidgetData(anchorEpochDay = anchor, adjustmentDays = adjustment)
            }
        }
    }

    @Test
    fun negativeNumbersRenderRatherThanIndexingTheDigitTable() {
        // `formatNumber(-5, ARABIC_INDIC)` used to index the digit table at -3 (WD-10b). Latent today
        // — day numbers are 1..30 — but one sign change away on a render path, and only the Urdu
        // branch reaches the table at all.
        val data = assertNotNull(
            buildHijriMonthWidgetData(
                hijriYear = 1448,
                hijriMonth = 3,
                adjustmentDays = 0,
                numeralStyle = NumeralStyle.ARABIC_INDIC,
            ),
        )
        assertTrue(data.days.isNotEmpty())
        // The only reachable negative number a caller can pass, rendered through the same path.
        assertNotNull(
            buildHijriMonthWidgetData(
                hijriYear = 1448,
                hijriMonth = 3,
                adjustmentDays = -30,
                numeralStyle = NumeralStyle.ARABIC_INDIC,
            ),
        )
    }

    // ── malformed text is still rejected, with the same shape ────────────────

    @Test
    fun unparseableTextIsStillNull() {
        for (text in listOf("", "   ", "not json", "[1,2]", "\"str\"", "null")) {
            assertNull(WidgetOptionsJson.decodeOrReport(text), "should have rejected: '$text'")
            assertNull(WidgetOptionsJson.decodeOrNull(text), "should have rejected: '$text'")
        }
    }
}
