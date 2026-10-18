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
 * ## Why the band has no height of its own
 *
 * The band's height is exactly its text's line box plus [TOP_PADDING_DP]/[BOTTOM_PADDING_DP]. The
 * asymmetric padding exists because a line box's leading is asymmetric — more below the baseline than
 * above — so *symmetric* padding around it lands off-centre by the font's descent and shows as a
 * stripe of empty accent above the text. The top pad keeps tall ascenders (Urdu/Arabic) from being
 * clipped; the bottom pad is what pushes the glyphs off the band's lower edge.
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
 * [modifier] carries the caller's position — a weighted slot, a gap above the band, and so on — and is
 * applied *before* [fillMaxWidth] and [background], so the band spans whatever the caller grants it
 * without ever losing its fill to an order mistake at a call site.
 */
@Composable
internal fun WeekdayBand(
    text: String,
    fontSize: TextUnit,
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
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                fontFamily = fontFamily,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
        )
    }
}
