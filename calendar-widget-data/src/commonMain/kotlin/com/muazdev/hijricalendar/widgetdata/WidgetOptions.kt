package com.muazdev.hijricalendar.widgetdata

import com.muazdev.hijricalendar.core.CalendarMonth
import com.muazdev.hijricalendar.core.WeekDay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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
 * accidentally render year `-1`.
 */
@Serializable
data class WidgetOptions(
    val adjustmentDays: Int = 0,
    val numeralStyle: NumeralStyle = NumeralStyle.WESTERN,
    /** 0-based index into [WeekDay] entries; 0 = Saturday, matching the in-app calendar. */
    val firstDayOfWeekIndex: Int = WeekDay.DEFAULT_FIRST_DAY.index,
    val pinnedYear: Int? = null,
    val pinnedMonth: Int? = null,
    val source: WidgetSource = WidgetSource.CALCULATION,
    val language: WidgetLanguage = WidgetLanguage.URDU,
    /**
     * Which language the Hijri/Gregorian month names render in, independently of [language]
     * (the latter also drives numerals, RTL and weekday names). Lets a widget show Eastern digits
     * with English month names, or Western digits with Urdu month names. Falls back to [language]
     * when absent, so options stored before this option existed keep the script they had.
     */
    val monthNameLanguage: WidgetLanguage? = null,
) {
    /** [monthNameLanguage] with the backward-compatible fallback applied. */
    val effectiveMonthNameLanguage: WidgetLanguage get() = monthNameLanguage ?: language

    /** True when the grid is pinned rather than following today. */
    val isPinned: Boolean get() = pinnedYear != null && pinnedMonth != null

    /**
     * The Hijri month + year this grid shows, given the [today] fallback a renderer resolved
     * locally (a renderer must supply "today" itself, because only it knows the user's timezone).
     * A pinned value wins; otherwise [today]'s Hijri year and month are used.
     */
    fun resolveGridMonth(today: Pair<Int, Int>): Pair<Int, Int> =
        if (isPinned) pinnedYear!! to pinnedMonth!! else today

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
    fun hijriMonthName(month: Int): String =
        localizedHijriMonthNames?.getOrNull(month - 1)
            ?: WidgetLocalization.englishHijriMonthNames.getOrNull(month - 1)
            ?: "Month $month"

    companion object {
        /**
         * Fresh-widget defaults: Urdu names, Eastern Arabic-Indic digits and the Calculation
         * source, matching the sample app's Urdu labels.
         */
        val DEFAULTS = WidgetOptions(
            adjustmentDays = 0,
            numeralStyle = WidgetLocalization.defaultNumeralStyle(WidgetLanguage.URDU),
            firstDayOfWeekIndex = WeekDay.DEFAULT_FIRST_DAY.index,
            pinnedYear = null,
            pinnedMonth = null,
            source = WidgetSource.CALCULATION,
            language = WidgetLanguage.URDU,
            monthNameLanguage = WidgetLanguage.URDU,
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
object WidgetOptionsJson {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Serializes [options]; the result is what a renderer stores and [decode] reads back. */
    fun encode(options: WidgetOptions): String = json.encodeToString(WidgetOptions.serializer(), options)

    /**
     * Parses [text], returning `null` when it is absent or cannot be read. [decode] is this with a
     * default applied; a caller that has another format to try (e.g. a migration from a previous
     * storage encoding) needs to tell "unreadable" apart from "readable and equal to the defaults".
     */
    fun decodeOrNull(text: String?): WidgetOptions? = try {
        if (text.isNullOrBlank()) null else json.decodeFromString(WidgetOptions.serializer(), text)
    } catch (_: Exception) {
        null
    }

    /**
     * Parses [text], returning [WidgetOptions.DEFAULTS] for anything unreadable — a corrupt or
     * absent value must degrade to a working widget rather than an empty one.
     */
    fun decode(text: String?): WidgetOptions = decodeOrNull(text) ?: WidgetOptions.DEFAULTS
}

/**
 * Builds [WidgetOptions] from plain values.
 *
 * Exists for native settings screens: a Swift/Java caller cannot conveniently construct a Kotlin
 * data class with eight parameters and two nullables, but it can call one flat function. `pinsMonth`
 * collapses the nullable pinned pair into a single flag, and a `false` clears any previous pin.
 */
@Suppress("LongParameterList")
fun createWidgetOptions(
    language: WidgetLanguage = WidgetLanguage.URDU,
    monthNameLanguage: WidgetLanguage? = null,
    source: WidgetSource = WidgetSource.CALCULATION,
    adjustmentDays: Int = 0,
    numeralStyle: NumeralStyle = NumeralStyle.WESTERN,
    firstDayOfWeekIndex: Int = WeekDay.DEFAULT_FIRST_DAY.index,
    pinsMonth: Boolean = false,
    pinnedYear: Int = 0,
    pinnedMonth: Int = 1,
): WidgetOptions = WidgetOptions(
    adjustmentDays = adjustmentDays,
    numeralStyle = numeralStyle,
    firstDayOfWeekIndex = firstDayOfWeekIndex.coerceIn(0, CalendarMonth.DAYS_IN_WEEK - 1),
    pinnedYear = if (pinsMonth) pinnedYear else null,
    pinnedMonth = if (pinsMonth) pinnedMonth.coerceIn(1, 12) else null,
    source = source,
    language = language,
    monthNameLanguage = monthNameLanguage ?: language,
)

/**
 * [buildHijriMonthWidgetData] driven straight from [options], so a renderer cannot forget to
 * thread one of the seven values through and end up disagreeing with the settings screen.
 *
 * Reading direction follows [WidgetOptions.language]. A renderer that must additionally
 * compensate for its platform mirroring (Glance rows are plain horizontal `LinearLayout`s the
 * platform already flips on an RTL device) uses the [rightToLeft] overload instead of adjusting
 * the options, so the stored value always means "the language I chose".
 */
fun buildHijriMonthWidgetData(
    hijriYear: Int,
    hijriMonth: Int,
    options: WidgetOptions,
    weekendDays: Set<WeekDay> = WeekDay.WEEKEND_DAYS,
): HijriMonthWidgetData? = buildHijriMonthWidgetData(
    hijriYear = hijriYear,
    hijriMonth = hijriMonth,
    options = options,
    weekendDays = weekendDays,
    rightToLeft = options.language.isRtl,
)

/** As above, with an explicit reading direction for platforms that mirror rows themselves. */
fun buildHijriMonthWidgetData(
    hijriYear: Int,
    hijriMonth: Int,
    options: WidgetOptions,
    weekendDays: Set<WeekDay>,
    rightToLeft: Boolean,
): HijriMonthWidgetData? = buildHijriMonthWidgetData(
    hijriYear = hijriYear,
    hijriMonth = hijriMonth,
    adjustmentDays = options.adjustmentDays,
    firstDayOfWeekIndex = options.firstDayOfWeekIndex,
    numeralStyle = options.numeralStyle,
    pakistan = options.source.pakistan,
    weekendDays = weekendDays,
    rightToLeft = rightToLeft,
    localizedHijriMonthNames = options.localizedHijriMonthNames,
    localizedGregorianMonthNames = options.localizedGregorianMonthNames,
    localizedWeekdayNames = options.localizedWeekdayNames,
)

/** [todayHijriWidgetData] driven straight from [options]. See the grid overload for why. */
fun todayHijriWidgetData(
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
)
