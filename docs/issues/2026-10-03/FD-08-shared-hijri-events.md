# FD-08: There is no notion of a notable Hijri date anywhere

**Issue:** #13
**Severity:** Medium
**Blocks:** FD-09
**Blocked by:** None (can start immediately)
**Module:** `calendar-core`
**Status:** Ready

---

## Problem

The library has no concept of an event, holiday or notable date. Not in `CalendarDay`, not in
`CalendarMonth`, not in `WidgetOptions`, not in the widget models. A repo-wide search for
`eid|holiday|event|milestone|ashura|muwid|muharram` across every Kotlin, Swift, XML and Markdown
file returns 25 lines, and every one of them is something else — month names like `Muharram` and
`Rabi' al-awwal`, KDoc prose about verified Ruet-e-Hilal month lengths, and Glance's `UpdateGlanceState`
event.

The request is for **popular events on Hijri dates**. This ticket builds the dataset and shows it
in-app. FD-09 puts it on the widget.

## Where it has to live

`calendar-core`. Not `calendar-widget-data`, because the in-app calendar needs it too, and
`calendar-widget-data` depends on `calendar-core`, not the other way round. Putting it in the widget
module would make the in-app calendar depend on Glance, which is the exact inversion the module
layout exists to prevent.

`calendar-core` is also coroutine-free and dependency-light (it re-exports `kotlinx-serialization-core`,
`kotlinx-collections-immutable` and `hijrah-datetime` as `api`). A hand-written table is fine there;
it does not need a resource file, a database or a network call.

## The limitation, stated before it is built

**These events match on the Hijri calendar, and Hijri dates are not what most South Asian Muslims
observe for them.**

In Pakistan and India, Eid al-Fitr, Eid al-Adha and Ashura are fixed to **Gregorian** dates — Eid
al-Adha in 2026 is the 27th of May, whatever 10 Dhu al-Hijjah 1447 happens to calculate to. Umm al-Qura
puts Eid al-Fitr roughly ten days earlier and Eid al-Adha around twelve days earlier. Every app that
shows a Hijri-calendar Eid date without a user-configured Gregorian override is **wrong for its
audience**, and has been since day one.

So the honest scope of this ticket is the observances that genuinely are Hijri-fixed:

| Hijri date | Observance | Notes |
|---|---|---|
| 1 Muharram | Islamic New Year | Hijri-fixed. Year-length dependent, correctly. |
| 10 Muharram | Ashura | **Gregorian-fixed in SA/IN practice.** Ships with a caveat. |
| 12 Rabi' al-Awwal | Mawlid | Gregorian-fixed in practice; sometimes 11th. |
| 27 Rajab | Isra and Mi'raj | Hijri-fixed. |
| 15 Sha'ban | Shab-e-Barat | Hijri-fixed. |
| 1 Shawwal | Eid al-Fitr | Gregorian-fixed in practice. |
| 9 Dhu al-Hijjah | Day of Arafah | Gregorian-fixed in practice. |
| 10 Dhu al-Hijjah | Eid al-Adha | Gregorian-fixed in practice. |

**Record this in the ticket, not in a comment nobody reads.** When the first user reports "your Eid
date is wrong", the answer should be a link to a paragraph that predicted it. A `HijriEvent` carries
a `isGregorianFixedInPractice: Boolean` so the renderer can say so, and so the eventual fix
(user-supplied Gregorian overrides) has a place to attach to.

## The shape

```kotlin
public data class HijriEvent(
    public val month: Int,
    public val day: Int,
    public val key: String,
    public val nameEn: String,
    public val nameUr: String,
    public val isGregorianFixedInPractice: Boolean = false,
)

public object HijriEvents {
    public fun forDate(hijriYear: Int, hijriMonth: Int, hijriDay: Int): HijriEvent?
    public fun forMonth(hijriMonth: Int): List<HijriEvent>   // for a month-level marker
}
```

A `@Serializable` data class plus a `public object` holding an immutable list, consistent with the
rest of the module. No `expect`/`actual`, no platform resources — the English and Urdu names are
fields, because a widget's language is a `WidgetOptions` field and not a resource configuration
(WG-12), and the in-app Urdu bundle needs the same two strings.

## Matching in three calendar spaces

`CalendarDay` carries up to three Hijri coordinates: `hijrahDate`, `pakistanDate`, `observedDate`.
`selectDay` routes Pakistan → observed → Umm al-Qura. An event lookup must do the same, or a
Pakistan-calendar user taps 10 Muharram and gets Ashura for the wrong coordinate.

Read the coordinate off the same cell the selection router read, in the same order. If a cell has
none of them, there is no event — and that must be a `null`, not a fallback to
`HijriDate(…, HijriCalendarCalculation.CALCULATION)`, which is the kind of silent substitution that
makes a bug untraceable.

## Acceptance criteria

- [ ] `HijriEvent` and `HijriEvents` live in `calendar-core`, coroutine-free, with `explicitApi()`.
- [ ] All eight observances above are present, with English and Urdu names.
- [ ] `isGregorianFixedInPractice` is set on the four that are Gregorian-fixed in South Asian
        practice, and the KDoc explains why the Hijri date is still shipped.
- [ ] `forDate` matches on the cell's Hijri coordinate in **Pakistan → observed → Umm al-Qura**
        order, and returns `null` when a cell carries none — it does not substitute a default.
- [ ] A roundtrip test asserts every observance resolves on a known Hijri date, in all three calendar
        spaces where it exists.
- [ ] The in-app calendar shows the selected day's event, localized by `HijriCalendarLabels`
        (a new label field with a default reproducing current output), in both display modes.
- [ ] `DateDisplayMode.HIJRI_ONLY` still shows the event — an event is not a Gregorian figure, and
        hiding it behind the display mode would make it unreachable in the default mode.
- [ ] The sample's selected-date card shows the event name.
- [ ] No `apiCheck` break beyond the additive dump; `CHANGELOG.md` records it.

## Deliberately out of scope

- **User-defined events.** A store, an editor, and merge semantics. Its own ticket, later.
- **Gregorian overrides** for the four Gregorian-fixed observances. This is the correct fix for the
  limitation above and it deserves its own design, not a half-built version inside this one.
- **Widget rendering.** That is FD-09, blocked by this.