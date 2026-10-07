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
 * Two earlier versions of this tile's sizing failed on-device as **unreadable text**, and both failures
 * were the tile not following the family:
 *
 * 1. Every zone sized as a *fraction of the granted height*, which rendered a **6sp weekday** on a real
 *    72dp cell.
 * 2. The remedy for that — a uniform shrink-to-fit — made it worse in a way nobody could see: raising the
 *    caption sizes moved the shrink's pivot up, so 20/20/23sp requested rendered a **12.9sp** header,
 *    smaller than the 15sp it replaced.
 *
 * The sibling tiles show the correct posture, and it is the one this file holds this tile to: **fixed,
 * generous sizes sized for the cell a launcher actually grants, with clipping below that accepted and
 * recorded rather than designed away.** `HijriDateWidget` and `GregorianDateWidget` ask for ~107–122dp of
 * line box in a cell whose declared minimum is 40dp, and
 * `DateTilePreviewLayoutTest.theThreeLinesStayWithinTheAcceptedVerticalBudget` asserts only that the figure
 * has not grown *past what was accepted*.
 *
 * So the strongest assertions here are **equality with [DateTileTypography]** — the tile must share the
 * sibling tiles' numbers rather than a parallel set — plus a recorded budget, exactly as the family records
 * its own.
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
     * The day figure is the sibling tiles' figure, less an explicit share — **and no more or less**.
     *
     * The figure dropped to 30sp so the four lines of this tile coexist. That reduction has to be bounded,
     * or "a bit smaller" becomes an open-ended licence: the assertion is that the day is *under* the
     * sibling tiles' 36sp and still well above every caption, so it stays the tile's loudest element.
     */
    @Test
    fun theDayFigureIsTheSiblingFigureLessAnExplicitShare() {
        assertTrue(
            "the day figure must stay below the sibling tiles' ${DateTileTypography.daySize.value}sp, " +
                "because this tile carries four lines rather than their three",
            DualDateTileTypography.daySize.value < DateTileTypography.daySize.value,
        )
        assertTrue(
            "and the captions must never reach it: the day is ${DualDateTileTypography.daySize.value}sp " +
                "against a caption ceiling of " +
                "${DualDateTileTypography.daySize.value * DualDateTileTypography.CAPTION_MAX_SHARE}sp",
            DualDateTileTypography.daySize.value >
                DualDateTileTypography.daySize.value * DualDateTileTypography.CAPTION_MAX_SHARE,
        )
        // And it is the fixed figure, not a width-scaled one, at every width.
        for (width in 1..200) {
            assertEquals(
                "at ${width}dp the day must be the fixed figure, not a width-scaled one",
                DualDateTileTypography.daySize.value,
                DualDateTileTypography.sizesFor(width.toFloat()).daySize.value,
                0.001f,
            )
        }
    }

    /**
     * The captions scale the sibling tiles' own rule — borrowed whole, dialled back.
     *
     * `CAPTION_RATIO` is a **scale** rather than a constant, so the band weekday and the Hijri month name
     * still widen with the granted width the way the sibling tiles' names do. It is also what keeps the
     * weekday under the day figure: unscaled, the sibling rule returns 32sp at a 72dp text width, which
     * would have made a weekday larger than the Hijri day.
     */
    @Test
    fun theCaptionsScaleTheSiblingTilesOwnRule() {
        for (width in 1..200) {
            val sibling = DateTileTypography.weekdaySizeFor(width.toFloat()).value
            val sizes = DualDateTileTypography.sizesFor(width.toFloat())

            // The relationship: the sibling rule, scaled, then clamped at both ends.
            val ceiling = DualDateTileTypography.daySize.value * DualDateTileTypography.CAPTION_MAX_SHARE
            val expected = (sibling * DualDateTileTypography.CAPTION_RATIO)
                .coerceIn(DateTileTypography.weekdaySizeFloor.value, ceiling)

            assertEquals(
                "the band's weekday at ${width}dp: the sibling rule returns $sibling, scaled and clamped " +
                    "it should be $expected",
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
     * No line on this tile is smaller than the smallest line on the sibling tiles.
     *
     * The legibility floor expressed as a relationship, so it cannot drift: the Gregorian date is held to
     * [DateTileTypography.weekdaySizeFloor], the floor those tiles hold their own captions to. A 1×1 tile
     * in this family must never hold the smallest text in the family.
     */
    @Test
    fun noLineIsSmallerThanTheSiblingTilesFloor() {
        for (width in 1..200) {
            val sizes = DualDateTileTypography.sizesFor(width.toFloat())
            for ((name, size) in sizes.namedLabels()) {
                assertTrue(
                    "at ${width}dp the $name is ${size.value}sp, below the sibling tiles' " +
                        "${DateTileTypography.weekdaySizeFloor.value}sp caption floor",
                    size.value >= DateTileTypography.weekdaySizeFloor.value - 0.001f,
                )
            }
        }
    }

    /**
     * The Gregorian date never out-weighs the month name above it, and never collapses.
     *
     * The one size this tile picks for itself, so it needs both bounds: a floor so it cannot vanish on a
     * narrow tile, and a ceiling so it stays a caption beside a heading rather than competing with it.
     */
    @Test
    fun theGregorianDateIsFlooredAndSubordinate() {
        assertEquals(
            "the Gregorian date's floor must be the sibling tiles' caption floor, not a number of its own",
            DateTileTypography.weekdaySizeFloor,
            DualDateTileTypography.subRowSizeFloor,
        )
        val narrowest = DualDateTileTypography.sizesFor(1f)
        assertTrue(
            "on the narrowest tile the Gregorian date is ${narrowest.subRowSize.value}sp",
            narrowest.subRowSize.value >= DateTileTypography.weekdaySizeFloor.value,
        )
        for (width in 1..200) {
            val sizes = DualDateTileTypography.sizesFor(width.toFloat())
            assertTrue(
                "at ${width}dp the Gregorian date (${sizes.subRowSize.value}sp) must not exceed the " +
                    "month name (${sizes.monthSize.value}sp)",
                sizes.subRowSize.value <= sizes.monthSize.value,
            )
        }
    }

    /**
     * The sizes depend on **width only** — there is no height term anywhere.
     *
     * The failure this pins. A height term is what made this tile's text tiny: it shrank the names
     * whenever the cell was short, and a four-line 1×1 is always short. A fixed 1×1 varies along width and
     * not height in any way worth reacting to, and the sibling tiles size on width for that reason.
     */
    @Test
    fun theSizesAreWidthDriven() {
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
     * The vertical budget, held to the figure accepted for this layout.
     *
     * **This is the sibling tiles' test, deliberately.** `DateTilePreviewLayoutTest` asserts that its
     * tile's lines ask for no more than ~129dp — past which the tile clips its last line — and explicitly
     * *not* that the tile fits its declared 40dp minimum, because it does not and cannot.
     *
     * The assertion is that the figure has not grown *past what was accepted*, so a future size increase
     * is visible instead of absorbed. **The number is not a target**: it is what this tile costs with the
     * family's sizes, and reducing it means choosing smaller text — the exact change that made this tile
     * unreadable.
     */
    @Test
    fun theFourZonesStayWithinTheAcceptedVerticalBudget() {
        val requested = DualDateTileTypography.verticalBudgetDp(
            DualDateTileTypography.TILE_REFERENCE_TEXT_WIDTH_DP,
        ).roundToInt()

        assertEquals(
            "the four zones ask for ~${requested}dp at the reference width, past the ~" +
                "$ACCEPTED_MAX_REQUESTED_DP dp accepted for this layout. Raising it is not free: the tile " +
                "already clips on a shorter cell, and the shrink-to-fit that avoided that is what made " +
                "the text unreadable",
            ACCEPTED_MAX_REQUESTED_DP,
            requested,
        )
    }

    /**
     * The budget does **not** exceed the sibling tiles' own worst case.
     *
     * Four lines across three zones is shorter than the siblings' three zones with a full-width figure, so
     * the fourth zone costs *less* height here rather than more. A future change that gave the day its own
     * full-width zone again, or dropped a caption, would push the figure past this and fail — which is the
     * point, since that is the arrangement that could not fit.
     */
    @Test
    fun theBudgetDoesNotExceedTheSiblingTilesOwn() {
        val siblingSum = DateTileTypography.weekdaySizeCeiling.value * 2 + DateTileTypography.daySize.value
        val siblingWorstCase = siblingSum * DualDateTileTypography.LINE_HEIGHT_RATIO
        val ours = DualDateTileTypography.verticalBudgetDp(
            DualDateTileTypography.TILE_REFERENCE_TEXT_WIDTH_DP,
        )

        assertTrue(
            "this tile asks for ~${ours.toInt()}dp against the sibling tiles' worst case of ~" +
                "${siblingWorstCase.toInt()}dp",
            ours <= siblingWorstCase,
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
     * The mirror's Hijri month name is the **complete** one, not the abbreviated `ربیع ٢` form.
     *
     * The abbreviated [com.muazdev.hijricalendar.widgetdata.TodayHijriWidgetData.hijriMonthShortName] only
     * ever existed because the band was too narrow for the long name. With the month moved into the body
     * there is room for it in full, and showing `ربیع الثانی` is both correct and clearer. Asserted because
     * the short form is still on the projection for iOS, so nothing else would stop it creeping back here.
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
     * The band carries the **weekday name alone** and has no padding of its own.
     *
     * The padding half is a reported defect turned into a test: an earlier version added 2dp inside the
     * band, and because a line box's leading is asymmetric — more space below the baseline than above it —
     * *symmetric* padding around it lands off-centre by the font's descent. On a device that showed as a
     * stripe of empty accent above the text, which reads as a layout mistake because it is one.
     *
     * The band-also-carries-the-day half is a reverted arrangement: folding the figure into the band did
     * save height, but it read as a header label rather than as the answer the tile exists to give.
     */
    @Test
    fun theBandCarriesOnlyTheWeekdayAndHasNoVerticalPadding() {
        val band = childrenOf(layoutRoot()).first()

        assertEquals("the band must be a single TextView", "TextView", band.tagName)
        assertEquals(
            "the band must carry the weekday name",
            KNOWN_WEEKDAY,
            attr(band, "android:text"),
        )
        assertEquals(
            "the mirror's band must not pad ABOVE its text: a line box's leading is asymmetric, so any " +
                "space above the glyphs reads as a stripe of empty accent",
            "0dp",
            attr(band, "android:paddingTop"),
        )
        assertEquals(
            "the band's extra height goes below its text, so the mirror must declare BAND_PADDING_DP there",
            "${DualDateTileTypography.BAND_PADDING_DP}dp",
            attr(band, "android:paddingBottom"),
        )

        // The live side: the band is its line box plus BAND_PADDING_DP, which is added **below** the text.
        val sizes = DualDateTileTypography.sizesFor(DualDateTileTypography.TILE_REFERENCE_TEXT_WIDTH_DP)
        val expected = sizes.weekdaySize.value * DualDateTileTypography.LINE_HEIGHT_RATIO +
            DualDateTileTypography.BAND_PADDING_DP
        assertEquals(
            "the live band's height is its line box plus the below-only padding",
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
     * The widget-info is a **strict** 1×1: a 40dp minimum and no resizing.
     *
     * The property that is easy to break by "fixing" the clipping: on launchers before Android 12 the cell
     * count is derived from `minWidth`/`minHeight`, so raising `minHeight` here would make this a two-cell
     * widget on exactly those launchers. The Today strip can afford a raised minimum because it is
     * resizable.
     */
    @Test
    fun theWidgetInfoIsAStrictSingleCell() {
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
            "a strict 1x1 must not be resizable, or it stops being the thing this ticket adds",
            "none",
            attr(root, "android:resizeMode"),
        )
        assertEquals("a strict 1x1 targets one cell", "1", attr(root, "android:targetCellWidth"))
        assertEquals("a strict 1x1 targets one cell", "1", attr(root, "android:targetCellHeight"))
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

    /** The live band's height in dp — its text's line box, with no padding added. */
    private fun bandHeightDp(sizes: DualDateTileSizes): Float =
        sizes.weekdaySize.value * DualDateTileTypography.LINE_HEIGHT_RATIO +
            DualDateTileTypography.BAND_PADDING_DP

    private companion object {
        /**
         * The most vertical space the four zones are accepted to ask for, in dp, at the reference width.
         *
         * ~92dp, against the sibling tiles' accepted ~129dp for three lines — **less**, because the four
         * lines sit across three zones rather than four.
         *
         * **It is not a target.** It is what this layout costs with the family's sizes, and the tile still
         * clips on a cell shorter than it — the same accepted cost the sibling tiles carry and record
         * rather than design away.
         */
        const val ACCEPTED_MAX_REQUESTED_DP = 92

        /**
         * How much leading may sit between a line box and its glyphs before the "equal by construction,
         * residual bounded" claim stops being honest.
         *
         * **It scales with the type size**, because it is leading: the residual is a share of a 1.17 line
         * box, so borrowing the sibling tiles' larger names makes it bigger than it was when this tile drew
         * 13sp text. That is the honest direction — readable type brings more font leading with it — and the
         * number is quoted so the trade is visible rather than discovered as an asymmetry on a device.
         */
        const val MAX_ACCEPTED_RESIDUAL_DP = 3f

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
