package com.muazdev.hijricalendar.widget.glance

import android.content.Context
import android.content.ContextWrapper
import com.muazdev.hijricalendar.widgetdata.HijriMonthWidgetData
import com.muazdev.hijricalendar.widgetdata.HijriYearMonth
import com.muazdev.hijricalendar.widgetdata.TodayHijriWidgetData
import com.muazdev.hijricalendar.widgetdata.WidgetLanguage
import com.muazdev.hijricalendar.widgetdata.WidgetOptions
import com.muazdev.hijricalendar.widgetdata.WidgetSource
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * WG-04 and WG-11: the render cache's *properties*, which are all load-bearing and none of which
 * were true when these tests were written.
 *
 *  - **Bounded.** A key is not the widget id — it also carries the anchor day, the resolved month,
 *    the adjustment, the week start, the digit style, both languages, the source, the reading
 *    direction and the override table. The cache was a pair of unbounded `HashMap`s whose KDoc
 *    claimed "at most one month + one today per widget instance", true only when the key *was* the
 *    id. And nothing in the module ever learns a widget was removed, so an LRU is the only correct
 *    design — a per-instance map cleaned on removal has no removal signal to hang off.
 *  - **The build runs outside the cache's lock.** It used to run inside a process-wide `synchronized`,
 *    so a settings preview's cold Pakistan century-table build could serialise a real widget's
 *    render behind it — and a Glance composition can be the main thread.
 *  - **One pipeline.** The preview used to bypass the cache for the month projection and use it for
 *    the "today" one, so "the preview cannot drift from the widget" was true of a function and false
 *    of a path.
 */
class HijriWidgetRenderCacheTest {

    /** Distinct widget ids, to grow the cache the way a real device would. */
    private fun options(
        language: WidgetLanguage = WidgetLanguage.URDU,
        source: WidgetSource = WidgetSource.CALCULATION,
        adjustmentDays: Int = 0,
    ) = WidgetOptions(language = language, source = source, adjustmentDays = adjustmentDays)

    @Test
    fun theCacheStaysBoundedUnderManyDistinctKeys() {
        repeat(4) { round ->
            repeat(HijriWidgetRenderCache.MAX_CACHE_ENTRIES * 3) { i ->
                val rendered = HijriWidgetRenderCache.render(
                    glanceId = "widget-$round-$i",
                    options = options(),
                    viewedMonth = null,
                    todayEpochDay = TODAY + i,
                    layoutRtl = false,
                )
                // Force the month projection too, which is the large one.
                assertNotNull(rendered.monthData, "render $round/$i produced no month data")
            }
        }
        val total = HijriWidgetRenderCache.cachedEntryCountForTest
        assertTrue(
            total <= 2 * HijriWidgetRenderCache.MAX_CACHE_ENTRIES,
            "cache grew to $total entries; it is bounded by construction, so this is a real defect",
        )
        assertTrue(total > 0, "the cache should still be warm")
    }

    @Test
    fun aRepeatedRenderIsServedFromTheCache() {
        val first = HijriWidgetRenderCache.render(
            glanceId = "repeat",
            options = options(),
            viewedMonth = ym(1448, 3),
            todayEpochDay = TODAY,
            layoutRtl = false,
        ).monthData
        val second = HijriWidgetRenderCache.render(
            glanceId = "repeat",
            options = options(),
            viewedMonth = ym(1448, 3),
            todayEpochDay = TODAY,
            layoutRtl = false,
        ).monthData
        // Referential identity is the assertion: a rebuild would allocate a second equal object.
        assertTrue(first === second, "the second render should have come from the cache")
    }

    @Test
    fun theCacheKeyTracksEverythingThatChangesTheOutput() {
        // The durable form of WG-04's key-completeness point. Each of these changes the projection,
        // so each must produce a *different* month projection rather than a stale hit.
        val baseline = HijriWidgetRenderCache.render(
            glanceId = "key",
            options = options(),
            viewedMonth = ym(1448, 3),
            todayEpochDay = TODAY,
            layoutRtl = false,
        ).monthData

        val variants = listOf(
            "language" to HijriWidgetRenderCache.render(
                "key", options(language = WidgetLanguage.ENGLISH), ym(1448, 3), TODAY, false,
            ).monthData,
            "source" to HijriWidgetRenderCache.render(
                "key", options(source = WidgetSource.PAKISTAN), ym(1448, 3), TODAY, false,
            ).monthData,
            "adjustment" to HijriWidgetRenderCache.render(
                "key", options(adjustmentDays = 2), ym(1448, 3), TODAY, false,
            ).monthData,
            "viewedMonth" to HijriWidgetRenderCache.render(
                "key", options(), ym(1448, 4), TODAY, false,
            ).monthData,
            "rightToLeft" to HijriWidgetRenderCache.render(
                "key", options(), ym(1448, 3), TODAY, true,
            ).monthData,
        )
        for ((what, data) in variants) {
            assertTrue(
                data !== baseline && data != baseline,
                "changing $what must not reuse the cached projection",
            )
        }
    }

