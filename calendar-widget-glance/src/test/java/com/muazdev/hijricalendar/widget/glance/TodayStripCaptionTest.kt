package com.muazdev.hijricalendar.widget.glance

import com.muazdev.hijricalendar.widgetdata.WidgetLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-2 feedback, point 9: in the squeezed two-date strip the **year** must survive and the era
 * marker is what may be lost. This round adds the third rung: when even the era-less caption does
 * not fit, the month's *spelling* is given up before the year — `October 2026` → `Oct 2026`.
 *
 * The choice is made by [TodayStripTypography.captionFor] before the `Text` is laid out, because a
 * centred single-line caption that overflows is clipped at *both* ends — so leaving it to the clip
 * loses part of the year, which is the whole complaint. These assertions pin the candidates and
 * the boundaries between them, from the typography's own constants rather than literals — three
 * candidates for English, two for Urdu, since an Urdu caption is never given a shorter month.
 *
 * The short forms are the projection's own outputs, not invented here: `Oct` is
 * `CalendarNames.englishGregorianMonthAbbreviations[9]`, and the `abbrevMonthName` this file hands
 * an Urdu half *is* its full month name — **Urdu month names are never shortened**, so
 * `TodayHijriWidgetData.hijriMonthShortName` returns `جمادى الأولى` unchanged and Urdu's third
 * candidate degrades to its second rather than to something shorter (the rule itself is pinned on
 * the projection in `ShortHijriMonthNameTest`; this file models what the strip is *handed*).
 */
class TodayStripCaptionTest {

    /** The projection's era-appended year, as `TodayHijriWidgetData.hijriYearText` builds it. */
    private val urduYear = "1448 ھ"
    private val enYear = "2026 AD"

    /** The round-2 spelling for Jumada al-ula, as `UrduCalendarNames` now holds it. */
    private val urduMonth = "جمادى الأولى"
    private val enMonth = "October"

    /**
     * What the projection hands this half as its abbreviation ([TodayStripTypography.captionFor]).
     *
     * For Urdu that is the full name itself — `hijriMonthShortName` never shortens one — so the
     * strip's last rung for Urdu *is* its second. Kept as its own value so the call sites model the
     * parameter they are passing rather than silently aliasing two concepts that only coincide.
     */
    private val urduAbbrev = urduMonth
    private val enAbbrev = "Oct"

    /**
     * Widths at each boundary, computed from [TodayStripTypography.estimatedCaptionWidthDp] rather
     * than chosen to make the assertions pass: caption 14sp, Urdu advance 0.42em/char, Latin 0.52em.
     *
     * Urdu: full 19 chars = 111.72, era-less 17 = 99.96 — and no shorter candidate exists, because
     * Urdu month names are never shortened, so [urduAbbrev] is the full name and the third rung
     * prints the same string as the second.
     * English: full 15 = 109.2, era-less 12 = 87.36, abbreviated 8 = 58.24.
     */
    private val urduKeepEra = 115f
    private val urduDropEra = 105f
    private val urduTooNarrow = 90f
    private val enKeepEra = 115f
    private val enDropEra = 95f
    private val enTooNarrow = 80f

    @Test
    fun `keeps the era when the half has room for it`() {
        assertEquals(
            "$urduMonth $urduYear",
            TodayStripTypography.captionFor(
                urduMonth,
                urduAbbrev,
                urduYear,
                halfWidthDp = urduKeepEra,
                language = WidgetLanguage.URDU,
            ),
        )
        assertEquals(
            "$enMonth $enYear",
            TodayStripTypography.captionFor(
                enMonth,
                enAbbrev,
                enYear,
                halfWidthDp = enKeepEra,
                language = WidgetLanguage.ENGLISH,
            ),
        )
    }

