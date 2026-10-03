package com.muazdev.hijricalendar.widget.glance

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.datastore.preferences.core.mutablePreferencesOf
import com.muazdev.hijricalendar.widgetdata.HijriYearMonth
import com.muazdev.hijricalendar.widgetdata.NumeralStyle
import com.muazdev.hijricalendar.widgetdata.WeekStart
import com.muazdev.hijricalendar.widgetdata.WidgetLanguage
import com.muazdev.hijricalendar.widgetdata.WidgetOptions
import com.muazdev.hijricalendar.widgetdata.WidgetOptionsJson
import com.muazdev.hijricalendar.widgetdata.WidgetSource
import com.muazdev.hijricalendar.widgetdata.monthLengthKey
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Guards the public [HijriWidgetConfig] surface for host-app settings screens: the Bundle-safe
 * [HijriWidgetConfig.widgetOptionsSaver] must round-trip every option, and stored configs written
 * before an option existed must keep their old meaning rather than flipping scripts or crashing.
 *
 * The round-trip tests **drive `save` and hand its output to `restore`.** The previous version
 * re-implemented `save` as a literal list, which asserted that the two halves of one function agreed
 * with each other rather than that either was correct — a `save` that silently dropped a field, or
 * moved one, kept the suite green (WG-06).
 */
class HijriWidgetConfigTest {

    /**
     * The scope `Saver.save` requires. It exists to hand a value to a registered `canBeSaved`
     * provider; the saver under test has no providers, so nothing is consulted.
     */
    private val scope = SaverScope { true }

    /**
     * Calls `Saver.save`, which is a *member extension* on `SaverScope` — so it needs both the
     * saver and a scope in scope. This is exactly how `rememberSaveable` invokes it.
     */
    private fun savedValue(saver: Saver<WidgetOptions, Any>, options: WidgetOptions): Any? =
        with(saver) { with(scope) { save(options) } }

    private fun Saver<WidgetOptions, Any>.roundTrip(options: WidgetOptions): WidgetOptions {
        val saved = requireNotNull(savedValue(this, options)) { "saver produced nothing" }
        return requireNotNull(restore(saved)) { "saver could not read back what it wrote" }
    }

    @Test
    fun widgetOptionsSaver_roundTripsAllFields() {
        val options = WidgetOptions(
            adjustmentDays = -1,
            numeralStyle = NumeralStyle.ARABIC_INDIC,
            weekStart = WeekStart.DEFAULT,
            pinnedYear = 1448,
            pinnedMonth = 3,
            source = WidgetSource.PAKISTAN,
            language = WidgetLanguage.URDU,
            monthNameLanguage = WidgetLanguage.ENGLISH,
            monthLengthOverrides = mapOf(monthLengthKey(1448, 2) to 30),
        )
        assertEquals(options, HijriWidgetConfig.widgetOptionsSaver().roundTrip(options))
    }

    @Test
    fun widgetOptionsSaver_roundTripsEveryOptionItDeclares() {
        // Belt and braces against a field being added to `WidgetOptions` and forgotten by the
        // saver: walk the declared properties so a new one fails here rather than silently not
        // surviving process death.
        val options = WidgetOptions(
            adjustmentDays = 3,
            numeralStyle = NumeralStyle.WESTERN,
            weekStart = WeekStart.TUESDAY,
            pinnedYear = 1447,
            pinnedMonth = 12,
            source = WidgetSource.CALCULATION,
            language = WidgetLanguage.ENGLISH,
            monthNameLanguage = WidgetLanguage.URDU,
            monthLengthOverrides = mapOf(monthLengthKey(1447, 11) to 29),
            showAdjacentDays = true,
        )
        val restored = HijriWidgetConfig.widgetOptionsSaver().roundTrip(options)

        val declared = WidgetOptions::class.java.declaredFields
            .map { it.name }
            // `Companion` and `DEFAULTS` are static, not stored per instance.
            .filterNot { it.startsWith("$") || it == "Companion" || it == "DEFAULTS" }
            .toSet()
        assertEquals(
            emptySet(),
            declared - declaredOptionsInUse(options, restored),
            "every stored field must be compared; a new WidgetOptions field is not covered yet",
        )
    }

