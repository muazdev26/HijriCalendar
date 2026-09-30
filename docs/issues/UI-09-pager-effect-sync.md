# Issue 09: Two pager effects are unsynchronised, and rotation animates the pager

**Severity:** Medium
**Blocks:** [Issue 06](UI-06-public-surface-and-docs.md)
**Blocked by:** [Issue 02](UI-02-compose-test-harness.md)
**Module:** `calendar-ui`

---

## Problem

`HijriCalendarGrid` runs two `LaunchedEffect`s that both write to the same state/pager pair.

**Effect A — state → page** (`:68-74`):

```kotlin
LaunchedEffect(calendarMonth.yearMonth) {
    val offset = monthOffset(calendarMonth.yearMonth, initialMonth)
    val page = (PAGER_CENTER_PAGE + offset).coerceIn(minAllowedPage, maxAllowedPage)
    if (page != pagerState.currentPage && page in 0 until PAGER_PAGE_COUNT) {
        pagerState.animateScrollToPage(page)
    }
}
```

**Effect B — page → state** (`:79-95`):

```kotlin
LaunchedEffect(pagerState) {
    snapshotFlow { pagerState.settledPage }
        .drop(1)
        .collect { page ->
            val clampedPage = page.coerceIn(minAllowedPage, maxAllowedPage)
            if (clampedPage != page) pagerState.animateScrollToPage(clampedPage)
            val month = initialMonth.plusPageOffset(clampedPage - PAGER_CENTER_PAGE)
            if (month != state.currentMonth) state.goToMonth(month)
        }
}
```

## Three defects

**1. Rotation animates the pager.** `rememberPagerState` restores via `rememberSaveable`. `.drop(1)`
at `:81` deliberately discards the restored value, so on the first composition after a
configuration change Effect B never calls `goToMonth`. Effect A then runs (it is keyed on
`calendarMonth.yearMonth`, which fires on first composition) and calls `animateScrollToPage` from
the *restored* page to the *state's* page. A user rotating the device sees a multi-month
horizontal scroll animation across the calendar. On a restored pager and a restored state that
disagree it is not a no-op.

The `.drop(1)` comment says it is there *"to avoid the pager's restored state … overwriting the
correct ViewModel state on configuration changes"* — the intent is right. The mechanism discards
the correction instead of making it, and leaves the disagreement in place for Effect A to resolve
by animating.

**2. The guard's correctness is undocumented and untested.** `page != pagerState.currentPage` is
what stops A and B from fighting. It works only because `settledPage` changes at rest while
`currentPage` tracks the in-flight target — a `HorizontalPager` implementation detail. Nothing in
the file says so, so the next person to touch either effect can remove the guard and cause a
scroll loop.

**3. `initialMonth` never follows a restored state.** `remember { calendarMonth.yearMonth }`
(`:46`) captures the first month the grid ever saw. On a config change the composable is
re-entered, `initialMonth` is recomputed from the newly-passed `calendarMonth` (fine), but any
*later* change to the state's month cannot re-anchor it — which is correct today, but the comment
gives no hint that the anchor is deliberately frozen.

## Proposed change

**1. Make the pager the single source of truth for the visible month**, or make the state. Not
both, driven by two effects.

The smallest correct version: keep both owners, but replace the two-effect handshake with one
snapshot-driven reconciliation:

```kotlin
LaunchedEffect(pagerState, calendarMonth.yearMonth, initialMonth) {
    snapshotFlow {
        val page = pagerState.settledPage
        val month = initialMonth.plusPageOffset(page - PAGER_CENTER_PAGE)
        month to page
    }.distinctUntilChanged().collect { (month, page) ->
        val clamped = page.coerceIn(minAllowedPage, maxAllowedPage)
        if (clamped != page) { pagerState.animateScrollToPage(clamped); return@collect }
        if (month != state.currentMonth) state.goToMonth(month)
        else if (page != pagerState.currentPage) pagerState.animateScrollToPage(page)
    }
}
```

One collector, one direction of travel per emission, and the restored page is *used* rather than
dropped — so a restored pager whose page disagrees with the restored state is reconciled by
settling, not by animating across ten months.

**2. Drop `.drop(1)` and drop the "first emission is untrusted" premise.** With one collector the
first emission is just the current page, and reconciling it is the correct thing to do.

**3. If two effects must stay**, document the invariant in a comment on each: "A animates, B never
animates except to clamp; `page != pagerState.currentPage` is load-bearing because `settledPage`
only changes at rest." That comment is the missing piece of the current code.

**4. Add the tests from [UI-02](UI-02-compose-test-harness.md).** At minimum:

- `goToNextMonth()` settles on the page naming the new month, with no visible multi-page scroll
- a swipe settles and calls `goToMonth` with the swiped-to month
- a swipe past `maxDate` snaps back to the boundary month and does not change `state.currentMonth`
- recreating the composition with a restored pager and a different state month settles, does not
  animate through intervening months

## Done when

- [ ] A test asserts `pagerState.settledPage` maps to `state.currentMonth` after
      `goToNextMonth()` and after a swipe
- [ ] No `LaunchedEffect` calls `animateScrollToPage` on the path that handles a restored pager
- [ ] A test recreates the composition with a mismatched pager/state pair and asserts it settles
      rather than animating
- [ ] `.drop(1)` is gone, or its removal is justified in a comment

## Notes

- **The paging window is odd but unbounded.** `PAGER_CENTER_PAGE = 500` with
  `PAGER_PAGE_COUNT = 1001` is ~41.7 years each way, and `HijriCalendarGridMathTest` asserts only
  `PAGER_PAGE_COUNT % 2 == 1` — a tautology given `COUNT = CENTER * 2 + 1`. The window is
  effectively unlimited while `state.canGoToPreviousMonth` (`HijriCalendarState.kt:124-125`)
  clamps at Umm al-Qura's table edge, so the window can never be exhausted in practice. Worth a
  comment stating which bound is authoritative; the test as written asserts nothing.
- `minAllowedPage` / `maxAllowedPage` (`:50-59`) are `remember(state.minDate, initialMonth)` keyed.
  `state.maxDate` and `state.minDate` are `val`s on a `@Stable` class, so this is sound — but the
  asymmetry (`?: 0` for min, `?: (PAGER_PAGE_COUNT - 1)` for max) is unexplained.
- `monthOffset` and `plusPageOffset` are pure and already tested. This ticket is about the
  *effects*, not the arithmetic.