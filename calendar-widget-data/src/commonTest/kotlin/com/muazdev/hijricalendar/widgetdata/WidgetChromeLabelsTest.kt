package com.muazdev.hijricalendar.widgetdata

import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * WG-12: the widget's chrome labels are a function of the *widget's* `WidgetOptions.language`, never
 * of the device locale.
 *
 * These live in `calendar-widget-data` rather than in the Android module because iOS needs them too:
 * its chevrons carry no explicit label, so VoiceOver derives one from the SF Symbol and the platform
 * localizes it — which is better than Android's hardcoded English literal but still answers for the
 * *device*. Being here also means these assertions run on Kotlin/Native in CI
 * (`iosSimulatorArm64Test`), on the platform the Swift side will read them from, and not only in an
 * Android unit test.
 *
 * What this file does **not** do is assert that either renderer calls these. That is structural —
 * the Android render path contains no `getString` for chrome, and the iOS path would use these
 * accessors — and no unit test here can see a `GlanceModifier.semantics` block or a SwiftUI view.
 * Asserting "the constants differ per language" is the part that is cheap to break; pretending the
 * file covers the wiring would make the suite look stronger than it is.
 */
class WidgetChromeLabelsTest {

    private fun labels(language: WidgetLanguage): List<String> = with(WidgetLocalization.ChromeLabels) {
        listOf(nextMonth(language), previousMonth(language), goToCurrentMonth(language), monthUnavailable(language))
    }

    @Test
    fun everyLanguageProducesANonBlankLabelForEveryChromeString() {
        WidgetLanguage.entries.forEach { language ->
            labels(language).forEach { label ->
                assertTrue(label.isNotBlank(), "blank chrome label for $language")
            }
        }
    }

    @Test
    fun theTwoLanguagesDoNotShareLabels() {
        assertNotEquals(
            labels(WidgetLanguage.URDU),
            labels(WidgetLanguage.ENGLISH),
            "chrome labels did not vary by language",
        )
    }

    @Test
    fun labelsAreDistinctWithinALanguage() {
        // The month title announces its *action* ("Go to current month") while the arrows announce
        // theirs, so the three header controls stay distinguishable when a screen-reader user swipes
        // between them. If two labels collapsed, navigation would become guesswork.
        WidgetLanguage.entries.forEach { language ->
            val labels = labels(language)
            assertTrue(
                labels.toSet().size == labels.size,
                "expected ${labels.size} distinct labels for $language, got $labels",
            )
        }
    }

    @Test
    fun urduLabelsUseArabicScript() {
        // Guards the copy-paste that swaps a translated string for the English one.
        labels(WidgetLanguage.URDU).forEach { label ->
            assertTrue(
                label.any { it.code in 0x0600..0x06FF },
                "expected Arabic script in \"$label\"",
            )
        }
    }

    @Test
    fun englishLabelsAreAscii() {
        labels(WidgetLanguage.ENGLISH).forEach { label ->
            assertTrue(
                label.none { it.code > 0x7F },
                "unexpected non-ASCII in \"$label\"",
            )
        }
    }
}
