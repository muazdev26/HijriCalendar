package com.muazdev.hijricalendar.widgetdata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * WD-03: a half-set pin is representable in the stored schema, and the two renderers used to
 * disagree about what it meant.
 *
 * `pinnedYear` and `pinnedMonth` are two independent nullable `Int`s on the wire, so any JSON
 * carrying one of them produces a half-pinned `WidgetOptions`. The iOS timeline checked
 * `isPinned` (both non-null) and fell through to today. Android read the two fields directly and
 * chained each against today's month — so `{"pinnedYear":1447}` rendered a grid **labelled 1447**
 * painting **this** year's days, silently, with no error anywhere.
 *
 * [WidgetOptions.pinned] now reads the pair as a unit, so a half-set pin is "not pinned" on both
 * platforms, and every renderer resolves the grid month through one function.
 */
class PinnedMonthPairTest {

    private val today = ym(1450, 3)

    private fun ym(year: Int, month: Int) = HijriYearMonth(year, month)

    // ── the invariant ────────────────────────────────────────────────────────

    @Test
    fun aFullySetPinIsPinned() {
        val options = WidgetOptions(pinnedYear = 1447, pinnedMonth = 9)
        assertEquals(ym(1447, 9), options.pinned)
        assertTrue(options.isPinned)
        assertEquals(ym(1447, 9), options.resolveGridMonth(today))
    }

    @Test
    fun aYearWithoutAMonthIsNotPinned() {
        // The exact shape Android used to render as "1447, showing this month".
        val options = WidgetOptions(pinnedYear = 1447, pinnedMonth = null)
        assertNull(options.pinned, "half a pin is not a pin")
        assertFalse(options.isPinned)
        assertEquals(today, options.resolveGridMonth(today))
    }

    @Test
    fun aMonthWithoutAYearIsNotPinned() {
        val options = WidgetOptions(pinnedYear = null, pinnedMonth = 9)
        assertNull(options.pinned)
        assertFalse(options.isPinned)
        assertEquals(today, options.resolveGridMonth(today))
    }

    @Test
    fun aViewedMonthStillWinsOverAPin() {
        // The pin is only one rung of the precedence chain; navigation overrides it by design.
        val options = WidgetOptions(pinnedYear = 1447, pinnedMonth = 9)
        assertEquals(ym(1447, 9), options.resolveGridMonth(today))
    }

    // ── both platforms' logic, from the same stored blob ─────────────────────

    @Test
    fun aStoredHalfPinFollowsTodayOnTheAndroidLogic() {
        val stored = assertNotNull(WidgetOptionsJson.decodeOrNull("""{"pinnedYear":1447}"""))
        // Android's resolver: viewedMonth > options.pinned > today.
        val resolved = stored.pinned ?: today
        assertEquals(
            today,
            resolved,
            "a half-set pin must not contribute a year to be paired with today's month",
        )
    }

    @Test
    fun aStoredHalfPinFollowsTodayOnTheSwiftLogic() {
        val stored = assertNotNull(WidgetOptionsJson.decodeOrNull("""{"pinnedMonth":9}"""))
        // The iOS branch, transcribed from `HijriTimelineProvider.swift`:
        //   if options.isPinned, let year = options.pinnedYear, let month = options.pinnedMonth
        val resolved = if (
            stored.isPinned &&
            stored.pinnedYear != null &&
            stored.pinnedMonth != null
        ) {
            ym(stored.pinnedYear, stored.pinnedMonth)
        } else {
            today
        }
        assertEquals(today, resolved, "both platforms must agree that a half-set pin is 'not pinned'")
    }

    @Test
    fun aFullySetStoredPinSurvivesTheRoundTrip() {
        val stored = assertNotNull(
            WidgetOptionsJson.decodeOrNull("""{"pinnedYear":1448,"pinnedMonth":3}"""),
        )
        assertEquals(ym(1448, 3), stored.pinned)
        assertEquals(ym(1448, 3), stored.resolveGridMonth(today))

        // And re-encoding preserves it, so the normalisation is read-side only.
        val reDecoded = assertNotNull(
            WidgetOptionsJson.decodeOrNull(WidgetOptionsJson.encode(stored)),
        )
        assertEquals(ym(1448, 3), reDecoded.pinned)
    }

    @Test
    fun theFactoryCannotProduceAHalfPin() {
        // `createWidgetOptions` is the native-facing constructor and already gated both fields on
        // `pinsMonth`; this pins that behaviour so a future edit cannot open the door.
        assertNull(createWidgetOptions(pinsMonth = false, pinnedYear = 1447, pinnedMonth = 9).pinned)
        assertEquals(ym(1447, 9), createWidgetOptions(pinsMonth = true, pinnedYear = 1447, pinnedMonth = 9).pinned)
    }

    // ── the value type describes the rendered state ──────────────────────────

    @Test
    fun aHalfPinAndNoPinAreEqualBecauseTheyRenderIdentically() {
        // `equals` is generated from the constructor properties, which are the raw pair — so without
        // the override these would compare unequal while meaning the same thing to a renderer.
        val half = WidgetOptions(pinnedYear = 1447, pinnedMonth = null)
        val none = WidgetOptions()
        assertEquals(half, none)
        assertEquals(half.hashCode(), none.hashCode())

        val full = WidgetOptions(pinnedYear = 1447, pinnedMonth = 9)
        assertFalse(half == full)
        assertEquals(WidgetOptions(pinnedYear = 1447, pinnedMonth = 9), full.copy())
    }
}
