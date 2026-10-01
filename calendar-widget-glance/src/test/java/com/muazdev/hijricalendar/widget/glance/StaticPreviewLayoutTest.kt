package com.muazdev.hijricalendar.widget.glance

import com.muazdev.hijricalendar.core.CalendarMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * WG-14: the four hand-maintained `previewLayout` files must keep the same *shape* as the live
 * render.
 *
 * **Why this needs a gate.** There are three preview tiers and only one of them is generated from
 * the same code as the widget. Android 15+ gets `providePreview` / `setWidgetPreviews`, built
 * through `buildRenderData` (WG-11), so it cannot drift. Android 12-14 gets a `previewLayout` XML
 * inflated by the framework, and anything older gets a vector drawable. The middle tier is a
 * hand-written mirror of a Glance composition, maintained by hand and checked by nothing — and it
 * was already wrong when this was written: four grid rows where the widget paints six, no Gregorian
 * sub-digit under any day, and no dimming on the out-of-month days.
 *
 * A preview that is missing a week does not look broken. It looks like a calendar. That is what
 * makes it worth a test rather than a review note.
 *
 * **What is asserted, and what cannot be.** Structure and the presence of each visual tier, checked
 * against the live render's own constants (`CalendarMonth.TOTAL_DAYS` and `DAYS_IN_WEEK`) so the two
 * cannot drift apart silently. The *dates* are not asserted and cannot be: a static preview is a
 * fixed snapshot, so asserting them would be asserting that a stale snapshot is fresh. Colour
 * *values* are not asserted either — that would only prove the XML says what the XML says — but both
 * the out-of-month and the in-month tiers must be present, so neither can be deleted wholesale.
 */
class StaticPreviewLayoutTest {

    private fun layout(name: String): File {
        // A unit test's working directory is the module project directory.
        val file = File("src/main/res/layout/$name.xml")
        assertTrue(
            "preview layout not found at ${file.absolutePath}; if the res directory moved, " +
                "fix this path rather than deleting this test",
            file.isFile,
        )
        return file
    }

    private fun root(name: String): Element {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
        return factory.newDocumentBuilder().parse(layout(name)).documentElement
            .also { assertEquals("root of $name", "LinearLayout", it.tagName) }
    }

    private fun gridPreview(): Element = root("hijri_widget_preview_layout")

