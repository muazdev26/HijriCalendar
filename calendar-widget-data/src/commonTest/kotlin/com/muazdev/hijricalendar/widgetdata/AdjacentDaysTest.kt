package com.muazdev.hijricalendar.widgetdata

import com.muazdev.hijricalendar.core.CalendarMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * FD-02: `showAdjacentDays`, and the two distinct effects it has on a month grid.
 *
 * The tempting implementation is one line — filter the out-of-month cells and re-chunk — and it is
 * **wrong**, because a Hijri month can begin on any weekday. Filtering slides the 1st into column
 * zero and puts every day of the month under the wrong weekday heading: a grid that still looks
 * like a calendar and is off by the leading-day count. So the flag has two separate effects, and
 * they are asserted separately here:
 *
 * 1. cells are **blanked**, not removed, so the columns stay aligned; and
 * 2. whole trailing weeks are **dropped**, so a month takes five rows when it spans five.
 *
 * `calendar-ui` mirrors this arithmetic in `HijriCalendarGrid` rather than calling in here (it cannot
 * depend on the widget module), and its row count is asserted by a Compose test of its own.
 */
class AdjacentDaysTest {

    /**
     * The row count is the number of weeks the month spans — five for a 29-day month, which is the
     * whole point of the flag.
     *
     * A Hijri month is 29 or 30 days and a week is 7, so a 29-day month can never need six rows
     * once the neighbours are out of the way. Before this flag every month rendered six, so a
     * quarter of every grid was padding nobody asked for.
     */
    @Test
    fun hidingAdjacentDaysCollapsesTheGridToTheWeeksTheMonthSpans() {
        var sawFiveRowMonth = false
        var sawSixRowMonth = false

        forEachMonth { _, _, data ->
            val length = data.days.count { it.isCurrentMonth }

            // Derived from the month's geometry in the grid rather than by counting weeks that
            // contain a day — that would restate the implementation and pass no matter what it did.
            //
            // The 1st sits in the last `c` columns of the first week (the weeks that hold only
            // padding, if any, precede it), so the month spans `(c + length)` cells from the start
            // of the grid it begins in.
            //
            // Counting, rather than index arithmetic on [HijriMonthWidgetData.days]: that list is
            // reversed *per row* for an RTL widget, so a month's days are not a contiguous run of
            // indices and `firstIndex..firstIndex + length - 1` spans the wrong rows. Only the
            // column count is direction-independent.
            val leadingCells = CalendarMonth.DAYS_IN_WEEK -
                data.days.take(CalendarMonth.DAYS_IN_WEEK).count { it.isCurrentMonth }
            val expectedRows = (leadingCells + length + 6) / 7

            assertEquals(
                expectedRows,
                data.weeksToRender(showAdjacentDays = false).size,
                "a $length-day month starting $leadingCells columns in spans $expectedRows weeks",
            )
            assertEquals(
                length,
                data.weeksToRender(showAdjacentDays = false).sumOf { week ->
                    week.count { it.isCurrentMonth }
                },
                "every one of the month's own days must survive",
            )
            when {
                expectedRows < CalendarMonth.WEEKS_IN_MONTH -> sawFiveRowMonth = true
                expectedRows == CalendarMonth.WEEKS_IN_MONTH -> sawSixRowMonth = true
            }
        }

        assertTrue(
            sawFiveRowMonth,
            "the whole premise of the flag is that some months need fewer than six rows; none of " +
                "1440-1450 did, so the row arithmetic is not doing anything",
        )
        assertTrue(
            sawSixRowMonth,
            "some months genuinely need six rows even without their neighbours; if none do, the " +
                "row count is not a function of the month at all",
        )
    }

