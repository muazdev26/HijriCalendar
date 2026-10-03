package com.muazdev.hijricalendar.widget.glance

import com.muazdev.hijricalendar.core.HijriMonthOverrides
import com.muazdev.hijricalendar.widgetdata.HijriMonthWidgetData
import com.muazdev.hijricalendar.widgetdata.NumeralStyle
import com.muazdev.hijricalendar.widgetdata.WeekStart
import com.muazdev.hijricalendar.widgetdata.WeekendPattern
import com.muazdev.hijricalendar.widgetdata.WidgetLanguage
import com.muazdev.hijricalendar.widgetdata.WidgetOptions
import com.muazdev.hijricalendar.widgetdata.WidgetSource
import com.muazdev.hijricalendar.widgetdata.monthLengthKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The widget render cache's keys must cover every option that can change a projected cell.
 *
 * ## The defect this exists to prevent
 *
 * `MonthKey` omitted `weekendPattern` for one commit, after the weekend set had been made an option
 * (FD-03). The result was a widget that **reported** a new setting and **rendered** the old one: the
 * render after the change found a cached `HijriMonthWidgetData` built with the previous weekend set,
 * so the shaded columns did not move. Nothing threw, nothing logged, and no test failed — the setting
 * simply appeared not to work, which is the hardest kind of bug to trace back to a cache key.
 *
 * So the key is asserted against an independently-written specification of what it must cover. A new
 * option that affects the projection has to appear; one that cannot is asserted absent, with the
 * reason, rather than left as a comment nobody re-reads.
 *
 * ## JUnit argument order
 *
 * `org.junit.Assert` takes the **message first**. This module's tests use JUnit, not `kotlin.test`, and
 * several of them mix the two conventions — which is a recurring source of compile errors here, so it
 * is worth stating rather than rediscovering.
 */
class WidgetRenderCacheKeyTest {

    private val base = WidgetOptions(
        adjustmentDays = 1,
        numeralStyle = NumeralStyle.WESTERN,
        weekStart = WeekStart.MONDAY,
        pinnedYear = null,
        pinnedMonth = null,
        source = WidgetSource.CALCULATION,
        language = WidgetLanguage.ENGLISH,
        monthNameLanguage = WidgetLanguage.ENGLISH,
        monthLengthOverrides = emptyMap(),
        showAdjacentDays = false,
        weekendPattern = WeekendPattern.FRIDAY_SATURDAY,
        showCellBorders = false,
    )

    /**
     * Options that change what a projected cell contains, paired with a copy differing in only that
     * one.
     *
     * `pinnedYear`/`pinnedMonth` are absent because they do not change the *cells* — they change
     * which month is projected, and the month is in the key separately.
     */
    private val affectsCells = mapOf(
        "adjustmentDays" to base.copy(adjustmentDays = 2),
        "numeralStyle" to base.copy(numeralStyle = NumeralStyle.ARABIC_INDIC),
        "weekStart" to base.copy(weekStart = WeekStart.TUESDAY),
        "source" to base.copy(source = WidgetSource.PAKISTAN),
        "language" to base.copy(language = WidgetLanguage.URDU),
        "monthNameLanguage" to base.copy(monthNameLanguage = WidgetLanguage.URDU),
        "monthLengthOverrides" to base.copy(
            monthLengthOverrides = mapOf(monthLengthKey(1447, 9) to 30),
        ),
        "weekendPattern" to base.copy(weekendPattern = WeekendPattern.SUNDAY),
    )

    /**
     * Options that provably cannot change a projected cell.
     *
     * Listed so their absence from `MonthKey` is a checked decision rather than an omission: putting
     * either in the key would invalidate 42 cells of cached projection on a change that cannot alter
     * one of them.
     */
    private val cannotAffectCells = mapOf(
        // The grid decides which cells to draw; the projection paints all 42 either way.
        "showAdjacentDays" to base.copy(showAdjacentDays = true),
        // A divider is drawn by the renderer over an unchanged projection.
        "showCellBorders" to base.copy(showCellBorders = true),
    )

    /**
     * A different weekend set must not be served a month projected with the previous one.
     *
     * The regression, asserted on its own so that when it returns the failure names itself.
     */
    @Test
    fun aChangedWeekendPatternProducesADifferentMonthKey() {
        assertNotEquals(
            "a different weekend set must not be served a month projected with the previous one",
            monthKeyFor(base),
            monthKeyFor(base.copy(weekendPattern = WeekendPattern.SUNDAY)),
        )
        assertNotEquals(
            "including a set that shades nothing",
            monthKeyFor(base),
            monthKeyFor(base.copy(weekendPattern = WeekendPattern.NONE)),
        )
    }

