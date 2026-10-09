package com.muazdev.hijricalendar.widgetdata

import com.muazdev.hijricalendar.core.HijriMonthLengths
import com.muazdev.hijricalendar.core.HijriMonthOverrides
import com.muazdev.hijricalendar.core.WeekDay
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * The single source of truth for how a widget is configured.
 *
 * Every native renderer reads this same value: the Android Glance widgets serialize it into
 * `Glance`'s preferences store per widget id, the iOS WidgetKit widgets serialize it into the
 * shared app group, and a host app's settings screen serializes it again when the user saves.
 * Nothing platform-specific is declared here, so a new option cannot be added on one platform and
 * silently missed on the other — [WidgetOptionsJson] is the wire format both sides agree on.
 *
 * [pinnedYear]/[pinnedMonth] are `null` when the grid should follow today; a non-null pair pins the
 * grid to a fixed Hijri month. They are nullable rather than a sentinel so a renderer cannot
 * accidentally render year `-1`. Read the pair through [pinned], never one half at a time.
 *
 * Read the pin through [pinned] and the week start through [effectiveWeekStart]; both have a
 * migration story that is easier to get wrong from the raw fields than from one accessor.
 */
@Serializable
public data class WidgetOptions(
    /** Clamped to ±[AdjustmentDaysSerializer.LIMIT] on both read and write — see that serializer. */
    @Serializable(with = AdjustmentDaysSerializer::class)
    val adjustmentDays: Int = 0,
    val numeralStyle: NumeralStyle = NumeralStyle.WESTERN,
    /**
     * Which day the week starts on. Stored by **name** for the reason [WidgetOptionsJson] exists
     * (WD-05): the previous field was a bare ordinal into [WeekDay], another module's enum, so
     * inserting a `WeekDay` entry would have silently re-aligned every placed widget's header row.
     */
    val weekStart: WeekStart = WeekStart.DEFAULT,
    val pinnedYear: Int? = null,
    val pinnedMonth: Int? = null,
    val source: WidgetSource = WidgetSource.PAKISTAN,
    val language: WidgetLanguage = WidgetLanguage.ENGLISH,
    /**
     * Which language the Hijri/Gregorian month names render in, independently of [language]
     * (the latter also drives numerals, RTL and weekday names). Lets a widget show Eastern digits
     * with English month names, or Western digits with Urdu month names. Falls back to [language]
     * when absent, so options stored before this option existed keep the script they had.
     */
    val monthNameLanguage: WidgetLanguage? = null,
    /**
     * User-forced month lengths, as `monthLengthKey(year, month) -> 29|30`.
     *
     * Empty means "no widget-level overrides", in which case the projection reads the process-wide
     * [HijriMonthOverrides.current] table — see [overridesTable]. A non-empty map *replaces* it, it
     * does not add to it, so the two can never be half-merged.
     *
     * This lives in the schema rather than being a call-site argument because the option has to
     * cross a process boundary: the iOS WidgetKit extension has its own pristine copy of the
     * process global, so an app that sets an override in-process would otherwise render a month the
     * extension cannot reproduce. Stored as `"1447-9"` keys (not a packed int, and no
     * `Pair<Int, Int>`, which stays out of the published ABI — WD-07).
     */
    val monthLengthOverrides: Map<String, Int> = emptyMap(),
    /**
     * Whether the grid also paints the days belonging to the neighbouring Hijri months.
     *
     * `false` by default, which is a **change** from every release before this one — adjacent days
     * were not optional, there was no flag to turn them off. With the flag off a widget's grid
     * takes five or six rows instead of always six, because the padded month's leading and
     * trailing cells are filtered before the grid chunks them into weeks; see
     * [HijriMonthWidgetData.visibleDays], which is the single place that filtering is defined.
     *
     * Presentational, like `weekendDays` is on the in-app side: it does not change which days
     * exist, what any of them resolve to, or the month the grid is showing. A hidden day is not a
     * disabled day — it is simply not in the list, so there is nothing to tap and nothing to
     * select.
     *
     * A widget stored before this field existed decodes into `false`, because the codec fills a
     * missing key with the data-class default. That is the intended upgrade: the field's default is
     * the behaviour the field was added to change, so "not configured" and "configured off" agree,
     * and no widget is left rendering a grid nobody asked for.
     */
    val showAdjacentDays: Boolean = false,
    /**
     * Which days the grid paints as non-working days.
     *
     * `FRIDAY_SATURDAY` by default, which is what every release before this field rendered — the set
     * was a hardcoded literal at the one call site that built a month projection, so a widget had no
     * way to change it and a user whose weekend is not Friday and Saturday had no way to either.
     * A widget stored before this field existed decodes into `FRIDAY_SATURDAY`, so upgrading changes
     * no already-placed widget's colours.
     *
     * A name-backed enum in this module rather than a set of [com.muazdev.hijricalendar.core.WeekDay]
     * for the reason WD-05 removed `firstDayOfWeekIndex` from the schema: a set of another module's
     * ordinals reinterprets itself the day someone inserts a `WeekDay` entry. See [WeekendPattern].
     *
     * [WeekDay.WEEKEND_DAYS] in `calendar-core` is **not** changed by this. It is a default for
     * consumers who never see a widget, and the default moves in the schema, not in core.
     */
    val weekendPattern: WeekendPattern = WeekendPattern.FRIDAY_SATURDAY,
    /**
     * Whether the grid draws a hairline between every cell (FD-04).
     *
     * `true` by default: on a wall-clock grid of 42 numbers an undivided month is genuinely hard to
     * scan — you read across, stop, and re-read — and a divider is the cheapest fix for that which
     * costs the layout nothing.
     *
     * Purely presentational, and **not** in the widget render cache's keys: the projection's cells are
     * identical either way, so folding it into `MonthKey` would invalidate 42 cells of cached
     * projection over a change that cannot alter one of them.
     *
     * Renderers draw the dividers as row and column separators rather than a border per cell — a border
     * on all 42 cells is 42 extra `RemoteViews` nodes, and Glance's cost is per view. Same visual
     * result, a fraction of the views.
     */
    val showCellBorders: Boolean = true,
) {
    /**
     * The first day of week to render with.
     *
     * [weekStart] is the whole truth, including for a blob written before it existed: the codec
     * folds a stored `firstDayOfWeekIndex` into it at decode time, so the legacy ordinal never
     * survives as a field (WD-05). See [WidgetOptionsJson] for that migration — it lives in the codec
     * rather than here because a persisted field is the one thing this ticket is removing, and
     * `@EncodeDefault(NEVER)` was measured not to suppress a field whose default is a non-constant
     * expression, so a schema-level marker could not be relied on to stay unwritten.
     */
    val effectiveWeekStart: WeekStart get() = weekStart

    /**
     * [effectiveWeekStart] as an index into [WeekDay] entries; 0 = Saturday, matching the in-app
     * calendar.
     *
     * Derived, and kept because existing callers (the sample's settings screen, the Android decoders)
     * speak this dialect. **Prefer [effectiveWeekStart]** in new code: this is an ordinal into
     * another module's enum, so it is only safe to *derive* and never to store.
     */
    val firstDayOfWeekIndexValue: Int get() = weekStart.dayOfWeek.index

    /** [monthNameLanguage] with the backward-compatible fallback applied. */
    val effectiveMonthNameLanguage: WidgetLanguage get() = monthNameLanguage ?: language

    /**
     * The month-length table the projection must render with: [HijriMonthOverrides.current] when
     * this options value carries none of its own, otherwise a table built from
     * [monthLengthOverrides].
     *
     * An empty map delegating to the global is the whole point of making this a method rather than
     * storing a table: a host app that has only ever touched the global keeps working with zero
     * configuration, and one that scopes overrides per widget gets exactly that widget's table.
     */
    public fun overridesTable(): HijriMonthLengths = if (monthLengthOverrides.isEmpty()) {
        HijriMonthOverrides.current
    } else {
        monthLengthsFrom(monthLengthOverrides)
    }

    /** True when the grid is pinned rather than following today. */
    val isPinned: Boolean get() = pinned != null

    /**
     * The pinned Hijri year and month as a unit, or `null` when the grid follows today.
     *
     * **This is the only way to read the pin.** A half-set pair is representable — the two fields
     * are separate wire fields, so a hand-edited or partially-migrated JSON blob can carry one
     * without the other — and the two renderers used to disagree about what that means. The iOS
     * timeline checked `isPinned` and fell through to today; the Android widget read the two fields
     * directly and chained each against today's month, producing a grid labelled 1447 that painted
     * *this* year's days. Nothing about that failure is loud, and the user has no signal.
     *
     * Reading the pair as a unit is the fix: a half-set pin is simply "not pinned", which is what
     * the platform that checked the pair already did and is the safer of the two answers.
     */
    val pinned: HijriYearMonth?
        get() {
            val year = pinnedYear ?: return null
            val month = pinnedMonth ?: return null
            // A stored month outside 1..12 is not a Hijri month, so it is not a pin — and the
            // `require` inside [HijriYearMonth] must not be reachable from a property a renderer
            // reads on a render path (it threw straight out of `decodeOrNull`, which only catches
            // `SerializationException`). Total by construction, and it folds in WD-09's month check.
            if (month !in 1..12) return null
            return HijriYearMonth(year = year, month = month)
        }

    /**
     * The Hijri month + year this grid shows, given the [today] fallback a renderer resolved
     * locally (a renderer must supply "today" itself, because only it knows the user's timezone).
     * A pinned value wins; otherwise [today]'s Hijri year and month are used.
     */
    public fun resolveGridMonth(today: HijriYearMonth): HijriYearMonth = pinned ?: today

    /**
     * As [resolveGridMonth], but answering `null` when it has nothing to fall back to.
     *
     * Exists because "I do not know today's date" cannot be expressed in the non-nullable form:
     * today is resolved by the *renderer*, since only the renderer knows the user's timezone. iOS
     * previously worked around that by hardcoding a fallback month at two call sites
     * (`(1447, 1)`), which is a value that can be wrong — and a `Pair` had nowhere to put "unknown"
     * even if it had been available.
     */
    public fun resolveGridMonthOrNull(today: HijriYearMonth?): HijriYearMonth? = pinned ?: today

    /**
     * `equals`/`hashCode`/`toString` are generated from the *constructor* parameters, which are the
     * raw, un-normalised pair — so two options that mean the same thing (`pinnedYear = 1447` and no
     * pin at all) would compare unequal. Overridden so the value type describes the state a
     * renderer actually sees, which is the whole point of normalising.
     *
     * ## ⚠️ Every field must be listed, and `WidgetOptionsEqualityTest` enforces it
     *
     * A hand-written `equals` does not fall behind its class the way a generated one does — it just
     * quietly stops comparing the fields added after it. And the failure is **silent and total**:
     * `copy(showCellBorders = true) == copy(showCellBorders = false)`, so the settings screen's
     * `if (newOptions == options) return` discards every change to that field and the toggle appears
     * dead. Three options shipped that way before the test existed.
     *
     * So the field list is asserted by reflection — the same shape as
     * `HijriWidgetConfigTest.widgetOptionsSaver_roundTripsEveryOptionItDeclares`, which exists for
     * the identical reason on the saver. Adding a field without updating either fails the build
     * rather than a user's widget.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WidgetOptions) return false
        return adjustmentDays == other.adjustmentDays &&
            numeralStyle == other.numeralStyle &&
            effectiveWeekStart == other.effectiveWeekStart &&
            pinned == other.pinned &&
            source == other.source &&
            language == other.language &&
            monthNameLanguage == other.monthNameLanguage &&
            monthLengthOverrides == other.monthLengthOverrides &&
            showAdjacentDays == other.showAdjacentDays &&
            weekendPattern == other.weekendPattern &&
            showCellBorders == other.showCellBorders
    }

    override fun hashCode(): Int {
        var result = adjustmentDays
        result = 31 * result + numeralStyle.hashCode()
        result = 31 * result + effectiveWeekStart.hashCode()
        result = 31 * result + (pinned?.hashCode() ?: 0)
        result = 31 * result + source.hashCode()
        result = 31 * result + language.hashCode()
        result = 31 * result + (monthNameLanguage?.hashCode() ?: 0)
        result = 31 * result + monthLengthOverrides.hashCode()
        result = 31 * result + showAdjacentDays.hashCode()
        result = 31 * result + weekendPattern.hashCode()
        result = 31 * result + showCellBorders.hashCode()
        return result
    }

    override fun toString(): String = "WidgetOptions(" +
        "adjustmentDays=$adjustmentDays, numeralStyle=$numeralStyle, " +
        "weekStart=$weekStart, pinned=$pinned, source=$source, " +
        "language=$language, monthNameLanguage=$monthNameLanguage, " +
        "monthLengthOverrides=$monthLengthOverrides)"

    val localizedHijriMonthNames: List<String>?
        get() = WidgetLocalization.hijriMonthNames(effectiveMonthNameLanguage)

    val localizedGregorianMonthNames: List<String>?
        get() = WidgetLocalization.gregorianMonthNames(effectiveMonthNameLanguage)

    val localizedWeekdayNames: List<String>?
        get() = WidgetLocalization.weekdayNames(language)

    /**
     * The Hijri month name for [month] (1-12) under [effectiveMonthNameLanguage], for a settings
     * screen's pinned-month picker — the exact string the widget will render.
     */
    public fun hijriMonthName(month: Int): String =
        localizedHijriMonthNames?.getOrNull(month - 1)
            ?: WidgetLocalization.englishHijriMonthNames.getOrNull(month - 1)
            ?: "Month $month"

    public companion object {
        /**
         * Fresh-widget defaults: English names, Western digits, the Pakistan source and the cell
         * dividers on, matching the sample app's default settings.
         */
        public val DEFAULTS: WidgetOptions = WidgetOptions(
            adjustmentDays = 0,
            numeralStyle = WidgetLocalization.defaultNumeralStyle(WidgetLanguage.ENGLISH),
            weekStart = WeekStart.DEFAULT,
            pinnedYear = null,
            pinnedMonth = null,
            source = WidgetSource.PAKISTAN,
            language = WidgetLanguage.ENGLISH,
            monthNameLanguage = WidgetLanguage.ENGLISH,
            showAdjacentDays = false,
            weekendPattern = WeekendPattern.FRIDAY_SATURDAY,
            showCellBorders = true,
        )
    }
}

