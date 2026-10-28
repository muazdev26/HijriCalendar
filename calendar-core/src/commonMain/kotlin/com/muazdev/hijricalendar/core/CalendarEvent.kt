package com.muazdev.hijricalendar.core

/**
 * Something a calendar cell can be marked and named for.
 *
 * The two implementations are the two *kinds* of notable date this library knows:
 *
 * - [HijriEvent] — fixed to a Hijri `(month, day)` and recurring on the Islamic calendar.
 * - [PakistanEvent] — fixed to a Gregorian `(month, day)`, the Pakistani state's own calendar.
 *
 * The interface exists so a renderer can hold **one ordered list of the marks on a month's cells**
 * without asking which coordinate a given date was fixed in. That is what the in-app event banner
 * needs: on a Pakistan-calendar month, 14 August and Ashura are two statements of the same kind — "this
 * day is notable" — even though one recurs on the Gregorian calendar and the other on the Hijri one.
 *
 * It is deliberately **narrow**: a stable [key], the two ship-with names, and the name lookup. No
 * `month`/`day`, because the two implementations' coordinates are not comparable — that is the whole
 * distinction the interface is papering over. A caller that needs the coordinate already knows which
 * concrete type it is holding.
 *
 * Sealed rather than open: the library ships the two calendar systems it supports, and a host adding a
 * third would need its own lookup and renderer anyway — the interface would not carry enough to help.
 */
public sealed interface CalendarEvent {
    /** A stable identifier, so a host can customise one entry without matching on its localized name. */
    public val key: String

    /** The English name. Never blank. */
    public val nameEn: String

    /** The Urdu name; blank when an entry has no translation, in which case [name] falls back. */
    public val nameUr: String

    /** The name in [language], falling back to English. */
    public fun name(language: HijriEventLanguage): String
}
