package com.muazdev.hijricalendar.widget.glance

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle

/**
 * The filled band every widget in the family draws behind its weekday name, and the paddings it
 * carries.
 *
 * Until this existed, only [HijriDualDateWidget] had a band: its weekday sat in an accent strip across
 * the top while [HijriDateWidget], [GregorianDateWidget] and [HijriTodayWidget] drew the same weekday
 * as bare text. Four widgets rendering the same line two different ways is the kind of divergence the
 * family is arranged to prevent, so the band is one composable and every weekday goes through it —
 * the same reasoning as [CaptionBand] holding the tiles' month line (FD-11).
 *
 * ## `widget_accent` on `widget_on_today`, not a colour of its own
 *
 * Reusing the family accent is what makes the band re-resolve for night mode and a forced theme, the
 * same argument [WidgetColors] makes for every token (FD-07). Giving it a blue because the reference
 * design's header is blue would produce the one colour in the family that does not re-resolve.
 *
 * ## Why the band's height is its ink, not its line box
 *
 * It used to be the text's line box plus [TOP_PADDING_DP]/[BOTTOM_PADDING_DP], and that is what made
 * the weekday look lost: a line box is ascent plus descent with most of it empty, so the band stood
 * 1.7 em tall for ~0.75 em of ink and a short word like `بدھ` was a smudge in a block of accent a
 * third of the tile tall. The word is now drawn by a raster **cropped to its ink** ([WidgetText]'s
 * `cropToInk`), so the band is the ink plus the two paddings and nothing else — tight, without
 * clipping a glyph and without taking height from the body.
 *
 * That is also why the band states no height: its content already *is* the height. The number the
 * stack reserves ([WeekdayBandFit.bandHeightDp]) is the face's measured ink rounded up, so a drawn band
 * can only ever be a fraction shorter than its own budget — never taller, which is the direction that
 * would push it through the layout below it.
 *
 * The asymmetric padding stays because ink is not symmetric about a baseline: the top pad keeps a tall
 * ascender off the band's edge and the larger bottom pad keeps the glyphs from sitting on the lower
 * one. A symmetric pad around an ink crop reads as a stripe of accent above the word.
 */
internal object WidgetBand {

    /** Clear space either side of the weekday text inside the band. */
    const val HORIZONTAL_PADDING_DP = 6

    /** Space above the weekday's line box, so tall ascenders are not clipped. */
    const val TOP_PADDING_DP = 2

    /** Space below the weekday's line box; see the object KDoc for why this is not symmetric. */
    const val BOTTOM_PADDING_DP = 3
}

/**
 * The weekday name in a full-width accent band, centred. The band is flush to the widget's edges; the
 * clear space its text needs is its own horizontal padding.
 *
 * [weekdaySize] comes from the caller's typography via [WeekdayBandFit.sizeFor], and the band's height
 * is derived from it here rather than at the call site: the height and the size are one decision, and
 * a call site that passed one without the other would draw a band the stack did not reserve for.
 *
 * [modifier] carries the caller's position — a weighted slot, a gap above the band, and so on — and is
 * applied *before* [fillMaxWidth] and [background], so the band spans whatever the caller grants it
 * without ever losing its fill to an order mistake at a call site.
 */
@Composable
internal fun WeekdayBand(
    text: String,
    weekdaySize: TextUnit,
    colors: WidgetColors,
    fontFamily: FontFamily?,
    modifier: GlanceModifier = GlanceModifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.accent),
        contentAlignment = Alignment.Center,
    ) {
        WidgetText(
            text = text,
            modifier = GlanceModifier.padding(
                start = WidgetBand.HORIZONTAL_PADDING_DP.dp,
                end = WidgetBand.HORIZONTAL_PADDING_DP.dp,
                top = WidgetBand.TOP_PADDING_DP.dp,
                bottom = WidgetBand.BOTTOM_PADDING_DP.dp,
            ),
            style = TextStyle(
                // The band's content colour, not the primary text tone: it is drawn *on* the accent,
                // and `widget_on_today` is the family's token for exactly that.
                color = colors.onTodayText,
                fontSize = weekdaySize,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            // The whole reason the band can be ink-tall: see the object KDoc.
            cropToInk = true,
        )
    }
}
