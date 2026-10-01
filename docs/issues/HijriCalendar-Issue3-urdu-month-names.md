# Issue 3: No way to localize month names (e.g. Urdu) — header shows English only

**Status:** Resolved — shipped as [UI-10](UI-10-localization-completion.md), plus the proposal's own steps.
**Filed against:** `1.0.0-alpha03`

**Library:** https://github.com/muazdev26/HijriCalendar
**Library version:** `1.0.0-alpha03`

---

## Current behavior (verified from source)

All user-visible text in the library is hard-coded English; there is **no API** to pass translations:

| Text | Source | Where |
|---|---|---|
| Hijri month name | `CalendarMonth.monthName` → `yearMonth.month.name` (English, from HijrahDateTime) | `HijriCalendarHeader.kt` |
| Gregorian month range under header | `CalendarMonth.gregorianMonthRange` → `month.name` / `"Month - Month year"` strings built inline | `CalendarMonth.kt`, rendered in `HijriCalendarHeader.kt` |
| Weekday labels | `WeekDay.shortName` via `generateDayOfWeekLabels()` | `HijriCalendarGrid.kt` |
| Accessibility descriptions | `"Day N"`, `"Previous month"`, `"Next month"` | `HijriCalendarDayCell.kt`, `HijriCalendarHeader.kt` |

Day cells show only numbers, so they are fine. Consumers cannot fix the header/labels from outside because `HijriCalendarHeader` is `internal` to module layout and no formatter parameters exist. The existing `dayContent` slot only covers day cells.

---

## The Prompt

> Add localization support to the HijriCalendar library so consumer apps can display month and weekday names in their own language (Urdu in my case), without forking.
>
> **Proposed API (add to `calendar-ui`, defaults keep current English behavior):**
>
> ```kotlin
> @Immutable
> data class HijriCalendarLabels(
>     val hijriMonthName: (year: Int, month: Int) -> String =
>         { _, m -> defaultHijriMonthNames[m - 1] },          // current English names
>     val gregorianMonthName: (monthNumber: Int) -> String =
>         { m -> defaultGregorianMonthNames[m - 1] },
>     val weekdayShortName: (weekDay: WeekDay) -> String = { it.shortName },
>     val previousMonthContentDescription: String = "Previous month",
>     val nextMonthContentDescription: String = "Next month",
> )
>
> @Composable fun HijriCalendarDefaults.labels(labels: HijriCalendarLabels = HijriCalendarLabels()): HijriCalendarLabels
> ```
>
> Then:
>
> 1. Add a `labels: HijriCalendarLabels = HijriCalendarDefaults.labels()` parameter to `HijriCalendar` and thread it into `HijriCalendarHeader` and `DayOfWeekLabels`.
> 2. Replace `calendarMonth.monthName` usage with `labels.hijriMonthName(calendarMonth.year, calendarMonth.month.number)`.
> 3. Build `gregorianMonthRange` through `labels.gregorianMonthName(...)` — move that formatting out of `CalendarMonth` (core) into the UI layer so core stays UI-free.
> 4. Wire `previousMonthContentDescription` / `nextMonthContentDescription` through instead of the current default parameters.
> 5. Keep everything backward-compatible: all new parameters have defaults equal to today's output.
> 6. In the sample app, demonstrate Urdu labels:
>
>    ```kotlin
>    val urduLabels = HijriCalendarLabels(
>        hijriMonthName = { _, m ->
>            listOf("محرم","صفر","ربیع الاول","ربیع الثانی","جمادی الاول","جمادی الثانی",
>                   "رجب","شعبان","رمضان","شوال","ذی القعدہ","ذی الحجہ")[m - 1]
>        },
>        gregorianMonthName = { m ->
>            listOf("جنوری","فروری","مارچ","اپریل","مئی","جون",
>                   "جولائی","اگست","ستمبر","اکتوبر","نومبر","دسمبر")[m - 1]
>        },
>    )
>    ```
> 7. Add preview composables covering RTL + Urdu labels, then release as `1.0.0-alpha04`.

---

## Consumer-side note

Until this ships, the app already renders Urdu month names in its own "selected date" card (`Res.string.hijri_1..12`) — only the calendar's internal header/weekday labels remain English.

## Resolution

**Shipped.** `HijriCalendarLabels` exists with the exact shape proposed here — same five fields,
same defaults, all-defaults reproducing the previous English output — and `labels` is threaded
through `HijriCalendar` → header → weekday row → day cell. Steps 1 through 5 of the proposal are
done as written.

**Three fields the proposal did not have were added, and they are the interesting part.** The first
version of this API was in the `UI-` review and was found to be *started, not finished*: the header
line was still concatenated inside the component, so the year rendered in Western digits even in the
library's own Urdu preview, and the Gregorian range's separator and ordering were hardcoded. Neither
is reachable through a month-name lambda, so both were fixed by widening the seam rather than by
documenting a limitation:

- **`headerTitle(monthName, year) -> String`** — the caller returns the **whole** title line, so
  ordering and numeral system are the caller's. Returning only a month name forces the year back
  into Western digits, which is the bug.
- **`gregorianMonthRangeLabel(first, last) -> String`** — takes `LocalDate`s, not month numbers, so
  a caller can order and punctuate the range across a year boundary.
- **`monthNamesFor` / `CalendarNames`** — the built-in English lists moved into `calendar-core` as
  `CalendarNames`, which `calendar-widget-data` also reads. An in-app header and a placed home-screen
  widget now cannot disagree about what March is called; before this they each held their own list.

**A caveat on the `@Immutable` this proposal asked for.** It is present, but it is *not* sound:
`HijriCalendarLabels` holds function fields, which the Compose compiler rates unstable, so the
annotation overrides inference in the wrong direction. The [UI-11](UI-11-stability-honesty.md) review
proposed removing it and **that ticket was withdrawn** — the premise was disproven in bytecode. The
annotation stands, and the KDoc documents the two real hazards instead: build the object once and
hold it (it is a `remember` key at three sites, and the lambdas compare by reference), and beware a
lambda that captures changing state, which keeps a stable identity while going stale.

**Step 6 (Urdu labels in the sample) and step 7 (RTL + Urdu previews) shipped too.** Note for anyone
following the proposal's sample snippet: the Urdu strings are the consumer's to supply — the library
deliberately ships English defaults only.
