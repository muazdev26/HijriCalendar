package com.muazdev.hijricalendar.widget.glance

import androidx.compose.ui.unit.TextUnit
import com.muazdev.hijricalendar.widgetdata.NumeralStyle
import com.muazdev.hijricalendar.widgetdata.WidgetLanguage
import com.muazdev.hijricalendar.widgetdata.createWidgetOptions
import com.muazdev.hijricalendar.widgetdata.todayHijriWidgetData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToInt

/**
 * FD-10: the dual-date tile takes its type sizes from the sibling tiles rather than inventing a set, and
 * its Android 12-14 `previewLayout` mirror is held to them.
 *
 * ## What this file is really about
 *
 * Three earlier versions of this tile's sizing failed, and each failure was the tile not following the
 * family:
 *
 * 1. Every zone sized as a *fraction of the granted height*, which rendered a **6sp weekday** on a real
 *    72dp cell.
 * 2. The remedy for that — a width-only rule **with a floor**, clipping whatever did not fit — fixed the
 *    unreadable-weekday case and reintroduced the failure the family is now arranged to prevent: on a
 *    short cell some lines grew while others were clipped, and on a device the *Hijri month name* was the
 *    line pushed off the bottom.
 * 3. The shrink-to-fit that followed made it worse in a way nobody could see: raising the caption sizes
 *    moved the shrink's pivot up, so 20/20/23sp requested rendered a **12.9sp** header, smaller than the
 *    15sp it replaced.
 *
 * What the tile does now is what the sibling tiles and the Today strip do: **one scale from both granted
 * axes** ([widgetTypeScale]), so every zone is `reference * scale`, the four lines always fit the granted
 * height, and none can be pushed off the bottom. The strongest assertions here are therefore **equality
 * with [DateTileTypography]** — the tile shares the family's reference caption rather than a parallel
 * set — plus a **fit** claim at every plausible cell, which replaces the old "recorded budget" test: a
 * clamped budget was the right claim while the tile clipped, and is the wrong one now that it fits.
 *
 * The composable is not unit-testable in this module (a Glance composition has no JVM host), so what is
 * asserted is the **data** the layout is built from plus the **XML** that mirrors it, the same division of
 * labour [DateTilePreviewLayoutTest] uses.
 *
 * **Note on argument order.** JUnit takes the message *first*; `kotlin.test` takes it last. This file uses
 * JUnit, like [DateTilePreviewLayoutTest].
 */
class DualDateTilePreviewLayoutTest {

    // ── The tile shares the sibling tiles' numbers ─────────────────────────────

    /**
     * The day figure is the sibling tiles' figure, less an explicit share — **at the reference cell**;
     * from there it grows with the tile up to its own ceiling.
     *
     * The figure dropped to 30sp so the four lines of this tile coexist. That reduction has to be bounded,
     * or "a bit smaller" becomes an open-ended licence: the assertion is that the day is *under* the
     * sibling tiles' reference figure at the reference cell and still well above every caption, so it stays
     * the tile's loudest element.
     *
     * **The scaling is point 7.** This test used to assert the opposite — "the fixed figure, not a
     * width-scaled one, at every width" — which was correct while the tile was a non-resizable 1x1 and
     * is exactly what made a resize a no-op. A ceiling bounds what a very large placement can reach, so
     * the tile still reads at arm's length rather than from across the room.
     */
    @Test
    fun theDayFigureGrowsFromTheSiblingFigureUpToItsCeiling() {
        assertEquals(
            "at the reference width the figure must be the reference figure, so a preview and a placed " +
                "1x1 agree",
            DualDateTileTypography.daySize.value.toDouble(),
            DualDateTileTypography.sizesFor(DualDateTileTypography.TILE_REFERENCE_TEXT_WIDTH_DP)
                .daySize.value.toDouble(),
            0.01,
        )
        assertTrue(
            "the reference figure must stay below the sibling tiles' " +
                "${DateTileTypography.daySize.value}sp, because this tile carries four lines rather " +
                "than their three",
            DualDateTileTypography.daySize.value < DateTileTypography.daySize.value,
        )
        assertTrue(
            "and the captions must never reach it: the day is ${DualDateTileTypography.daySize.value}sp " +
                "against a caption ceiling of " +
                "${DualDateTileTypography.daySize.value * DualDateTileTypography.CAPTION_MAX_SHARE}sp",
            DualDateTileTypography.daySize.value >
                DualDateTileTypography.daySize.value * DualDateTileTypography.CAPTION_MAX_SHARE,
        )
        assertTrue(
            "the ceiling must leave room to grow, or a stretched tile renders the same figure",
            DualDateTileTypography.daySizeCeiling.value > DualDateTileTypography.daySize.value,
        )
        assertTrue(
            "a wider cell must render a larger figure",
            DualDateTileTypography.sizesFor(138f).daySize.value >
                DualDateTileTypography.sizesFor(51f).daySize.value,
        )
        for (width in 1..400) {
            val day = DualDateTileTypography.sizesFor(width.toFloat()).daySize.value
            val floor = DualDateTileTypography.daySize.value * MIN_TYPE_SCALE
            assertTrue(
                "at ${width}dp the day (${day}sp) must sit between ${floor}sp and " +
                    "${DualDateTileTypography.daySizeCeiling.value}sp",
                day >= floor - 0.001f &&
                    day <= DualDateTileTypography.daySizeCeiling.value + 0.001f,
            )
        }
        assertEquals(
            "and the ceiling is final however far the tile is stretched",
            DualDateTileTypography.daySizeCeiling.value.toDouble(),
            DualDateTileTypography.sizesFor(4000f).daySize.value.toDouble(),
            0.01,
        )
    }

