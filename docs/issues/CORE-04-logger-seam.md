# Issue 4: Pluggable logger instead of `println`

**Severity:** Medium
**Blocks:** [Issue 5](CORE-05-exception-semantics.md)
**Blocked by:** —
**Module:** `calendar-core`

---

## Problem

`calendar-core` ships diagnostics to standard out:

- `TodayHijriDate.kt:26` — `println("HijriCalendar: failed to resolve today's Hijri date: $exception")`
- `PakistanHijriCalendar.kt:252` — `println("PakistanHijriCalendar: failed to resolve today's date: $exception")`

Both sit in `commonMain`, which is precisely why they were written this way: `android.util.Log`
is not available in a multiplatform source set. But `println` is not a workable answer for a
published library:

- A consumer on Android cannot route it to logcat. Their only option is redirecting `stdout`,
  which captures unrelated output and loses per-tag filtering.
- There is no way to silence it. A library that cannot be quiet is a library people delete.
- There is no level, so an expected "date unresolvable" path is indistinguishable from a crash
  in a log aggregator.
- Issue 5 needs somewhere to report *internal faults* as distinct from benign null returns. This
  is the seam it will use.

The ecosystem norm for exactly this constraint is a tiny `expect fun` logger: coil3, ktor, and
most multiplatform database builders all ship one.

## Why it matters

Small on its own, but it is a published artifact at 1.0.0 and the fix is ~40 lines. Doing it
first also gives Issue 5 somewhere to put the diagnostics it needs to add.

## Proposed change

> **Design note — deviation from the original plan.** This was first written proposing an
> `expect fun` logger with per-target `actual`s. On implementation, `calendar-core` turned out
> to have **no platform source sets at all** (`commonMain`, `commonTest`, `desktopTest` only —
> no `androidMain`, no `iosMain`). Adding three of them purely to reach `android.util.Log` costs
> more than it buys. Shipped instead: a pure-common settable sink defaulting to no-op, with the
> platform binding left to the consumer as a one-liner. Same outcome, zero target-specific code,
> and the library stays free of platform dependencies.

**1. `CalendarLog` in `commonMain`** (`CalendarLog.kt`):

```kotlin
public enum class CalendarLogLevel { DEBUG, INFO, WARN, ERROR }

public fun interface CalendarLogger {
    public fun log(level: CalendarLogLevel, tag: String, message: String, cause: Throwable?)
}

public object CalendarLog {
    public var logger: CalendarLogger   // @Volatile sink, defaults to no-op
    public fun reset()
    public val isInstalled: Boolean
    internal fun d/w/e(tag, message, cause = null)   // internal: consumers install, not emit
}
```

The emit functions are `internal` on purpose — a consumer installs a sink, it does not
manufacture library diagnostics.

**2. Replace both `println` sites** with `CalendarLog.w(...)`, keeping the message text and
attaching the `Throwable` as `cause` so nothing diagnostic is lost.

**3. Default to silence, not stdout.** A newly published library must not start printing. This
is the single most important property of the change and is what `isSilentByDefault` pins.

## Done when

- [x] No `println` in `calendar-core/src/commonMain`
- [x] A test installs a logger, provokes the `todayHijriDate` failure path, and asserts the
      message, level, tag and `cause` are delivered
- [x] `CalendarLog` defaults to no-op (`isSilentByDefault`, `resetRestoresSilence`)
- [x] Replacing a logger does not also keep the previous one
- [x] `calendar-core:desktopTest` green — 5/5 in `CalendarLogTest`

## Notes

- The failure path is provoked deterministically with a `500_000`-day `adjustmentDays` shift,
  which pushes the date outside the supported Umm al-Qura range so `toHijrahDate()` throws.
  Timezone-independent, so it is stable on every CI runner.
- Do **not** change behaviour beyond routing. Issue 5 decides what the failure paths mean; this
  issue only gives them somewhere to report.
- The KDoc on [TodayHijriDate](../calendar-core/src/commonMain/kotlin/com/muazdev/hijricalendar/core/TodayHijriDate.kt)
  was updated: it used to promise the failure is "echoed so it is diagnosable in production
  logs", which is no longer true without a logger installed.