    private fun Element.children(): List<Element> = buildList {
        val nodes = childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) add(node as Element)
        }
    }

    private fun Element.attr(name: String): String? = getAttribute(name).takeIf { it.isNotEmpty() }

    /** This element *and* its descendants. */
    private fun Element.deep(): List<Element> = buildList {
        add(this@deep)
        children().forEach { addAll(it.deep()) }
    }

    private fun Element.deepAttr(name: String): List<String> = deep().mapNotNull { it.attr(name) }

    /**
     * The grid's week rows, identified structurally rather than by index: a horizontal
     * `LinearLayout` whose seven children all carry `layout_weight="1"`, matching the live
     * `MonthGrid`'s `days.chunked(7)` rows.
     */
    private fun Element.gridRows(): List<Element> = children().filter { row ->
        row.tagName == "LinearLayout" &&
            row.attr("android:orientation") == "horizontal" &&
            row.attr("android:layout_height") == "0dp" &&
            row.children().size == CalendarMonth.DAYS_IN_WEEK &&
            row.children().all { it.attr("android:layout_weight") == "1" }
    }

    /**
     * The grid's cells and the today cell among them, from a **single** parse of the file.
     *
     * One parse on purpose: [root] re-reads the file, so two calls produce elements from two
     * different DOMs, and `Element` does not override `equals` — a `todayCell in todayCells()`
     * across two parses is an identity comparison between documents and silently always false.
     * A test that re-parses per lookup has no way to notice that.
     */
    private data class GridCells(val all: List<Element>, val today: List<Element>)

    private fun gridCells(): GridCells {
        val cells = gridPreview().gridRows().flatMap { it.children() }
        return GridCells(
            all = cells,
            today = cells.filter { cell ->
                cell.deepAttr("android:background").any { it.contains("widget_today_background") }
            },
        )
    }

    @Test
    fun theStaticGridPaintsAsManyCellsAsTheLiveRender() {
        // The assertion that matters. `CalendarMonth.TOTAL_DAYS` is the same constant the live
        // `MonthGrid` chunks into rows, so this cannot go stale relative to the widget: change the
        // grid and the preview fails with it.
        val painted = gridCells().all.size
        assertEquals(
            "static preview paints $painted cells; the live render paints " +
                "${CalendarMonth.TOTAL_DAYS}",
            CalendarMonth.TOTAL_DAYS,
            painted,
        )
    }

    @Test
    fun theStaticGridHasTheSameNumberOfWeeksAsTheLiveRender() {
        val weeks = gridPreview().gridRows().size
        assertEquals(
            "static preview has $weeks week rows; the live render has " +
                "${CalendarMonth.TOTAL_DAYS / CalendarMonth.DAYS_IN_WEEK}",
            CalendarMonth.TOTAL_DAYS / CalendarMonth.DAYS_IN_WEEK,
            weeks,
        )
    }

    @Test
    fun exactlyOneCellIsHighlightedAsToday() {
        assertEquals("exactly one cell should carry today's fill", 1, gridCells().today.size)
    }

    @Test
    fun everyOtherCellCarriesBothDigitsLikeTheLiveDayCell() {
        // `DayCell` renders the Hijri day *and* the Gregorian day in every cell. The old preview
        // showed the Hijri digit alone everywhere except the highlighted cell, so the picker
        // advertised a widget with one line per day while the placed widget has two.
        //
        // The today cell reaches two lines differently — two `TextView`s inside a filled container,
        // because it needs the background — so it is exempt. The rest must use the `\n` escape,
        // which is the only thing that can work: a literal newline inside an XML attribute value is
        // normalised to a space by any conformant parser before AAPT sees it, so a preview written
        // with real newlines renders every cell on one line and looks fine.
        val cells = gridCells()
        val singleDigit = cells.all
            .filterNot { it in cells.today }
            .filter { cell -> cell.deepAttr("android:text").none { it.contains("\\n") } }
        assertTrue(
            "every cell should carry a Hijri digit and a Gregorian digit on a second line; " +
                "offending cells: ${singleDigit.map { it.deepAttr("android:text") }}",
            singleDigit.isEmpty(),
        )
    }

    @Test
    fun everyVisualTierTheLiveRenderUsesIsPresent() {
        // The live render dims out-of-month days to `primaryText.copy(alpha = 0.38f)` and shades
        // weekends. The alpha is flattened into `widget_text_muted` for the preview (a
        // `previewLayout` is inflated by the framework, not by Glance, so it cannot apply one).
        // Asserting the tier is present, not the hex value — that would only prove the XML says
        // what the XML says.
        val root = gridPreview()
        val referenced = root.deepAttr("android:textColor") + root.deepAttr("android:background")
        listOf(
            "widget_text_primary" to "in-month days",
            "widget_text_muted" to "out-of-month days",
            "widget_weekend_text" to "weekend days",
            "widget_today_background" to "today's fill",
            "widget_on_today" to "today's content",
        ).forEach { (token, what) ->
            assertTrue(
                "no $what styling ($token) in the static grid preview",
                referenced.any { it.contains(token) },
            )
        }
    }

    @Test
    fun theStaticGridHasOneWeekdayHeaderPerDay() {
        // Selected on `layout_height`, not on child tags: four of the six grid rows are also
        // horizontal, seven-wide and all-`TextView` (every row but the one holding the today cell),
        // so a selector that ignored height would match five rows instead of one.
        val header = gridPreview().children().single { row ->
            row.attr("android:orientation") == "horizontal" &&
                row.attr("android:layout_height") == "wrap_content" &&
                row.children().size == CalendarMonth.DAYS_IN_WEEK &&
                row.children().all { it.tagName == "TextView" }
        }
        assertEquals(
            "weekday header count",
            CalendarMonth.DAYS_IN_WEEK,
            header.children().size,
        )
    }

    @Test
    fun theStaticGridKeepsBothNavigationArrows() {
        // The arrows are the grid's only interactive controls (WG-12), so a preview missing them
        // misrepresents what the user can do with the widget they are about to place.
        val arrows = gridPreview().deep().filter { it.tagName == "ImageView" }
        assertEquals("the grid preview should show both nav arrows", 2, arrows.size)
        assertEquals(
            "both arrows should be the nav chevrons",
            setOf("@drawable/ic_arrow_left", "@drawable/ic_arrow_right"),
            arrows.mapNotNull { it.attr("android:src") }.toSet(),
        )
    }

    @Test
    fun theStaticGridNamesBothTheHijriAndTheGregorianMonthOnOneLine() {
        // The live header is a single centred line carrying both (WD-02). The preview used to carry
        // only a Gregorian range, which no longer matches anything the widget renders.
        val titles = gridPreview().deepAttr("android:text").filter { it.contains("2026") }
        assertEquals("expected exactly one combined month+year title", 1, titles.size)
        assertTrue(
            "the title should carry both calendars, got \"${titles.single()}\"",
            titles.single().contains('·'),
        )
    }

    @Test
    fun theTodayStripPreviewShowsBothDateHalves() {
        // Two halves, each a bold day figure over a month and year, matching the live `DateSide`.
        val texts = root("hijri_today_widget_preview_layout").deepAttr("android:text")
        assertEquals("the strip shows two figures and two captions", 4, texts.size)
    }

    @Test
    fun theTilePreviewsShowADayOverAMonthAndNoYear() {
        // Matches the live `DateTileRoot`: a day figure and a month. The year is deliberately
        // absent — the tile is too small for one — so the count is the assertion.
        listOf("hijri_date_widget_preview_layout", "gregorian_date_widget_preview_layout")
            .forEach { name ->
                val texts = root(name).deepAttr("android:text")
                assertEquals("$name should show exactly a day and a month", 2, texts.size)
            }
    }
}
