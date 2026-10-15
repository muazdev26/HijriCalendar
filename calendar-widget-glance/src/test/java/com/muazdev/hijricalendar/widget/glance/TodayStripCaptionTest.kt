package com.muazdev.hijricalendar.widget.glance

import com.muazdev.hijricalendar.widgetdata.WidgetLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-2 feedback, point 9: in the squeezed two-date strip the **year** must survive and the era
 * marker is what may be lost.
 *
 * The choice is made by [TodayStripTypography.captionFor] before the `Text` is laid out, because a
 * centred single-line caption that overflows is clipped at *both* ends — so leaving it to the clip
 * loses part of the year, which is the whole complaint. These assertions pin the two candidates and
 * the boundary between them, from the typography's own constants rather than literals.
 */
class TodayStripCaptionTest {

    /** The projection's era-appended year, as `TodayHijriWidgetData.hijriYearText` builds it. */
    private val urduYear = "1448 ھ"
    private val enYear = "2026 AD"

    /** The round-2 spelling for Jumada al-ula, as `UrduCalendarNames` now holds it. */
    private val urduMonth = "جمادى الأولى"
    private val enMonth = "October"

    @Test
    fun `keeps the era when the half has room for it`() {
        assertEquals(
            "$urduMonth $urduYear",
            TodayStripTypography.captionFor(urduMonth, urduYear, halfWidthDp = 120f, language = WidgetLanguage.URDU),
        )
        assertEquals(
            "$enMonth $enYear",
            TodayStripTypography.captionFor(enMonth, enYear, halfWidthDp = 120f, language = WidgetLanguage.ENGLISH),
        )
    }

    @Test
    fun `drops the era but never the year when the half is narrow`() {
        val urdu = TodayStripTypography.captionFor(
            urduMonth,
            urduYear,
            halfWidthDp = 60f,
            language = WidgetLanguage.URDU,
        )
        assertEquals("$urduMonth 1448", urdu)

        val english = TodayStripTypography.captionFor(
            enMonth,
            enYear,
            halfWidthDp = 60f,
            language = WidgetLanguage.ENGLISH,
        )
        assertEquals("$enMonth 2026", english)

        // The point of the whole exercise: the *year* is still there, the era is the part that went.
        assertTrue(urdu.contains("1448"))
        assertTrue(english.contains("2026"))
        assertTrue(!urdu.contains("ھ"))
        assertTrue(!english.contains("AD"))
    }

    @Test
    fun `an era-less year text yields the same caption either way`() {
        // The projection omits the era entirely when no marker was supplied, and then there is nothing
        // to drop: the two candidates collapse to one string.
        assertEquals(
            "$urduMonth 1448",
            TodayStripTypography.captionFor(urduMonth, "1448", halfWidthDp = 60f, language = WidgetLanguage.URDU),
        )
        assertEquals(
            "$urduMonth 1448",
            TodayStripTypography.captionFor(urduMonth, "1448", halfWidthDp = 400f, language = WidgetLanguage.URDU),
        )
    }

    @Test
    fun `the estimate is script-aware`() {
        // Urdu Nastaliq's joined forms advance less per character than the Latin faces; one constant
        // would drop the Urdu era far too early. Measured from the device, so assert the relationship
        // rather than the literal em values.
        val text = "$urduMonth $urduYear"
        val urdu = TodayStripTypography.estimatedCaptionWidthDp(text, WidgetLanguage.URDU)
        val english = TodayStripTypography.estimatedCaptionWidthDp(text, WidgetLanguage.ENGLISH)
        assertTrue("Urdu must estimate narrower than the Latin advance ($urdu vs $english)", urdu < english)
    }

    @Test
    fun `the half width subtracts the strip's own chrome`() {
        // 218dp was the granted width of the strip on the test device; the halves split what the
        // padding, the divider and its clear space leave, so this is the number the caption sees.
        val padding = TodayStripTypography.HORIZONTAL_PADDING_DP * 2
        val divider = TodayStripTypography.DIVIDER_WIDTH_DP
        // `DIVIDER_GAP_DP` either side of the hairline is file-private to the strip, so the gap is
        // restated here: ten is the two of them.
        val gap = 10f
        val expected = (218f - padding - divider - gap) / 2f
        assertEquals(
            expected.toDouble(),
            TodayStripTypography.captionHalfWidthDp(218f).toDouble(),
            0.01,
        )
        // Never negative, however absurdly narrow the placement.
        assertEquals(0.0, TodayStripTypography.captionHalfWidthDp(4f).toDouble(), 0.01)
    }
}
