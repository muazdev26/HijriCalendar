# Issue 05: A `minDate`/`maxDate` beyond ±500 months crashes on first composition

**Severity:** High
**Blocks:** [Issue 06](UI-06-public-surface-and-docs.md)
**Blocked by:** —
**Module:** `calendar-ui`

---

## Problem

```kotlin
// HijriCalendarGrid.kt:30-31
internal const val PAGER_CENTER_PAGE = 500
internal const val PAGER_PAGE_COUNT = PAGER_CENTER_PAGE * 2 + 1   // 1001

// HijriCalendarGrid.kt:50-64
val minAllowedPage = remember(state.minDate, initialMonth) {
    state.minDate?.yearMonth?.let { PAGER_CENTER_PAGE + monthOffset(it, initialMonth) } ?: 0
}
val maxAllowedPage = remember(state.maxDate, initialMonth) {
    state.maxDate?.yearMonth?.let { PAGER_CENTER_PAGE + monthOffset(it, initialMonth) }
        ?: (PAGER_PAGE_COUNT - 1)
}

val pagerState = rememberPagerState(
    initialPage = PAGER_CENTER_PAGE.coerceIn(minAllowedPage, maxAllowedPage),   // ← line 62
    pageCount = { PAGER_PAGE_COUNT },
)
```

`Int.coerceIn(minimumValue, maximumValue)` documents *"Requires `minimumValue <= maximumValue`.
Throws `IllegalArgumentException` otherwise."* Both bounds are derived from **caller-supplied**
`minDate`/`maxDate` against a **fixed** ±500-month window, so nothing prevents an inverted or
out-of-window range. The calendar then throws while composing — in the consumer's app, on their
first frame, with a `coerceIn` stack trace and no mention of a calendar limit.

## Verified

Replicated `HijriCalendarGrid.kt:50-64` against the module's real `monthOffset` (`initialMonth`
= 1447-09, the default `HijriCalendarState()` month at the time of review):

| Bounds | `minAllowedPage` | `maxAllowedPage` | Result |
|---|---|---|---|
| both `null` | 0 | 1000 | ok, page 500 |
| 1440-01 … 1450-01 | 408 | 528 | ok, page 500 |
| `minDate` 1489-05 (**exactly 500 months**) | 1000 | 1000 | ok, page 1000 |
| `minDate` 1489-06 (**501 months**) | 1001 | 1000 | **throws** |
| `minDate` 1520-01, no `maxDate` | 1368 | 1000 | **throws** |
| `maxDate` 1380-01, no `minDate` | 0 | −312 | **throws** |
| `minDate` 1450-01, `maxDate` 1440-01 | 528 | 408 | **throws** |

Exception text: `Cannot coerce value to an empty range: maximum 1000 is less than minimum 1368.`

The threshold is **exactly ±500 months ≈ 41 years 8 months** — the widest span the pager can
represent. Beyond it, the bound is not clamped, rejected, or documented; it throws.

## Why the limit is not obvious

`HijriCalendarState`'s KDoc says `minDate`/`maxDate` *"also gate navigation"* and that a month is
navigable *"if it contains at least one day within the bounded range"* — accurate, and silent about
the ±500-month ceiling. Nothing in `HijriCalendarState`, `HijriCalendarGrid`'s KDoc, or the README
states a navigation horizon. `PakistanHijriCalendar`'s documented range is 1400–1500 AH — a
century — so a consumer mirroring it is past the limit **from one end to the other**.

## Two related defects in the same block

**a. `pageCount` is the constant, so bounds are enforced by fighting the user.**
`pageCount = { PAGER_PAGE_COUNT }` (`:63`) is always 1001. The bound is applied only after the fact
in the swipe collector (`:85-88`), which calls `animateScrollToPage` to yank the page back. A
bounded calendar should simply have fewer pages.

**b. `drop(1)` is load-bearing and unpinned** (`:79-81`).

```kotlin
snapshotFlow { pagerState.settledPage }
    .drop(1)   // comment explains *that*, not why the count is exactly 1
    .collect { … state.goToMonth(month) }
```

This is a hard contract with `rememberPagerState`'s saveable restore. If Compose ever emits **0**
times before the first interaction, the first genuine swipe is swallowed; if it emits **2** on
restore, a real navigation is swallowed. Either way the grid silently desyncs from the header —
the worst failure mode in a calendar. This belongs with
[UI-09](UI-09-pager-effect-sync.md), which covers the same two-effect sync problem.

## Proposed change

**1. Make the window a value type whose bounds cannot invert.** This removes the crash and makes
the failure mode explicit:

```kotlin
internal data class PageWindow(val first: Int, val last: Int) {
    init { require(first <= last) { "PageWindow is empty: $first..$last" } }
    val count: Int get() = last - first + 1
    fun coerce(page: Int): Int = page.coerceIn(first, last)   // single-arg: cannot throw
}
```

`initialPage` then uses the **single-argument** `coerceIn`, which cannot throw, and `pageCount`
becomes `{ window.count }` — so **a** is fixed for free and the collector's clamp at `:85-88`
deletes itself.

**2. Do not crash on a long horizon — serve it.** The ±500 constant is the real limitation. Either:

- derive `PAGER_PAGE_COUNT` from the caller's span (one page per navigable month, no waste), or
- keep 1001 pages but **clamp the bounds** into range rather than throwing, i.e.
  `minAllowedPage.coerceAtMost(maxAllowedPage)`.

Prefer the first. `require`-ing a documented range still crashes legitimate consumers (a
reservation app with a 5-year horizon, an archive view); clamping silently hides the truncation.
A `PageWindow` sized to the caller's span does neither.

**3. Document the horizon** in `minDate`/`maxDate` KDoc and the README, whatever the outcome.

**4. Replace `drop(1)`** with an explicit first-render guard (e.g. a `remember { false }` flag
compared against a `LaunchedEffect` that has run), so the intent is in code rather than in a count.
See [UI-09](UI-09-pager-effect-sync.md).

## Done when

- [ ] `HijriCalendarGrid` composes with `minDate` 100 years ahead and `maxDate` 100 years behind
- [ ] `minDate` after `maxDate` degrades predictably (documented) instead of throwing
- [ ] `pageCount` equals the navigable month count, not a constant 1001
- [ ] The `:85-88` clamp is deleted
- [ ] Tests cover: unbounded, ordinary, exactly 500 months, 501 months, inverted
- [ ] `minDate`/`maxDate` KDoc states the navigation horizon

## Notes

- **The bug is a one-word fix; the ticket is not.** `coerceIn(min, max)` → a clamp is a two-line
  change that stops the crash. Deriving `pageCount` is the change that actually removes the
  arbitrary limit. Do not stop at the former and close the ticket.
- Reproduce without a Compose harness by calling the module's `internal monthOffset` from
  `commonTest` — `commonTest` has **only** `kotlin.test` (see
  [UI-02](UI-02-compose-test-harness.md)), so `runComposeUiTest` does not resolve. The expression
  is pure Kotlin; it throws identically outside composition. Once the harness exists, add a
  composition-level assertion too.
- `HijriCalendarState.canGoToPreviousMonth` / `canGoToNextMonth` (`:121-133`) are *unaffected* —
  they have no ±500 window, so the header arrows can enable a month the pager cannot represent.
  That inconsistency is a second symptom of the same constant and should be fixed with it.
- `remember(state.minDate, initialMonth)` is correct as written (`minDate` is an immutable `val` on
  the state, `:64-65`). No defect here — noted so the rewrite does not "fix" it into a `var` read.