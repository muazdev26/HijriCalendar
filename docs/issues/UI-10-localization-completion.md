# Issue 10: Header text and accessibility strings are not localizable

**Severity:** Medium
**Blocks:** —
**Blocked by:** —
**Module:** `calendar-ui`

---

## Problem

The module has a localization seam (`HijriCalendarLabels`) and two shipped previews proving it
works. Three gaps remain, all in the header.

## Gap 1 — the header line is composed in code

```kotlin
// calendar-ui/.../HijriCalendarHeader.kt:69
text = "$monthName $year",
```

The month name and year are concatenated in the component. A locale cannot reorder them, and the
year is rendered with the platform's default digits regardless of `useArabicIndicNumerals`. So
`HijriCalendarUrduRtlPreview` — the module's own proof that Urdu works — shows Arabic-Indic day
numbers under a **Western-numeral** year. That is the module's most visible localisation
inconsistency, in the module's own demo.

`HijriCalendarLabels.hijriMonthName(year, month)` already receives the year
(`HijriCalendarLabels.kt:20`) and **both shipped defaults ignore it** (`{ _, month -> … }`). The
parameter exists for exactly this and is unused.

## Gap 2 — the navigation strings are hardcoded English, in two places

```kotlin
// HijriCalendarHeader.kt:33-34        ← parameter defaults
previousMonthContentDescription: String = "Previous month",
nextMonthContentDescription: String = "Next month",

// HijriCalendarLabels.kt:29-30        ← field defaults
val previousMonthContentDescription: String = "Previous month",
val nextMonthContentDescription: String = "Next month",
```

The same literals live in two public surfaces. A consumer who localizes month and weekday names —
the entire documented use of `HijriCalendarLabels` — still ships English screen-reader strings,
because they set `hijriMonthName` and nothing else. `HijriCalendar.kt:86-87` passes the *labels*
values to the header, so `HijriCalendar` is fine; a consumer calling `HijriCalendarHeader` directly
gets the literals and has no obvious reason to know `HijriCalendarLabels` also has them.

## Gap 3 — the Gregorian range label has no seam

```kotlin
// calendar-ui/.../HijriCalendar.kt:153-163
internal fun sameMonthRangeLabel(first: LocalDate, last: LocalDate, labels: HijriCalendarLabels): String =
    when {
        first.month == last.month && first.year == last.year ->
            "${labels.gregorianMonthName(first.month.ordinal + 1)} ${first.year}"
        ...
```

`gregorianMonthName` is a hook, so the month names are localizable — but the `" - "` separator, the
`year` rendering, and the whole three-branch shape are hardcoded. A locale that wants a different
range separator, or a different order, cannot get one.

## Proposed change

**1. Move the header line into `HijriCalendarLabels`.**

```kotlin
/** Formats the header's title line. Return the whole string so order and digits are the caller's. */
val headerTitle: (monthName: String, year: Int) -> String = { m, y -> "$m $y" }
```

Then `HijriCalendarHeader` takes `title: String` (or keeps `monthName`/`year` for compatibility and
delegates to `labels.headerTitle`). The default preserves current output; the Urdu default supplies
`"$monthName $yearArabicIndic"` and honours RTL order. `useArabicIndicNumerals` should reach the
header — right now it only reaches cells (`HijriCalendar.kt:97`).

**2. Delete the duplicated literals.** Make `HijriCalendarHeader`'s two content-description
parameters **nullable with no string default**, and source them from `labels` when null — or better,
give `HijriCalendarHeader` a `labels: HijriCalendarLabels` parameter and remove the two literals
entirely. `AGENTS.md` already recorded the failure mode this repeats for the widget schema
(`WidgetOptionsJson`: "enums by name, never by ordinal"). Two homes for one string will drift the
same way.

**3. Add a `gregorianMonthRangeLabel` hook to `HijriCalendarLabels`** taking `(first, last)` and
returning the whole range string, so the separator and ordering are the consumer's. Keep
`sameMonthRangeLabel` as the default implementation and keep its four existing tests.

**4. Use `YearMonth`-style formatting, not string concatenation,** so `year` can be rendered
through `DateTimeFormatter` in the caller's locale/numeral system.

## Done when

- [ ] `rg '"Previous month"|"Next month"' calendar-ui/src/commonMain` returns at most one hit
- [ ] A test asserts the Urdu header line renders the year in Arabic-Indic digits
- [ ] `HijriCalendarLabels.headerTitle` and `.gregorianMonthRangeLabel` exist and are covered
- [ ] `HijriCalendarHeader` reads its a11y strings from `labels`, not from parameter literals
- [ ] `HijriCalendarUrduRtlPreview` (or its replacement) shows no Western numerals

## Notes

- `HijriCalendarLabels`'s own KDoc (`HijriCalendarLabels.kt:9-16`) says *"All header and weekday text
  can be localized via `labels`."* That claim is currently **false for the year, the header line and
  the navigation strings.** Fixing this makes the documentation true rather than aspirational —
  worth saying in the changelog.
- `CalendarNames` / `UrduCalendarNames` live in `calendar-core` today and are shared with
  `calendar-widget-data` deliberately ("so an in-app header and a placed widget cannot show
  different month names", `HijriCalendarLabels.kt:14-15`). Keep that property; this ticket adds
  *hooks*, it does not move the data.
- The two a11y strings also appear in `sample-shared`'s device test as literals
  (`CalendarScreenDeviceUiTest.kt`). Fixing this breaks those assertions — update them to read
  from `HijriCalendarLabels` so they cannot drift again.