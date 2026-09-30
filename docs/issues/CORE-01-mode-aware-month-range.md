# Issue 1: One source for the mode-aware month range

**Severity:** High
**Blocks:** [Issue 2](CORE-02-scope-month-overrides.md), [Issue 3](CORE-03-api-stability.md), [Issue 6](CORE-06-core-purity.md)
**Blocked by:** —
**Module:** `calendar-core` + 3 consumers
**Do this first** (with Issue 4) — it is the prerequisite for the rest.

---

## Problem

`CalendarMonth` exposes a Gregorian month extent that is **wrong in two of its three modes**:

```kotlin
// calendar-core/.../CalendarMonth.kt:27-29
val gregorianFirstDay: LocalDate get() = firstDay.toLocalDate().minus(adjustmentDays, DateTimeUnit.DAY)
val gregorianLastDay:  LocalDate get() = lastDay.toLocalDate().minus(adjustmentDays, DateTimeUnit.DAY)
```

`firstDay` / `lastDay` come from `yearMonth`, i.e. `HijrahYearMonth` — the **Umm al-Qura
calculation**. So these two properties answer the question "what is this month's Gregorian
extent?" correctly only when no overrides exist and Pakistan mode is off. In observed mode
(`HijriMonthOverrides` non-empty) and in Pakistan mode (`FIXES` table) they are both wrong,
because in those calendars the month's start and length come from somewhere else entirely.

Nothing in the code or the docs says so. The properties are undeprecated, unqualified, and read
like general-purpose accessors.

### The duplication this caused

Because the correct answer was never available from the model, every consumer re-derived it by
hand. There are **four** copies, not three:

| # | Location | Derivation |
|---|---|---|
| 1 | `calendar-core/.../CalendarMonth.kt:27-29` | plain UAQ (the wrong one) |
| 2 | `calendar-ui/.../HijriCalendar.kt:52-78` | full 3-mode branch, with fully-qualified `com.muazdev.hijricalendar.core.HijriMonthOverrides` calls inline |
| 3 | `calendar-widget-data/.../WidgetDataApi.kt:187-198` | 2-mode branch (UAQ + observed; no Pakistan case) |
| 4 | `sample-shared/.../CalendarScreen.kt:311-320` | 2-mode branch |

Note copies 3 and 4 are not even consistent with copy 2 — the widget projection has no Pakistan
branch at all, and falls through to the UAQ answer there. That is a live cross-platform
disagreement, not a theoretical one.

Plus the dead `CalendarMonth.gregorianMonthRange` (`:32-48`), deprecated with a message about
*localization* when the actual problem is *wrongness*.

## Why it matters

- It is the only place the library can silently hand a consumer the wrong month, and it fails
  quietly — no exception, no warning, just a header that disagrees with the grid below it.
- AGENTS.md already enforces the opposite rule for the widget module: *"must both build data
  through the top-level `buildRenderData`/`buildMonthData`/`HijriWidgetRenderCache` helpers … so
  they cannot drift"*. That discipline was simply never applied here.
- Every future mode or override rule is currently a four-site edit with no compiler help.

## Proposed change

**1. One internal resolver in core.** Add to `CalendarMonthExt.kt` (or a new
`MonthRange.kt`):

```kotlin
/** The Gregorian extent of [year]/[month] in the active calendar space. */
internal fun resolveGregorianRange(
    year: Int,
    month: Int,
    pakistan: Boolean,
    overrides: Map<Pair<Int, Int>, Int>,
    adjustmentDays: Int,
): Pair<LocalDate, LocalDate>
```

Branching exactly once, on the same precedence the grid generation already uses
(`CalendarMonthExt.kt:44-61`): Pakistan table → observed → UAQ. Prefer routing the observed
branch through `ObservedHijriCalendar.observedToGregorian` + `observedLength` so the override
precedence rule stays in one place.

**2. `CalendarMonth` carries the resolved range.** Add `gregorianRange: Pair<LocalDate, LocalDate>`
as a constructor field set at grid-generation time (where the mode is already known), and reduce
`gregorianFirstDay` / `gregorianLastDay` to reads of it. Deprecate `gregorianMonthRange` with a
message about correctness, not localization.