/**
 * The wire format for [WidgetOptions].
 *
 * Native renderers persist the JSON text and hand it straight back, which avoids re-declaring the
 * schema (or hand-mapping it) on each platform: a Swift or Java settings screen builds an options
 * value, encodes it once, and the widget decodes it with no knowledge of the field set. Kotlin's
 * `Json` is configured to [ignoreUnknownKeys] so options written by a newer version of a renderer
 * still load in an older one, and [encodeDefaults] is on so an omitted field is written
 * explicitly rather than depending on the reader's default.
 */
public object WidgetOptionsJson {
    private val json = Json {
        // Unknown *keys*: a widget outlives the release that wrote it, so a field a newer renderer
        // added must not stop an older one loading the rest.
        ignoreUnknownKeys = true
        // Write every field, so a reader never has to know the defaults *this* release uses.
        encodeDefaults = true
        // Unknown or wrong-typed *values* (WD-06). `ignoreUnknownKeys` does nothing for these: one
        // unrecognised enum value threw, the `catch` returned null, and `decode` substituted
        // wholesale DEFAULTS — so a single renamed `language` reset adjustment days, numerals, first
        // day of week, pin, source and month-name language at once. This confines the damage to the
        // one field that is actually unreadable.
        coerceInputValues = true
    }

    /**
     * The field `weekStart` replaced: a bare ordinal into `WeekDay`, persisted by every release
     * before it. Read at decode time and folded into [WidgetOptions.weekStart]; never written.
     */
    private const val LEGACY_WEEK_START_KEY = "firstDayOfWeekIndex"

