package com.muazdev.hijricalendar.widget.glance

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.currentState
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.PreviewSizeMode
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.muazdev.hijricalendar.widgetdata.TodayHijriWidgetData
import com.muazdev.hijricalendar.widgetdata.WidgetLanguage
import com.muazdev.hijricalendar.widgetdata.WidgetLocalization

/**
 * Resizable "today" strip widget: full screen width, showing the weekday name above today's Hijri
 * and Gregorian dates side by side, separated by a hairline. It is resizable
 * (`resizeMode="horizontal|vertical"` in its provider info) down to its natural text bounds, so
 * the text is never clipped or hidden at the minimum size — which is why the widget-info's declared
 * minimum is [TodayStripTypography.STRIP_MIN_HEIGHT_DP] and not the 40dp it used to declare: that
 * figure does not hold three lines of type.
 *
 * Unlike the calendar grid it has no per-widget settings screen: it follows the family options
 * mirror ([HijriWidgetConfig.loadFamily]) written by every configurations-screen save on the grid
 * widget, so language, numerals, Hijri source and moon-sighting adjustment always match the
 * family's latest choices. Tapping anywhere opens the app, like the grid widget.
 */
public class HijriTodayWidget : GlanceAppWidget() {

    /**
     * `SizeMode.Exact`, so the strip's composition sees the cell the launcher actually granted.
     *
     * `Single` builds `LocalSize` from the provider's declared *minimum*
     * (`AppWidgetUtils.kt` → `SizeMode.Single -> listOf(minSize)`), so the design's "a tall placement
     * renders a bigger figure" was never true on a device: the strip rendered one size no matter how
     * far it was resized, and a *short* grant clipped its caption.
     *
     * `Exact` was tried once before and reverted: on a Pixel 6a (strip 218x97dp) the figure jumped to
     * the 40sp ceiling and **the month caption was clipped off the bottom**, because the old rule grew
     * only the figure and modelled a line box as 1.17em while the real Urdu Nastaliq boxes are taller.
     * The fix is not to keep `Single` but to make the whole stack scale together from the granted
     * height ([TodayStripTypography.sizesFor]): the caption can then never be the line that overflows,
     * because it is sized from the same scale as the figure.
     *
     * `Exact` is safe for this widget the same way it is for the tiles: the strip's only interaction is
     * one `actionStartActivity`, a `PendingIntent` on the `RemoteViews`, so composing per size cannot
     * double-launch the app.
     */
    override val sizeMode: SizeMode = SizeMode.Exact

    /**
     * Picker previews are composed at a realistic placed strip size instead of the widget's own
     * minimum, so the weekday line and both date halves actually fit without clipping.
     */
    override val previewSizeMode: PreviewSizeMode = SizeMode.Responsive(
        setOf(DpSize(144.dp, TodayStripTypography.STRIP_MIN_HEIGHT_DP.dp)),
    )

    @OptIn(ExperimentalGlanceApi::class)
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // One-shot peek: port any legacy flat-file config (which seeds the family mirror once) and
        // warm up the Pakistan century table if needed. The options themselves come from the
        // process-global family mirror below — the strip has no settings screen of its own and
        // follows whatever the family's most recent configure-screen save configured.
        HijriWidgetConfig.migrateLegacyIfNeeded(context, id)
        val peeked = HijriWidgetConfig.load(context, id)
        if (peeked.source.pakistan) {
            PakistanWarmUp.ensureWarm()
        }
        val openAction = actionStartActivity(openAppIntent(context))