**3. Collapse the four call sites** onto the resolver. `calendar-ui` and `calendar-widget-data`
must stop re-branching; `sample-shared` too. This is the point of the ticket.

**4. Add the missing Pakistan branch to the widget projection** as part of the collapse — it
falls out for free once the resolver owns the branch.

## Shipped

**`CalendarMonth`** gained `gregorianRange: GregorianMonthRange?` (default `null`) plus a
`resolvedGregorianRange` accessor. `gregorianFirstDay` / `gregorianLastDay` are now reads of
that, so they are correct in every space. The `null` default resolves lazily in the plain Umm
al-Qura space, which keeps hand-constructed instances (previews, tests) working without forcing
a mode through the constructor.

`CalendarMonth` also got the KDoc the audit called for — every field is now documented, including
the one that actually matters: **`yearMonth` is always the calculated month and never implies
which space the cells are in.**

**New file** `GregorianMonthRange.kt` holds `GregorianMonthRange` (with a `lengthInDays`
convenience) and the single public `resolveGregorianMonthRange(year, month, pakistan, adjustmentDays)`.
It branches once, on the same precedence `toCalendarMonth` uses.

All three `toCalendarMonth` variants now pass their mode's resolved range into the `CalendarMonth`
they construct, so a consumer holding a grid is guaranteed the range describes the same space the
cells came from.

**Collapsed:** `calendar-ui/HijriCalendar.kt` and `calendar-widget-data/WidgetDataApi.kt` no longer
branch, and no longer import `HijriMonthOverrides` or `ObservedHijriCalendar` at all. The
`calendar-ui` header keeps its deliberate no-grid-build optimisation — it calls the resolver with
`state.currentMonth` rather than reading `state.calendarMonth`.

`CalendarMonth.gregorianMonthRange` stays, now deprecated for **correctness** rather than
localization.

### Two stale fixtures caught

Both "the bug this replaces" tests failed on first run, and in both cases the *test* was wrong,
not the fix:

- 1440-11's **start** agrees with Umm al-Qura (both 2019-07-04); only the **length** diverges.
  The original assertion checked the wrong end.
- 1448-03 is a **29**-day month in UAQ, so forcing 29 was a no-op and the range legitimately did
  not move. Rewritten to read `ObservedHijriCalendar.defaultLength` and force the *opposite*
  value, so the test cannot silently degrade into asserting nothing.

`thePakistanTableDisagreesWithTheCalculationSomewhereInItsRange` was added for the same reason:
it proves the two spaces really differ, so the table-consulted assertion is not vacuous.

## Done when

- [x] A test asserts `CalendarMonth.gregorianFirstDay`/`LastDay` differ from the UAQ answer under
      an override, and follow the `FIXES` table in Pakistan mode
- [x] `rg "observedToGregorian|observedLength" calendar-ui calendar-widget-data` returns nothing
      in `main` — no hand-rolled branches remain in consumers
- [x] `calendar-ui` and `calendar-widget-data` no longer import `HijriMonthOverrides` or
      `ObservedHijriCalendar`
- [x] All four test suites green (12 new tests in `GregorianMonthRangeTest`)
- [x] `CalendarMonth` gains field KDoc naming the space it answers in

## Notes

- **Deferred to CORE-02:** `resolveGregorianMonthRange` has no `overrides` parameter — it reads the
  process-wide `HijriMonthOverrides`. Threading an explicit table through `ObservedHijriCalendar`
  is the same change CORE-02 needs for `PakistanHijriCalendar.prewarm()` /
  `isWarmForCurrentOverrides()`, so it is deliberately left there rather than half-done here.
- `resolveGregorianMonthRange` is `public`, not `internal`, because the consumers live in other
  Gradle modules. It is documented as the *only* place the three spaces are branched on for this
  question.
- The one remaining direct `PakistanHijriCalendar.hijriToGregorian` call outside core is in
  `WidgetDataParityAndNavigationTest.kt:83`, asserting an absolute anchor. That is deliberate —
  the same reasoning that put absolute anchors in `PakistanCalendarMonthTest`.

