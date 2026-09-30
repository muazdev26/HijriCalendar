# Issue 2: Scoped month-length overrides instead of a process global

**Severity:** High
**Blocks:** [Issue 5](CORE-05-exception-semantics.md)
**Blocked by:** [Issue 1](CORE-01-mode-aware-month-range.md)
**Module:** `calendar-core` + sample app
**Biggest single change in this batch. Expect the widest diff.**

---

## Problem

`HijriMonthOverrides` is a process-wide `object` holding a mutable map:

```kotlin
// calendar-core/.../HijriMonthOverrides.kt:24-28
object HijriMonthOverrides {
    private val overrides: AtomicReference<Map<Pair<Int, Int>, Int>> = AtomicReference(emptyMap())
```

The *precedence rule* (`user > FIXES > UAQ`) is genuinely core domain logic and belongs here.
The *storage* being global does not. Three concrete costs, all already visible in the repo:

**a. No scoping.** A consumer cannot render two calendars with different override sets, cannot
render a preview with overrides, and cannot scope overrides to a screen. Two
`HijriCalendarState` instances on different screens cannot disagree about what month 1448-03
looked like — which is not a bug, it is a category error. There is no API that could express
the intent.

**b. No persistence, and the state holder is the only writer.**

```kotlin
// calendar-core/.../HijriCalendarState.kt:168-171
fun setMonthLength(year: Int, month: Int, length: Int) {
    HijriMonthOverrides.setMonthLength(year, month, length)
    _overridesRevision = HijriMonthOverrides.currentRevision
}
```

A per-instance state object writing into library-wide mutable state means ownership is
everywhere and nowhere.

**c. The Compose bridge is hand-rolled.** Because the singleton is not Compose state, the grid
would never recompute when overrides change, so the state holder mirrors the revision counter
into its own `mutableStateOf` and reads it inside `derivedStateOf`:

```kotlin
// calendar-core/.../HijriCalendarState.kt:57
private var _overridesRevision by mutableStateOf(HijriMonthOverrides.currentRevision)
// ...:110-113
val calendarMonth: CalendarMonth by derivedStateOf {
    _overridesRevision   // establishes a snapshot dependency on a non-Compose singleton
    _currentMonth.toCalendarMonth(...)
}
```