    /**
     * The captions are the tile's own reference caption scaled with the cell, and stay under the day.
     *
     * `CAPTION_RATIO` dials the family's reference caption back so a weekday can never outrank the
     * Hijri day; the single scale then moves the caption with the cell, so the weekday and the month
     * name widen together as the sibling tiles' names do. The clamp is against the figure the **same**
     * cell renders (`sizes.daySize`), not the reference one, so the two cannot drift apart as the tile
     * grows.
     */
    @Test
    fun theCaptionsScaleWithTheCellAndStayUnderTheDay() {
        val maxScale = DualDateTileTypography.daySizeCeiling.value / DualDateTileTypography.daySize.value
        for (width in 1..400) {
            val sizes = DualDateTileTypography.sizesFor(width.toFloat())
            val scale = (width / DualDateTileTypography.TILE_REFERENCE_TEXT_WIDTH_DP)
                .coerceIn(MIN_TYPE_SCALE, maxScale)
            val expected = (DualDateTileTypography.captionAtReference.value * scale)
                .coerceAtMost(DualDateTileTypography.monthSizeCeiling.value)

            assertEquals(
                "the band's weekday at ${width}dp: the reference caption is " +
                    "${DualDateTileTypography.captionAtReference.value}, scaled it should be $expected",
                expected.toDouble(),
                sizes.weekdaySize.value.toDouble(),
                0.01,
            )
            assertEquals(
                "the Hijri month name at ${width}dp",
                expected.toDouble(),
                sizes.monthSize.value.toDouble(),
                0.01,
            )
            assertTrue(
                "at ${width}dp the captions must never overtake the day figure (${sizes.daySize.value}sp)",
                sizes.weekdaySize.value <= sizes.daySize.value &&
                    sizes.monthSize.value <= sizes.daySize.value,
            )
        }
    }

    /**
     * The band weekday and the Hijri month name are sized **alike**.
     *
     * Not a rounding artefact of sharing one rule: they are the same class of line — a name on a page
     * whose figure is the day — and giving them one rule is what stops them drifting apart as the width
     * changes.
     */
    @Test
    fun theBandWeekdayAndTheMonthNameAreSizedAlike() {
        for (width in 1..200) {
            val sizes = DualDateTileTypography.sizesFor(width.toFloat())
            assertEquals(
                "at ${width}dp the band weekday (${sizes.weekdaySize.value}sp) and the month name " +
                    "(${sizes.monthSize.value}sp) must be the same size",
                sizes.weekdaySize.value,
                sizes.monthSize.value,
                0.001f,
            )
        }
    }

    /**
     * No line ever vanishes as the tile shrinks, and no caption exceeds the figure.
     *
     * The old form of this test held every line above the sibling tiles' caption **floor**. A floor is
     * exactly what this round removed from the family: a per-line floor is what pushes the last line
     * off a short cell, because it keeps a line at a size the cell can no longer hold. What survives is
     * the property the floor was standing in for — every line is still rendered at the narrowest tile,
     * and no caption ever outgrows the figure.
     */
    @Test
    fun everyLineSurvivesTheNarrowestTileAndStaysUnderTheDay() {
        for (width in 1..200) {
            val sizes = DualDateTileTypography.sizesFor(width.toFloat())
            for ((name, size) in sizes.namedLabels()) {
                assertTrue(
                    "at ${width}dp the $name is ${size.value}sp; every line must still be rendered",
                    size.value > 0f,
                )
                assertTrue(
                    "at ${width}dp the $name (${size.value}sp) must not exceed the day " +
                        "(${sizes.daySize.value}sp)",
                    size.value <= sizes.daySize.value + 0.001f,
                )
            }
        }
    }

