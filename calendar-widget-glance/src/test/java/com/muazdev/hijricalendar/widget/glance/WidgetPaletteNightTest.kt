package com.muazdev.hijricalendar.widget.glance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * FD-07: every colour the widget paints must exist in **both** `values/` and `values-night/`, and the
 * two must differ.
 *
 * That is the whole fix, and it is worth being precise about the mechanism. The palette used to resolve
 * `context.getColor(R.color.x)` at compose time and hand Glance a literal int. A number carries no idea
 * of where it came from, so the launcher could not re-resolve it, and a night-mode switch had to
 * invalidate the whole `RemoteViews` and rebuild it — the widget went blank while the new one was
 * composed. That is the "restart" the report describes.
 *
 * The palette is now built from `@ColorRes` ids, so what Glance serialises is a resource reference and
 * the launcher resolves it against **its own** configuration at bind time. A theme switch re-resolves
 * inside the existing view: no re-compose, no invalidation, no placeholder.
 *
 * A colour with no night variant is invisible in that scheme — it simply keeps its day value — which is
 * exactly the defect this caught: `widget_text_muted` and `widget_text_faint` existed only in `values/`,
 * so a dimmed day figure was the one thing on the widget that could not follow the theme. They are now
 * `widget_day_out_of_month` and `widget_day_out_faint` with night variants, alongside a third
 * `widget_day_gregorian_sub`, because a `ColorProvider` cannot carry an alpha and re-resolving one per
 * render is what this change removed.
 *
 * ## Why the XML and not a `Context`
 *
 * This module has no Robolectric and no `androidx.test`, so a local unit test cannot obtain a `Context`
 * and resolve a real colour — and a stubbed `android.jar` answers `0` for everything, which would make
 * such a test pass vacuously. Parsing the two resource files is the honest option: it asserts the thing
 * that must be true (a night variant exists and differs) without pretending to exercise the platform.
 *
 * It does not prove there is no restart. That is a property of Glance's serialization and the launcher's
 * bind, and it needs a placed widget and `adb shell cmd uimode night yes|no` with an eye on it. The PR
 * must say so rather than claim the restart is tested away.
 *
 * Colour *values* are not asserted beyond being distinct: a hex assertion here would only prove the XML
 * says what the XML says.
 */
class WidgetPaletteNightTest {

    /**
     * The dimmed nav arrow: painted by the widget, but not a [WidgetColors] member.
     *
     * It is the disabled state of a control rather than part of the palette, so it is referenced at its
     * one call site. It is in this list because it has the same requirement as the rest — a night
     * variant — and because it was added for the same reason a `ColorProvider` cannot carry an alpha.
     */
    private val arrowDimmed = "widget_arrow_dimmed"

    /** Every colour the Glance render paints, by resource name. */
    private val palette = listOf(
        "widget_background",
        "widget_accent",
        "widget_text_primary",
        "widget_text_secondary",
        "widget_weekend_text",
        "widget_today_background",
        "widget_on_today",
        // FD-07: the dimmed day figures. Real resources now, precisely because a ColorProvider cannot
        // carry an alpha.
        "widget_day_out_of_month",
        "widget_day_gregorian_sub",
        "widget_day_out_faint",
        // The cell divider (FD-04) and the selected-day badge (FD-09).
        "widget_cell_border",
        "widget_selected_day",
        // The observance fill (FD-08): two resources, because a ColorProvider cannot carry an alpha
        // and the content colour has to change with its own container.
        "widget_event_day_background",
        "widget_on_event_day",
        // Not a WidgetColors member — see `arrowDimmed`.
        arrowDimmed,
    )

    private fun coloursIn(qualifier: String): Map<String, String> {
        val file = File("src/main/res/values$qualifier/colors.xml")
        assertTrue(
            "colour resources not found at ${file.absolutePath}; if the res directory moved, fix this " +
                "path rather than deleting this test",
            file.isFile,
        )
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
        val root = factory.newDocumentBuilder().parse(file).documentElement
        assertEquals("root of colors.xml", "resources", root.tagName)

        return buildMap {
            val children = root.childNodes
            for (i in 0 until children.length) {
                val node = children.item(i)
                if (node is Element && node.tagName == "color") {
                    val name = node.getAttribute("name")
                    val value = node.textContent.trim()
                    // Asserted rather than overwritten: two colours sharing a name is a resource-merge
                    // failure, and silently collapsing them here would hide it.
                    assertTrue(
                        "duplicate colour '$name' in values$qualifier/colors.xml",
                        put(name, value) == null,
                    )
                }
            }
        }
    }

