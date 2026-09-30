# Issue 09: Two pager effects are unsynchronised, and rotation animates the pager

**Severity:** Medium
**Blocks:** [Issue 06](UI-06-public-surface-and-docs.md)
**Blocked by:** [Issue 02](UI-02-compose-test-harness.md)
**Module:** `calendar-ui`
**Status:** Shipped — see "Shipped" below.

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

- [x] A test asserts `pagerState.settledPage` maps to `state.currentMonth` after
      `goToNextMonth()` and after a swipe
- [x] No `LaunchedEffect` calls `animateScrollToPage` on the path that handles a restored pager
- [~] A test recreates the composition with a mismatched pager/state pair and asserts it settles
      rather than animating — **replaced.** See "The test that could not be written" below.
- [x] `.drop(1)` is gone, or its removal is justified in a comment

## Shipped

**The two `LaunchedEffect`s are now one.** The state and the pager are two owners of "which month is
showing"; they were kept in agreement by an effect each, plus a `.drop(1)` whose correctness
depended on how many times Compose happened to emit.

The invariant is now stated once, in one place: **the state is the authority for which month is
displayed.**

- The pager settles somewhere the state is not → the state follows the pager. That is a user swipe.
- The state moves somewhere the pager is not → the pager follows the state.

### The polarity problem, and why `lastTargetPage` exists

My first attempt branched on `page != targetPage`, which is **wrong**, and the test suite said so.
A user swipe and a restored pager disagreeing with the state have the *identical* signature — the
pager is one page ahead of the state — and need opposite actions. Under my first version a swipe was
treated as "the state moved" and the pager snapped back, failing both swipe tests.

Values alone cannot distinguish them. The discriminator is **did the state change since the previous
emission**:

```kotlin
var lastTargetPage = -1   // no real page can be -1, so the first emission always reads as "state led"
…
if (targetPage != lastTargetPage) { /* the state moved: the pager follows */ }
else                             { /* the state did not: the pager moved, so the state follows */ }
```

Seeding to `-1` is what makes restore work: the first emission is always classified as "the state
led", so a restored pager disagreeing with a restored state is corrected by moving the *pager* to
the state — never by overwriting the state from the pager, which was the actual defect.

### `drop(1)` is gone

No emission-count heuristic survives. Correctness now depends on comparing the target against the
previously-seen target, which is well-defined whether Compose emits once, twice, or not at all.

### The jump-vs-animate rule, extracted so it can be tested

`if (abs(targetPage - page) == 1) animateScrollToPage else scrollToPage` is now `pageJump(from, to):
PageJump`, a named enum and a pure function with its own file's worth of KDoc. It is the whole of
this ticket's user-visible defect, so it gets tested directly: `PageJumpTest` (6 cases) pins that
one page animates, zero pages does not, two does not, and 140 does not.

**And `PageJumpTest` found a real bug on its first run**, in the code I had just written:
`abs(toPage - fromPage)` on `Int`s overflows — `Int.MAX_VALUE` minus `Int.MIN_VALUE` wraps to `-1`,
whose absolute value is `1`, which classified the single largest possible jump as a one-page
animation. Page indices derive from caller-supplied bounds, so they are not guaranteed to be near
each other. The subtraction is now done in `Long`, and `extremePages_doNotOverflow` pins it.

## The test that could not be written

The done-when asks for a test that recreates the composition with a mismatched pager/state pair and
asserts it **settles rather than animating**. I could not make that test mean anything, and I am
recording why rather than shipping a green one.

Two independent obstacles:

1. **Compose's test clock does not separate the two behaviours.** With `autoAdvance = false` the
   clock is under the test's control, so a settle budget looked like it should work. It does not:
   `HorizontalPager` composes the *same nodes* whether it jumps or animates, because both move
   `currentPage` immediately and differ only in the scroll offset over time. A version of this test
   asserting "the far month is painted within 250ms of simulated time" **passed with the animation
   restored**. It was worth nothing, and I only found out by deliberately re-introducing the bug.
2. **`pagerState` is not reachable from the test.** It is a local inside the composable, so the test
   cannot assert `settledPage` directly.

What was written instead:

- `aLargeStateJumpReachesTheFarMonth` — that the calendar *reaches* a far month and stays there,
  and that the state is not written back. Real, and not a proxy for the animation question.
- `PageJumpTest` — the policy itself, deterministically.
- `HijriCalendarRenderAgreementTest` already asserts that the header's month and the painted cells
  agree after every kind of navigation, which is the user-visible consequence of the whole
  reconciliation.

The remaining gap is honest: **jump vs animate is not covered by a test.** It is covered by a
comment, a named type, and a policy test. If that ever matters, the way to get it is a screenshot or
timing test on a real device — not a desktop Compose clock.

## Notes

- **The `page != pagerState.currentPage` guard from the old code is gone**, and so is the
  `HorizontalPager` implementation detail it depended on (`settledPage` changes at rest while
  `currentPage` tracks the in-flight target). The new collector compares the *target* against the
  previously-seen target, which does not depend on any of that.
- **The window is no longer a ±500-month magic number.** UI-05 replaced it with `PageWindow`, whose
  unbounded sides reach `HijrahDate.MIN`/`MAX` — the same edges `canGoToPreviousMonth` /
  `canGoToNextMonth` clamp at. So the old note about "the window can never be exhausted in practice"
  no longer describes the code: the window is now as wide as the Hijri table, and
  `pagerWindow_hasOddPageCountCentered` (which asserted only that the count was odd) has been
  replaced by `PageWindowTest`.
- **`remember(state.minDate, state.maxDate, initialMonth)` for the window is not a maintenance
  surface.** Both bounds are immutable `val`s on the state, so that key list cannot drift the way
  the old month-builder key list did. This is the one multi-key `remember` left in the module.
- **`monthOffset` and `plusPageOffset` are pure and already tested** (`HijriCalendarGridMathTest`).
  This ticket was about the *effects*, not the arithmetic, and it still is.
- **`state.currentMonth` is read inside `snapshotFlow` on purpose.** That read is what makes the
  flow re-emit when the state changes. It is not an incidental dependency — moving it out of the
  flow would break the reconciliation silently, so it is commented where it happens.
- `git show 337f631` is referenced by
  [UI-04](UI-04-accessible-text-scaling.md), not this one. The commit that introduced the pager's
  saveable restore behaviour is worth reading before changing how restore is reconciled.
