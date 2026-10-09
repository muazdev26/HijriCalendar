package com.muazdev.hijricalendar.widgetdata

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * How much of a date a **grid** widget paints in each day cell.
 *
 * Mirrors the in-app calendar's switch of the same name, so a host app that already stores one can
 * mirror it into the widget options with a name lookup (`WidgetDateDisplayMode.valueOf(mode.name)`)
 * rather than a hand-written translation that can fall out of step. It is a **separate enum** from
 * `calendar-ui`'s `DateDisplayMode` because `calendar-widget-data` does not depend on the UI module —
 * pulling that dependency in so two enums could be the same type would invert the layering the
 * modules are built on.
 *
 * Grid-only, like the in-app switch: the strip and the 1x1 tiles each exist to show one specific
 * calendar and have no cell to configure.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
public enum class WidgetDateDisplayMode {
    /** The Hijri day alone in each cell. */
    @JsonNames("HIJRI")
    HIJRI_ONLY,

    /** The Gregorian day alone, as the cell's main figure. */
    @JsonNames("GREGORIAN")
    GREGORIAN_ONLY,

    /** The Hijri day as the main figure with the Gregorian day beneath it. */
    @JsonNames("DUAL")
    BOTH,
}