    /**
     * The Gregorian date never out-weighs the month name above it, and never vanishes.
     *
     * The one size this tile picks for itself, so it needs both bounds: it is held under the month name
     * (so the tile has one heading, not two competing ones) and above zero at the narrowest cell. The
     * old form of this test also pinned a *floor* equal to the sibling tiles' caption floor; that floor
     * is gone with the family's other floors, for the same reason — a floor is what keeps a line at a
     * size the granted cell can no longer hold.
     */
    @Test
    fun theGregorianDateIsSubordinateToTheMonthName() {
        for (width in 1..200) {
            val sizes = DualDateTileTypography.sizesFor(width.toFloat())
            assertTrue(
                "at ${width}dp the Gregorian date (${sizes.subRowSize.value}sp) must not exceed the " +
                    "month name (${sizes.monthSize.value}sp)",
                sizes.subRowSize.value <= sizes.monthSize.value + 0.001f,
            )
            assertTrue(
                "at ${width}dp the Gregorian date (${sizes.subRowSize.value}sp) must still be rendered",
                sizes.subRowSize.value > 0f,
            )
        }
        assertEquals(
            "the Gregorian date has a ceiling of this tile's own, so a large tile keeps it a caption",
            DualDateTileTypography.subRowSizeCeiling.value.toDouble(),
            DualDateTileTypography.sizesFor(400f).subRowSize.value.toDouble(),
            0.01,
        )
    }

    /**
     * The width-only projection — the one the static mirror states — is monotonic in width.
     *
     * The mirror has no granted height of its own, so it calls `sizesFor(width)`; a rule that was not
     * monotonic would render a smaller caption on a wider tile at some width, which a single visual
     * check never catches.
     *
     * **The live tile takes both axes** (`sizesFor(width, height)`). The old form of this test asserted
     * there was no height term anywhere, which was correct while this tile clipped rather than fit —
     * and is exactly the behaviour a resize report removed. The height-bounded behaviour is asserted by
     * [theFourZonesFitTheHeightTheyAreGranted].
     */
    @Test
    fun theWidthOnlyProjectionIsMonotonic() {
        var previous = 0f
        for (width in 1..400) {
            val caption = DualDateTileTypography.sizesFor(width.toFloat()).weekdaySize.value
            assertTrue(
                "at ${width}dp the weekday dropped to $caption sp from $previous",
                caption >= previous,
            )
            previous = caption
        }
    }

    // ── Emphasis ──────────────────────────────────────────────────────────────

    /**
     * **The Hijri day is the largest text on the tile at every width.**
     *
     * The tile's one non-negotiable property: it exists to show the Hijri day. Swept rather than sampled
     * because the captions scale with width and could cross the figure at some width, which a single check
     * would miss.
     */
    @Test
    fun theHijriDayIsTheLargestTextAtEveryWidth() {
        for (width in 1..200) {
            val sizes = DualDateTileTypography.sizesFor(width.toFloat())
            assertTrue(
                "at ${width}dp the Hijri day (${sizes.daySize.value}sp) must exceed the band weekday " +
                    "(${sizes.weekdaySize.value}sp), the month name (${sizes.monthSize.value}sp) and the " +
                    "Gregorian date (${sizes.subRowSize.value}sp)",
                sizes.isDayDominant(),
            )
        }
    }

    // ── The vertical budget, recorded rather than met ─────────────────────────

    /**
     * The four zones fit the height the tile is granted — all of them, at every plausible cell.
     *
     * **This replaces the recorded-budget test, and the replacement is the point.** The tile used to
     * size on width alone and *accept* that a short cell clipped its last zone; the cost was quoted as
     * [ACCEPTED_MAX_REQUESTED_DP] so a size increase was visible instead of absorbed. Now the whole
     * stack scales with the granted height, so the honest claim is not "it asks for ~Ndp" but "it fits
     * what it was given" — which is falsifiable at every cell rather than at one, and is the behaviour a
     * resize report asked for.
     */
    @Test
    fun theFourZonesFitTheHeightTheyAreGranted() {
        for (height in listOf(48f, 60f, 84f, 120f, 200f)) {
            val requested = DualDateTileTypography.verticalBudgetDp(
                DualDateTileTypography.TILE_REFERENCE_TEXT_WIDTH_DP,
                height,
            )
            assertTrue(
                "at ${height}dp the four zones ask for ${requested}dp; the tile must fit the height " +
                    "it was given or its last zone is clipped",
                requested <= height + 0.01f,
            )
        }
        // The reference height is exactly the stack's own request, which is what keeps a preview and a
        // placed tile in agreement.
        assertEquals(
            "the reference height must be the stack's own request",
            DualDateTileTypography.REFERENCE_STACK_HEIGHT_DP.toDouble(),
            DualDateTileTypography.verticalBudgetDp(
                DualDateTileTypography.TILE_REFERENCE_TEXT_WIDTH_DP,
                DualDateTileTypography.REFERENCE_STACK_HEIGHT_DP,
            ).toDouble(),
            0.01,
        )
    }