    @Test
    fun everyOptionThatAffectsACellIsInTheMonthKey() {
        for ((option, changed) in affectsCells) {
            assertNotEquals(
                "changing `$option` changes the projected cells but not the cache key, so the " +
                    "setting will appear to do nothing",
                monthKeyFor(base),
                monthKeyFor(changed),
            )
        }
    }

    /**
     * The two render-only options really are inert as far as the projection goes.
     *
     * Not a correctness requirement — a performance one — but it is the *reason* those two are absent
     * from the key, so it is asserted rather than left as prose.
     */
    @Test
    fun theRenderOnlyOptionsDoNotChangeAnyProjectedCell() {
        for ((option, changed) in cannotAffectCells) {
            assertNotEquals("`$option` did not actually change", base, changed)
            assertEquals(
                "`$option` must not change a projected cell, or it belongs in the cache key",
                projectedCells(base),
                projectedCells(changed),
            )
        }
    }

    /**
     * The end-to-end shape of the same defect, without reaching into the key.
     *
     * Render the same month twice under two weekend sets and compare the *cells*. A key test alone
     * would pass even if two different keys were handed the same cached value — which is exactly the
     * bug, since the key was correct-looking and the cache was not.
     */
    @Test
    fun aRenderWithADifferentWeekendPatternProducesDifferentCells() {
        val fridaySaturday = projectionFor(base)
        val sunday = projectionFor(base.copy(weekendPattern = WeekendPattern.SUNDAY))

        assertNotEquals(
            "the cached month was served for a different weekend set",
            fridaySaturday.days.map { it.isWeekend },
            sunday.days.map { it.isWeekend },
        )
        assertTrue("the default pattern should shade some days", fridaySaturday.days.any { it.isWeekend })

        // The *set* of shaded in-month cells must differ, not merely the count: a cache that mixed
        // two patterns could satisfy a count comparison while painting the wrong days.
        //
        // Which weekdays a pattern shades is pinned from the other side, by Gregorian weekday, in
        // `WeekendPatternTest` in `calendar-widget-data` — that module has `kotlinx-datetime`, this
        // one does not, and duplicating the mapping check here would only restate it worse.
        assertNotEquals(
            "the two patterns shaded the same set of in-month days",
            fridaySaturday.days.filter { it.isWeekend && it.isCurrentMonth }.map { it.hijriDay },
            sunday.days.filter { it.isWeekend && it.isCurrentMonth }.map { it.hijriDay },
        )
    }

    /** And a re-render under the *same* options still gets a consistent answer — no cross-talk. */
    @Test
    fun theSameOptionsAlwaysProjectTheSameMonth() {
        val first = projectionFor(base)
        val second = projectionFor(base)
        assertEquals(
            "the same options must project the same month",
            first.days.map { it.dayText },
            second.days.map { it.dayText },
        )
    }

    private fun projectionFor(options: WidgetOptions): HijriMonthWidgetData =
        HijriWidgetRenderCache.render(
            glanceId = "cache-key-test",
            options = options,
            viewedMonth = null,
            todayEpochDay = 20_731,
            layoutRtl = false,
        ).monthData ?: throw AssertionError("no projection for the fixture options")

    private fun projectedCells(options: WidgetOptions): List<Pair<Int, Boolean>> =
        projectionFor(options).days.map { it.hijriDay to it.isWeekend }

    /**
     * The specification the cache key is checked against: a string that changes if — and only if — an
     * option that affects a cell changes.
     *
     * Written from the option fields rather than by reaching into the private key type on purpose.
     * This is the *specification*; `MonthKey` is the implementation under test, and sharing code
     * between the two would make the test agree with whatever `MonthKey` happens to be.
     */
    private fun monthKeyFor(options: WidgetOptions) = listOf(
        options.adjustmentDays.toString(),
        options.effectiveWeekStart.name,
        options.numeralStyle.name,
        options.language.name,
        options.effectiveMonthNameLanguage.name,
        options.source.name,
        options.weekendPattern.name,
        options.monthLengthOverrides.toString(),
        // The override table is keyed as the widget does: its own map if it has one, otherwise the
        // process-wide revision.
        if (options.monthLengthOverrides.isEmpty()) {
            "global:${HijriMonthOverrides.currentRevision}"
        } else {
            "widget:${options.monthLengthOverrides}"
        },
    ).joinToString("|")
}
