# Issue 3: No way to localize month names (e.g. Urdu) — header shows English only

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