        provideContent {
            val prefs = currentState<Preferences>()
            val rawOptions = HijriWidgetConfig.decodeOptions(prefs)
            val options = rawOptions ?: HijriWidgetConfig.loadFamily(context)
            val optionsFrom = if (rawOptions != null) "perWidget" else "family"
            HijriWidgetRefreshLog.d(
                "render:tile",
                "type=Today overrides=${options.monthLengthOverrides.size} language=${options.language} optionsFrom=$optionsFrom",
            )
            val colors = WidgetColors.forTheme(options.theme)
            val data = HijriWidgetRenderCache.today(
                glanceId = id.toString(),
                options = options,
                todayEpochDay = HijriWidgetRefreshScheduler.todayEpochDay(),
            )
            HijriTodayRoot(
                today = data,
                colors = colors,
                openAction = openAction,
                layoutRtl = computeLayoutRtl(context, options.language),
                language = options.language,
            )
        }
    }

    /**
     * Real picker preview for Android 15+: today's Hijri + Gregorian dates in the family's current
     * options, non-interactive. [HijriWidgetPreviewPublisher] publishes the result.
     */
    @OptIn(ExperimentalGlanceApi::class)
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val options = HijriWidgetConfig.loadFamily(context)
        if (options.source.pakistan) {
            PakistanWarmUp.ensureWarm()
        }
        HijriWidgetRefreshLog.d(
            "render:tile",
            "type=Today overrides=${options.monthLengthOverrides.size} language=${options.language} optionsFrom=family",
        )
        val colors = WidgetColors.forTheme(options.theme)
        provideContent {
            val data = buildRenderData(context, options, viewedMonth = null, glanceId = "preview", optionsFrom = "family")
            HijriTodayRoot(
                today = data.todayHijri,
                colors = colors,
                openAction = null,
                layoutRtl = data.layoutRtl,
                language = options.language,
            )
        }
    }
}

/**
 * The strip's type sizes and vertical budget, as data so the Android 12-14 `previewLayout` mirror
 * and its test can be held to them — the same arrangement as [DateTileTypography].
 *
 * **Three lines, not two.** The weekday name goes above the pair of dates rather than into either
 * caption, for the reason [DateTileRoot] gives for the tiles: merged into a caption it stops being the
 * answer to "what day is it?" and becomes small print beside a month name. It is centred across the
 * whole strip, **not repeated on each side** — both halves are the same physical day, so a second copy
 * would say nothing the first did not.
 *
 * The day figure is what the strip exists to show, so it is not traded away for the other two lines.
 * Every line takes one scale from the granted height ([sizesFor]), so the figure grows whenever the
 * strip does, and the round-3 rebalance below moves size *between* the band and the captions rather
 * than taking it from the figure. A taller placement really is a taller figure because
 * [HijriTodayWidget.sizeMode] is `SizeMode.Exact` — see that property for why the declared minimum is
 * not the only cell the type has to fit.
 */
internal object TodayStripTypography {

    /**
     * The weekday name, centred above both dates, at the reference height.
     *
     * Drawn in the family's shared accent band ([WeekdayBand]) now, so it matches the tiles' weekday
     * exactly rather than being a lookalike: same fill, same on-accent text, same weight. It was the
     * only bare-text weekday left in the family.
     *
     * **A step smaller than it shipped** (17sp → 13sp, round 3). At a placed strip's height the band
     * was taking ~43% of the widget and leaving the month captions under it ~9.4sp, which is exactly
     * what the feedback reported: the weekday line "taking a large portion of day name ... which
     * shortens the month names which are almost un readable". The band is one full-width word with the
     * whole row to itself, so it carries a size the captions cannot; 13sp puts it at ~33% of the same
     * height and lifts the captions by ~30% with no change to the grant. It stays well under
     * [daySize], so the figure is still the loudest thing on the strip.
     */
    val weekdaySize = 13.sp

    /**
     * The day figure at the reference height: 28sp.
     *
     * Every line on the strip is now `reference * scale`, where the scale comes from the granted
     * height (see [sizesFor]). This replaces a rule that grew the figure into the space the other two
     * lines had not claimed: that rule modelled a line box as 1.17em, the real Urdu Nastaliq boxes are
     * taller, and on a device the *month caption* — not the figure — was the line it clipped. A single
     * scale with all three lines in it cannot clip a later line at the expense of an earlier one.
     */
    val daySize = 28.sp