    /** The field names whose values provably survive, derived from the two values themselves. */
    private fun declaredOptionsInUse(
        options: WidgetOptions,
        restored: WidgetOptions,
    ): Set<String> {
        val surviving = buildSet {
            if (options.adjustmentDays == restored.adjustmentDays) add("adjustmentDays")
            if (options.numeralStyle == restored.numeralStyle) add("numeralStyle")
            if (options.weekStart == restored.weekStart) add("weekStart")
            if (options.pinnedYear == restored.pinnedYear) add("pinnedYear")
            if (options.pinnedMonth == restored.pinnedMonth) add("pinnedMonth")
            if (options.source == restored.source) add("source")
            if (options.language == restored.language) add("language")
            if (options.monthNameLanguage == restored.monthNameLanguage) add("monthNameLanguage")
            if (options.monthLengthOverrides == restored.monthLengthOverrides) add("monthLengthOverrides")
            if (options.showAdjacentDays == restored.showAdjacentDays) add("showAdjacentDays")
        }
        return surviving
    }

    @Test
    fun widgetOptionsSaver_savedValueIsAString() {
        // A single String, not a positional list. This is the property that makes the saver tolerant
        // of another app version's Bundle (WG-06): nothing in it is positional, so nothing in it can
        // be indexed out of range.
        val saver = HijriWidgetConfig.widgetOptionsSaver()
        val saved = requireNotNull(savedValue(saver, WidgetOptions(adjustmentDays = 1)))
        assertTrue(saved is String, "a Bundle-safe saver must save a String, got ${saved::class}")
    }

    @Test
    fun widgetOptionsSaver_unreadableValuesYieldNullRatherThanThrowing() {
        // `restore` runs inside `rememberSaveable` during composition; anything it throws takes down
        // the settings screen. Every shape that used to be able to throw must now degrade.
        val saver = HijriWidgetConfig.widgetOptionsSaver()
        assertNull(saver.restore("not json"))
        assertNull(saver.restore(42 as Any), "a value written by some other saver entirely")

        // An unknown enum *name* is no longer unreadable: `coerceInputValues` contains it to that one
        // field (WD-06), so the other options survive. Before, this returned null.
        val coerced = saver.restore("""{"adjustmentDays":-2,"language":"PERSIAN"}""")
        assertEquals(-2, assertNotNull(coerced).adjustmentDays)

        // An empty object is *valid* JSON for this schema — every field has a default — so it
        // restores to the field defaults rather than to null. Note those are deliberately not
        // `WidgetOptions.DEFAULTS`: a stored value that omits a field means "never chosen", which
        // is Western digits, not the fresh-widget Urdu default. Either answer is survivable here;
        // a throw is not.
        assertEquals(WidgetOptions(), saver.restore("""{}"""))
    }

    @Test
    fun widgetOptionsSaver_readsAStoredPinAndFallsBackOnAHalfPin() {
        // The pin is stored as two independent nullable fields, so a stored value can carry one
        // without the other (WD-03). It must come back meaning "not pinned", not as a half-pin.
        val saver = HijriWidgetConfig.widgetOptionsSaver()
        val pinned = requireNotNull(
            saver.restore(WidgetOptionsJson.encode(WidgetOptions(pinnedYear = 1448, pinnedMonth = 3))),
        )
        assertEquals(HijriYearMonth(1448, 3), pinned.pinned)

        val half = requireNotNull(saver.restore("""{"pinnedYear":1448}"""))
        assertNull(half.pinned, "a half-stored pin must not survive the round trip as a half-pin")
    }

