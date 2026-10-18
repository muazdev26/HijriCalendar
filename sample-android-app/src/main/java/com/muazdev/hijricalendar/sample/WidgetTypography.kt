package com.muazdev.hijricalendar.sample

import android.content.Context
import android.util.Log
import com.muazdev.hijricalendar.widget.glance.HijriWidgetFonts

/**
 * Demonstrates the widget font hook, end to end.
 *
 * Feedback round 2, point 8: `HijriWidgetFonts` shipped with no caller in the sample, so "the fonts
 * do nothing" was the only observable behaviour. This is the missing caller.
 *
 * ## The family must already be known to the *system*, and that is not a detail
 *
 * `HijriWidgetFonts` stores a family **name**, and Glance turns it into a
 * `TypefaceSpan(familyName)` inside a RemoteViews `CharSequence`. That span is resolved by the
 * `TextView` that renders it — which lives in the **launcher's** process, not ours. So the name is
 * looked up in the *system* font map, exactly like `adb shell cmd font dump <family>` looks it up.
 *
 * Two consequences, both measured on a Pixel 6a (Android 17) while doing this work:
 *
 * - **A Google Fonts `FontRequest` does not help.** Asking the Play services font provider for a
 *   family (the `res/font` + certs route, which this sample used first) succeeds — the callback
 *   reports a delivered `Typeface` — but the family is cached for the *requesting app* and never
 *   enters the system font config: `cmd font dump amiri` still answers `not found` after a
 *   successful request *and* after `cmd font restart`. `/data/fonts/files/` is the **font updater**
 *   directory, which can only replace a family the system config already names (its entry there
 *   reads `Font is updated from /system/fonts/NotoColorEmoji.ttf`) — it is not a place a third-party
 *   app can add one to. The span therefore resolves to nothing and the widget silently keeps the
 *   default face. `docs/FEEDBACK-TODOS-2.md` records the experiment.
 * - **A bundled `res/font` cannot help either**, for the same reason stated in
 *   [HijriWidgetFonts]' KDoc: it is an app-private resource the system has never seen.
 *
 * What does work is a family the system already has: the AOSP built-ins (`serif`, `sans-serif`,
 * `monospace`, `cursive`, `casual`, `serif-monospace`, `sans-serif-condensed`, `sans-serif-smallcaps`)
 * and whatever the OEM preinstalled or the user added through the system font settings.
 *
 * ## What this sample sets
 *
 * [FAMILY] is `serif`, chosen because it is on every Android image, has a real bold face
 * (`NotoSerif-Bold.ttf`, so the bold day figures stay bold), and is unmistakably different from the
 * default sans face. It is applied to the two Latin slots — the Gregorian month title and the day
 * figures. The Arabic-script slots are deliberately left alone *by this hook*: `serif`'s Arabic
 * fallback is `NotoNaskhArabic`, i.e. the same face they already render in, so naming it would be a
 * no-op that only looks like a working font.
 *
 * A host that wants a Google font here has to get it installed system-wide (OEM font settings, or
 * `/data/fonts` on a rooted/privileged device) — there is no app-side path, and this sample
 * demonstrates that rather than pretending otherwise.
 *
 * ## The bundled face is the *other* mechanism, and the sample uses both
 *
 * Everything above is about naming a family the system already has. A font the library *ships* can
 * never be reached that way, which is why [HijriWidgetFonts.bundledUrduFont] exists: the slot is
 * rasterised in this app's process and the launcher is handed a bitmap.
 *
 * So this sample sets both, and they cover different slots:
 *
 * - [FAMILY] → the **Latin** slots, through the system font map.
 * - the bundled Noto Nastaliq Urdu → the **Arabic-script** slots, through the rasteriser.
 *
 * The second one is behind a switch ([isBundledUrduEnabled]/[setBundledUrduEnabled]) because that is
 * what the feature asks of a host: it is on by default here so the sample shows the face it was built
 * for, and off is a single assignment away.
 */
internal object WidgetTypography {

    /**
     * A family that is guaranteed to resolve, and to look different from the default face.
     *
     * Not a Google Fonts family on purpose: see the class KDoc — a downloaded family is not in the
     * system font map, so it would render as "no font set" and demonstrate nothing.
     */
    const val FAMILY: String = "serif"

    private const val TAG = "HijriWidgetFont"

    /** Sample-local preferences, not the library's: the font choice is a host decision, not an option. */
    private const val PREFS = "hijri_widget_sample"

    private const val KEY_BUNDLED_URDU_FONT = "bundled_urdu_font"

    /**
     * Whether the bundled Noto Nastaliq Urdu face draws the widgets' Arabic-script slots.
     *
     * `true` by default, and the sample's whole point: the library ships the face so a widget can be
     * set in the script Urdu is actually written in, and a sample that left it off would demonstrate
     * nothing about the feature. It is a *plain preference* rather than an option on the shared
     * schema on purpose — see [HijriWidgetFonts.bundledUrduFont]'s KDoc: it is Android-only (the wire
     * format is shared with the iOS widget, which cannot use an Android font resource), and it is a
     * host-level choice rather than a per-widget one, exactly like [FAMILY].
     */
    fun isBundledUrduEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_BUNDLED_URDU_FONT, true)

    /**
     * Flips the bundled face and re-installs it immediately.
     *
     * The value is read at *render* time rather than stored in a widget, so this one assignment is
     * enough for every already-placed widget to pick it up on its next recomposition — the caller
     * only has to ask for one (the settings screen does, through `HijriWidgetRefresher`).
     */
    fun setBundledUrduEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BUNDLED_URDU_FONT, enabled)
            .apply()
        install(context)
    }

    /**
     * Points every widget at [FAMILY] for the Latin slots, and at the library's bundled Nastaliq for
     * the Arabic-script ones when the preference says so.
     *
     * Called from `HijriCalendarApp.onCreate`, before any widget can be composed, because the value
     * is read at render time rather than stored: a widget placed later, or re-rendered after a
     * reboot, picks it up from this one assignment. The only thing that has to be persisted is the
     * preference itself — see [isBundledUrduEnabled].
     */
    fun install(context: Context) {
        val bundledUrdu = isBundledUrduEnabled(context)
        HijriWidgetFonts.default = HijriWidgetFonts(
            gregorianTitle = FAMILY,
            dayNumber = FAMILY,
            bundledUrduFont = bundledUrdu,
        )
        Log.i(
            TAG,
            "widget typography → $FAMILY (gregorianTitle, dayNumber); bundled Urdu Nastaliq = " +
                "$bundledUrdu (Arabic-script slots only, figures keep the system face)",
        )
    }
}