    /** Serializes [options]; the result is what a renderer stores and [decode] reads back. */
    public fun encode(options: WidgetOptions): String = json.encodeToString(WidgetOptions.serializer(), options)

    /**
     * Parses [text], returning `null` when it is absent or cannot be read at all. [decode] is this
     * with a default applied; a caller that has another format to try (e.g. a migration from a
     * previous storage encoding) needs to tell "unreadable" apart from "readable and equal to the
     * defaults".
     *
     * **"Unreadable" is now a narrow set.** With [coerceInputValues] enabled, a single bad field no
     * longer reaches here — only text that is not this format at all (malformed JSON, a JSON array)
     * does. That matters for callers layering a legacy reader on top, like
     * `calendar-widget-glance`'s `decodeOptionsJson`: a *newer* blob with an unrecognised enum no
     * longer lands in that fallback and silently reverts to all-defaults (WG-09).
     *
     * The catch is narrowed to [SerializationException] (an `IllegalArgumentException`) rather than
     * `Exception`, so a genuine defect in a serializer surfaces in a test instead of quietly
     * producing defaults.
     */
    public fun decodeOrNull(text: String?): WidgetOptions? = decodeOrReport(text)?.options

    /**
     * [decodeOrNull], plus a report of which fields had to be repaired.
     *
     * A repaired field is evidence of a bug upstream — a hand-edited store, a renderer that wrote
     * something impossible — and this module is coroutine-free and must stay that way (CORE-06), so
     * it cannot log the fact itself. Handing the list to the *platform* is how `HijriWidgetRefreshLog`
     * gets to say "read a corrupt option" instead of the repair being invisible.
     */
    public fun decodeOrReport(text: String?): DecodeResult? = try {
        if (text.isNullOrBlank()) {
            null
        } else {
            val element = json.parseToJsonElement(text)
            json.decodeFromJsonElement(WidgetOptions.serializer(), element)
                .withLegacyWeekStart(element)
                .validated()
        }
    } catch (_: SerializationException) {
        null
    }

