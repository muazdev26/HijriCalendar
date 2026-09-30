# Issue 5: Distinguish "out of range" from "internal fault"

**Severity:** Medium
**Blocks:** [Issue 3](CORE-03-api-stability.md)
**Blocked by:** [Issue 2](CORE-02-scope-month-overrides.md), [Issue 4](CORE-04-logger-seam.md)
**Module:** `calendar-core`

---

## Problem

Eleven `catch (_: Exception)` sites in `commonMain` swallow every failure into a `null`. The
worst is `gregorianToHijri`, which wraps its **entire month walk**:

```kotlin
// calendar-core/.../PakistanHijriCalendar.kt:231-243
fun gregorianToHijri(date: LocalDate): PakistanHijriDate? {
    val target = date.toEpochDays()
    return try {
        val anchorIndex = sortedFixes.indexOfLast { it.second.startEpochDays <= target }
        if (anchorIndex >= 0) walkFrom(sortedFixes[anchorIndex], target)
        else walkBackFrom(sortedFixes[0], target)
    } catch (_: Exception) {
        null
    }
}
```

`walkFrom` and its two siblings contain the arithmetic, the month-table lookups, the iteration
caps, and the `require` guards. If any of them throws — a `require` on an out-of-range year, an
`IllegalStateException` from a missing table entry, an arithmetic slip — the caller receives
exactly the same `null` it receives for a legitimately out-of-window date. **An internal bug is
indistinguishable from a valid answer.**

Downstream, that `null` is laundered further:

```kotlin
// calendar-core/.../CalendarMonthExt.kt:137-158
val converted = PakistanHijriCalendar.gregorianToHijri(shifted)
if (converted != null && converted.year in MIN_YEAR..MAX_YEAR) { /* real cell */ }
else { /* disabled placeholder */ }
```

So an arithmetic fault silently renders as a grid full of disabled cells. And
`CalendarDay.dayOfMonth` returns `0` for such a cell (`:33`) — a plausible-looking day number
rather than a visible failure. The `?:` cascade in `CalendarDay` is what makes the whole thing
feel robust; it is also what hides the bug.

The same pattern appears at `TodayHijriDate.kt:21-28`, `ObservedHijriCalendar.kt:58-62` and
`:102-109`, `HijriCalendarState.kt:69-73`, `:307-311`, `:268`, `:480-487`, and
`CalendarMonthExt.kt:170-176`.

## Why it matters

This module's correctness argument rests on subtle, hand-verified invariants — the Pakistan
`FIXES` anchors, the `monthStart` forward chain, the observed drift bound. `AGENTS.md` is
explicit that an off-by-one there "silently shifts every later month ~30 days" and that
"roundtrip tests can't catch it". Silently is the operative word. An error handler that turns
arithmetic faults into empty grids removes the only signal that something is wrong.

## Proposed change

**1. Report, do not swallow.** Every catch routes through the Issue-4 logger with the
`Throwable` attached as `cause`, at `ERROR` for internal faults and `DEBUG` for the benign
"system clock is unusable" case.

**2. Narrow the catches.** Catch what can actually be thrown at each site rather than
`Exception`:

- `toHijrahDate()` (`CalendarMonthExt.kt:170-176`) throws on out-of-range conversion — catch
  that library's specific exception type, or pre-check the range.
- `TimeZone.currentSystemDefault()` and `Clock.System.now()` failures are environment faults;
  they belong in the "cannot resolve today" bucket, not the "bad input" bucket.
- `require` failures inside `PakistanHijriCalendar` are **programming errors** and should
  propagate, not become `null`.

**3. Separate the two meanings at the API boundary.** Either:

- a sealed result, e.g. `sealed interface HijriConversion { data class Found(...); data object OutOfRange; data class Failed(val cause: Throwable) }`, or
- keep `PakistanHijriDate?` for the common path and add an internal `tryConvert` that
  distinguishes the cases for internal callers.

The second is the smaller change; prefer it unless a consumer genuinely needs to tell the
difference.