    /** Every row is a whole week of cells, always — a partial row would read as a rendering fault. */
    @Test
    fun everyRenderedWeekIsAFullWeekOfCells() {
        forEachMonth { year, month, data ->
            for (show in listOf(true, false)) {
                val weeks = data.weeksToRender(show)
                assertTrue(weeks.isNotEmpty(), "year=$year month=$month showAdjacentDays=$show rendered no rows")
                weeks.forEach { week ->
                    assertEquals(
                        CalendarMonth.DAYS_IN_WEEK,
                        week.size,
                        "year=$year month=$month showAdjacentDays=$show produced a partial week",
                    )
                }
            }
        }
    }

    /**
     * With the neighbours shown, every month is the full padded grid.
     *
     * This is the "unchanged" half of the change, and the half that has to be asserted: a widget
     * already configured to show them must keep rendering exactly what it rendered before.
     */
    @Test
    fun showingAdjacentDaysKeepsTheFullPaddedGrid() {
        forEachMonth { year, month, data ->
            assertEquals(CalendarMonth.TOTAL_DAYS, data.days.size, "year=$year month=$month")
            assertEquals(
                CalendarMonth.WEEKS_IN_MONTH,
                data.weeksToRender(showAdjacentDays = true).size,
                "year=$year month=$month",
            )
            assertTrue(
                data.days.any { !it.isCurrentMonth },
                "year=$year month=$month must contain neighbouring days for this to be meaningful",
            )
        }
    }

    /**
     * The 1st keeps its column.
     *
     * The regression this file exists to prevent, phrased so it holds for a reversed (RTL) list as
     * well as a normal one: **the 1st's index within its week is the same whether the neighbours are
     * shown or hidden.** Anything that slid it sideways — filtering the cells out instead of
     * blanking them — would move it, and would put every day of the month under the wrong weekday
     * heading while still looking like a calendar.
     */
    @Test
    fun theFirstOfTheMonthStaysInItsOwnWeekdayColumn() {
        var sawPaddedWeek = false

        forEachMonth { _, _, data ->
            val firstIndex = data.days.indexOfFirst { it.isCurrentMonth }
            if (firstIndex <= 0) return@forEachMonth

            val shown = data.weeksToRender(showAdjacentDays = true)
            val hidden = data.weeksToRender(showAdjacentDays = false)
            val columnWhenShown = shown.first { week -> week.any { it.isCurrentMonth } }
                .indexOfFirst { it.isCurrentMonth }
            val columnWhenHidden = hidden.first { week -> week.any { it.isCurrentMonth } }
                .indexOfFirst { it.isCurrentMonth }

            assertEquals(
                columnWhenShown,
                columnWhenHidden,
                "hiding the neighbours moved the 1st out of its weekday column",
            )
            if (columnWhenShown != 0) sawPaddedWeek = true
        }

        assertTrue(
            sawPaddedWeek,
            "no month had a partially-blank first week, so the alignment case was never exercised",
        )
    }

    /**
     * Exactly the out-of-month cells stop being painted, and nothing else.
     *
     * [paintsDay] is the whole of the blanking rule, so a divergence between it and
     * [weeksToRender] — a week trimmed while some of its cells were still supposed to be inked, or
     * vice versa — shows up here as a count mismatch.
     */
    @Test
    fun onlyTheOutOfMonthCellsStopBeingPainted() {
        forEachMonth { _, _, data ->
            val painted = data.days.count { data.paintsDay(it, showAdjacentDays = false) }
            val inMonth = data.days.count { it.isCurrentMonth }

            assertEquals(
                inMonth,
                painted,
                "every one of the month's own days must be painted when the neighbours are hidden",
            )
            assertTrue(
                data.days.any { !it.isCurrentMonth },
                "a month with no out-of-month days makes this assertion vacuous",
            )
        }
    }

    /**
     * The projection itself is always padded, whatever the flag says.
     *
     * The point of keeping the filter in the renderer: the widget render cache, the pager's page
     * arithmetic and `CalendarMonth.TOTAL_DAYS` all work in padded cells, and a builder-level filter
     * would have made all three grid-shaped. So `days` must come out of the projection at 42 every
     * time, and no week that is entirely blank may survive into the rendered rows.
     */
    @Test
    fun theProjectionIsAlwaysPaddedAndNoRenderedRowIsBlank() {
        forEachMonth { _, _, data ->
            assertEquals(
                CalendarMonth.TOTAL_DAYS,
                data.days.size,
                "the projection must be padded whatever the flag is",
            )
            assertTrue(
                data.weeksToRender(showAdjacentDays = false).all { week ->
                    week.any { it.isCurrentMonth }
                },
                "a rendered week with no day of this month in it means a whole blank row is still " +
                    "being laid out",
            )
        }
    }

