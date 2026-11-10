package com.muazdev.hijricalendar.shared

import com.muazdev.hijricalendar.core.HijriEvents
import com.muazdev.hijricalendar.core.PakistanEvents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins that every line the event banner can print is in Urdu.
 *
 * The banner is not one lambda: [com.muazdev.hijricalendar.ui.HijriCalendarLabels] picks
 * `monthEventBanner` for a month with only Hijri observances and `monthEventBannerWithPakistan` for a
 * month holding a Pakistani national day, and `pakistanEventName` is a third slot because `eventName`
 * takes a `HijriEvent` and cannot answer for 14 August. Overriding the first and forgetting the
 * others is silent — the build is clean, the widget tests are clean, and a Pakistan-mode month simply
 * prints "Iqbal Day" in Latin script across an otherwise Urdu screen. That is exactly what happened:
 * the bundle predates the two Pakistan slots, so every national day fell back to the library's
 * English defaults, and only running the app showed it.
 *
 * A `nameEn` is the tell, so the assertions are on the script rather than on a copied string table:
 * a name that came from the wrong slot has Latin letters in it.
 */
class UrduCalendarLabelsTest {

    /** Any letter that can only have come from a Latin rendering. */
    private fun String.hasLatinLetters(): Boolean = any { it in 'a'..'z' || it in 'A'..'Z' }

    @Test
    fun everyNationalDayIsNamedInUrdu() {
        val events = PakistanEvents.all
        assertTrue(events.isNotEmpty(), "the national-day table must not be empty")

        events.forEach { event ->
            val name = UrduCalendarLabels.pakistanEventName(event)
            assertEquals(event.nameUr, name, "$event.key must keep its own Urdu name")
            assertFalse(
                name.hasLatinLetters(),
                event.key + " printed '" + name + "' — that is the library's nameEn default, " +
                    "which means `pakistanEventName` is not overridden",
            )
        }
    }

    @Test
    fun everyHijriObservanceIsNamedInUrdu() {
        HijriEvents.all.forEach { event ->
            assertFalse(
                UrduCalendarLabels.eventName(event).hasLatinLetters(),
                "${event.key} is not Urdu",
            )
        }
    }

    /**
     * The mixed month, which is the case the Pakistan slot exists for: one Gregorian-fixed national
     * day and one Hijri observance in the same banner, both in Urdu, joined with the Urdu comma.
     */
    @Test
    fun aMonthHoldingBothKindsNamesBothInUrdu() {
        val hijri = HijriEvents.all.first { it.key == "ashura" }
        val national = PakistanEvents.all.first { it.key == "iqbal_day" }

        val banner = UrduCalendarLabels.monthEventBannerWithPakistan(listOf(hijri, national))

        assertTrue(banner.contains(hijri.nameUr), "the observance is missing: $banner")
        assertTrue(banner.contains(national.nameUr), "the national day is missing: $banner")
        assertFalse(
            banner.hasLatinLetters(),
            "the banner fell back to the library default: '$banner'",
        )
        assertFalse(
            banner.contains(" · "),
            "the Urdu bundle joins with '، ', not the English middle dot: '$banner'",
        )
    }

    /** A month with no national day still goes through `monthEventBanner`, and still reads Urdu. */
    @Test
    fun theHijriOnlyBannerStaysUrdu() {
        val banner = UrduCalendarLabels.monthEventBanner(HijriEvents.all)

        assertFalse(banner.hasLatinLetters(), "the Hijri-only banner is not Urdu: '$banner'")
        assertFalse(banner.contains(" · "), "the Urdu bundle joins with '، ': '$banner'")
    }

    @Test
    fun theEmptyMonthStillSaysSomethingInUrdu() {
        assertFalse(
            UrduCalendarLabels.noEventBanner.hasLatinLetters(),
            "the always-present bar must not speak English in an Urdu screen",
        )
    }
}