    /** Never larger than this, however tall: a strip should still read as a strip. */
    val daySizeCeiling = 40.sp

    /**
     * The month + year (and era) caption under each day figure — the bottom line of each column.
     *
     * **A step larger than it shipped** (11sp → 14sp, round 3): this is the line the strip's feedback
     * is about. It was the smallest text in the family — ~9.4sp at a placed strip's height, against a
     * 16sp weekday band and a 24sp figure — while carrying the month and year a reader needs in order
     * to know *when* the date is. The band above it gave the height up ([weekdaySize]), so this costs
     * no height, and [captionFor] still trims the string for a half too narrow for it.
     */
    val captionSize = 14.sp

    /** The largest scale any line renders at, set by [daySizeCeiling]. */
    private val MAX_SCALE: Float = daySizeCeiling.value / daySize.value

    /** Gap under the weekday line, and under each day figure. */
    val lineGap = 1.dp

    /**
     * The declared minimum height, in dp.
     *
     * **Sized against the height a *placed* strip has, not against whatever would be comfortable.**
     * That is the whole lesson of this layout: an already-placed widget keeps the size the launcher
     * granted it, and raising `minHeight` in the XML does **not** resize it. Growing the type without
     * this figure moving with it is what pushed the month caption off the bottom of a strip whose
     * numbers had been enlarged — the widget stayed its old height, the content outgrew it, and the
     * last line was what got clipped.
     *
     * Since the type now scales to fit whatever height it is granted, this is a floor the launcher
     * cannot go below, not a height the type is tuned against; `TodayStripPreviewLayoutTest`
     * recomputes the three lines' request from these constants in every widget-info qualifier.
     */
    const val STRIP_MIN_HEIGHT_DP = 72

    /**
     * The height at which the static Android 12-14 `previewLayout` mirror renders: the declared
     * minimum, since that is the smallest height a placed strip can have and the one a static XML can
     * state.
     */
    val DAY_HEIGHT_REFERENCE_DP: Float = STRIP_MIN_HEIGHT_DP.toFloat()

    /** The floor for a vertical resize — just below the natural height of the three lines. */
    const val STRIP_MIN_RESIZE_HEIGHT_DP = 68

    /** Horizontal padding either side of the date row. The weekday band stays flush to the edges. */
    const val HORIZONTAL_PADDING_DP = 12

    /**
     * The scalable part at scale 1: the weekday and the caption are script text and the figure is digits,
     * so they are counted at [scriptRatio] and [DIGIT_LINE_RATIO].
     */
    fun referenceTextHeightDp(scriptRatio: Float): Float =
        weekdaySize.value * scriptRatio +
            daySize.value * DIGIT_LINE_RATIO +
            captionSize.value * scriptRatio

    /** [referenceTextHeightDp] at the system script ratio; the static mirror and the tests read this. */
    val REFERENCE_TEXT_HEIGHT_DP: Float = referenceTextHeightDp(TEXT_LINE_RATIO)

    /** The non-scaling part of the stack: the band's padding and the gap under it. */
    val FIXED_HEIGHT_DP: Float =
        (WidgetBand.TOP_PADDING_DP + WidgetBand.BOTTOM_PADDING_DP).toFloat() + lineGap.value

    /**
     * The height the three lines plus the band's padding and the gap under it ask for at scale 1 — the
     * yardstick [widgetTypeScale] measures a granted height against.
     */
    val REFERENCE_STACK_HEIGHT_DP: Float = REFERENCE_TEXT_HEIGHT_DP + FIXED_HEIGHT_DP