    @Test
    fun widgetOptionsSaver_defaultsAnOptionStoredBeforeItExisted() {
        // Options written before `monthNameLanguage` existed decode to `null` for it, which the
        // widget resolves to its `language`. The screen must show what the widget will render, so
        // the resolved value is what matters here.
        val stored = requireNotNull(
            HijriWidgetConfig.widgetOptionsSaver().restore("""{"language":"ENGLISH"}"""),
        )
        assertEquals(WidgetLanguage.ENGLISH, stored.language)
        assertEquals(WidgetLanguage.ENGLISH, stored.effectiveMonthNameLanguage)
    }

    @Test
    fun decodeOptionsJson_readsTheSharedNamedFormat() {
        // Written by `WidgetOptionsJson`, i.e. by every other native renderer too.
        val options = WidgetOptions(
            adjustmentDays = -2,
            numeralStyle = NumeralStyle.WESTERN,
            weekStart = WeekStart.FRIDAY,
            pinnedYear = 1448,
            pinnedMonth = 3,
            source = WidgetSource.PAKISTAN,
            language = WidgetLanguage.ENGLISH,
            monthNameLanguage = WidgetLanguage.URDU,
        )
        val encoded = WidgetOptionsJson.encode(options)
        assertEquals(options, HijriWidgetConfig.decodeOptionsJson(encoded))
    }

    @Test
    fun decodeOptionsJson_doesNotReinterpretANewerBlobAsLegacy() {
        // WG-09's worked example. The discriminator is the *shape*, not "the shared decode failed":
        // a blob whose enums are strings is in the current format even when this library cannot read
        // one of its values, and the legacy reader must never claim it.
        val modern = """{"adjustmentDays":-2,"numeralStyle":"ARABIC_INDIC","source":"PAKISTAN",
            "language":"PERSIAN","weekStart":"MONDAY"}"""
        val options = requireNotNull(HijriWidgetConfig.decodeOptionsJson(modern))
        assertEquals(-2, options.adjustmentDays)
        assertEquals(WidgetSource.PAKISTAN, options.source)
        assertEquals(WeekStart.MONDAY, options.effectiveWeekStart)
        // Only the field that is genuinely unreadable falls back — see WD-06.
        assertEquals(WidgetLanguage.URDU, options.language)
    }

    @Test
    fun decodeOptionsJson_aNamedBlobNeverReachesTheLegacyReader() {
        // The pure form of the fence: every enum written by name means "current format", so the
        // legacy reader is not even consulted and cannot manufacture defaults out of it.
        val cases = mapOf(
            """{"language":"ENGLISH"}""" to WidgetOptions(language = WidgetLanguage.ENGLISH),
            """{"source":"PAKISTAN"}""" to WidgetOptions(source = WidgetSource.PAKISTAN),
            """{"numeralStyle":"ARABIC_INDIC"}""" to
                WidgetOptions(numeralStyle = NumeralStyle.ARABIC_INDIC),
            """{"weekStart":"MONDAY"}""" to WidgetOptions(weekStart = WeekStart.MONDAY),
        )
        for ((blob, expected) in cases) {
            assertEquals(
                expected,
                requireNotNull(HijriWidgetConfig.decodeOptionsJson(blob)) { blob },
                "for blob: $blob",
            )
        }
    }

    @Test
    fun decodeOptionsJson_keepsTheOtherFieldsWhenOneEnumIsUnreadable() {
        // WD-06, reaching this module: `coerceInputValues` contains the damage to the one field, so
        // the blob decodes and the -2 adjustment survives. Under the old codec this returned null and
        // then, via the unfenced fallback, all-defaults.
        val options = requireNotNull(
            HijriWidgetConfig.decodeOptionsJson(
                """{"adjustmentDays":-2,"language":"PERSIAN","source":"PAKISTAN"}""",
            ),
        )
        assertEquals(-2, options.adjustmentDays)
        assertEquals(WidgetSource.PAKISTAN, options.source)
    }