    /**
     * What a decode produced, and what had to be repaired to produce it.
     *
     * [repairedFields] is empty for a clean blob. Its entries are field names, chosen so a log line
     * reads as the field list rather than a wall of text.
     */
    public data class DecodeResult(
        public val options: WidgetOptions,
        public val repairedFields: List<String> = emptyList(),
    ) {
        /** True when the stored blob was not self-consistent and fields were coerced or dropped. */
        public val wasRepaired: Boolean get() = repairedFields.isNotEmpty()
    }

    /**
     * Coerce or drop every field whose stored value cannot be rendered (WD-09).
     *
     * kotlinx-serialization checks *types*, not values, so `{"pinnedMonth":13}` decoded cleanly
     * into a `WidgetOptions` that no projection can build. The consequences were asymmetric and all
     * silent: the grid builder returns `null` for an impossible month, and `HijriWidgetRoot` answers
     * a `null` by falling back to a **compact today card** — so a widget the user had resized to a
     * four-column grid quietly showed a small card in a large frame, which reads as intentional and
     * gives nothing to diagnose. On iOS the same `null` produced a different wrong answer from the
     * same stored data.
     *
     * The asymmetry was the giveaway: `firstDayOfWeekIndex` was coerced, `pinnedMonth` was not.
     *
     * **Prefer repairing over rejecting.** `pinnedMonth = 13` becomes *unpinned* rather than
     * failing the decode, because "show today" is correct and useful where "today card in a grid
     * frame" is neither.
     *
     * The year is deliberately *not* range-checked. The valid Hijri year range depends on
     * [WidgetOptions.source] (Pakistan mode supports a narrower window), so a bound written here
     * would be wrong for one of the two modes; the builder's own `null` stands for that case, and it
     * is the same `null` every mode already handles. The month needs no such caveat — 1..12 is 1..12
     * in every calendar this library speaks.
     */
    private fun WidgetOptions.validated(): DecodeResult {
        val repaired = mutableListOf<String>()

        // The week start is already total: `withLegacyWeekStart` folds a stored legacy ordinal in
        // through `WeekStart.fromIndex`, which clamps rather than throwing (WD-05). Nothing to repair.

        // Two distinct repairs, reported separately because they are two distinct bugs upstream:
        // a month that is not a month, and a pin with only one half. Reporting both as "pin" would
        // hide the half-set case, which is the one a renderer used to render as a stale year.
        val validMonth = pinnedMonth?.takeIf { it in 1..12 }
        if (validMonth != pinnedMonth) repaired += "pinnedMonth"
        if ((pinnedYear != null) != (pinnedMonth != null)) repaired += "pin"

        // A half-set pin is not a pin (WD-03), so either half being absent clears the month too and
        // leaves the options value self-consistent for `equals`.
        val normalisedMonth = if (validMonth == null || pinnedYear == null) null else validMonth
        return DecodeResult(options = copy(pinnedMonth = normalisedMonth), repairedFields = repaired)
    }

