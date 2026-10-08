package com.muazdev.hijricalendar.core

import kotlinx.serialization.Serializable

/**
 * One notable date on the Hijri calendar, with its name in the two languages the library ships.
 *
 * ## ⚠️ Six of these nine are wrong for most South Asian Muslims, and deliberately ship anyway
 *
 * In Pakistan and India, **Eid al-Fitr, Eid al-Adha and both days of Ashura are fixed to a Gregorian
 * date**, not to a Hijri one. Eid al-Adha in 2026 is the 27th of May whatever 10 Dhu al-Hijjah 1447
 * calculates to.
 * Umm al-Qura puts Eid al-Fitr roughly ten days earlier and Eid al-Adha around twelve days earlier.
 *
 * So a Hijri-calendar Eid date, with no user-configured Gregorian override, is **wrong for its
 * audience**. That is not a bug to be discovered later — it is the design's central limitation, and it
 * is recorded here rather than in a comment nobody reads, so that when the first report arrives ("your
 * Eid date is wrong") the answer is a link to the paragraph that predicted it.
 *
 * [isGregorianFixedInPractice] marks the affected observances so a renderer can say so, and so the
 * eventual fix — user-supplied Gregorian overrides — has somewhere to attach.
 *
 * ## What is genuinely Hijri-fixed
 *
 * [Ashura][ashura] and its eve, the 9th, are in both lists on purpose. They are fixed to a Hijri date
 * in Shia practice and to a Gregorian one in Sunni practice in the region, which is the sharpest version
 * of the problem: the same user, the same library, two answers. The `isGregorianFixedInPractice` flag is
 * the honest expression of that.
 *
 * Islamic New Year, Isra and Mi'raj and Shab-e-Barat are Hijri-fixed everywhere. They are the reason
 * this table exists at all.
 *
 * @property month the Hijri month, 1-12.
 * @property day the Hijri day of [month].
 * @property key a stable identifier, for a host that wants to customise one entry's rendering without
 *   matching on its name — the names are localized and a host may override them.
 * @property isGregorianFixedInPractice `true` where observers in the region follow a Gregorian date
 *   rather than this one. See the class KDoc.
 */
@Serializable
public data class HijriEvent(
    public val month: Int,
    public val day: Int,
    public val key: String,
    public val nameEn: String,
    public val nameUr: String,
    public val isGregorianFixedInPractice: Boolean = false,
) {
    /**
     * The name in [language], falling back to English.
     *
     * A table with a missing translation should render *something*; the fallback is English rather
     * than `null` because every caller of a name is a renderer, and a renderer with `null` either
     * crashes or draws a blank. An untranslated entry is visible as English, which is discoverable;
     * a blank is not.
     */
    public fun name(language: HijriEventLanguage): String = when (language) {
        HijriEventLanguage.ENGLISH -> nameEn
        HijriEventLanguage.URDU -> nameUr.ifBlank { nameEn }
    }
}

/**
 * Which language an [HijriEvent]'s name is wanted in.
 *
 * A tiny enum rather than a `WidgetLanguage`, because events are a **core** concept and
 * `calendar-core` must not depend on the widget schema — that dependency runs the other way. It is also
 * deliberately not a bare `Boolean`: the third language would otherwise be a flag flipping.
 *
 * Both supported languages ship names for every observance, so this is about not growing a boolean
 * pair as the table does.
 */
public enum class HijriEventLanguage {
    ENGLISH,
    URDU,
}

/**
 * The curated table of notable Hijri dates, and the lookup a renderer needs.
 *
 * **Static and hand-written on purpose.** `calendar-core` is coroutine-free and dependency-light, and
 * this is a fixed table of nine entries — a resource file, a database or a network fetch would each
 * add a dependency and a failure mode to answer a question with nine strings in it. `HijrahDate` is
 * already serialised by `calendar-core` (see [ObservedHijriDate]), so [HijriEvent] follows it.
 *
 * Matching is on the **Hijri coordinate only**, and the Gregorian-fixed observances are marked
 * rather than corrected; see [HijriEvent].
 */
public object HijriEvents {

