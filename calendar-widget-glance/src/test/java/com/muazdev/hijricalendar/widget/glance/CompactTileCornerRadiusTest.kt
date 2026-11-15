package com.muazdev.hijricalendar.widget.glance

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The compact tile rounds its own square, because it is the only widget in the family that paints
 * *inside* its cell and therefore does not inherit the launcher's clip (see
 * `rememberWidgetCornerRadius` in `HijriCompactDateWidget.kt`).
 *
 * The rule that matters is that the tile **matches** its neighbours rather than approximating them:
 * it uses the platform's own `system_app_widget_background_radius` when the platform has one, and the
 * AOSP default when it does not.
 */
class CompactTileCornerRadiusTest {

    @Test
    fun thePlatformRadiusIsUsedAsItIs() {
        // A 28dp launcher radius, stated in pixels at two densities: the dp that comes back must not
        // move with the density, or the tile would round differently on a tablet.
        assertEquals(28.0, widgetCornerRadiusDp(28f * 2.75f, 2.75f).toDouble(), 0.01)
        assertEquals(28.0, widgetCornerRadiusDp(28f * 1f, 1f).toDouble(), 0.01)
    }

    @Test
    fun aPlatformWithoutARadiusFallsBackToTheAospDefault() {
        // `null` is what pre-S reports, where Glance cannot round a view at all.
        assertEquals(
            WIDGET_CORNER_RADIUS_FALLBACK_DP.toDouble(),
            widgetCornerRadiusDp(null, 2.75f).toDouble(),
            0.01,
        )
    }

    @Test
    fun anEmptyPlatformRadiusIsTreatedAsMissing() {
        // A platform that answers 0px must not square the tile off silently.
        assertEquals(
            WIDGET_CORNER_RADIUS_FALLBACK_DP.toDouble(),
            widgetCornerRadiusDp(0f, 3f).toDouble(),
            0.01,
        )
    }

    @Test
    fun theFallbackIsTheAospWidgetBackgroundRadius() {
        assertEquals(16.0, WIDGET_CORNER_RADIUS_FALLBACK_DP.toDouble(), 0.01)
    }
}