    /**
     * Folds a pre-[WidgetOptions.weekStart] `firstDayOfWeekIndex` into [WidgetOptions.weekStart].
     *
     * The migration lives here rather than as a field on the schema, and that placement is the point
     * (WD-05). A field would have to be *omitted* on write while still being *read*, and
     * `@EncodeDefault(NEVER)` was measured not to suppress a field whose default is a non-constant
     * expression — so the legacy ordinal would have kept being written back on every save, which is
     * the exact thing the ticket removes. Keeping it in the codec also means [WidgetOptions] has no
     * field that only exists for old blobs.
     *
     * [WidgetOptions.weekStart] wins when both are present, because a release that writes `weekStart`
     * is the one whose choice is authoritative; a transitional blob carrying both has them agreeing
     * anyway. An out-of-range index degrades to [WeekStart.DEFAULT] rather than throwing, since this
     * runs on a render path over persisted data.
     */
    private fun WidgetOptions.withLegacyWeekStart(element: JsonElement): WidgetOptions {
        val legacy = (element as? JsonObject)?.get(LEGACY_WEEK_START_KEY) as? JsonPrimitive
        val index = legacy?.intOrNull ?: return this
        return copy(weekStart = WeekStart.fromIndex(index))
    }

    /**
     * Parses [text], returning [WidgetOptions.DEFAULTS] only for text that is *entirely* unreadable —
     * a corrupt or absent value must degrade to a working widget rather than an empty one.
     *
     * Note that these are the class-field defaults, not the fresh-widget [WidgetOptions.DEFAULTS]:
     * a stored blob that omits a field means "never chosen", and the two differ only in
     * `monthNameLanguage`, which stays `null` here so it follows whatever `language` the blob did
     * store. That distinction is deliberate and pinned by a test.
     */
    public fun decode(text: String?): WidgetOptions = decodeOrNull(text) ?: WidgetOptions.DEFAULTS
}

