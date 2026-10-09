# FD-06: The widget header names one Gregorian month when the Hijri month spans two

**Issue:** #11
**Severity:** Low
**Blocks:** —
**Blocked by:** None (can start immediately)
**Module:** `calendar-widget-data`
**Status:** Ready

---

## Problem

A Hijri month is 29 or 30 days. A Gregorian month is 28–31. They do not divide evenly, so roughly
half of all Hijri months start in one Gregorian month and end in another. For those months the
widget header names only the first one:

```kotlin
// calendar-widget-data/src/commonMain/.../WidgetDataApi.kt:272-273
val gregorianMonthTitle =
    "${gregorianMonthNames[gregorianFirst.month.ordinal]} ${gregorianFirst.year}"
```

`gregorianFirst` is the real-world day the Hijri month's 1st falls on. So **Safar 1448 starting on
30 July 2026** renders a header reading `July 2026`, and the widget is showing 29 days of which not
one is in August. The widget says the month is July. It is wrong for a third of the calendar.

## The in-app calendar already solves this

Worth being precise, because the report reads as if neither side handles it. `calendar-ui` does:

```kotlin
// calendar-ui/.../HijriCalendarLabels.kt:94-107
internal fun defaultGregorianMonthRangeLabel(first: LocalDate, last: LocalDate): String =
    when {
        first.month == last.month && first.year == last.year -> "${monthName(first)} ${first.year}"
        first.year == last.year  -> "${monthName(first)} - ${monthName(last)} ${first.year}"
        else -> "${monthName(first)} ${first.year} - ${monthName(last)} ${last.year}"
    }
```

asserted verbatim in `HijriCalendarRangeLabelTest.kt`:

- `"September 2026"`
- `"September - October 2026"`
- `"December 2026 - January 2027"`

So the two surfaces **disagree**, by construction, for roughly half the months. That disagreement is
the real defect: the same library shows `September - October 2026` in the app and `September 2026`
on the home screen for the same month, and a user who checks one against the other concludes one is
broken. Both cannot be right.

`WidgetDataApi` already has `last` in hand — `GregorianMonthRange` carries `first` and `last`, and the
projection already reads both. The truncation is a one-line decision made where the string is built.

## Proposed change

`gregorianMonthTitle` becomes a range, reusing the same three-case shape. The month names come from
`options.localizedGregorianMonthNames`, which is keyed on `effectiveMonthNameLanguage` — so an Urdu
widget gets `ستمبر - اکتوبر`, not an English range in an Urdu header.

The existing agreement test (`GregorianHeaderAgreementTest.kt`) pins the widget header against the
in-app header for the *first* in-month Gregorian day. **That test will need its premise changed**, not
just extended: it currently encodes the truncation as the expected behaviour. The right replacement
pins the widget's range against `state.gregorianRangeFor(month)` — the same two dates — so the two
surfaces are held to one answer.

## Why this is a `widget-data` change and not a `widget-glance` one

The string is built in the shared projection, so both platforms get it. Fixing it in the Glance
renderer would leave iOS wrong and, worse, make the two correct implementations differ — which is how
the current disagreement happened in the first place.

## Acceptance criteria

- [ ] `gregorianMonthTitle` names both Gregorian months when they differ, with the single-year and
      two-year forms matching `defaultGregorianMonthRangeLabel` exactly.
- [ ] A month that starts and ends inside one Gregorian month is **unchanged** — `"September 2026"`,
      not `"September - September 2026"`.
- [ ] A month crossing a year boundary renders `"December 2026 - January 2027"`.
- [ ] The names follow `effectiveMonthNameLanguage`, not the widget's layout language.
- [ ] The in-app and widget headers are held to **the same** answer by a test, replacing the
      first-day agreement assertion.
- [ ] iOS picks the change up from the shared projection without a Swift edit.
- [ ] `apiDump` regenerated — `HijriMonthWidgetData.gregorianMonthTitle` is in the ABI.

## Note

Do not try to solve this by showing the Gregorian *month grid* instead of a title. The widget grid is
a Hijri month; overlaying a Gregorian one is a different product, and it collides with FD-02's
adjacent-day decision — those days are exactly the neighbouring Gregorian months.