    @Test
    fun theCacheKeyMonthMatchesWhatTheWidgetRenders() {
        // WG-11's dangerous case: the key is computed from one resolution of the grid month and the
        // data from another. If they disagree, the cache hands back a correct-looking grid for the
        // wrong month with no error anywhere.
        val cases = listOf(
            "today-following" to (null to options()),
            "pinned" to (null to options().copy(pinnedYear = 1445, pinnedMonth = 12)),
            "viewed" to (ym(1441, 7) to options()),
        )
        for ((name, pair) in cases) {
            val viewed = pair.first
            val opts = assertNotNull(pair.second)
            val rendered = HijriWidgetRenderCache.render(
                glanceId = "key-$name",
                options = opts,
                viewedMonth = viewed,
                todayEpochDay = TODAY,
                layoutRtl = false,
            )
            val expected = assertNotNull(
                resolveGridMonth(opts, viewed, assertNotNull(rendered.todayHijri)),
                name,
            )
            val data = assertNotNull(rendered.monthData, name)
            assertEquals(
                expected.year,
                data.hijriYear,
                "$name: the cached key must resolve the same year the projection rendered",
            )
            assertEquals(
                expected.month,
                data.hijriMonth,
                "$name: the cached key must resolve the same month the projection rendered",
            )
        }
    }

    @Test
    fun aHalfSetPinDoesNotProduceAKeyedGridOfTheWrongMonth() {
        // WD-03's symptom, reached through the cache: a stored year with no month used to be paired
        // with *today's* month, so the key said 1447 and the data was this month's grid.
        val half = options().copy(pinnedYear = 1447, pinnedMonth = null)
        val rendered = HijriWidgetRenderCache.render(
            glanceId = "half-pin",
            options = half,
            viewedMonth = null,
            todayEpochDay = TODAY,
            layoutRtl = false,
        )
        val today = assertNotNull(rendered.todayHijri)
        val data: HijriMonthWidgetData = assertNotNull(rendered.monthData)
        assertEquals(
            today.hijriYear,
            data.hijriYear,
            "a half-set pin must not contribute a year",
        )
        assertEquals(today.hijriMonth, data.hijriMonth)
    }

    @Test
    fun theAnchorDayIsKeyedOnTheTodayProjection() {
        // The month grid does *not* depend on the anchor day — a viewed month pins it — so this
        // asserts the keying where it actually matters, on the "today" projection.
        val first = HijriWidgetRenderCache.today("anchor", options(), TODAY)
        val second = HijriWidgetRenderCache.today("anchor", options(), TODAY + 1)
        assertTrue(first !== second, "a different day must not reuse today's projection")
        assertTrue(
            second?.hijriDay != first?.hijriDay || second?.hijriMonth != first?.hijriMonth,
            "the two days should differ",
        )
    }

    @Test
    fun aPreviewRecomposeHitsTheCacheAndAddsNoEntry() {
        // The settings preview recomposes on every option touch. Two properties:
        //   * it goes through the cache at all (it used to call the builders directly), and
        //   * the second recompose is an *identity* hit rather than a new entry.
        // The widget's own id and the preview's id are deliberately different keys — they are
        // different widget instances — so this asserts one pipeline, not one shared slot.
        val opts = options()
        val first = HijriWidgetRenderCache.render(
            glanceId = HijriWidgetRenderCache.PREVIEW_CACHE_ID,
            options = opts,
            viewedMonth = null,
            todayEpochDay = TODAY,
            layoutRtl = false,
        )
        val afterFirst = HijriWidgetRenderCache.cachedEntryCountForTest
        val second = HijriWidgetRenderCache.render(
            glanceId = HijriWidgetRenderCache.PREVIEW_CACHE_ID,
            options = opts,
            viewedMonth = null,
            todayEpochDay = TODAY,
            layoutRtl = false,
        )
        val afterSecond = HijriWidgetRenderCache.cachedEntryCountForTest

        assertTrue(first.monthData === second.monthData, "a recompose must be served from the cache")
        assertEquals(afterFirst, afterSecond, "a recompose must not grow the cache")
        assertTrue(afterFirst > 0, "the preview path must have populated the cache")
    }

    @Test
    fun theWidgetAndThePreviewGetSeparateCacheSlots() {
        // They are different instances, so sharing a slot would let the settings screen's preview
        // evict (or be served) the home-screen widget's projection.
        val opts = options()
        val widget = HijriWidgetRenderCache.render(
            glanceId = "widget-1",
            options = opts,
            viewedMonth = null,
            todayEpochDay = TODAY,
            layoutRtl = false,
        )
        val preview = HijriWidgetRenderCache.render(
            glanceId = HijriWidgetRenderCache.PREVIEW_CACHE_ID,
            options = opts,
            viewedMonth = null,
            todayEpochDay = TODAY,
            layoutRtl = false,
        )
        // Referential, not equality: `HijriMonthWidgetData` is a data class, so two separate builds
        // of the same month are `==`. What must differ is that they are separate *cached objects*.
        assertTrue(
            widget.monthData !== preview.monthData,
            "distinct instances must not share a cache slot",
        )
    }

    @Test
    fun todayProjectionsAreCachedTooAndStillBounded() {
        val first: TodayHijriWidgetData? =
            HijriWidgetRenderCache.today("today-widget", options(), TODAY)
        val second = HijriWidgetRenderCache.today("today-widget", options(), TODAY)
        assertTrue(first === second)
        assertTrue(
            HijriWidgetRenderCache.cachedEntryCountForTest <=
                2 * HijriWidgetRenderCache.MAX_CACHE_ENTRIES,
        )
    }

    private fun ym(year: Int, month: Int) = HijriYearMonth(year, month)

    private companion object {
        /** A fixed day so the tests do not depend on the wall clock. */
        const val TODAY = 20_731L

        /**
         * `computeLayoutRtl` is not exercised here (it needs a real `Configuration`), and the
         * recording paths below never dereference the context.
         */
        val context: Context = ContextWrapper(null)
    }
}
