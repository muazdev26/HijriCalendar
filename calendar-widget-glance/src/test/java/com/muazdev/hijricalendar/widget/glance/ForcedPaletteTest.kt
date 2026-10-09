package com.muazdev.hijricalendar.widget.glance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * `WidgetOptions.theme`'s two forced palettes, against the system ones they were derived from.
 *
 * ## What has to be true
 *
 * - Every `widget_light_*` value equals the **day** value of the `widget_*` colour it copies.
 * - Every `widget_dark_*` value equals the **night** value of the `widget_*` colour it copies.
 * - Neither forced file has a `values-night` counterpart.
 *
 * ## Why the duplication is asserted rather than removed
 *
 * A forced theme cannot use the configuration-qualified `widget_*` resources — re-resolving them
 * *is* following the system, which is exactly what forcing exists to override (FD-07). So the values
 * are restated in `colors_widget_theme.xml` and `colors_widget_dark_theme.xml`, and Android resources
 * cannot alias one colour onto another. That leaves each forced value able to drift away from the
 * palette it is supposed to mirror, which is the failure this class exists to catch: a `widget_light_*`
 * a shade lighter than its system counterpart would be invisible in every other test in this module and
 * wrong on a dark-mode phone running a light-forced widget.
 *
 * Colour *values* are not asserted beyond being equal to their counterpart — the point is agreement
 * with the system palette, not any particular hex.
 */
class ForcedPaletteTest {

    /** The palette member name → the system colour resource each forced variant copies. */
    private val counterparts = mapOf(
        "background" to "widget_background",
        "accent" to "widget_accent",
        "text_primary" to "widget_text_primary",
        "text_secondary" to "widget_text_secondary",
        "day_out_of_month" to "widget_day_out_of_month",
        "day_gregorian_sub" to "widget_day_gregorian_sub",
        "day_out_faint" to "widget_day_out_faint",
        "weekend_text" to "widget_weekend_text",
        "today_background" to "widget_today_background",
        "on_today" to "widget_on_today",
        "event_day_background" to "widget_event_day_background",
        "on_event_day" to "widget_on_event_day",
        "cell_border" to "widget_cell_border",
        "selected_day" to "widget_selected_day",
        "arrow_dimmed" to "widget_arrow_dimmed",
    )

    private fun coloursIn(file: String): Map<String, String> {
        val path = File("src/main/res/$file")
        assertTrue(
            "colour resources not found at ${path.absolutePath}; if the res directory moved, fix this " +
                "path rather than deleting this test",
            path.isFile,
        )
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
        val root = factory.newDocumentBuilder().parse(path).documentElement
        assertEquals("root of $file", "resources", root.tagName)

        return buildMap {
            val children = root.childNodes
            for (i in 0 until children.length) {
                val node = children.item(i)
                if (node is Element && node.tagName == "color") {
                    val name = node.getAttribute("name")
                    assertTrue(
                        "duplicate colour '$name' in $file",
                        put(name, node.textContent.trim()) == null,
                    )
                }
            }
        }
    }

    @Test
    fun everyForcedLightColourMatchesItsDayCounterpart() {
        val forced = coloursIn("values/colors_widget_theme.xml")
        val day = coloursIn("values/colors.xml")
        for ((forcedSuffix, systemName) in counterparts) {
            val forcedName = "widget_light_$forcedSuffix"
            assertTrue("'$forcedName' is not defined", forced.containsKey(forcedName))
            assertEquals(
                "'$forcedName' has drifted from the day value of '$systemName'",
                day.getValue(systemName),
                forced.getValue(forcedName),
            )
        }
    }

    @Test
    fun everyForcedDarkColourMatchesItsNightCounterpart() {
        val forced = coloursIn("values/colors_widget_dark_theme.xml")
        val night = coloursIn("values-night/colors.xml")
        for ((forcedSuffix, systemName) in counterparts) {
            val forcedName = "widget_dark_$forcedSuffix"
            assertTrue("'$forcedName' is not defined", forced.containsKey(forcedName))
            assertEquals(
                "'$forcedName' has drifted from the night value of '$systemName'",
                night.getValue(systemName),
                forced.getValue(forcedName),
            )
        }
    }

    @Test
    fun theForcedPalettesDefineExactlyTheCounterpartsTheyClaimTo() {
        val light = coloursIn("values/colors_widget_theme.xml").keys
        val dark = coloursIn("values/colors_widget_dark_theme.xml").keys
        assertEquals(
            "the forced light palette should hold one colour per WidgetColors member",
            counterparts.keys.map { "widget_light_$it" }.toSet(),
            light,
        )
        assertEquals(
            "the forced dark palette should hold one colour per WidgetColors member",
            counterparts.keys.map { "widget_dark_$it" }.toSet(),
            dark,
        )
    }

    @Test
    fun neitherForcedPaletteHasANightVariant() {
        // A `values-night/colors_widget_theme.xml` would be selected in preference to the default on a
        // dark device and would silently reintroduce the system-following behaviour forcing removes.
        // Both forced files must be unqualified; this is the guard against a well-meaning move.
        assertFalse(
            "the forced palettes must live in the default values folder only; a values-night copy " +
                "would be chosen instead on a dark device",
            File("src/main/res/values-night/colors_widget_theme.xml").exists() ||
                File("src/main/res/values-night/colors_widget_dark_theme.xml").exists(),
        )
    }
}
