package com.muazdev.hijricalendar.widget.glance

import com.muazdev.hijricalendar.widgetdata.WidgetLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WG-16: `resolveLayoutRtl` — the widget's reading direction as a function of its own language and
 * the device's.
 *
 * This is the whole truth table rather than a couple of rows, because the two mixed rows are the
 * ones that carry the logic and they are the ones an "obvious" implementation gets wrong. A widget
 * on an Urdu phone and an English widget on an Urdu phone both need *some* correction; only one of
 * them needs the projection pre-reversed.
 *
 * The background is the platform behaviour the XOR exists to cancel: Glance rows are plain
 * horizontal `LinearLayout`s, so on an RTL-locale device the platform already mirrors child order.
 * Reversing the projection on top of that would undo the mirroring, so the flag means "pre-reverse
 * to cancel a mirroring that is not otherwise happening".
 */
class LayoutDirectionTest {

    /**
     * An English widget on an English phone. Nothing to correct — the platform lays out LTR and the
     * widget wants LTR.
     */
    @Test
    fun englishOnAnLtrDeviceNeedsNoReversal() {
        assertFalse(resolveLayoutRtl(deviceRtl = false, language = WidgetLanguage.ENGLISH))
    }

    /**
     * The case the widget exists for: an Urdu widget on a phone with no Urdu locale. The platform
     * will lay the `LinearLayout` rows out LTR, so the projection reverses itself to get RTL.
     */
    @Test
    fun urduOnAnLtrDeviceReverses() {
        assertTrue(resolveLayoutRtl(deviceRtl = false, language = WidgetLanguage.URDU))
    }

    /**
     * The double-mirror row. The device is RTL, so the platform is *already* mirroring the rows;
     * reversing as well would leave the widget LTR. `language.isRtl` alone — the obvious
     * implementation — returns `true` here and breaks the common case.
     */
    @Test
    fun urduOnAnRtlDeviceDoesNotReverse() {
        assertFalse(resolveLayoutRtl(deviceRtl = true, language = WidgetLanguage.URDU))
    }

    /**
     * The other mixed row: English words on an Urdu phone. The platform will mirror the rows, so
     * the projection reverses to get them back to LTR.
     */
    @Test
    fun englishOnAnRtlDeviceReverses() {
        assertTrue(resolveLayoutRtl(deviceRtl = true, language = WidgetLanguage.ENGLISH))
    }

    @Test
    fun theAnswerIsTheXorOfTheTwoInputs() {
        // Stated as a property over the full cross product rather than as four cases above, so a
        // change that keeps the four named rows but breaks the rule would still be caught.
        for (deviceRtl in listOf(false, true)) {
            for (language in WidgetLanguage.entries) {
                assertEquals(
                    "deviceRtl=$deviceRtl language=$language",
                    language.isRtl != deviceRtl,
                    resolveLayoutRtl(deviceRtl = deviceRtl, language = language),
                )
            }
        }
    }

    @Test
    fun flippingTheDeviceFlipsTheAnswerForEveryLanguage() {
        // Consequence of the XOR, and the reason it is an XOR: reversing the device's direction
        // reverses the correction, for every language, with no exceptions to enumerate.
        for (language in WidgetLanguage.entries) {
            assertEquals(
                "language=$language",
                !resolveLayoutRtl(deviceRtl = false, language = language),
                resolveLayoutRtl(deviceRtl = true, language = language),
            )
        }
    }
}
