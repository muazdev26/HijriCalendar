package com.muazdev.hijricalendar.widgetdata

import com.muazdev.hijricalendar.core.WeekDay
import kotlinx.datetime.LocalDate
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.elementNames
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * FD-03: `WeekendPattern` — which days the grid paints as non-working days.
 *
 * The report this came from was "two days are showing red, like Friday and Sunday — every calendar
 * only shows one". They were Friday and Saturday, from a hardcoded `WeekDay.WEEKEND_DATES` literal at
 * the one call site that built a projection, which is right for the Pakistan calendar this library
 * also supports and unwrong-able by the user. So the behaviour was correct and the *configuration* was
 * missing; these tests pin the replacement without blessing the old behaviour as the only answer.
 *
 * The two properties that matter beyond "it decodes":
 *
 * - the schema stores a **name**, never an ordinal into another module's enum — the WD-05 hazard this
 *   field would otherwise reintroduce a second time; and
 * - the default is `FRIDAY_SATURDAY`, so upgrading changes no already-placed widget.
 */
class WeekendPatternTest {

    @Test
    fun fridaySaturdayIsTheDefault() {
        assertEquals(
            WeekendPattern.FRIDAY_SATURDAY,
            WidgetOptions().weekendPattern,
            "the data-class default must be Friday+Saturday — what every release before this field " +
                "rendered, so an unchanged default means an unchanged widget",
        )
        assertEquals(
            WeekendPattern.FRIDAY_SATURDAY,
            WidgetOptions.DEFAULTS.weekendPattern,
            "DEFAULTS is what a fresh install and the family mirror resolve to",
        )
        assertEquals(
            WeekendPattern.FRIDAY_SATURDAY,
            createWidgetOptions().weekendPattern,
            "the native factory must agree",
        )
    }

    /**
     * `FRIDAY_SATURDAY` is *exactly* the set the hardcoded literal used.
     *
     * Not approximately. If `WeekDay.WEEKEND_DAYS` ever changed in core, this fails — which is the
     * point: the schema is describing the behaviour being replaced, and a divergence would mean the
     * default quietly means something else on one platform.
     */
    @Test
    fun theDefaultPatternIsExactlyTheSetItReplaces() {
        assertEquals(
            WeekDay.WEEKEND_DAYS,
            WeekendPattern.FRIDAY_SATURDAY.toWeekDays(),
            "FRIDAY_SATURDAY must be the set the hardcoded call site used, or the default is a " +
                "behaviour change disguised as a configuration one",
        )
    }

    @Test
    fun eachPatternMapsToTheDaysItNames() {
        assertEquals(setOf(WeekDay.FRIDAY, WeekDay.SATURDAY), WeekendPattern.FRIDAY_SATURDAY.toWeekDays())
        assertEquals(setOf(WeekDay.SUNDAY), WeekendPattern.SUNDAY.toWeekDays())
        assertEquals(setOf(WeekDay.FRIDAY), WeekendPattern.FRIDAY_ONLY.toWeekDays())
        assertTrue(WeekendPattern.NONE.toWeekDays().isEmpty())
    }

    /**
     * The round trip through `WeekendPattern.of` is total for the four patterns and honest about the
     * rest.
     *
     * `of` returns `null` for an unrecognised set rather than falling back to `FRIDAY_SATURDAY`. That
     * is deliberate: a caller holding a set the enum does not describe — a host that resolved its own —
     * wants to know, and silently shading Friday and Saturday because the caller asked for something
     * else is how a calendar starts lying about which days matter.
     */
    @Test
    fun ofIsTotalForItsOwnPatternsAndNullOtherwise() {
        for (pattern in WeekendPattern.entries) {
            assertEquals(
                pattern,
                WeekendPattern.of(pattern.toWeekDays()),
                "$pattern must map back to itself",
            )
        }
        assertNull(
            WeekendPattern.of(setOf(WeekDay.SUNDAY, WeekDay.SATURDAY)),
            "a Saturday-Sunday weekend is deliberately not one of the four; of() must say so rather " +
                "than pick a near miss",
        )
        assertNull(WeekendPattern.of(WeekDay.entries.toSet()))
    }

