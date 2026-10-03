package com.muazdev.hijricalendar.widgetdata

import com.muazdev.hijricalendar.core.HijriMonthLengths
import com.muazdev.hijricalendar.core.HijriMonthOverrides
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * WD-02: the header and the cells must describe the same Gregorian month.
 *
 * `buildHijriMonthWidgetData` built the grid with `toCalendarMonth(…, overrides = overrides)` and
 * then computed the header from a *second* call to `resolveGregorianMonthRange`. Both read the same
 * inputs, but at two different instants, and the override table is a CAS-published process global —
 * so an override landing in between produced a header naming one Gregorian month above cells
 * containing another. Silent, and in a Hijri↔Gregorian bridge widget the two halves contradicting
 * each other is the worst failure available.
 *
 * It is also what completed WD-01: once `overrides` is threaded in, a second call that does *not*
 * receive the parameter re-reads the global, so the header keeps ignoring the caller's table however
 * correctly the grid honours it.
 */
class GregorianHeaderAgreementTest {

    /**
     * The month name the header reports, cross-checked against the month the first cell of the
     * rendered month actually falls in. This is the assertion the ticket asks for: header and cells
     * agreeing, not the header agreeing with itself.
     */
    private fun assertHeaderMatchesCells(options: WidgetOptions) {
        val (year, month) = options.resolveGridMonth(HijriYearMonth(1448, 3))
        val data = assertNotNull(
            buildHijriMonthWidgetData(
                hijriYear = year,
                hijriMonth = month,
                options = options,
            ),
        )
        val firstCell = assertNotNull(
            data.days.firstOrNull { it.isCurrentMonth },
            "the projection painted no cell of its own month",
        )
        val cellDate = LocalDate.fromEpochDays(firstCell.gregorianEpochDay)
        // The year carries its era marker (FD-05), so the tail is "<year> <era>" and the year has to
        // be taken as the token before it. Reading the last token instead would silently compare the
        // *era* against a year and pass for any title — which is exactly what it did the moment the
        // marker was added.
        val headerMonthName = data.gregorianMonthTitle.substringBefore(' ')
        val headerYear = data.gregorianMonthTitle.substringAfterLast(' ').let { lastToken ->
            if (lastToken.toIntOrNull() != null) {
                lastToken
            } else {
                data.gregorianMonthTitle.substringBeforeLast(' ').substringAfterLast(' ')
            }
        }
        // `LocalDate.month.name` and `CalendarNames.englishGregorianMonths` are the same twelve
        // strings, so this needs no index arithmetic (and no `Month.number`, which is deprecated and
        // was the one member of this pair that behaved differently across targets).
        assertEquals(
            cellDate.month.name.lowercase(),
            headerMonthName.lowercase(),
            "header says '$headerMonthName' but the first painted cell is a ${cellDate.month.name}",
        )
        assertEquals(
            "${cellDate.year}",
            headerYear,
            "the header's year must be the cell's year",
        )
        assertTrue(
            data.gregorianMonthTitle.endsWith(" AD"),
            "and the header must still carry its era marker; it read " +
                "'${data.gregorianMonthTitle}'",
        )
    }

    @Test
    fun aPlainMonthAgrees() {
        assertHeaderMatchesCells(WidgetOptions(language = WidgetLanguage.ENGLISH))
    }

    @Test
    fun aPinnedMonthAgrees() {
        assertHeaderMatchesCells(
            WidgetOptions(
                language = WidgetLanguage.ENGLISH,
                pinnedYear = 1445,
                pinnedMonth = 12,
            ),
        )
    }

    @Test
    fun aPakistanMonthAgrees() {
        // Pakistan mode resolves the extent through the Ruet-e-Hilal table, a different space from
        // Umm al-Qura — which is precisely why re-deriving the range independently was able to
        // disagree.
        assertHeaderMatchesCells(
            WidgetOptions(language = WidgetLanguage.ENGLISH, source = WidgetSource.PAKISTAN),
        )
    }

    @Test
    fun aMonthWhoseLengthIsOverriddenAgrees() {
        // The case the second read could get wrong: the override table is what the two calls read
        // at different instants. A scoped table forcing a month to 29 days shifts every later
        // month's start, so the header and the cells genuinely could differ.
        val forced = HijriMonthLengths().apply { setMonthLength(1447, 1, 29) }
        val options = WidgetOptions(
            language = WidgetLanguage.ENGLISH,
            monthLengthOverrides = monthLengthsToMap(forced),
        )
        assertHeaderMatchesCells(options)
    }

    @Test
    fun theHeaderTracksTheProcessGlobalWhenTheOptionsCarryNoTable() {
        // The other half of the contract: an empty map means "follow the process-wide table", so a
        // change there has to reach the header as well as the cells.
        val before = headerFor(WidgetOptions(language = WidgetLanguage.ENGLISH))
        HijriMonthOverrides.setMonthLength(1447, 1, 29)
        try {
            assertEquals(
                before,
                headerFor(WidgetOptions(language = WidgetLanguage.ENGLISH)),
                "the header must not depend on anything but the table the grid used",
            )
        } finally {
            HijriMonthOverrides.clearAll()
        }
    }

    private fun headerFor(options: WidgetOptions): String {
        val (year, month) = options.resolveGridMonth(HijriYearMonth(1448, 3))
        return assertNotNull(
            buildHijriMonthWidgetData(hijriYear = year, hijriMonth = month, options = options),
        ).gregorianMonthTitle
    }
}