    /**
     * The strip's three type sizes for a granted height: **one scale**, so the weekday, the day figure
     * and the caption grow and shrink together and the stack always fits the cell.
     *
     * Only the height is consulted. The width is not a constraint the type has to satisfy: the strip
     * spans whatever the launcher grants, and the one horizontally tight line — the caption — chooses
     * its own string for the width it sees ([captionFor]) rather than shrinking the whole strip. The
     * height is the axis a launcher genuinely varies for a strip, and the axis whose overflow past the
     * declared minimum used to clip the caption.
     */
    fun sizesFor(grantedHeightDp: Float, scriptRatio: Float = TEXT_LINE_RATIO): TodayStripSizes {
        // Height only: the width is not a constraint the strip's type has to satisfy (see the KDoc), so
        // this is the height axis of [widgetTypeScale] with the width term dropped.
        val scale = ((grantedHeightDp - FIXED_HEIGHT_DP) / referenceTextHeightDp(scriptRatio))
            .coerceIn(MIN_TYPE_SCALE, MAX_SCALE)
        return TodayStripSizes(
            weekdaySize = (weekdaySize.value * scale).sp,
            daySize = (daySize.value * scale).sp,
            captionSize = (captionSize.value * scale).sp,
        )
    }

    /**
     * The day figure for a granted height — [sizesFor]'s figure. Kept as a named function because it
     * is the strip's headline number and the tests sweep it.
     */
    fun daySizeFor(grantedHeightDp: Float): TextUnit = sizesFor(grantedHeightDp).daySize

    /**
     * The height the three lines ask for at [TodayStripSizes] — used by tests and the mirror.
     */
    fun requestedHeightDp(sizes: TodayStripSizes, scriptRatio: Float = TEXT_LINE_RATIO): Float =
        sizes.weekdaySize.value * scriptRatio +
            sizes.daySize.value * DIGIT_LINE_RATIO +
            sizes.captionSize.value * scriptRatio +
            WidgetBand.TOP_PADDING_DP + WidgetBand.BOTTOM_PADDING_DP +
            lineGap.value

    /**
     * One half's caption: the month + year, abbreviated in that order when the half is too narrow.
     *
     * Round-2 feedback, point 9. The caption is a single `Text` with `maxLines = 1`, and Glance clips
     * rather than ellipsising — and because the line is centred, an over-long caption loses characters
     * off **both** ends, which is why the year was disappearing from the squeezed two-date strip even
     * though it is the part worth keeping. So the string is chosen here instead, before it is measured.
     *
     * Three candidates, in the order they are given up: **era, then the month's spelling, never the
     * year.** English: `Jumada al-ula 1448 ھ` → `Jumada al-ula 1448` → `Jumada I 1448`, and
     * `October 2026` → `Oct 2026`. Urdu has only two rungs — **Urdu month names are never
     * shortened**, so [abbrevMonthName] equals [monthName] there and the last rung degrades to the
     * second: `جمادى الأولى 1448 ھ` → `جمادى الأولى 1448`. The abbreviation rung is what a squeezed
     * half reaches when the full spelling will not fit: `Oct` still answers "which month?" where an
     * ellipsised `Octo…` only says that something was there. [abbrevMonthName] is what the
     * projection built for that slot ([TodayHijriWidgetData.gregorianMonthShortName] /
     * [TodayHijriWidgetData.hijriMonthShortName]), and whenever the projection did not abbreviate —
     * Urdu, or a month with no short form — it equals [monthName] rather than being blank.
     *
     * [yearText] is the projection's era-appended year (`1448 ھ`, `2026 AH`) — the bare year is its
     * first space-separated part, so no extra projection field is needed and the era keeps the spelling
     * the projection chose for the widget's language (WG-12). When the projection appended no era the
     * first two candidates are the same string and the era rung is skipped.
     *
     * The width is *estimated* (see [estimatedCaptionWidthDp]); both wrong answers are benign, and the
     * estimate errs towards giving something up early rather than the year being cut.
     */
    fun captionFor(
        monthName: String,
        abbrevMonthName: String,
        yearText: String,
        halfWidthDp: Float,
        language: WidgetLanguage,
        captionSizeSp: Float = captionSize.value,
    ): String {
        val full = "$monthName $yearText"
        val bareYear = yearText.substringBeforeLast(' ')
        val noEra = "$monthName $bareYear"
        fun fits(text: String): Boolean =
            estimatedCaptionWidthDp(text, language, captionSizeSp) <= halfWidthDp

        // `halfWidthDp` reaches us from `LocalSize.current.width`, which under `SizeMode.Exact` is the
        // width the launcher actually granted. A composition can still be handed a tiny width — a
        // picker preview, or a host mid-placement — and dropping content on an artefact rather than a
        // real placement is what once hid the era marker even on full-screen strips, so nothing is
        // given up whenever the width we see is clearly not real.
        return when {
            halfWidthDp < 20f || fits(full) -> full
            noEra != full && fits(noEra) -> noEra
            else -> "$abbrevMonthName $bareYear"
        }
    }

