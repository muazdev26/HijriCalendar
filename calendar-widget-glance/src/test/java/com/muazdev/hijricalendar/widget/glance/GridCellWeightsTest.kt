package com.muazdev.hijricalendar.widget.glance

import androidx.glance.text.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The day cell's two text weights.
 *
 * Reported as "the days in the grid is not bold as well", right after the weekday header was made
 * bold. The Hijri figure turned out to be **already** `Bold` — which is what made the report read as
 * a contradiction — so the cause was the Gregorian digit underneath it carrying no weight at all.
 * The eye weighs the pair, not the larger line: a bold figure over a plain digit still reads plain.
 *
 * These assert [GridCellWeights], which the two `Text` calls in `DayCell` reference directly. There
 * is a deliberate reason there is no test that calls `DayCell`: a `@Composable` cannot be invoked from
 * a JVM unit test, and the alternative — re-declaring the weights inside the test — would assert only
 * that the test file agrees with itself. Naming the constants was the only way to make a real
 * assertion about the rendered weights.
 */
class GridCellWeightsTest {

    /** The day figure is bold. It always was; this is the guard, not the fix. */
    @Test
    fun theHijriDayFigureIsBold() {
        assertEquals(
            "the Hijri day figure is the cell's hero and must be bold",
            FontWeight.Bold,
            GridCellWeights.HIJRI_DAY,
        )
    }

    /**
     * The Gregorian digit is Medium — not plain.
     *
     * Plain is what made the cell read as unbolded despite the bold figure above it.
     */
    @Test
    fun theGregorianDigitIsNotPlain() {
        assertEquals(
            "a plain-weight digit under a bold figure makes the whole cell read as unbolded",
            FontWeight.Medium,
            GridCellWeights.GREGORIAN_DAY,
        )
    }

    /**
     * The pair is a hierarchy: the figure outweighs the digit it sits above.
     *
     * Without this the two weights could both become `Bold` — every assertion above would still pass,
     * and the cell would have two competing numbers in it. `Medium` is the deliberate middle: enough
     * weight to look deliberate at 9-11sp, not enough to compete with the figure.
     */
    @Test
    fun theTwoLinesAreAHierarchyNotTwoHeroes() {
        assertTrue(
            "the figure must outweigh the digit (${GridCellWeights.HIJRI_DAY.value} vs " +
                "${GridCellWeights.GREGORIAN_DAY.value}); two equally bold numbers read as a mistake",
            GridCellWeights.HIJRI_DAY.value > GridCellWeights.GREGORIAN_DAY.value,
        )
    }
}
