package com.muazdev.hijricalendar.core

import kotlinx.serialization.Serializable

/**
 * A date on the **Gregorian** calendar that Pakistan observes nationally, with its name in the two
 * languages the library ships.
 *
 * ## Why this is not a [HijriEvent]
 *
 * [HijriEvent] is anchored to a Hijri `(month, day)` and recurs on the Islamic calendar. These are
 * the other kind: the state's own calendar, fixed to a Gregorian `(month, day)` by statute or by a
 * decision of the government. Independence Day is 14 August every year — not 14 August on some Hijri
 * reckoning — and a table that tried to express it as a Hijri coordinate would move it eleven days
 * earlier every year.
 *
 * The two tables therefore look up different coordinates and are never interchangeable. They share
 * [HijriEventLanguage] only, because a name still needs a language.
 *
 * ## Why it is a static table and not a fetch
 *
 * The same reasoning as [HijriEvents]: `calendar-core` is coroutine-free and dependency-light, and
 * this is a fixed list of eight national days. A network fetch, a database or a resource file would
 * each add a dependency and a failure mode to answer a question whose answer changes only when a
 * government adds a holiday. The list is compiled in, versioned with the library, and extendable by a
 * host that wants its own observances (see [PakistanEvents.all]).
 *
 * Sources for the entries: the Government of Pakistan's published public-holiday list (Cabinet
 * Division) and the national commemoration days announced by the Ministry of Information. Every entry
 * is a Gregorian-fixed day that recurs annually; no year is stored, because the table is valid for
 * every year.
 *
 * ## What it deliberately does not contain
 *
 * - **Eid al-Fitr, Eid al-Adha, Ashura, Eid Milad-un-Nabi.** Those are Islamic observances and live in
 *   [HijriEvents]. Pakistan *announces* them on a Gregorian date each year, but that announcement is a
 *   moon-sighting decision, not a fixed calendar rule — [HijriEvent.isGregorianFixedInPractice]
 *   records the gap, and `PakistanHijriCalendar` is the source that actually answers it.
 * - **Provincial holidays** (e.g. Sindh's cultural days). A library-wide "Pakistan" table that picked
 *   one province's days would be wrong in the other three.
 * - **One-off days** (an election, a census). A table that never expires is the only kind this module
 *   can ship.
 *
 * @property month the **Gregorian** month, 1-12.
 * @property day the Gregorian day of [month].
 * @property key a stable identifier, for a host that wants to customise one entry's rendering without
 *   matching on its name — exactly as [HijriEvent.key] exists for.
 */
@Serializable
public data class PakistanEvent(
    public val month: Int,
    public val day: Int,
    override val key: String,
    override val nameEn: String,
    override val nameUr: String,
) : CalendarEvent {
    /**
     * The name in [language], falling back to English.
     *
     * The same fallback rule [HijriEvent.name] uses, and for the same reason: every caller of a name
     * is a renderer, and a renderer handed `null` either crashes or draws a blank. English is
     * discoverable as an untranslated entry; a blank is not.
     */
    override fun name(language: HijriEventLanguage): String = when (language) {
        HijriEventLanguage.ENGLISH -> nameEn
        HijriEventLanguage.URDU -> nameUr.ifBlank { nameEn }
    }
}

/**
 * The curated table of Pakistani national days, and the Gregorian lookup a renderer needs.
 *
 * **Gregorian-fixed, and that is the whole point.** [forDate] takes a Gregorian `(month, day)`, not a
 * Hijri one. `CalendarDay.pakistanEvent` is where the two are joined: it reads the cell's real-world
 * Gregorian day and asks this table.
 *
 * A host with its own observances can render them alongside these — this object is a default table,
 * not a closed registry. Nothing in the library reads [`all`][all] except [forDate] and [forMonth], so
 * a host that wants a different list builds its own lookup with the same shape.
 */
public object PakistanEvents {

    /**
     * Every national day, in Gregorian calendar order.
     *
     * Eight entries, all recurring annually. [coordinateByKey] pins each one's month a second time so a
     * wrong index has to be written down twice before it can ship, exactly as [HijriEvents] does it.
     */
    public val all: List<PakistanEvent> = listOf(
        PakistanEvent(
            month = 2,
            day = 5,
            key = "kashmir_solidarity_day",
            nameEn = "Kashmir Solidarity Day",
            nameUr = "یوم یکجہتی کشمیر",
        ),
        PakistanEvent(
            month = 3,
            day = 23,
            key = "pakistan_day",
            nameEn = "Pakistan Day",
            nameUr = "یوم پاکستان",
        ),
        PakistanEvent(
            month = 5,
            day = 1,
            key = "labour_day",
            nameEn = "Labour Day",
            nameUr = "یوم مزدور",
        ),
        PakistanEvent(
            // The day of the 1998 nuclear tests, a national commemoration since 1999.
            month = 5,
            day = 28,
            key = "youm_e_takbir",
            nameEn = "Youm-e-Takbir",
            nameUr = "یوم تکبیر",
        ),
        PakistanEvent(
            month = 8,
            day = 14,
            key = "independence_day",
            nameEn = "Independence Day",
            nameUr = "یوم آزادی",
        ),
        PakistanEvent(
            // Commemorates the defence of 6 September 1965.
            month = 9,
            day = 6,
            key = "defence_day",
            nameEn = "Defence Day",
            nameUr = "یوم دفاع",
        ),
        PakistanEvent(
            // Allama Muhammad Iqbal's birthday.
            month = 11,
            day = 9,
            key = "iqbal_day",
            nameEn = "Iqbal Day",
            nameUr = "یوم اقبال",
        ),
        PakistanEvent(
            // Quaid-e-Azam Muhammad Ali Jinnah's birthday.
            month = 12,
            day = 25,
            key = "quaid_e_azam_day",
            nameEn = "Quaid-e-Azam Day",
            nameUr = "یوم قائد اعظم",
        ),
    )

    /**
     * The Gregorian day each national day belongs to, by key.
     *
     * **A second listing of the same fact on purpose**, mirroring [HijriEvents.coordinateByKey]: an
     * index read back out of [all] agrees with itself even when it is wrong. `PakistanEventsTest`
     * asserts this map and [all] agree, so a wrong month has to be written down twice.
     */
    public val coordinateByKey: Map<String, Int> = mapOf(
        "kashmir_solidarity_day" to 2,
        "pakistan_day" to 3,
        "labour_day" to 5,
        "youm_e_takbir" to 5,
        "independence_day" to 8,
        "defence_day" to 9,
        "iqbal_day" to 11,
        "quaid_e_azam_day" to 12,
    )

    /**
     * The national day on a Gregorian date, or `null` when the day carries none.
     *
     * Matching on `(month, day)` only — never on the year — because the table is the same every year.
     * One entry per day: no two national days fall on the same Gregorian date.
     */
    public fun forDate(month: Int, day: Int): PakistanEvent? =
        all.firstOrNull { it.month == month && it.day == day }

    /** The national days inside a Gregorian month, in day order. */
    public fun forMonth(month: Int): List<PakistanEvent> =
        all.filter { it.month == month }.sortedBy { it.day }

    /** Whether any day of the Gregorian [month] carries a national day. */
    public fun hasAnyIn(month: Int): Boolean = all.any { it.month == month }
}