    /**
     * A rough rendered width for a caption line, in dp.
     *
     * Glance 1.2 offers no text measurement, and the only decision this feeds is whether the era is
     * dropped, so a per-character average is enough. The two constants below are **measured** rather
     * than guessed — a caption's rendered width divided by its character count, at this size:
     * an 18-character Urdu caption measured 0.42em per character on a Pixel 6a and a 21-character
     * Latin one 0.52em, which is the whole reason this takes a [WidgetLanguage] instead of one
     * constant. Nastaliq's joined forms are narrower than the Latin faces' advance, so one constant
     * would either drop the Urdu era far too early or keep the Latin one long past the clip.
     */
    fun estimatedCaptionWidthDp(
        text: String,
        language: WidgetLanguage,
        captionSizeSp: Float = captionSize.value,
    ): Float = text.length * advanceEmFor(language) * captionSizeSp

    /** The width one date half has for its caption: half of the strip less its padding and divider. */
    fun captionHalfWidthDp(grantedWidthDp: Float): Float =
        ((grantedWidthDp - HORIZONTAL_PADDING_DP * 2 - DIVIDER_WIDTH_DP - DIVIDER_GAP_DP * 2) / 2f)
            .coerceAtLeast(0f)

    /** Per-character advance used by [estimatedCaptionWidthDp]. */
    private fun advanceEmFor(language: WidgetLanguage): Float =
        if (language.isRtl) RTL_ADVANCE_EM else LTR_ADVANCE_EM

    /** Measured on a Pixel 6a: Urdu Nastaliq, ~0.42em per character at [captionSize]. */
    private const val RTL_ADVANCE_EM = 0.42f

    /** Measured on a Pixel 6a: Latin, ~0.52em per character at [captionSize]. */
    private const val LTR_ADVANCE_EM = 0.52f

    /** The divider line's own width, so [captionHalfWidthDp] subtracts what the row actually spends. */
    const val DIVIDER_WIDTH_DP = 1
}

/**
 * The three type sizes the Today strip renders at, as one value so a single scale drives all of them.
 */
internal data class TodayStripSizes(
    val weekdaySize: TextUnit,
    val daySize: TextUnit,
    val captionSize: TextUnit,
)

/**
 * The strip's layout: the weekday name centred across the top, then the Gregorian and Hijri dates
 * side by side below it, separated by a vertical hairline. Each half is a bold day figure with the
 * month + year underneath.
 *
 * The Hijri side always sits on the right (the Gregorian on the left), regardless of the widget's
 * reading direction: the children are ordered by [layoutRtl] so platform RTL mirroring lands and keeps
 * the Hijri half on the right. The divider goes between the two halves in both orderings, which is the
 * only placement that is correct in either.
 */