    /**
     * The same fit with the **bundled Nastaliq** face drawing the three script lines.
     *
     * The ratio is threaded through `sizesFor`/`requestedHeightDp` for the reason
     * [NASTALIQ_LINE_RATIO] gives: Nastaliq's box is 2.5 em against the fallback's 1.7, so a stack
     * measured at the wrong one overflows the cell by ~47% of its script lines' height — and this tile
     * is a strict 1x1, so there is nowhere for the overflow to go.
     */
    @Test
    fun theNastaliqZonesFitTheHeightTheyAreGrantedToo() {
        for (height in listOf(40f, 48f, 60f, 84f, 120f, 200f)) {
            val requested = DualDateTileTypography.verticalBudgetDp(
                DualDateTileTypography.TILE_REFERENCE_TEXT_WIDTH_DP,
                height,
                NASTALIQ_LINE_RATIO,
            )
            assertTrue(
                "at ${height}dp the Nastaliq zones ask for ${requested}dp, which does not fit",
                requested <= height + 0.01f,
            )
        }

        val system = DualDateTileTypography.sizesFor(84f, 84f, TEXT_LINE_RATIO)
        val nastaliq = DualDateTileTypography.sizesFor(84f, 84f, NASTALIQ_LINE_RATIO)
        assertTrue(
            "Nastaliq's taller box must shrink the tile's type at a fixed cell; got " +
                "${nastaliq.daySize} against ${system.daySize}",
            nastaliq.daySize.value < system.daySize.value,
        )
    }

    // ── The residual ──────────────────────────────────────────────────────────

    /**
     * The residue left by Glance's line boxes is bounded and quoted, not claimed away.
     *
     * [DualDateTileTypography.RESIDUAL_MARGIN_DP] is the whole distance between "the insets are equal" and
     * "the margins are equal to within 1dp". Asserting it is small and finite is what keeps the honest form
     * of the criterion honest: if a change inflated the leading, the number grows and this fails rather than
     * the claim quietly becoming false.
     */
    @Test
    fun theResidualIsBounded() {
        val residual = DualDateTileTypography.RESIDUAL_MARGIN_DP

        assertTrue(
            "the residual must be finite and non-negative, was $residual",
            residual.isFinite() && residual >= 0f,
        )
        assertTrue(
            "the residual ($residual dp) is the distance between the insets being equal and the margins " +
                "being equal; past a few dp it stops being a rounding difference",
            residual <= MAX_ACCEPTED_RESIDUAL_DP,
        )
    }

    // ── The static preview mirror ─────────────────────────────────────────────

    /**
     * The Android 12-14 `previewLayout` mirror agrees with the live composition's numbers.
     *
     * Hand-maintained XML is the only mirror this repo has and it has drifted before. **A snapshot, not a
     * follower**: the live tile scales its captions with the granted *width*, which a static XML cannot do,
     * so the mirror shows the reference width — exactly as the sibling tiles' mirrors do.
     */
    @Test
    fun thePreviewLayoutMatchesTheLiveSizesAtTheReferenceWidth() {
        val sizes = DualDateTileTypography.sizesFor(DualDateTileTypography.TILE_REFERENCE_TEXT_WIDTH_DP)
        val lines = zoneTextViews()

        assertEquals(
            "the mirror must have exactly six text lines: the band's weekday, the Hijri day, the Hijri " +
                "month name, and the Gregorian date's month, separator and day",
            6,
            lines.size,
        )
        assertEquals("the band's weekday name", spOf(lines[0]), spInXml(sizes.weekdaySize.value))
        assertEquals("the centred Hijri day", spOf(lines[DAY_LINE_INDEX]), spInXml(sizes.daySize.value))
        assertEquals("the Hijri month name", spOf(lines[2]), spInXml(sizes.monthSize.value))
        assertEquals(
            "the Gregorian date's month half",
            spOf(lines[3]),
            spInXml(sizes.subRowSize.value),
        )
        assertEquals(
            "the separator between the Gregorian halves",
            spOf(lines[4]),
            spInXml(sizes.subRowSize.value),
        )
        assertEquals(
            "the Gregorian date's day half",
            spOf(lines[5]),
            spInXml(sizes.subRowSize.value),
        )
    }

    /**
     * The mirror stacks the zones in the order the design specifies, with the **weekday in the band**.
     *
     * Band (weekday), then the centred Hijri day, then the full Hijri month name, then the Gregorian date.
     * The weekday moved from last to first when the band changed from a month name to a day name: it is one
     * short word in every language, so it fits a full-width band, and it answers the question a glance
     * actually asks.
     */
    @Test
    fun theMirrorStacksTheZonesInOrderWithTheWeekdayInTheBand() {
        val lines = zoneTextViews()

        assertEquals(
            "the band must carry the weekday name",
            KNOWN_WEEKDAY,
            attr(lines.first(), "android:text"),
        )
        assertTrue(
            "the centred Hijri day line must be strictly larger than every other line in the mirror",
            lines.indices
                .filter { it != DAY_LINE_INDEX }
                .all { spOf(lines[DAY_LINE_INDEX]) > spOf(lines[it]) },
        )
    }

