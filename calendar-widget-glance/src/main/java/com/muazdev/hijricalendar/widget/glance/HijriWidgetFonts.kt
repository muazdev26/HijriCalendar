package com.muazdev.hijricalendar.widget.glance

import androidx.glance.text.FontFamily

/**
 * Per-field font families for every text slot the Android widgets render, as **family names**.
 *
 * Set [default] once from the host app — typically in `Application.onCreate`, before any widget can
 * be placed — and every widget picks it up. Each field is independent so a host can, for example,
 * give Urdu weekday names a different face from the digits without restyling the whole widget:
 *
 * ```kotlin
 * HijriWidgetFonts.default = HijriWidgetFonts(
 *     weekday = "Noto Nastaliq Urdu",
 *     monthTitle = "Noto Nastaliq Urdu",
 *     dayNumber = null,          // keep the default face for the digits
 * )
 * ```
 *
 * ## Why a family *name* and not a font resource — read this before wiring it up
 *
 * Glance's `FontFamily` has exactly one constructor, `FontFamily(family: String)`, and it applies it
 * as `TypefaceSpan(family.family)` (Glance's `TextTranslator`). A `TypefaceSpan` resolves through the
 * **system** font manager. So the family must be one the *system* knows:
 *
 * - **Downloadable fonts** (the Google Fonts provider / `FontRequest`) — cached by family name by the
 *   system once fetched. **These work.**
 * - **Fonts the user installed system-wide.** These work.
 * - The built-ins: `sans-serif`, `serif`, `monospace`, `cursive`.
 *
 * **A font bundled in the app's `res/font/` will NOT work, and no amount of wiring changes that.**
 * A bundled font is an app-private resource; the system font manager has never seen it, so the
 * `TypefaceSpan` falls back to the default and the widget silently renders in the system face. That
 * includes a font shipped through Compose Multiplatform's `composeResources` — on Android it compiles
 * to an ordinary bundled `res/font` entry, which is the case this API cannot serve. Glance exposes no
 * resource-id font API to route around it, so a host in that position must move the font to a
 * downloadable font for this to take effect.
 *
 * There is no error and no log when a name does not resolve; it is a silent fallback. If a widget
 * looks unchanged after setting this, that is the first thing to check.
 *
 * ## Why this is not a field on `WidgetOptions`
 *
 * `WidgetOptions` lives in `calendar-widget-data`, which is `commonMain` multiplatform and has no
 * Android resource or font concept, and it is the serialized wire format shared with the iOS widget.
 * A font family is an Android-only, render-time concern that iOS cannot represent, so it is passed at
 * render time here rather than stored per widget.
 *
 * ## What the static picker previews show
 *
 * The Android 12-14 `previewLayout` mirrors are hand-maintained XML and cannot read a runtime value,
 * so they keep their declared `android:fontFamily`. Only the live widgets and the Android 15+
 * `providePreview` trees — which compose for real — honour these settings.
 *
 * @property monthTitle The Hijri month name and year: the grid header's Hijri half, the compact
 *   today's-card month line, the bottom line of both 1x1 single-date tiles, and the dual tile's
 *   header band.
 * @property gregorianTitle The Gregorian month and year: the grid header's Gregorian half, the
 *   compact card's Gregorian line, the Gregorian tile's bottom line, and the dual tile's sub-row.
 * @property weekday Weekday names: the grid's header row, the top line of both 1x1 single-date
 *   tiles, and the dual tile's last line.
 * @property dayNumber Day figures: both lines of every grid cell, the 1x1 tiles' big day number, and
 *   the Today strip's figures.
 */
public data class HijriWidgetFonts(
    val monthTitle: String? = null,
    val gregorianTitle: String? = null,
    val weekday: String? = null,
    val dayNumber: String? = null,
) {
    public companion object {
        /**
         * The fonts every widget renders with, until a host replaces it.
         *
         * All-null by default, which leaves every slot on Glance's own default face — the behaviour
         * this library had before the option existed.
         *
         * `@Volatile` and a single field rather than a `State`: this is read from a Glance
         * composition on a background session worker, never recomposed on, and a host sets it once at
         * startup rather than in response to anything. A snapshot-backed value would add a
         * recomposition contract this API has no use for.
         */
        @Volatile
        public var default: HijriWidgetFonts = HijriWidgetFonts()
    }
}

/**
 * The Glance font for [this] family name, or `null` to leave the slot on Glance's default face.
 *
 * `null` means "unset" rather than "the default family", so a host that sets only [HijriWidgetFonts.weekday]
 * leaves the other three slots exactly as they were.
 */
internal fun String?.toGlanceFontFamily(): FontFamily? = this?.let { familyName -> FontFamily(familyName) }

/**
 * The family for whichever calendar's month title this slot shows.
 *
 * The 1x1 tiles and the grid's header render through shared composables, so "which calendar" is a
 * call-site fact rather than something the text can say.
 */
internal fun HijriWidgetFonts.forMonthTitle(gregorian: Boolean): String? =
    if (gregorian) gregorianTitle else monthTitle