/**
 * Builds [WidgetOptions] from plain values.
 *
 * Exists for native settings screens: a Swift/Java caller cannot conveniently construct a Kotlin
 * data class with nine parameters and two nullables, but it can call one flat function. `pinsMonth`
 * collapses the nullable pinned pair into a single flag, and a `false` clears any previous pin.
 *
 * **The pinned month is clamped into 1..12 and a half-pin is impossible** — `pinsMonth` gates both
 * halves. The pinned *year* is deliberately **not** range-checked here: the valid window depends on
 * [WidgetSource] (Pakistan mode supports a narrower one), so a bound written in this factory would be
 * wrong for one of the two modes. The same reasoning as [WidgetOptionsJson]'s decode validation,
 * which drops an impossible month for the same reason.
 *
 * [overridesCsv] is [encodeMonthLengthsCsv]'s `"<year>-<month>:<length>"` form rather than a map,
 * for the same reason the map is not a constructor parameter on the native-facing surface: Swift
 * and Java have no dict literal that maps cleanly onto a Kotlin `Map`, and the CSV is what the
 * Android `rememberSaveable` saver carries anyway.
 */
@Suppress("LongParameterList")
public fun createWidgetOptions(
    language: WidgetLanguage = WidgetLanguage.ENGLISH,
    monthNameLanguage: WidgetLanguage? = null,
    source: WidgetSource = WidgetSource.PAKISTAN,
    adjustmentDays: Int = 0,
    numeralStyle: NumeralStyle = NumeralStyle.WESTERN,
    weekStart: WeekStart = WeekStart.DEFAULT,
    pinsMonth: Boolean = false,
    pinnedYear: Int = 0,
    pinnedMonth: Int = 1,
    overridesCsv: String? = null,
    showAdjacentDays: Boolean = false,
    weekendPattern: WeekendPattern = WeekendPattern.FRIDAY_SATURDAY,
    showCellBorders: Boolean = true,
): WidgetOptions = WidgetOptions(
    adjustmentDays = adjustmentDays,
    numeralStyle = numeralStyle,
    weekStart = weekStart,
    pinnedYear = if (pinsMonth) pinnedYear else null,
    pinnedMonth = if (pinsMonth) pinnedMonth.coerceIn(1, 12) else null,
    source = source,
    language = language,
    monthNameLanguage = monthNameLanguage ?: language,
    monthLengthOverrides = decodeMonthLengthsCsv(overridesCsv),
    showAdjacentDays = showAdjacentDays,
    weekendPattern = weekendPattern,
    showCellBorders = showCellBorders,
)