    @Test
    fun decodeOptionsJson_stillReadsThePreSharedOrdinalFormat() {
        // Options stored by an earlier version encoded the same fields as enum *ordinals*. Dropping
        // this reader would silently reset every already-placed widget to the defaults on upgrade,
        // so it is pinned here rather than left to a manual test.
        val legacy = """
            {"adjustmentDays":1,"numeralStyle":0,"firstDayOfWeekIndex":6,"pinnedYear":1448,
             "pinnedMonth":3,"source":1,"language":1,"monthNameLanguage":0}
        """.trimIndent()
        val options = requireNotNull(HijriWidgetConfig.decodeOptionsJson(legacy))
        assertEquals(1, options.adjustmentDays)
        assertEquals(NumeralStyle.WESTERN, options.numeralStyle)
        assertEquals(WeekStart.FRIDAY, options.effectiveWeekStart)
        assertEquals(1448, options.pinnedYear)
        assertEquals(3, options.pinnedMonth)
        assertEquals(WidgetSource.PAKISTAN, options.source)
        assertEquals(WidgetLanguage.ENGLISH, options.language)
        assertEquals(WidgetLanguage.URDU, options.effectiveMonthNameLanguage)
    }

    @Test
    fun decodeOptionsJson_legacyReaderSeedsNumeralsFromTheLanguage() {
        // A widget that predates the numeral-style option stored no value for it, so the shared
        // decoder must fall back to the language's default rather than the field default.
        // `{"language":0}` is an *integer* enum, which is exactly what the fence looks for (WG-09).
        val legacy = """{"language":0}"""
        val urdu = requireNotNull(HijriWidgetConfig.decodeOptionsJson(legacy))
        assertEquals(NumeralStyle.ARABIC_INDIC, urdu.numeralStyle)

        val english = requireNotNull(HijriWidgetConfig.decodeOptionsJson("""{"language":1}"""))
        assertEquals(NumeralStyle.WESTERN, english.numeralStyle)
    }

    @Test
    fun decodeOptionsJson_ignoresFieldsFromANewerLibrary() {
        // The widget can outlive an app update, so an unknown key must not break the render.
        val future = """{"language":"ENGLISH","somethingAddedLater":true}"""
        val options = requireNotNull(HijriWidgetConfig.decodeOptionsJson(future))
        assertEquals(WidgetLanguage.ENGLISH, options.language)
    }

    @Test
    fun decodeOptionsJson_returnsNullForUnusableInput() {
        // Absent/malformed values return null so the caller can choose its own fallback, unlike the
        // shared decoder which substitutes the defaults.
        assertNull(HijriWidgetConfig.decodeOptionsJson(""))
        assertNull(HijriWidgetConfig.decodeOptionsJson("not json"))
    }

    @Test
    fun encodeViewedRoundTripsThroughDecodeViewed() {
        // The viewed month is a separate key from the options so a settings-screen save cannot
        // clobber navigation state; both halves of that pair are covered here. It used to be
        // encoded with `org.json`, which is stubbed in local unit tests, so this path was only ever
        // reachable on a device.
        val prefs = mutablePreferencesOf(
            HijriWidgetConfig.VIEWED_KEY to HijriWidgetConfig.encodeViewed(1448, 3),
        )
        assertEquals(HijriYearMonth(1448, 3), HijriWidgetConfig.decodeViewed(prefs))
    }

    @Test
    fun decodeViewed_returnsNullWhenAbsentOrIncomplete() {
        assertNull(HijriWidgetConfig.decodeViewed(mutablePreferencesOf()))
        // A half-written value must not resolve to a bogus month.
        assertNull(
            HijriWidgetConfig.decodeViewed(
                mutablePreferencesOf(HijriWidgetConfig.VIEWED_KEY to """{"year":1448}"""),
            ),
        )
    }
}