@Composable
internal fun HijriTodayRoot(
    today: TodayHijriWidgetData?,
    colors: WidgetColors,
    openAction: Action?,
    layoutRtl: Boolean,
    language: WidgetLanguage,
) {
    val fonts = HijriWidgetFonts.default
    val cell = LocalSize.current
    val modifier = GlanceModifier.fillMaxSize().background(colors.background)
    val clickableModifier = if (openAction != null) modifier.clickable(openAction) else modifier

    // One scale from the granted height drives every line — a taller strip renders every line
    // larger — and the stack always fits, so the caption can never be the line that overflows.
    val sizes = TodayStripTypography.sizesFor(cell.height.value, fonts.scriptLineRatio())

    Column(
        modifier = clickableModifier,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        if (today == null) {
            Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                StripUnavailable(colors = colors, language = language)
            }
            return@Column
        }
        // The weekday both halves share: `TodayHijriWidgetData.weekdayName`, projected in the widget's
        // own language (WG-12) since the projection was written, and rendered by iOS and by every 1x1
        // tile. This strip was the one place it was dropped — and the one bare-text weekday left in the
        // family, now drawn in the same accent band ([WeekdayBand]) the tiles use.
        WeekdayBand(
            text = today.weekdayName,
            fontSize = sizes.weekdaySize,
            colors = colors,
            fontFamily = fonts.weekday.toGlanceFontFamily(),
            modifier = GlanceModifier.padding(bottom = TodayStripTypography.lineGap),
        )
        // The dates centre in whatever height the band left; the band is flush to the strip's edges.
        Box(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                // `fillMaxWidth` is what makes the two halves *spread*: `defaultWeight` splits only the
                // width a Row is given, so a content-sized Row in a centred Column would bunch both dates
                // in the middle. Stated here so the halves' share is independent of how the platform
                // measures an unconstrained Row.
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .padding(horizontal = TodayStripTypography.HORIZONTAL_PADDING_DP.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                val halfWidthDp = TodayStripTypography.captionHalfWidthDp(cell.width.value)
                val hijri = @Composable {
                    DateHalf(
                        today = today,
                        gregorian = false,
                        sizes = sizes,
                        halfWidthDp = halfWidthDp,
                        language = language,
                        colors = colors,
                    )
                }
                val gregorian = @Composable {
                    DateHalf(
                        today = today,
                        gregorian = true,
                        sizes = sizes,
                        halfWidthDp = halfWidthDp,
                        language = language,
                        colors = colors,
                    )
                }
                // Order so the Hijri side lands on the right in either reading direction.
                if (layoutRtl) {
                    hijri()
                    DateDivider(colors)
                    gregorian()
                } else {
                    gregorian()
                    DateDivider(colors)
                    hijri()
                }
            }
        }
    }
}

/**
 * One side of the strip, built from the projection's own fields.
 *
 * The caption comes from [TodayStripTypography.captionFor] — the rung ladder that gives up the era
 * first, then the month's spelling, and never the year — and the projection's `*YearText` is
 * era-appended (`1448 ھ`, `2026 AH`), whose bare part is its first space-separated piece, so no extra
 * projection field is needed and the era keeps the spelling the projection chose for the widget's
 * language (WG-12, FD-05). Both halves go through here so that ladder is stated once rather than
 * twice.
 */
@Composable
private fun RowScope.DateHalf(
    today: TodayHijriWidgetData,
    gregorian: Boolean,
    sizes: TodayStripSizes,
    halfWidthDp: Float,
    language: WidgetLanguage,
    colors: WidgetColors,
) {
    DateSide(
        dayText = if (gregorian) today.gregorianDayText else today.hijriDayText,
        caption = TodayStripTypography.captionFor(
            monthName = if (gregorian) today.gregorianMonthName else today.hijriMonthName,
            abbrevMonthName = if (gregorian) {
                today.gregorianMonthShortName
            } else {
                today.hijriMonthShortName
            },
            yearText = if (gregorian) today.gregorianYearText else today.hijriYearText,
            halfWidthDp = halfWidthDp,
            language = language,
            captionSizeSp = sizes.captionSize.value,
        ),
        daySize = sizes.daySize,
        captionSize = sizes.captionSize,
        colors = colors,
        gregorian = gregorian,
    )
}

/**
 * The strip's body when the projection could not be built at all.
 *
 * Its own composable rather than an inline branch because `HijriTodayRoot` had grown past detekt's
 * method-length limit, and this is the one part of it that is not the strip's layout: it is the
 * `ChromeLabels.monthUnavailable` message, resolved from the widget's own language rather than the
 * device locale (WG-12, the same rule every other string on this widget follows).
 */