    @Test
    fun `drops the era but keeps the full month when that is enough`() {
        val urdu = TodayStripTypography.captionFor(
            urduMonth,
            urduAbbrev,
            urduYear,
            halfWidthDp = urduDropEra,
            language = WidgetLanguage.URDU,
        )
        assertEquals("$urduMonth 1448", urdu)

        val english = TodayStripTypography.captionFor(
            enMonth,
            enAbbrev,
            enYear,
            halfWidthDp = enDropEra,
            language = WidgetLanguage.ENGLISH,
        )
        assertEquals("$enMonth 2026", english)

        // The point of the round-2 exercise: the *year* is still there, the era is the part that went.
        assertTrue(urdu.contains("1448"))
        assertTrue(english.contains("2026"))
        assertTrue(!urdu.contains("ھ"))
        assertTrue(!english.contains("AD"))
    }

    @Test
    fun `gives up the month spelling for english and never for urdu`() {
        // English: below the era-less width the caption would be clipped at both ends — which, at a
        // squeezed placement, is every placement — so the month spelling goes next and the year
        // still never does.
        // Urdu: there is nothing shorter to give — its month names are never abbreviated — so the
        // full month is what the last rung prints, and the year with it.
        val urdu = TodayStripTypography.captionFor(
            urduMonth,
            urduAbbrev,
            urduYear,
            halfWidthDp = urduTooNarrow,
            language = WidgetLanguage.URDU,
        )
        assertEquals("$urduAbbrev 1448", urdu)
        assertTrue("the year survives every rung", urdu.contains("1448"))

        val english = TodayStripTypography.captionFor(
            enMonth,
            enAbbrev,
            enYear,
            halfWidthDp = enTooNarrow,
            language = WidgetLanguage.ENGLISH,
        )
        assertEquals("$enAbbrev 2026", english)
        assertTrue("the year survives every rung", english.contains("2026"))
        assertTrue(!english.contains("AD"))
    }

    @Test
    fun `an era-less year text keeps the month while it fits and abbreviates after`() {
        // The projection omits the era entirely when no marker was supplied, and then there is nothing
        // to drop: the first two rungs collapse into one and the width decides between full and short.
        // Urdu is the rule's exception — every width yields the full month, because its third rung is
        // its second.
        assertEquals(
            "$urduMonth 1448",
            TodayStripTypography.captionFor(
                urduMonth,
                urduAbbrev,
                "1448",
                halfWidthDp = urduDropEra,
                language = WidgetLanguage.URDU,
            ),
        )
        assertEquals(
            "$urduAbbrev 1448",
            TodayStripTypography.captionFor(
                urduMonth,
                urduAbbrev,
                "1448",
                halfWidthDp = urduTooNarrow,
                language = WidgetLanguage.URDU,
            ),
        )
        assertEquals(
            "$urduMonth 1448",
            TodayStripTypography.captionFor(
                urduMonth,
                urduAbbrev,
                "1448",
                halfWidthDp = 400f,
                language = WidgetLanguage.URDU,
            ),
        )
        // English is where the era-less collapse actually decides something.
        assertEquals(
            "$enMonth 2026",
            TodayStripTypography.captionFor(
                enMonth,
                enAbbrev,
                "2026",
                halfWidthDp = enDropEra,
                language = WidgetLanguage.ENGLISH,
            ),
        )
        assertEquals(
            "$enAbbrev 2026",
            TodayStripTypography.captionFor(
                enMonth,
                enAbbrev,
                "2026",
                halfWidthDp = enTooNarrow,
                language = WidgetLanguage.ENGLISH,
            ),
        )
    }

    @Test
    fun `an unrealistically small width changes nothing`() {
        // A picker preview or a host mid-placement can be handed a width no real placement has;
        // giving content up on that artefact is what once hid the era marker on full-screen strips.
        assertEquals(
            "$urduMonth $urduYear",
            TodayStripTypography.captionFor(
                urduMonth,
                urduAbbrev,
                urduYear,
                halfWidthDp = 4f,
                language = WidgetLanguage.URDU,
            ),
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
