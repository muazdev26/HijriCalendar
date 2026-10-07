package com.muazdev.hijricalendar.widget.glance

import androidx.glance.text.FontFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `HijriWidgetFonts` — the per-field font option a host writes to at startup.
 *
 * The composables that consume it are Glance and have no JVM host, so what is pinned here is the part
 * that *is* testable and that the whole feature rests on: that a family name becomes a Glance
 * [FontFamily] carrying that name, and that an unset field stays unset rather than becoming some
 * default family. A slot that silently picked up `sans-serif` would look almost right on a device and
 * would be indistinguishable from "the host set nothing".
 */
class HijriWidgetFontsTest {

    /**
     * A family name reaches Glance verbatim.
     *
     * Glance applies it as `TypefaceSpan(family.family)`, so this string *is* the lookup key. If it
     * were normalised, trimmed or case-folded here, a host's exact family name would stop matching and
     * the widget would fall back to the system face for no visible reason.
     */
    @Test
    fun aFamilyNameIsCarriedThroughToGlanceVerbatim() {
        assertEquals(
            "Noto Nastaliq Urdu",
            "Noto Nastaliq Urdu".toGlanceFontFamily()?.family,
        )
    }

    /**
     * An unset field leaves the slot on Glance's default face.
     *
     * This is what makes the four fields independent. If `null` became `FontFamily.SansSerif`, then a
     * host setting only `weekday` would also restyle the digits, the titles and the month names —
     * silently overriding fonts the host never touched.
     */
    @Test
    fun anUnsetFieldProducesNoFontFamilyAtAll() {
        assertNull(
            "an unset font must stay unset, not fall back to a default family",
            (null as String?).toGlanceFontFamily(),
        )
        assertNull(
            "every field of the all-default fonts must be unset",
            HijriWidgetFonts().weekday.toGlanceFontFamily(),
        )
    }

    /**
     * The default instance sets nothing, so upgrading changes no existing host's widgets.
     *
     * The option has to be inert until a host opts in: four widgets currently render on Glance's own
     * default face, and that is what a consumer on the current version must keep getting.
     */
    @Test
    fun theDefaultFontsSetNothing() {
        val fonts = HijriWidgetFonts.default
        assertNull("monthTitle", fonts.monthTitle)
        assertNull("gregorianTitle", fonts.gregorianTitle)
        assertNull("weekday", fonts.weekday)
        assertNull("dayNumber", fonts.dayNumber)
    }

    /**
     * The tiles and the strip share one composable per layout, so "which calendar is this" has to be
     * carried explicitly — the text cannot say. Getting it backwards would put the Urdu face on a
     * Gregorian month name, which is the exact case this option exists to get right.
     */
    @Test
    fun eachCalendarResolvesItsOwnTitleSlot() {
        val fonts = HijriWidgetFonts(monthTitle = "UrduFace", gregorianTitle = "LatinFace")

        assertEquals("UrduFace", fonts.forMonthTitle(gregorian = false))
        assertEquals("LatinFace", fonts.forMonthTitle(gregorian = true))
    }

    /**
     * A host that sets only one field leaves the other three alone.
     *
     * The realistic Urdu case: a Nastaliq face for the Urdu month and weekday names, the system face
     * for the digits. Nastaliq at 36sp would be unreadable for numerals, so this independence is the
     * feature rather than a convenience.
     */
    @Test
    fun fieldsAreIndependentOfEachOther() {
        val onlyWeekday = HijriWidgetFonts(weekday = "UrduFace")

        assertEquals("UrduFace", onlyWeekday.weekday)
        assertNull("setting only weekday must not restyle the titles", onlyWeekday.monthTitle)
        assertNull("setting only weekday must not restyle the digits", onlyWeekday.dayNumber)
        assertNotEquals(
            "a host setting one field should not end up equal to an all-defaults instance",
            HijriWidgetFonts(),
            onlyWeekday,
        )
    }
}
