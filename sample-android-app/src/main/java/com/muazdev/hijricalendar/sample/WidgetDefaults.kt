package com.muazdev.hijricalendar.sample

import android.content.Context
import android.util.Log
import com.muazdev.hijricalendar.widget.glance.HijriWidgetConfig
import com.muazdev.hijricalendar.widgetdata.NumeralStyle
import com.muazdev.hijricalendar.widgetdata.WidgetLanguage
import com.muazdev.hijricalendar.widgetdata.createWidgetOptions

/**
 * The sample's widget defaults: **Urdu, with the month names in Urdu and Western figures.**
 *
 * The library's own defaults are English/English, and deliberately so — a library cannot pick a
 * language for its consumer. The sample can, and this is where it says so.
 *
 * ## Why the family mirror and not a per-widget default
 *
 * A widget with no stored options of its own already follows `HijriWidgetConfig.loadFamily`
 * (2.2.1's fallback), and the grid falls back to it too when nothing has been pinned or navigated.
 * That makes the mirror the one place where "what a freshly placed widget looks like" can be set for
 * all five kinds at once, including the three tiles and the strip, which have no options of their
 * own at all.
 *
 * ## Seeded once, and only when nothing is there
 *
 * [HijriWidgetConfig.hasFamily] rather than a flag of this app's own: a mirror the user has already
 * written through a settings screen is left exactly as it is, so this is a first-run seed rather than
 * a migration that overwrites choices. A widget with its *own* stored options is untouched either
 * way — per-instance options always win over the mirror.
 *
 * ## Why Western figures
 *
 * Urdu is the language, not the numeral system: `ARABIC_INDIC` is what the library's own "Urdu"
 * description mentions, but a Hijri date whose day and year are the digits a reader of Urdu also
 * reads in every other context is the friendlier default, and it is the one asked for here.
 * [NumeralStyle] stays a per-widget setting, so switching to Eastern figures is one radio button
 * away.
 */
internal object WidgetDefaults {

    private const val TAG = "HijriWidgetDefaults"

    /**
     * Writes the Urdu family mirror if it has never been written.
     *
     * Called from `HijriCalendarApp.onCreate`, before any widget can be placed for this process.
     */
    fun seedIfNeeded(context: Context) {
        if (HijriWidgetConfig.hasFamily(context)) {
            Log.i(TAG, "family mirror already set; leaving it alone")
            return
        }
        val options = createWidgetOptions(
            language = WidgetLanguage.URDU,
            monthNameLanguage = WidgetLanguage.URDU,
            numeralStyle = NumeralStyle.WESTERN,
        )
        HijriWidgetConfig.saveFamilyOptions(context, options, caller = "sample-seed")
        Log.i(
            TAG,
            "seeded the family mirror: language=URDU monthNames=URDU numerals=WESTERN",
        )
    }
}