    /**
     * The mirror's Hijri month name is the **complete** one, `ربیع الثانی`.
     *
     * The mirror's sample content is Urdu, and **Urdu month names are never shortened** — the
     * projection returns [com.muazdev.hijricalendar.widgetdata.TodayHijriWidgetData.hijriMonthShortName]
     * byte-identical to [com.muazdev.hijricalendar.widgetdata.TodayHijriWidgetData.hijriMonthName]
     * for Urdu, so the live dual tile renders exactly this string too. (An English widget renders
     * `Rabi' II` there instead — pinned on the projection in `ShortHijriMonthNameTest`.) Asserted
     * so a change to either side, mirror or rule, cannot quietly diverge from the other.
     */
    @Test
    fun theMirrorShowsTheCompleteHijriMonthName() {
        val monthLine = zoneTextViews()[2]

        assertEquals(
            "the Hijri month name must be rendered in full, not abbreviated",
            COMPLETE_HIJRI_MONTH,
            attr(monthLine, "android:text"),
        )
    }

    /**
     * The Hijri and Gregorian month names are drawn in the **same colour**.
     *
     * They are one date in two calendars, so they are one weight of the same sentence. The Gregorian half
     * used to be muted to `widget_text_secondary`, which made it read as a footnote on the Hijri month name
     * rather than as its counterpart — the same instinct as weighting one of the two halves of the Today
     * strip, and wrong for the same reason.
     *
     * The distinction the tile still needs now comes from the **day figures** (the Hijri one is the accent
     * colour) and from size, not from muting a month name.
     *
     * Asserted on the mirror because that is the only render path a JVM test can reach; the live path
     * passes `colors.primaryText` for both.
     */
    @Test
    fun bothMonthNamesAreDrawnInTheSameColour() {
        val lines = zoneTextViews()
        val hijriMonth = lines[MONTH_LINE_INDEX]
        val gregorianMonth = lines[GREGORIAN_MONTH_LINE_INDEX]

        assertEquals(
            "the Hijri and Gregorian month names must be the same weight of the same sentence",
            attr(hijriMonth, "android:textColor"),
            attr(gregorianMonth, "android:textColor"),
        )
        assertEquals(
            "and that colour is the primary text token, not the muted one",
            "@color/widget_text_primary",
            attr(hijriMonth, "android:textColor"),
        )
    }

    /**
     * The Gregorian month and day are joined by a **dash**, and it stays punctuation.
     *
     * Two halves of one date with nothing between them read as two labels — `ستمبر` and `۳۰` are not
     * obviously one date — and the dash is what makes it one. It is muted to `widget_text_secondary` so it
     * reads as a join rather than as a third piece of content: both month names are `primaryText` now
     * (they are one date in two calendars), and a dash in that weight would compete with them.
     *
     * The *string* is a literal, which is deliberate and worth pinning: a separator is punctuation, not
     * text, so it is not language-specific and does not belong in the projection — unlike the month name,
     * the weekday and the numerals, which are per-widget by WG-12. The grid header joins its own two title
     * halves with a literal for the same reason.
     */
    @Test
    fun theGregorianHalvesAreJoinedByAMutedDash() {
        val lines = zoneTextViews()
        val separator = lines[GREGORIAN_SEPARATOR_LINE_INDEX]

        assertEquals(
            "the separator between the two halves",
            DualDateTileTypography.DATE_SEPARATOR,
            attr(separator, "android:text"),
        )
        assertEquals(
            "and it stays punctuation, not a third piece of content",
            "@color/widget_text_secondary",
            attr(separator, "android:textColor"),
        )
        assertEquals(
            "the separator must sit between the month and the day",
            "@color/widget_text_primary",
            attr(lines[GREGORIAN_MONTH_LINE_INDEX], "android:textColor"),
        )
        assertEquals(
            "…with the day in the same weight as the month, so only the dash is muted",
            "@color/widget_text_primary",
            attr(lines[5], "android:textColor"),
        )
    }