/**
 * [buildHijriMonthWidgetData] driven straight from [options], so a renderer cannot forget to
 * thread one of the eight values through and end up disagreeing with the settings screen.
 *
 * Reading direction follows [WidgetOptions.language]. A renderer that must additionally
 * compensate for its platform mirroring (Glance rows are plain horizontal `LinearLayout`s the
 * platform already flips on an RTL device) uses the [rightToLeft] overload instead of adjusting
 * the options, so the stored value always means "the language I chose".
 *
 * Month-length overrides are **not** a parameter here — they are part of [options], so the grid a
 * widget renders is exactly the table its own settings screen stored (see
 * [WidgetOptions.overridesTable]).
 */
public fun buildHijriMonthWidgetData(
    hijriYear: Int,
    hijriMonth: Int,
    options: WidgetOptions,
    weekendDays: Set<WeekDay> = options.weekendPattern.toWeekDays(),
): HijriMonthWidgetData? = buildHijriMonthWidgetData(
    hijriYear = hijriYear,
    hijriMonth = hijriMonth,
    options = options,
    weekendDays = weekendDays,
    rightToLeft = options.language.isRtl,
)

/**
 * As above, with an explicit reading direction for platforms that mirror rows themselves, and an
 * explicit weekend set.
 *
 * [weekendDays] is a parameter here only so a caller that has already resolved a set — a host
 * rendering its own calendar, say — need not round-trip through an enum. A widget renderer should use
 * the [options] overload above, which reads [WidgetOptions.weekendPattern] and cannot forget it.
 */
public fun buildHijriMonthWidgetData(
    hijriYear: Int,
    hijriMonth: Int,
    options: WidgetOptions,
    weekendDays: Set<WeekDay>,
    rightToLeft: Boolean,
): HijriMonthWidgetData? = buildHijriMonthWidgetData(
    hijriYear = hijriYear,
    hijriMonth = hijriMonth,
    adjustmentDays = options.adjustmentDays,
    weekStart = options.effectiveWeekStart,
    numeralStyle = options.numeralStyle,
    pakistan = options.source.pakistan,
    weekendDays = weekendDays,
    rightToLeft = rightToLeft,
    localizedHijriMonthNames = options.localizedHijriMonthNames,
    localizedGregorianMonthNames = options.localizedGregorianMonthNames,
    localizedWeekdayNames = options.localizedWeekdayNames,
    overrides = options.overridesTable(),
    // Era markers follow `effectiveMonthNameLanguage`, not `language`: an era marker is *text* that
    // sits beside the month names, so it belongs to the same locale as the names it is read with.
    // Using `language` here produced "April - May 2026 ء" — a Latin month range with an Urdu suffix —
    // for exactly the widget that had asked for English month names.
    //
    // Numerals stay independent, because `numeralStyle` is an explicit user choice rather than a
    // locale: a widget may show Arabic-Indic digits with English month names, and that mix is what it
    // asked for.
    hijriEra = WidgetLocalization.ChromeLabels.hijriEra(options.effectiveMonthNameLanguage),
    gregorianEra = WidgetLocalization.ChromeLabels.gregorianEra(options.effectiveMonthNameLanguage),
)

/** [todayHijriWidgetData] driven straight from [options]. See the grid overload for why. */
public fun todayHijriWidgetData(
    anchorEpochDay: Long,
    options: WidgetOptions,
): TodayHijriWidgetData? = todayHijriWidgetData(
    anchorEpochDay = anchorEpochDay,
    adjustmentDays = options.adjustmentDays,
    localizedHijriMonthNames = options.localizedHijriMonthNames,
    localizedGregorianMonthNames = options.localizedGregorianMonthNames,
    localizedWeekdayNames = options.localizedWeekdayNames,
    numeralStyle = options.numeralStyle,
    pakistan = options.source.pakistan,
    overrides = options.overridesTable(),
    hijriEra = WidgetLocalization.ChromeLabels.hijriEra(options.effectiveMonthNameLanguage),
    gregorianEra = WidgetLocalization.ChromeLabels.gregorianEra(options.effectiveMonthNameLanguage),
    // The short Hijri month name is *text* too, so it follows the month-name language rather than
    // `language` and rather than the device (WG-12). Passed alongside the names it abbreviates, so a
    // caller cannot localize the names one way and pick the short base names another.
    monthNameLanguage = options.effectiveMonthNameLanguage,
)