    /**
     * Every observance, in calendar order.
     *
     * A list rather than a map keyed by `"month-day"`: a map invites string keys into the core, and a
     * month/day pair is two small integers. Lookups are [forDate] and [forMonth].
     */
    public val all: List<HijriEvent> = listOf(
        HijriEvent(
            month = 1,
            day = 1,
            key = "islamic_new_year",
            nameEn = "Islamic New Year",
            nameUr = "اسلامی نیا سال",
        ),
        HijriEvent(
            month = 1,
            day = 9,
            key = "ashura_ninth",
            nameEn = "Ashura (9th Muharram)",
            nameUr = "عاشورہ (9 محرم)",
            // Same flag as the 10th: where the 10th is pinned to a Gregorian date, the 9th is
            // observed with it.
            isGregorianFixedInPractice = true,
        ),
        HijriEvent(
            month = 1,
            day = 10,
            key = "ashura",
            nameEn = "Ashura",
            nameUr = "عاشورہ",
            // Sunni practice in South Asia fixes Ashura to a Gregorian date; Shia practice follows the
            // Hijri one. Both audiences exist in the same country.
            isGregorianFixedInPractice = true,
        ),
        HijriEvent(
            month = 3,
            day = 12,
            key = "mawlid",
            nameEn = "Prophet's Birthday",
            nameUr = "عید النبی",
            isGregorianFixedInPractice = true,
        ),
        HijriEvent(
            month = 7,
            day = 27,
            key = "isra_and_miraj",
            nameEn = "Isra and Mi'raj",
            nameUr = "اسراء و معراج",
        ),
        HijriEvent(
            month = 8,
            day = 15,
            key = "shab_e_barat",
            nameEn = "Shab-e-Barat",
            nameUr = "شب برات",
        ),
        HijriEvent(
            // Shawwal is month **10**: 9 is Ramadan. This was wrong in the first version of the table,
            // which put Eid al-Fitr on the 1st of Ramadan — and the table is looked up by `(month, day)`
            // alone, so the error was invisible to every test that did not compare against the month
            // *names*. `HijriEventsTest` now asserts each entry against `englishHijriMonths`.
            month = 10,
            day = 1,
            key = "eid_al_fitr",
            nameEn = "Eid al-Fitr",
            nameUr = "عید الفطر",
            isGregorianFixedInPractice = true,
        ),
        HijriEvent(
            month = 12,
            day = 9,
            key = "day_of_arafah",
            nameEn = "Day of Arafah",
            nameUr = "يوم عرفہ",
            isGregorianFixedInPractice = true,
        ),
        HijriEvent(
            month = 12,
            day = 10,
            key = "eid_al_adha",
            nameEn = "Eid al-Adha",
            nameUr = "عید الأضحیٰ",
            isGregorianFixedInPractice = true,
        ),
    )

    /**
     * The observance on a Hijri date, or `null` when the day carries none.
     *
     * Matching on `(month, day)` only — **not** on the year. That is what makes the table correct across
     * centuries and cheap enough to call from a composable: an observance recurs annually, and the Hijri
     * year is not needed to identify which one.
     *
     * One event per day, deliberately. Ashura and the 11th of Muharram are the same observance observed
     * on different days by different communities; a table that tried to carry both would need a set, and
     * every caller would then have to choose. See the class KDoc.
     */
    public fun forDate(month: Int, day: Int): HijriEvent? =
        all.firstOrNull { it.month == month && it.day == day }

    /**
     * The Hijri month each observance belongs in, by key, 1-12.
     *
     * **A second listing of the same fact on purpose.** [all] carries a month *index*, and an index
     * means nothing on its own — which is exactly how Eid al-Fitr shipped on month 9, Ramadan: every
     * test read the index back out of the table and agreed with it. This map is written against the
     * observance's **name**, and `HijriEventsTest` asserts the two agree, so a wrong index has to be
     * written down twice before it can ship.
     *
     * Keys are [all]'s; the test asserts the sets match, so a new entry cannot slip in unchecked.
     */
    public val coordinateByKey: Map<String, Int> = mapOf(
        "islamic_new_year" to 1, // Muharram
        "ashura_ninth" to 1, // Muharram
        "ashura" to 1, // Muharram
        "mawlid" to 3, // Rabi' al-awwal
        "isra_and_miraj" to 7, // Rajab
        "shab_e_barat" to 8, // Sha'ban
        "eid_al_fitr" to 10, // Shawwal — **not** 9, which is Ramadan
        "day_of_arafah" to 12, // Dhu al-Hijjah
        "eid_al_adha" to 12, // Dhu al-Hijjah
    )

    /**
     * The observances falling inside a Hijri month, in day order.
     *
     * For a month-level marker — a dot on the month's name in a picker, say — rather than a per-day one.
     */
    public fun forMonth(month: Int): List<HijriEvent> =
        all.filter { it.month == month }.sortedBy { it.day }

    /**
     * Whether any day of [month] carries an observance.
     *
     * [forMonth] would answer it, but a month-level marker that does not care *which* day should not
     * have to build a list to find out.
     */
    public fun hasAnyIn(month: Int): Boolean = all.any { it.month == month }
}
