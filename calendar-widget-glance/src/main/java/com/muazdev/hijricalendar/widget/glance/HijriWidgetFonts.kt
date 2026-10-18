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
 * **system** font manager — and it resolves in the **host's** process, not the app's, because the
 * `CharSequence` travels to the launcher inside a RemoteViews. So the family must be one the
 * *system* knows:
 *
 * - The built-ins: `serif`, `sans-serif`, `monospace`, `cursive`, `casual`, `serif-monospace`,
 *   `sans-serif-condensed`, `sans-serif-smallcaps`. These work.
 * - Whatever the OEM preinstalled (`roboto-flex`, `google-sans`, `lato`, `rubik`, … on a Pixel) and
 *   anything the user added through the system's own font settings. These work.
 * - **Fonts bundled in the app's `res/font/` do NOT work**, and no amount of wiring changes that. A
 *   bundled font is an app-private resource; the system font manager has never seen it, so the
 *   `TypefaceSpan` falls back to the default and the widget silently renders in the system face. That
 *   includes a font shipped through Compose Multiplatform's `composeResources` — on Android it
 *   compiles to an ordinary bundled `res/font` entry, which is the case this API cannot serve. That
 *   is a statement about *naming* a bundled family here; [HijriWidgetFonts.bundledUrduFont] is the
 *   route to actually *use* one, and it does not go through this mechanism at all.
 * - **Downloadable fonts (the Google Fonts provider / `FontRequest`) do NOT work either** — this was
 *   long believed to be the workaround for the paragraph above, and it is not one. The request
 *   succeeds and the provider hands back a `Typeface`, but the family stays private to the requesting
 *   app: it never enters the system font configuration, so the launcher's `Typeface.create(name, …)`
 *   finds nothing. Measured on a Pixel 6a (Android 17) while wiring the sample: after a successful
 *   `FontsContractCompat.requestFont`, `adb shell cmd font dump amiri` still answers `not found`,
 *   both before and after `cmd font restart`. The `/data/fonts/files/` directory is the **font
 *   updater**'s, and its entries read `Font is updated from /system/fonts/…` — it can only replace a
 *   family the system config already names, so it is not a route an app can add one through. Putting
 *   a Google font on the widgets therefore takes a system-level install (OEM font settings, or
 *   `/data/fonts` on a privileged device), not app code.
 *
 * Glance exposes no resource-id font API to route around any of this.
 *
 * There is no error and no log when a name does not resolve; it is a silent fallback. If a widget
 * looks unchanged after setting this, that is the first thing to check — and
 * `adb shell cmd font dump <family>` answers exactly the question the launcher's `TypefaceSpan` asks.
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
    /**
     * Draw Arabic-script text with the library's **bundled** Noto Nastaliq Urdu face, instead of the
     * family the host named above.
     *
     * This is the one way to get a font the *system* does not have onto a widget — see the "Why a
     * family name and not a font resource" section above, which is the long version of why naming a
     * bundled family never works. The mechanism is different in kind: a slot whose text carries
     * Arabic **letters** is rasterised into a `Bitmap` with `Typeface` loaded from the library's own
     * `res/font`, and the widget draws that bitmap through Glance's `Image`. The drawing happens in
     * *this* app's process, where the bundled font is visible, and only the finished bitmap crosses
     * into the launcher — so the system font map is never consulted.
     *
     * ## What it applies to, and what it deliberately does not
     *
     * A slot is drawn with the bundled face when its text contains Arabic-script **letters**
     * (see `hasArabicLetters`). That excludes a slot holding only digits, whatever numeral style is
     * chosen: figures keep the system face, which is the right face for them and keeps the grid's
     * cell geometry — whose type is derived from width and is not fitted — exactly as it was.
     * Latin text (the Gregorian month, an English month name) is untouched for the same reason.
     *
     * ## Why this is not the `monthTitle`/`weekday` mechanism
     *
     * Those fields name a family for *one* slot each. This one is a switch rather than a name
     * because there is nothing to name: the library ships exactly one face, the family it was
     * designed around, and a host either wants it or does not. `false` — the default — leaves every
     * widget exactly as it rendered before this field existed.
     *
     * ## What the static picker previews show
     *
     * Nothing: the Android 12-14 `previewLayout` mirrors are XML and cannot rasterise. Only the live
     * widgets and the Android 15+ `providePreview` trees, which compose for real, honour this.
     */
    val bundledUrduFont: Boolean = false,
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

/**
 * The line-box ratio a widget's stack is measured with, given which face will draw its script lines.
 *
 * [NASTALIQ_LINE_RATIO] when [HijriWidgetFonts.bundledUrduFont] is on, [TEXT_LINE_RATIO] otherwise —
 * the two numbers are measurements of the two faces (see each), and a stack sized against the wrong
 * one is a stack that clips.
 *
 * It is one value for the whole stack rather than one per line, and that is deliberately the
 * conservative direction: a stack can hold an Urdu weekday beside an English month name, and sizing
 * the English line at Nastaliq's ratio over-reserves its height. Over-reserving costs a little empty
 * space; under-reserving clips a line, which is the bug this whole file's ratios exist to prevent.
 */
internal fun HijriWidgetFonts.scriptLineRatio(): Float =
    if (bundledUrduFont) NASTALIQ_LINE_RATIO else TEXT_LINE_RATIO
