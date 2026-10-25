package com.muazdev.hijricalendar.widgetdata

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * Which date a **grid** widget paints as the top, bold figure when it paints both.
 *
 * Mirrors the in-app calendar's `PrimaryDate`, exactly as [WidgetDateDisplayMode] mirrors
 * `DateDisplayMode` — so a host app that already stores one can mirror it into the options with a
 * name lookup (`WidgetDatePrimary.valueOf(primary.name)`) rather than a hand-written translation that
 * can fall out of step. It is a separate enum because `calendar-widget-data` must not depend on the UI
 * module.
 *
 * Only meaningful under [WidgetDateDisplayMode.BOTH]. A single-figure mode already says which date is
 * primary: [WidgetDateDisplayMode.HIJRI_ONLY] draws the Hijri day and
 * [WidgetDateDisplayMode.GREGORIAN_ONLY] the Gregorian one, so a renderer must ignore this there
 * rather than shrinking the primary figure away.
 *
 * Stored **by name**, like every enum in this module: reordering these entries reinterprets nothing.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
public enum class WidgetDatePrimary {
    /** The Hijri figure on top, bold — the library's historical two-line order. */
    @JsonNames("HIJRI_FIRST", "HIJRI_ON_TOP", "ISLAMIC")
    HIJRI,

    /** The Gregorian figure on top, bold, with the Hijri figure beneath it. */
    @JsonNames("GREGORIAN_FIRST", "GREGORIAN_ON_TOP", "GREGORIAN_TOP")
    GREGORIAN,
}