    /**
     * Reordering these entries must not change what a stored widget renders.
     *
     * The whole reason this is a name-backed enum owned by `calendar-widget-data` rather than a set of
     * `WeekDay` ordinals: `WeekDay`'s own KDoc warns that its declaration order is persisted, and
     * inserting an entry there would have silently re-interpreted every placed widget (WD-05). This
     * cannot be asserted by reordering the enum in a test, so it is asserted the only way that
     * actually holds: by reading the names back out of the encoded form.
     */
    @Test
    fun theWireFormatStoresTheNameSoEntryOrderIsIrrelevant() {
        for (pattern in WeekendPattern.entries) {
            val text = WidgetOptionsJson.encode(
                WidgetOptions(weekendPattern = pattern),
            )
            assertTrue(
                "\"weekendPattern\":\"${pattern.name}\"" in text,
                "expected the enum's *name* in $text, not an index",
            )
            assertEquals(
                pattern,
                assertNotNull(WidgetOptionsJson.decodeOrNull(text)).weekendPattern,
                "$pattern did not survive the round trip by name",
            )
        }
    }

    /**
     * A non-default pattern reaches the projection.
     *
     * The end-to-end half: `isWeekend` on a grid cell is the only thing the renderer reads, so a
     * pattern that decodes correctly but never reaches `buildHijriMonthWidgetData` would be a schema
     * change that changed nothing. Two anchoring cells, one per end of the week, so a Sunday pattern
     * cannot pass by accident on a month where it happens to overlap Friday.
     */
    @Test
    fun aNonDefaultPatternReachesTheProjectedCells() {
        val options = createWidgetOptions(
            language = WidgetLanguage.ENGLISH,
            weekendPattern = WeekendPattern.SUNDAY,
        )
        val month = assertNotNull(
            buildHijriMonthWidgetData(hijriYear = 1447, hijriMonth = 9, options = options),
            "no projection for 1447-09",
        )

        val shaded = month.days.filter { it.isWeekend }
        assertTrue(shaded.isNotEmpty(), "a Sunday pattern must shade some cells")

        // Every shaded cell's *Gregorian* weekday has to be a Sunday. The grid is pre-reversed for RTL,
        // so the assertion is over the whole month rather than a column position.
        val shadedWeekdays = shaded.map { it.gregorianEpochDay.toWeekDay() }.toSet()
        assertEquals(
            setOf(WeekDay.SUNDAY),
            shadedWeekdays,
            "a Sunday pattern shaded a day that is not a Sunday",
        )

        // And the month has both Sundays and non-Sundays, so the previous assertion is not passing
        // because everything or nothing was shaded.
        val allWeekdays = month.days.map { it.gregorianEpochDay.toWeekDay() }.toSet()
        assertEquals(
            WeekDay.entries.toSet(),
            allWeekdays,
            "the fixture month must contain every weekday, or the assertion above is vacuous",
        )
    }

    /**
     * `NONE` shades nothing at all, which is the case a boolean flag could not express.
     *
     * Worth its own test because "no shaded days" is the one answer a two-state toggle cannot give,
     * and it is the answer most likely to be wanted by a secular or purely business calendar.
     */
    @Test
    fun theNonePatternShadesNothing() {
        val options = createWidgetOptions(
            language = WidgetLanguage.ENGLISH,
            weekendPattern = WeekendPattern.NONE,
        )
        val month = assertNotNull(
            buildHijriMonthWidgetData(hijriYear = 1447, hijriMonth = 9, options = options),
        )
        assertTrue(
            month.days.none { it.isWeekend },
            "NONE must shade nothing, so a widget can show a plain seven-column grid",
        )
    }

