# Issue 10: Header text and accessibility strings are not localizable

**Severity:** Medium
**Blocks:** —
**Blocked by:** —
**Module:** `calendar-ui`
**Status:** Shipped — see "Shipped" below.

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

- [x] `rg '"Previous month"|"Next month"' calendar-ui/src/commonMain` returns at most one hit
- [x] A test asserts the Urdu header line renders the year in Arabic-Indic digits
- [x] `HijriCalendarLabels.headerTitle` and `.gregorianMonthRangeLabel` exist and are covered
- [x] `HijriCalendarHeader` reads its a11y strings from `labels`, not from parameter literals
- [x] `HijriCalendarUrduRtlPreview` (or its replacement) shows no Western numerals

## Shipped

Three gaps closed, and the label object's KDoc claim — *"All header and weekday text can be localized
via `labels`"* — is now true rather than aspirational.

- **`HijriCalendarLabels.headerTitle: (monthName, year) -> String`.** The header used to join the two
  with a space itself, so a locale could neither reorder them nor write the year in its own digits.
  The module's own Urdu preview demonstrated the consequence: Arabic-Indic **day figures** under a
  **Western-numeral year**, in the module's own demo of its own feature. Returning the whole string
  puts both decisions with the caller. The default reproduces `"$monthName $year"` exactly, so an
  existing consumer sees no change.
- **`HijriCalendarLabels.gregorianMonthRangeLabel: (first, last) -> String`.** The `" - "` separator,
  the three-branch shape and the year rendering were hardcoded. The default delegates to a new
  internal `defaultGregorianMonthRangeLabel`, which is still a directly tested pure function — so the
  documented English output stays pinned while becoming overridable.
- **`HijriCalendarHeader` takes `title: String` and `labels`, and no longer has English parameter
  defaults.** `previousMonthContentDescription` / `nextMonthContentDescription` were parameter
  defaults in the header *and* field defaults on the labels — two homes for the same two strings,
  which is how they drift. The header now reads them from `labels`, so a direct caller gets the
  consumer's strings rather than English it never asked for.

### Tests: 8 new, and they reach the rendered header

The object-level tests are necessary but weak — a seam can exist and be ignored. So three of them
assert against the **rendered semantics tree**:

- `aCustomHeaderTitleIsRendered` — `headerTitle = { m, y -> "$y / $m" }` renders `1447 / Ramadan`.
- `aCustomRangeLabelIsRendered` — a custom range label appears in the header.
- `localizedNavigationDescriptionsReplaceTheEnglishDefaults` — Urdu descriptions render, **and the
  English literals are asserted absent**, so they are replaced rather than supplemented.
- `urduLabelsLocalizeTheWholeHeaderLine` — `"رمضان ١٤٤٧"`, the exact defect this ticket is about.

Plus `theDefaultHeaderTitleStaysWesternAndSpaceSeparated` and
`theDefaultRangeLabelMatchesTheDocumentedEnglishOutput` (across all three shapes), so the defaults
cannot drift silently.

### Two bugs found while writing the tests, both mine

1. **An off-by-one shifted every month by one and threw on December.** `gregorianMonthName` is
   **1-based** by contract while `CalendarNames.englishGregorianMonths` is 0-based, so indexing the
   list directly needed `month.ordinal`, not `ordinal + 1`. The symptom was a beautifully specific
   `expected:<Septem>ber> but was:<Octo>ber>` plus `ArrayIndexOutOfBoundsException: Index 12`. The
   comment in the function now says which is which.
2. **The Urdu test asserted the wrong thing first.** It claimed the *default* `headerTitle` would
   render Arabic-Indic digits — it does not, by design, and it should not. The whole point is that
   the consumer supplies the formatter. The test now supplies one and asserts what it produces.

### The sample's device test

`CalendarScreenDeviceUiTest` matched on `"Next month"` / `"Previous month"` as literals. The defaults
are unchanged, so it would have kept passing — but the ticket's point is that it *shouldn't* depend
on that, and it breaks the moment the default does. Both call sites now read
`HijriCalendarLabels().nextMonthContentDescription` / `…previousMonthContentDescription`, so they
cannot drift. (This file is instrumented and still not in CI; see UI-02's "Not done".)

## Notes



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