    /**
     * The mirror reproduces the live layout's two load-bearing mechanics.
     *
     * Both are things a preview could plausibly flatten and thereby advertise a tile the widget does not
     * draw:
     *
     * - **the region under the band is `gravity="center_vertical"`**, because the live weighted Box is
     *   centre-aligned and that centring is what splits the leftover height between the two margins;
     * - **the Gregorian date is full width and centred in the mirror**, because it is one date. A
     *   weighted half is what pushed the two to opposite ends, which read as two unrelated labels; and a
     *   revision that aligned the phrase to the reading-start end pushed the whole date to one side of the
     *   tile, which is a different mistake with the same-looking symptom.
     */
    @Test
    fun theMirrorReproducesTheCentredRegionAndTheCompactGregorianPhrase() {
        val root = layoutRoot()
        val band = childrenOf(root).first()
        val centred = childrenOf(root)[1]

        assertEquals(
            "the band is the first child and carries the accent fill",
            "@color/widget_accent",
            attr(band, "android:background"),
        )
        assertEquals(
            "the region under the band must be centre-aligned vertically, matching the live weighted Box",
            "center_vertical",
            attr(centred, "android:gravity"),
        )
        assertEquals(
            "the centred region must take the remaining height, as the live Box's defaultWeight does",
            "1",
            attr(centred, "android:layout_weight"),
        )

        val phrase = childrenOf(centred).first {
            it.tagName == "LinearLayout" && attr(it, "android:orientation") == "horizontal"
        }
        assertEquals(
            "the Gregorian date must be one horizontal phrase",
            "horizontal",
            attr(phrase, "android:orientation"),
        )
        assertEquals(
            "the phrase must be full width so it can align to the reading-start end of the tile",
            "match_parent",
            attr(phrase, "android:layout_width"),
        )
        assertEquals(
            "the phrase is one date, so it is centred rather than pushed to one side",
            "center_horizontal|center_vertical",
            attr(phrase, "android:gravity"),
        )
        val halves = childrenOf(phrase).filter { it.tagName == "TextView" }
        assertEquals("the phrase has month, separator and day", 3, halves.size)
        assertTrue(
            "no half may be weighted: a weighted half is what pushed them to opposite ends",
            halves.none { attr(it, "android:layout_weight") != null },
        )
    }

    /**
     * The band carries the **weekday name alone** and includes vertical padding so glyphs are not clipped.
     *
     * Top padding (BAND_TOP_PADDING_DP = 2dp) prevents taller glyphs/ascenders (e.g. Urdu/Arabic script)
     * from being cut off at the top of the accent band.
     */
    @Test
    fun theBandCarriesOnlyTheWeekdayAndHasVerticalPadding() {
        val band = childrenOf(layoutRoot()).first()

        assertEquals("the band must be a single TextView", "TextView", band.tagName)
        assertEquals(
            "the band must carry the weekday name",
            KNOWN_WEEKDAY,
            attr(band, "android:text"),
        )
        assertEquals(
            "the mirror's band must include top padding to prevent glyph clipping",
            "${DualDateTileTypography.BAND_TOP_PADDING_DP}dp",
            attr(band, "android:paddingTop"),
        )
        assertEquals(
            "the band's extra height goes below its text, so the mirror must declare BAND_PADDING_DP there",
            "${DualDateTileTypography.BAND_PADDING_DP}dp",
            attr(band, "android:paddingBottom"),
        )

        // The live side: the band includes top and bottom padding around its line box.
        val sizes = DualDateTileTypography.sizesFor(DualDateTileTypography.TILE_REFERENCE_TEXT_WIDTH_DP)
        val expected = sizes.weekdaySize.value * TEXT_LINE_RATIO +
            DualDateTileTypography.BAND_TOP_PADDING_DP +
            DualDateTileTypography.BAND_PADDING_DP
        assertEquals(
            "the live band's height includes top and bottom padding",
            expected.toDouble(),
            bandHeightDp(sizes).toDouble(),
            0.001,
        )
    }

    /**
     * The band uses the family accent, not a colour of its own.
     *
     * The reference tear-off page's header is blue. Giving this tile a blue would look right on one launcher
     * and wrong in night mode and against a themed host, and it would be the only colour in the family that
     * did not re-resolve for night mode (FD-07). Asserted because it is exactly the kind of one-off that
     * looks like an improvement in a diff.
     */
    @Test
    fun theBandUsesTheFamilyAccentAndItsOwnTextToken() {
        val band = childrenOf(layoutRoot()).first()

        assertEquals(
            "the header band must be the family accent so it re-resolves for night mode",
            "@color/widget_accent",
            attr(band, "android:background"),
        )
        assertEquals(
            "the band's text must use the on-accent token, not a literal white",
            "@color/widget_on_today",
            attr(band, "android:textColor"),
        )
        assertTrue(
            "no colour token may be added for this tile alone; the band reuses the family's",
            !attr(band, "android:background").orEmpty().contains("dual"),
        )
    }