    /**
     * A blob written before this field existed decodes into the old behaviour.
     *
     * And — the part that is easy to get wrong — an *unreadable* value also lands on the default rather
     * than throwing and resetting the whole widget. `coerceInputValues` (WD-06) is what makes that so;
     * the alternative is one bad field silently reverting eight others.
     */
    @Test
    fun aPreFieldOrCorruptBlobDecodesToTheDefaultWithoutLosingAnythingElse() {
        val fromTwoZeroZero = """
            {"adjustmentDays":-2,"numeralStyle":"WESTERN","weekStart":"MONDAY","pinnedYear":null,
             "pinnedMonth":null,"source":"CALCULATION","language":"ENGLISH",
             "monthNameLanguage":"ENGLISH","monthLengthOverrides":{},"showAdjacentDays":true}
        """.trimIndent()

        val legacy = assertNotNull(WidgetOptionsJson.decodeOrNull(fromTwoZeroZero))
        assertEquals(WeekendPattern.FRIDAY_SATURDAY, legacy.weekendPattern)
        assertEquals(-2, legacy.adjustmentDays, "no other field may be disturbed")
        assertTrue(legacy.showAdjacentDays, "a field added by an earlier ticket must survive too")

        val corrupt = """
            {"adjustmentDays":5,"weekendPattern":"NOT_A_PATTERN","language":"ENGLISH"}
        """.trimIndent()
        val repaired = assertNotNull(WidgetOptionsJson.decodeOrNull(corrupt))
        assertEquals(WeekendPattern.FRIDAY_SATURDAY, repaired.weekendPattern)
        assertEquals(
            5,
            repaired.adjustmentDays,
            "one unreadable field must not reset the rest (WD-06)",
        )
    }

    /**
     * The legacy *ordinal* format must not be routed by this field.
     *
     * `HijriWidgetConfig`'s `LEGACY_ENUM_KEYS` deliberately does **not** list `weekendPattern`, because
     * the pre-shared format never wrote it and widening that gate would send blobs whose other enums
     * are already named through a reader that reads those as ordinals. This test states the reasoning
     * from the schema side: the shared decoder is the one that must handle it, and it does.
     */
    @Test
    fun anOrdinalWeekendValueIsCoercedNotTreatedAsALegacyBlob() {
        val blob = """
            {"adjustmentDays":0,"weekendPattern":2,"language":"URDU","numeralStyle":"ARABIC_INDIC",
             "source":"CALCULATION","monthNameLanguage":"URDU"}
        """.trimIndent()

        // The shape discriminator in HijriWidgetConfig only fires on an *integer* in one of the four
        // fields that format wrote. Simulate both readings: the integer here must NOT be what decides,
        // and the shared decoder must produce the coerced default with everything else intact.
        assertEquals(
            WeekendPattern.FRIDAY_SATURDAY,
            assertNotNull(WidgetOptionsJson.decodeOrNull(blob)).weekendPattern,
            "an ordinal in the current format is coerced to the default, not read as an index",
        )
        assertEquals(
            WidgetLanguage.URDU,
            assertNotNull(WidgetOptionsJson.decodeOrNull(blob)).language,
            "and the named enums beside it must still decode — the reason the legacy gate is not widened",
        )
    }

    @Test
    fun theEnumIsStableUnderSerialisationToItsOwnName() {
        // A regression guard for the shape rather than the values: if `WeekendPattern` were ever
        // annotated for ordinal serialisation, every assertion above would still pass on the JVM and
        // fail on Kotlin/Native. Reading the serializer's own descriptor catches it at the source,
        // and `elementNames` is what would hold an ordinal.
        val descriptor = WeekendPattern.serializer().descriptor
        assertEquals(
            SerialKind.ENUM,
            descriptor.kind,
            "WeekendPattern must serialise as a named enum",
        )
        assertEquals(
            WeekendPattern.entries.map { it.name },
            descriptor.elementNames.toList(),
            "the descriptor's element names are what the wire format stores, in declaration order",
        )
    }

    /** The Gregorian weekday a cell's epoch day falls on, through the same core conversion the grid uses. */
    private fun Long.toWeekDay(): WeekDay =
        WeekDay.fromDayOfWeek(LocalDate.fromEpochDays(this).dayOfWeek)
}