@Composable
private fun StripUnavailable(colors: WidgetColors, language: WidgetLanguage) {
    WidgetText(
        text = WidgetLocalization.ChromeLabels.monthUnavailable(language),
        // A sentence, not a label: it wraps, and wrapping is the one thing the bundled face's
        // rasteriser cannot do — so this slot stays on Glance's `Text` deliberately.
        maxLines = Int.MAX_VALUE,
        style = TextStyle(
            color = colors.secondaryText,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        ),
    )
}

/**
 * The hairline between the two date halves.
 *
 * `widget_cell_border` is the grid's own divider token (FD-04), reused rather than given a second
 * near-identical colour: it is already a hairline alpha over the surface with a night variant, which
 * is exactly what this needs, and it is a `@ColorRes` `ColorProvider` like every other member of
 * [WidgetColors] so a night-mode switch re-resolves it at bind time rather than restarting the widget
 * (FD-07).
 *
 * Unweighted, so it stays a hairline rather than taking a share of the width from the two dates; the
 * [DIVIDER_GAP_DP] padding either side is what keeps it from touching their text, which matters because
 * each half is only half the strip's width and a long caption can reach all the way to the middle.
 */
@Composable
private fun RowScope.DateDivider(colors: WidgetColors) {
    Box(
        modifier = GlanceModifier
            .padding(horizontal = DIVIDER_GAP_DP.dp)
            .width(TodayStripTypography.DIVIDER_WIDTH_DP.dp)
            .fillMaxHeight()
            .background(colors.cellBorder),
    ) {}
}

/** Clear space either side of [DateDivider]. */
private const val DIVIDER_GAP_DP = 5

/**
 * One half of the strip: a bold day figure with the month + year line underneath. Weighted, so
 * the two halves split the width and sit visibly at the strip's two ends.
 */
@Composable
private fun RowScope.DateSide(
    dayText: String,
    caption: String,
    /** Derived from the granted height by the caller — see [TodayStripTypography.sizesFor]. */
    daySize: TextUnit,
    /** The caption size from the same scale, so the two lines cannot drift apart. */
    captionSize: TextUnit,
    colors: WidgetColors,
    /**
     * Which calendar this half shows. The strip renders both through this one composable, so the caption's
     * font slot cannot be inferred from the text — see [HijriWidgetFonts.forMonthTitle].
     *
     * It now decides the **day figure's** colour too, which is where the halves' remaining distinction
     * lives: the Hijri figure is the accent colour, the Gregorian one primary. Taking the palette rather
     * than a per-side colour is what makes that a rule instead of two call-site arguments that can drift.
     */
    gregorian: Boolean,
) {
    val fonts = HijriWidgetFonts.default
    val dayColor = if (gregorian) colors.primaryText else colors.accent
    // The caption's own half of the strip, as the rasteriser's ellipsis budget. The bundled Nastaliq
    // face is wider than the system fallback at the same size, so a caption the width estimator
    // accepted for the fallback can still be too wide for the half it sits in; without this it would
    // overflow into its sibling rather than being trimmed.
    val captionMaxWidthDp = (LocalSize.current.width.value / 2f).toInt()
    Column(
        modifier = GlanceModifier.defaultWeight(),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        WidgetText(
            text = dayText,
            style = TextStyle(
                color = dayColor,
                fontSize = daySize,
                fontWeight = FontWeight.Bold,
                fontFamily = fonts.dayNumber.toGlanceFontFamily(),
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
        WidgetText(
            text = caption,
            modifier = GlanceModifier.padding(top = TodayStripTypography.lineGap),
            style = TextStyle(
                color = colors.primaryText,
                fontSize = captionSize,
                fontWeight = FontWeight.Bold,
                fontFamily = fonts.forMonthTitle(gregorian).toGlanceFontFamily(),
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            maxWidthDp = captionMaxWidthDp,
        )
    }
}
