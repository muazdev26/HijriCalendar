package com.muazdev.hijricalendar.sample

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
 * figures. The Arabic-script slots are deliberately left alone: `serif`'s Arabic fallback is
 * `NotoNaskhArabic`, i.e. the same face they already render in, so naming it would be a no-op that
 * only looks like a working font.
 *
 * A host that wants a Google font here has to get it installed system-wide (OEM font settings, or
 * `/data/fonts` on a rooted/privileged device) — there is no app-side path, and this sample
 * demonstrates that rather than pretending otherwise.
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

    /**
     * Points every widget at [FAMILY] for the Latin slots.
     *
     * Called from `HijriCalendarApp.onCreate`, before any widget can be composed, because the value
     * is read at render time rather than stored: a widget placed later, or re-rendered after a
     * reboot, picks it up from this one assignment. There is nothing to persist and nothing to keep
     * in sync with a settings screen.
     */
    fun install() {
        HijriWidgetFonts.default = HijriWidgetFonts(
            gregorianTitle = FAMILY,
            dayNumber = FAMILY,
        )
        Log.i(
            TAG,
            "widget typography → $FAMILY (gregorianTitle, dayNumber); " +
                "Arabic-script slots stay on the system Naskh face",
        )
    }
}