**That bridge is the tell.** The complaint is not "state in core" — a state holder in this
module is fine and normal (see [INDEX.md](INDEX.md#is-state-in-calendar-core-normal)). The
complaint is *state in core that the consumer does not own*.

**d. Test fragility.** `MonthLengthOverridesTest` carries `@AfterTest { HijriMonthOverrides.clearAll() }`
plus four inline `clearAll()` calls. If a test fails before its cleanup line, the pollution leaks
into the next test and produces a confusing failure far from the cause.

## What is already right — do not regress this

The concurrency design is excellent and must survive the refactor unchanged in spirit:

- `AtomicReference<Map>` + `AtomicLong` revision, CAS loops, no `synchronized` in `commonMain`
- no-op writes deliberately skip the revision bump (`:38`)
- `replaceAll` for restoring persisted state at startup (`:82-95`)
- `PakistanHijriCalendar` CAS-publishes an immutable `MonthTable` per revision
  (`PakistanHijriCalendar.kt:104-134`), and `HijriCalendarState` tracks `overridesRevision` so
  the grid recomputes

`AGENTS.md` documents all of this in detail. The refactor changes *ownership*, not the algorithm.

## Proposed change

**1. Make the table an instantiable value.**

```kotlin
class HijriMonthLengths(initial: Map<Pair<Int, Int>, Int> = emptyMap()) {
    val currentRevision: Long
    fun monthLength(year: Int, month: Int): Int?
    fun all(): Map<Pair<Int, Int>, Int>
    fun setMonthLength(year: Int, month: Int, length: Int)
    fun clearMonthLength(year: Int, month: Int)
    fun clearAll()
    fun replaceAll(source: Map<Pair<Int, Int>, Int>)
}
```

Same CAS internals, same revision semantics. `object HijriMonthOverrides` becomes a thin
process-wide **default** that `HijriCalendarState` reads once at construction, so existing
consumers keep working unchanged.

**2. `HijriCalendarState` owns an instance**, as a constructor parameter defaulting to the
process default. Its `setMonthLength` / `clearMonthLength` / `clearAllMonthLengths` write to the
instance. `overridesRevision` reads the instance's revision, so the `_overridesRevision` mirror
disappears as a separate concept.

**3. Persistence stays with the app** — do not move it into the library. The sample already
persists a compact `"year-month:length"` CSV in its `SavedStateHandle`; thread the instance
through `replaceAll` at startup as it already does.

## Shipped

`HijriMonthLengths` is now a `class` with the same CAS internals and the same revision semantics;
`HijriMonthOverrides` became a thin delegating `object` whose `current` property exposes the
process default. It is deliberately **not** a supertype of `HijriMonthLengths`, so code that
*writes* overrides has to name the instance it is writing to.

Every function that consults overrides now takes an explicit `overrides: HijriMonthLengths = HijriMonthOverrides.current`
— so the single-calendar case needs no plumbing at all (the sample apps and the widgets were not
modified and still work), while a second calendar can pass its own table.

`HijriCalendarState` gained a `monthLengths: HijriMonthLengths = HijriMonthOverrides.current`
constructor parameter; its three mutators, `monthLengthOf`, `overridesRevision` and the
`derivedStateOf` snapshot read all go through the instance. The `_overridesRevision` mirror stays
(it is still needed — the table is not Compose state), but it now reads *the instance's*
revision, so the global is no longer load-bearing.

### The `MonthTable` cache had to become per-instance

`PakistanHijriCalendar` cached one `MonthTable` keyed on a single `revision: Long`. With several
instances that is a correctness bug, not a performance one: instance A's chain would be handed
to instance B whenever their revisions happened to match. The cache is now
`AtomicReference<Map<HijriMonthLengths, MonthTable>>` keyed on **identity** (not equality — two
tables with identical content still have independent mutation state, so sharing an entry would
serve a stale chain after a write).

While there, the override-free default lengths were split out of `MonthTable` into their own
process-wide cache. They depend on nothing but the constants, so they were being rebuilt per
instance for no reason.

`prewarm(overrides)` and `isWarmForCurrentOverrides(overrides)` take the table explicitly, so
`PakistanWarmUp` can no longer prime the warm-up for the wrong instance.

### Four stale fixtures again

`thePakistanMonthTableIsScopedPerInstance` took four attempts. Each failure was the fixture, not
the code, and each one is now a trap closed off rather than a hardcoded date:

- 1441-06 is **already** 29 days in `FIXES`, so "force it to 29" was a no-op.
- 1441-06 is a **fix month**, so its start is pinned no matter what is forced.
- Forcing *down* to 29 does not make day 29 invalid — the assertion had the direction backwards.
- The month that survived those checks (1401-01) sits **before the first fix**, where the next
  fix pins the *end* and a shorter month moves its *start* forward. Asserting on the end was
  wrong for that region, so the test now asserts on the whole resolved extent, which is valid on
  either side of the first fix, and derives the month from `FIXES` so it cannot silently rot.

This is worth recording: four of the six fixture failures in this batch came from the same
assumption — that a month-length override always moves a month *forward*. It only does after the
first fix.

## Done when

- [x] Two `HijriCalendarState` instances with different `HijriMonthLengths` render different
      grids simultaneously, in the same test
- [x] A test constructs a state with a private overrides instance, mutates it, and confirms the
      process default is untouched
- [x] Existing single-instance consumers (sample, `calendar-ui`, widgets) compile and pass
      unchanged — none of them needed a code change
- [x] All four test suites green (13 new tests in `HijriMonthLengthsTest`)

## Deliberately not done

`MonthLengthOverridesTest` keeps its `@AfterTest { HijriMonthOverrides.clearAll() }`. That test
class is the one place still exercising the *process default* on purpose, so its cleanup is now
correct rather than load-bearing. The isolation problem is solved by the new tests above, which
never touch the global and therefore need no teardown.

## Notes

- **Do this before Issue 5.** Both touch the `PakistanHijriCalendar` surface (the `MonthTable`
  rebuild reads `HijriMonthOverrides`). Reversing the order means reworking it twice.
- `PakistanHijriCalendar.prewarm()` / `isWarmForCurrentOverrides()` (`:186-200`) read the
  *process* overrides today. They need to take an explicit table, or the warm-up can be primed
  for the wrong instance. The `PakistanWarmUp` coordinator in `calendar-widget-glance` is the
  caller to check.
- Leave the KDoc promise "overrides are global (library-wide)" honest: the **default** is global,
  not that overrides are. Done.
- This is a **breaking change**, accepted deliberately. The global remains as a compatibility
  default, so the migration for most consumers is nil.