**4. Narrow the iteration caps from "returns null" to "provably unreachable".** The
`iterations++ < 12 * (MAX_YEAR - MIN_YEAR + 1)` guards (`PakistanHijriCalendar.kt:263`, `:280`,
`:298`) are defensive. Reaching one is a bug, not a date — log it as such rather than returning
a null that reads as "out of range".

## Shipped

**New file `OutOfRange.kt`** holds `orNullIfOutOfRange(onFailure, block)` — the one place in the
module that decides what "no answer" means.

Narrowing the catch turned out to need **two** exception types, not one. `hijrah-datetime`
reports an out-of-range date as `IllegalArgumentException` from `require`, but wraps its
`safeApi` failures in `kotlinx.datetime.DateTimeArithmeticException`, which extends
`RuntimeException` and **not** `IllegalArgumentException`. Catching only the latter would have
let half of them escape — the exact failure this ticket is about. Both clauses are in the helper
and documented there.

Everything else now propagates: `IllegalStateException`, `NoSuchElementException` from a
`getValue` on the month table, `ArithmeticException`. `monthStart` was changed from
`starts.getValue(key)` to `checkNotNull(starts[key]) { "No month start for ..." }`, so a hole in
the table is a named defect rather than an anonymous `NoSuchElementException`.

### The walks no longer rely on an exception to find the boundary

All three walks now check `isSupported(year)` before stepping and return null at the edge, so
"the date is past `MAX_YEAR`" is an answer rather than a thrown `require` caught upstream. The
iteration budget became `MAX_WALK_STEPS` with a name, and hitting it logs at `ERROR` — reaching
it means the chain is broken, which is a defect, not a date.

`OutOfRangeTest` verifies the classification directly: `IllegalArgumentException` and
`DateTimeArithmeticException` become null; `IllegalStateException`, `NoSuchElementException` and
`ArithmeticException` propagate *and* record nothing.

### An alpha-library bug we stopped depending on

`HijriYearMonth.plusMonth` past the end of the table throws
`ArrayIndexOutOfBoundsException: Index 301 out of bounds for length 301` from an unguarded array
index in `UmmAlQuraRules` — not a documented exception type at all. Two navigation tests failed
when the catch was narrowed. Rather than widen the catch back to `Exception`, the state holder now
pre-checks `wouldLeaveHijriTable(months)` against `HijrahDate.MIN.year`..`MAX.year`, and keeps
`orNullIfOutOfRange` only as a second net. The bound is now stated in our code instead of being
inferred from a library implementation detail.

### Every remaining broad catch

None. `rg "catch \(_?:? ?Exception\)" calendar-core/src/commonMain` matches only the KDoc in
`OutOfRange.kt` that explains why they were removed.

## Done when

- [x] A defect no longer produces a silent grid: `orNullIfOutOfRange` is proven not to catch
      `IllegalStateException` / `NoSuchElementException` / `ArithmeticException`, and the
      `MonthTable` hole it guards now has a named error message
- [x] `PakistanHijriCalendar.gregorianToHijri` no longer has a bare `catch (_: Exception) -> null`
      around the walk
- [x] Every `catch (_: Exception)` remaining in `commonMain` is either narrowed to a specific
      type or has a comment justifying the broad catch
- [x] `ObservedHijriCalendar.observedDateAt` returning `null` for a date inside the supported
      range is impossible — swept across 2000–2050 on a prime stride
- [x] All four test suites green (14 new tests in `OutOfRangeTest`)

## Notes

- **Blocked by Issue 4** because the fix needs a logger. **Blocked by Issue 2** because
  `PakistanHijriCalendar`'s month table must take an explicit overrides instance before its
  internals are restructured — doing this first means touching the same code twice.
- Be careful not to convert a benign `null` into a thrown exception. `todayHijriDate` returning
  `null` on a broken system clock is correct, documented behaviour
  (`TodayHijriDate.kt:11-18`); consumers rely on it not throwing.
- After this lands, revisit `CalendarDay.dayOfMonth`'s `?: 0`. With faults logged rather than
  swallowed, `0` stops being a plausible day number and starts being the sentinel it should be —
  or `CalendarDay` should be the sealed union described in
  [INDEX.md](INDEX.md#why-three-date-types).