    /**
     * The widget-info is a **resizable 1×1**: a 40dp minimum, on both axes, pinned to the same 40dp.
     *
     * Point 7 made this tile resizable, which is what lets its type grow with the cell
     * (`SizeMode.Exact` on the widget). What must *not* change is the 40dp minimum: on launchers before
     * Android 12 the cell count is derived from `minWidth`/`minHeight`, so raising either — or letting
     * `minResizeWidth`/`minResizeHeight` drift above them — would make this a two-cell widget on
     * exactly those launchers. The Today strip can afford a raised minimum; a widget placed as a 1x1
     * cannot.
     */
    @Test
    fun theWidgetInfoIsAResizableSingleCellWithAFortyDpFloor() {
        val info = File("src/main/res/xml/hijri_dual_date_widget_info.xml")
        assertTrue(
            "widget-info not found at ${info.absolutePath}; if the res directory moved, fix this path " +
                "rather than deleting this test",
            info.isFile,
        )
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
        val root = factory.newDocumentBuilder().parse(info).documentElement

        assertEquals("appwidget-provider", root.tagName)
        assertEquals(
            "the tile must be resizable on both axes, or its type can never grow (point 7)",
            "horizontal|vertical",
            attr(root, "android:resizeMode"),
        )
        assertEquals("a 1x1 targets one cell", "1", attr(root, "android:targetCellWidth"))
        assertEquals("a 1x1 targets one cell", "1", attr(root, "android:targetCellHeight"))
        assertEquals(
            "the declared minimum height must stay at the tiles' 40dp: on launchers before Android 12 the " +
                "cell count is derived from minWidth/minHeight, so a larger value makes this a two-cell " +
                "widget there",
            "${DualDateTileTypography.TILE_MIN_HEIGHT_DP}dp",
            attr(root, "android:minHeight"),
        )
        assertEquals(
            "the declared minimum width",
            "${DualDateTileTypography.TILE_MIN_WIDTH_DP}dp",
            attr(root, "android:minWidth"),
        )
        assertEquals(
            "minResizeHeight must not exceed the minimum, for the same pre-Android-12 reason",
            "${DualDateTileTypography.TILE_MIN_HEIGHT_DP}dp",
            attr(root, "android:minResizeHeight"),
        )
        assertEquals(
            "and the same for the width",
            "${DualDateTileTypography.TILE_MIN_WIDTH_DP}dp",
            attr(root, "android:minResizeWidth"),
        )
        assertEquals(
            "the 30-minute sweep every widget-info in this family declares",
            "1800000",
            attr(root, "android:updatePeriodMillis"),
        )
    }

    // ── The data the tile renders ─────────────────────────────────────────────

    /**
     * Every zone reads a projected field, in the widget's own language and numerals.
     *
     * The composable is not reachable from a JVM test, so this covers the render paths' inputs: the band
     * reads [com.muazdev.hijricalendar.widgetdata.TodayHijriWidgetData.weekdayName], the body reads the
     * Hijri day, the **full** [com.muazdev.hijricalendar.widgetdata.TodayHijriWidgetData.hijriMonthName], and
     * the Gregorian line reads its month and day — all in the widget's language rather than the device's
     * (WG-12).
     */
    @Test
    fun everyZoneReadsAProjectedFieldInTheWidgetsLanguage() {
        val anchor = HijriWidgetRefreshScheduler.todayEpochDay()
        val urdu = checkNotNull(
            todayHijriWidgetData(
                anchorEpochDay = anchor,
                options = createWidgetOptions(
                    language = WidgetLanguage.URDU,
                    numeralStyle = NumeralStyle.ARABIC_INDIC,
                ),
            ),
        ) { "no today record for Urdu" }
        val english = checkNotNull(
            todayHijriWidgetData(
                anchorEpochDay = anchor,
                options = createWidgetOptions(
                    language = WidgetLanguage.ENGLISH,
                    numeralStyle = NumeralStyle.WESTERN,
                ),
            ),
        ) { "no today record for English" }

        for ((language, data) in listOf(
            WidgetLanguage.URDU to urdu,
            WidgetLanguage.ENGLISH to english,
        )) {
            assertTrue(
                "$language: the band's weekday name must not be blank",
                data.weekdayName.isNotBlank(),
            )
            assertTrue("$language: the Hijri day must not be blank", data.hijriDayText.isNotBlank())
            assertTrue(
                "$language: the Hijri month name must not be blank",
                data.hijriMonthName.isNotBlank(),
            )
            assertTrue(
                "$language: the Gregorian month must not be blank",
                data.gregorianMonthName.isNotBlank(),
            )
            assertTrue(
                "$language: the Gregorian day must not be blank",
                data.gregorianDayText.isNotBlank(),
            )
        }

        // The language follows the widget, not the device (WG-12) — the same claim the strip's and the
        // tiles' tests make, held for the dual tile because it is a fifth widget that could get it wrong.
        assertTrue(
            "an Urdu widget must not render an English weekday name; got '${urdu.weekdayName}'",
            urdu.weekdayName.any { it.code > 0x7F },
        )
        assertTrue(
            "an Urdu widget must not render an English month name; got '${urdu.hijriMonthName}'",
            urdu.hijriMonthName.any { it.code > 0x7F },
        )
        assertTrue(
            "an English widget must not render an Urdu month name; got '${english.hijriMonthName}'",
            english.hijriMonthName.all { it.code < 0x80 },
        )
    }