    @Test
    fun everyPaletteColourHasANightVariantThatDiffers() {
        val day = coloursIn("")
        val night = coloursIn("-night")

        for (name in palette) {
            assertTrue(
                "'$name' is not defined in values/colors.xml",
                day.containsKey(name),
            )
            assertTrue(
                "'$name' has no values-night variant, so it cannot follow a theme switch — this is " +
                    "exactly how widget_text_muted and widget_text_faint were missed",
                night.containsKey(name),
            )
            assertTrue(
                "'$name' resolves to the same value in day and night, so nothing about it can follow " +
                    "a theme switch",
                day[name] != night[name],
            )
        }
    }

    /**
     * The two palettes hold the same set of names.
     *
     * A colour added to one folder and not the other is the failure mode above; the reverse — a night
     * variant nobody paints — is untidy rather than wrong, but it is nearly always a rename that landed
     * in one file. Asserting the sets match makes both halves of that visible.
     */
    @Test
    fun theDayAndNightPalettesDefineTheSameNames() {
        assertEquals(
            "values/ and values-night/ must define the same colour names; a one-sided entry is a " +
                "partial rename",
            coloursIn("").keys.sorted(),
            coloursIn("-night").keys.sorted(),
        )
    }

    /**
     * The palette is built from resource ids, not resolved colours.
     *
     * The regression guard for the *mechanism* rather than the outcome. A future edit that helpfully
     * restored a `WidgetColors.from(context)` factory would pass every other test here and bring the
     * restart straight back, because the failure is invisible until someone toggles a setting on a
     * device.
     */
    @Test
    fun thePaletteHasNoContextTakingFactory() {
        val hasFactory = WidgetColors::class.java.declaredMethods.any { it.name == "from" }
        assertTrue(
            "WidgetColors must not offer a Context-taking factory; resolving the colours too early is " +
                "what FD-07 removed",
            !hasFactory,
        )
        assertTrue(
            "WidgetColors.DEFAULT is the palette — a render must not build its own",
            WidgetColors::class.java.declaredFields.isNotEmpty(),
        )
    }

    /**
     * Every palette member is a `ColorProvider`.
     *
     * The unit that makes the mechanism work: Glance serialises a resource-backed provider as a
     * reference the launcher re-resolves, and a resolved `Color` as a literal it cannot. A `Color`
     * sneaking back into one member would leave that one colour stuck in day mode — a defect invisible
     * in every other test here, and invisible on screen until a user switches theme.
     */
    @Test
    fun everyPaletteMemberIsAColorProvider() {
        val expected = listOf(
            "accent", "background", "cellBorder", "eventDayBackground", "gregorianDay", "onEventDayText",
            "onTodayText", "outOfMonthDay", "outOfMonthGregorianDay", "primaryText", "secondaryText",
            "selectedDay", "todayBackground", "weekendText",
        )
        // Compose adds $stable and the companion's Companion/DEFAULT are static, not per-instance.
        val actual = WidgetColors::class.java.declaredFields
            .filterNot { it.name.startsWith("\$") || it.name == "Companion" || it.name == "DEFAULT" }
            .map { it.name }
            .sorted()

        assertEquals(
            "the palette's members changed; a new one needs a night variant in colors.xml and a line " +
                "in `palette` above",
            expected,
            actual,
        )
        // One `palette` entry per member, less the arrow, which is referenced at its call site rather
        // than held. So this is the check that a new palette colour was added to both files and to
        // this list.
        assertEquals(
            "one WidgetColors member per entry in `palette`, less the arrow; a new colour needs a " +
                "line in both files and a night variant in colors.xml",
            palette.size - 1,
            actual.size,
        )

        for (field in WidgetColors::class.java.declaredFields.filterNot { it.name.startsWith("\$") }) {
            if (field.name == "Companion" || field.name == "DEFAULT") continue
            assertEquals(
                "WidgetColors.${field.name} must be a ColorProvider, not a resolved Color, or the " +
                    "launcher cannot re-resolve it",
                androidx.glance.unit.ColorProvider::class.java,
                field.type,
            )
        }
    }
}