    /** The default is off, on the field, on [WidgetOptions.DEFAULTS] and on the native factory. */
    @Test
    fun adjacentDaysAreHiddenByDefault() {
        assertFalse(WidgetOptions().showAdjacentDays, "the data-class default must be false")
        assertFalse(
            WidgetOptions.DEFAULTS.showAdjacentDays,
            "DEFAULTS is what a fresh install and the family mirror resolve to, and must agree with " +
                "the field default",
        )
        assertFalse(createWidgetOptions().showAdjacentDays, "the native factory must agree too")
    }

    /**
     * A blob written before this field existed decodes into the new behaviour.
     *
     * WD-08 in miniature. The field's default *is* the behaviour the field was added to change, so
     * "not configured" and "configured off" have to agree — otherwise every widget a user already
     * had would silently keep rendering the old grid, with no way to tell why.
     */
    @Test
    fun aPreFieldBlobDecodesWithTheFlagAtItsDefault() {
        val legacy = """
            {"adjustmentDays":1,"numeralStyle":"WESTERN","weekStart":"MONDAY","pinnedYear":null,
             "pinnedMonth":null,"source":"CALCULATION","language":"URDU","monthNameLanguage":"URDU",
             "monthLengthOverrides":{}}
        """.trimIndent()

        val decoded = assertNotNull(WidgetOptionsJson.decodeOrNull(legacy), "the legacy blob must decode")

        assertFalse(decoded.showAdjacentDays, "a pre-field blob must land on the new default")
        assertEquals(1, decoded.adjustmentDays, "no other field may be disturbed by the missing key")
        assertEquals("MONDAY", decoded.effectiveWeekStart.name)
    }

    /** And it survives a round trip once set — written explicitly, not positionally. */
    @Test
    fun theFlagRoundTripsThroughTheCodec() {
        for (show in listOf(true, false)) {
            val options = createWidgetOptions(language = WidgetLanguage.ENGLISH, showAdjacentDays = show)
            val text = WidgetOptionsJson.encode(options)

            assertTrue(
                "\"showAdjacentDays\":$show" in text,
                "encodeDefaults must write the flag explicitly, so a reader never has to know the " +
                    "default this release uses",
            )
            assertEquals(
                show,
                assertNotNull(WidgetOptionsJson.decodeOrNull(text), "the encoded blob must decode").showAdjacentDays,
            )
        }
    }

    /**
     * Every month of eleven Hijri years, in **both** reading directions.
     *
     * Both, because the projection pre-reverses [HijriMonthWidgetData.days] for an RTL widget (the
     * platform already mirrors a Glance `LinearLayout`, so the reversal is what cancels it). That
     * means a month's padding sits at the opposite end of the list depending on the widget's
     * language, and any logic phrased as "drop the trailing weeks" silently trims the wrong ones for
     * half the widgets in the world. Every assertion here therefore runs against both.
     */
    private inline fun forEachMonth(block: (year: Int, month: Int, data: HijriMonthWidgetData) -> Unit) {
        for (language in listOf(WidgetLanguage.ENGLISH, WidgetLanguage.URDU)) {
            val options = WidgetOptions.DEFAULTS.copy(language = language)
            for (year in 1440..1450) {
                for (month in 1..12) {
                    block(
                        year,
                        month,
                        assertNotNull(
                            buildHijriMonthWidgetData(hijriYear = year, hijriMonth = month, options = options),
                            "no projection was produced for $year-$month in ${language.name}",
                        ),
                    )
                }
            }
        }
    }
}