    /**
     * The Gregorian date's halves are **day-right, month-left, in both directions**.
     *
     * The placement is **fixed**, so the rule keys on the *device* — a Glance `Row` is a horizontal
     * `LinearLayout` the platform mirrors on an RTL device, and the emission order has to run against that
     * mirroring exactly once.
     *
     * Using [resolveLayoutRtl] here would be the category error worth guarding: it folds the widget's
     * language in, so on an Urdu widget it would emit month-first — which with the platform mirroring
     * lands the **day on the left**, the opposite of the design. It looks correct on an English phone,
     * which is how it would ship.
     */
    @Test
    fun theGregorianDateIsDayRightMonthLeftInBothDirections() {
        assertEquals(
            "on an LTR device the children are emitted month-first, which lands month-left / day-right",
            false,
            dayFirstForDayOnRight(deviceRtl = false),
        )
        assertEquals(
            "on an RTL device they are emitted day-first, so the mirroring still lands day on the right",
            true,
            dayFirstForDayOnRight(deviceRtl = true),
        )
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun layoutFile(): File {
        val file = File("src/main/res/layout/hijri_dual_date_widget_preview_layout.xml")
        assertTrue(
            "preview layout not found at ${file.absolutePath}; if the res directory moved, fix this path " +
                "rather than deleting this test",
            file.isFile,
        )
        return file
    }

    private fun layoutRoot(): Element {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
        return factory.newDocumentBuilder().parse(layoutFile()).documentElement
            .also { assertEquals("the root must be a LinearLayout", "LinearLayout", it.tagName) }
    }

    private fun childrenOf(element: Element): List<Element> = buildList {
        val nodes = element.childNodes
        for (i in 0 until nodes.length) {
            val node = nodes.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) add(node as Element)
        }
    }

    private fun descendants(element: Element): List<Element> = buildList {
        for (child in childrenOf(element)) {
            add(child)
            addAll(descendants(child))
        }
    }

    /** The five `TextView`s in document order: band weekday, day, month, greg month, greg day. */
    private fun zoneTextViews(): List<Element> = descendants(layoutRoot()).filter { it.tagName == "TextView" }

    private fun attr(element: Element, name: String): String? =
        element.getAttribute(name).takeIf { it.isNotEmpty() }

    /** A `textSize` in the XML, in whole sp. Int because JUnit's `assertEquals` is not for Floats. */
    private fun spOf(element: Element): Int =
        attr(element, TEXT_SIZE)?.removeSuffix("sp")?.toInt()
            ?: error("a preview TextView has no $TEXT_SIZE: $element")

    /** The sp an XML mirror should declare for a computed size. */
    private fun spInXml(sizeSp: Float): Int = sizeSp.roundToInt()

    /** The three captions by name, so a sweep's failure message says which line is wrong. */
    private fun DualDateTileSizes.namedLabels(): Map<String, TextUnit> = mapOf(
        "band weekday" to weekdaySize,
        "Hijri month" to monthSize,
        "Gregorian date" to subRowSize,
    )

    /** The live band's height in dp — its text's line box, plus top and bottom padding. */
    private fun bandHeightDp(sizes: DualDateTileSizes): Float =
        sizes.weekdaySize.value * TEXT_LINE_RATIO +
            DualDateTileTypography.BAND_TOP_PADDING_DP +
            DualDateTileTypography.BAND_PADDING_DP

    private companion object {
        /**
         * How much leading may sit between a line box and its glyphs before the "equal by construction,
         * residual bounded" claim stops being honest.
         *
         * **It scales with the type size and the modelled line ratio**, because it *is* leading: the
         * residual is a share of a [TEXT_LINE_RATIO] line box. That ratio is now the family's
         * worst-case model rather than a Latin face's measured metric, so the bound moved up with it —
         * the honest direction, since a readable Nastaliq setting brings more font leading with it, and
         * the number is quoted so the trade is visible rather than discovered as an asymmetry on a
         * device.
         */
        const val MAX_ACCEPTED_RESIDUAL_DP = 5f

        /** The preview XML attribute every text size lives in. */
        const val TEXT_SIZE = "android:textSize"

        /**
         * The centred Hijri day figure's index in [zoneTextViews]' document order: the band's weekday, the
         * day, the month name, the Gregorian halves.
         *
         * Named rather than written as `1` so a line added to the mirror moves this one entry instead of
         * silently shifting every index assertion onto the wrong line — a class of drift that fails as a
         * *plausible* assertion rather than as an error.
         */
        const val DAY_LINE_INDEX = 1

        /**
         * The Hijri month name's index in [zoneTextViews]: band weekday, day, month name, then the
         * Gregorian date's two halves.
         */
        const val MONTH_LINE_INDEX = 2

        /** The Gregorian date's month half — the one that used to be muted. */
        const val GREGORIAN_MONTH_LINE_INDEX = 3

        /** The dash between the Gregorian halves: month, separator, day. */
        const val GREGORIAN_SEPARATOR_LINE_INDEX = 4

        /** The weekday the mirror declares. */
        const val KNOWN_WEEKDAY = "بدھ"

        /** The **complete** Hijri month name the mirror declares — not `ربیع ۲`. */
        const val COMPLETE_HIJRI_MONTH = "ربیع الثانی"
    }
}
