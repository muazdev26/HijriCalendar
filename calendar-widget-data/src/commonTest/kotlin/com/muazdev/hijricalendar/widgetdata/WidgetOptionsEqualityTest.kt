package com.muazdev.hijricalendar.widgetdata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Every [WidgetOptions] field must be compared by `equals` and mixed into `hashCode`.
 *
 * ## Why this test exists at all
 *
 * `WidgetOptions` normalises its values (a half-set pin is "not pinned", WD-03), so it has a
 * hand-written `equals`/`hashCode` rather than the generated pair. A generated one falls behind its
 * class loudly — the compiler regenerates it. A hand-written one falls behind **silently**: nothing
 * tells you a field added three tickets ago is not being compared.
 *
 * The failure is total and invisible. Three options — `showAdjacentDays`, `weekendPattern` and
 * `showCellBorders` — shipped in exactly that state, and the symptom was that the three settings chips
 * in the sample app did nothing at all: the settings screen opens with
 * `if (newOptions == options) return`, so `copy(showCellBorders = true)` compared *equal* to its
 * source, the write was skipped, and the widget never changed. Three working render paths that nothing
 * could reach.
 *
 * `HijriWidgetConfigTest.widgetOptionsSaver_roundTripsEveryOptionItDeclares` exists for the identical
 * reason on the saver side, by walking the declared fields. This is the same guard for the comparison.
 */
class WidgetOptionsEqualityTest {

    private val base = WidgetOptions(
        adjustmentDays = 1,
        numeralStyle = NumeralStyle.WESTERN,
        weekStart = WeekStart.MONDAY,
        pinnedYear = 1447,
        pinnedMonth = 9,
        source = WidgetSource.CALCULATION,
        language = WidgetLanguage.ENGLISH,
        monthNameLanguage = WidgetLanguage.ENGLISH,
        monthLengthOverrides = mapOf("1447-9" to 30),
        showAdjacentDays = true,
        weekendPattern = WeekendPattern.SUNDAY,
        showCellBorders = true,
        theme = WidgetTheme.DARK,
        dateDisplayMode = WidgetDateDisplayMode.GREGORIAN_ONLY,
    )

    /** Each field paired with a copy of [base] that differs in *only* that field. */
    internal val oneFieldChanged: Map<String, WidgetOptions> = mapOf(
        "adjustmentDays" to base.copy(adjustmentDays = 2),
        "numeralStyle" to base.copy(numeralStyle = NumeralStyle.ARABIC_INDIC),
        "weekStart" to base.copy(weekStart = WeekStart.TUESDAY),
        "pinnedYear" to base.copy(pinnedYear = 1448),
        "pinnedMonth" to base.copy(pinnedMonth = 10),
        "source" to base.copy(source = WidgetSource.PAKISTAN),
        "language" to base.copy(language = WidgetLanguage.URDU),
        "monthNameLanguage" to base.copy(monthNameLanguage = WidgetLanguage.URDU),
        "monthLengthOverrides" to base.copy(monthLengthOverrides = mapOf("1448-2" to 29)),
        "showAdjacentDays" to base.copy(showAdjacentDays = false),
        "weekendPattern" to base.copy(weekendPattern = WeekendPattern.NONE),
        "showCellBorders" to base.copy(showCellBorders = false),
        "theme" to base.copy(theme = WidgetTheme.LIGHT),
        "dateDisplayMode" to base.copy(dateDisplayMode = WidgetDateDisplayMode.HIJRI_ONLY),
    )

    /**
     * Changing any single field makes the value unequal.
     *
     * The assertion a settings screen depends on: its `update` helper short-circuits on equality, so
     * an `equals` that misses a field makes that control dead.
     */
    @Test
    fun changingAnyOneFieldMakesTheOptionsUnequal() {
        for ((field, changed) in oneFieldChanged) {
            assertNotEquals(
                base,
                changed,
                "changing only `$field` must change the value; an equals that ignores it makes " +
                    "every settings control for that field a no-op",
            )
        }
    }

    /**
     * `hashCode` moves with `equals`.
     *
     * Checked separately from `equals` because a stale `hashCode` is invisible in every assertion
     * above and catastrophic in exactly one place — the widget render cache, whose whole design is
     * keyed on options.
     */
    @Test
    fun hashCodeMovesWithEveryFieldToo() {
        for ((field, changed) in oneFieldChanged) {
            assertNotEquals(
                base.hashCode(),
                changed.hashCode(),
                "changing only `$field` must change hashCode; the render cache keys on it",
            )
        }
    }

    /**
     * `equals` still compares the *normalised* values, which is the reason it is hand-written.
     *
     * If someone "simplifies" it back to the generated pair this still passes — so this is the test
     * that says the normalisation is load-bearing, and the generated form would fail it.
     */
    @Test
    fun theNormalisationIsStillWhatEqualsUses() {
        val halfPin = WidgetOptions(pinnedYear = 1447, pinnedMonth = null)
        val noPin = WidgetOptions(pinnedYear = null, pinnedMonth = null)

        assertEquals(
            noPin,
            halfPin,
            "a half-set pin means the same thing as no pin (WD-03), and equals must say so",
        )
        assertEquals(noPin.hashCode(), halfPin.hashCode(), "and hashCode with it")

        // An impossible month is dropped by `pinned` too, so it is also "not pinned".
        val impossibleMonth = WidgetOptions(pinnedYear = 1447, pinnedMonth = 13)
        assertEquals(noPin, impossibleMonth, "a month outside 1..12 is not a pin")
    }

    /** A real difference is still a difference — the normalisation must not swallow one. */
    @Test
    fun aFullyDifferentValueIsNotEqual() {
        val other = base.copy(
            language = WidgetLanguage.URDU,
            weekendPattern = WeekendPattern.FRIDAY_ONLY,
            pinnedYear = 1400,
            pinnedMonth = 1,
        )
        assertNotEquals(base, other)
        assertTrue(base.toString() != other.toString(), "toString must reflect the fields too")
    }
}
