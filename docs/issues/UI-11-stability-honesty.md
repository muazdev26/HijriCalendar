# Issue 11: WITHDRAWN — `@Immutable` on `HijriCalendarLabels` is sound, not unsound

**Severity:** ~~Medium~~ None
**Blocks:** —
**Blocked by:** —
**Module:** `calendar-ui`
**Status:** Withdrawn 2026-09-30 — the premise was disproven. Kept as a record so it is not
re-raised, and so the disproof is findable.

---

## Why this was withdrawn

The ticket claimed that `@Immutable` on `HijriCalendarLabels` "overrides inference in the wrong
direction", because its four fields are `kotlin.Function1` / `Function2`, which the Compose compiler
was said to rate **unstable**. It cited `javap` on the compiled class as verification, and added a
note that an earlier reading of the source had been wrong — so the claim had already survived one
self-correction.

**It is still wrong.** Decompiling the compiler settles it:

```bash
# StabilityInferencer.stabilityOf  (kotlin-compose-compiler-plugin-embeddable 2.4.20)
33: isUnit?                        ─┐
41: isPrimitiveType?                │
51: isFunctionOrKFunction?          ├─▶ 71: return Stable
57: isSyntheticComposableFunction?  │
64: isString?                      ─┘
```

Every branch from offset 33 funnels into the same `return Stable`, and `isFunctionOrKFunction` is
one of them. **Compose infers function-typed fields as stable.** A class whose only non-value-class
fields are lambdas is inferred stable, so `@Immutable` on `HijriCalendarLabels` is *redundant*, not
unsound. Nothing is being lied to, so neither failure mode described in the original ticket can
occur.

The corroborating evidence was wrong too. The ticket said `HijriCalendarColors` "has no annotation
and is nonetheless stable, because the compiler infers stability. **The asymmetry is the tell.**"
`HijriCalendarColors.kt:7` carries `@Immutable`. Both classes show `$stable` and the annotation in
`javap` output. There is no asymmetry, and no stable-by-inference case to contrast against.

The ticket did correctly warn: *"Verify with the compiler, not by reading."* Reading was exactly
what went wrong, on both counts.

## What survives

One finding in the original ticket was real, and it is **not** about `@Immutable`. It is now
recorded as a KDoc requirement on `HijriCalendarLabels` rather than as a ticket, because it is a
sentence of documentation, not a defect:

> `HijriCalendar` uses `labels` as a `remember` key at three sites (`HijriCalendar.kt:37, 45, 85`).
> `HijriCalendarLabels` is a `data class` whose four function fields compare by **reference**, so a
> consumer who constructs it inline in a composable produces a new object on every recomposition and
> discards all three caches. Construct it once and hold it.

That is a real recomposition cost, and it is a consumer-facing usage note. It is unaffected by the
annotation question either way, which is why removing `@Immutable` would not have addressed it.

## The genuine hazard, for the record

A label lambda that captures snapshot state —

```kotlin
HijriCalendarLabels(hijriMonthName = { _, m -> names[currentLocale] })
```

— has a stable identity while `currentLocale` changes, so the object compares equal and the header
can go stale until something else invalidates. That is a real Compose footgun with *any* lambda
field, stable-annotated or not, and it is the kind of thing that surfaces as an unreproducible
"the month name is wrong" report. It is not this ticket's finding either: it happens identically
with and without `@Immutable`, so the ticket's proposed fix would not have touched it. If it is
worth addressing, the address is the table-driven variant the original ticket sketched in its
proposal §3 (resolve lambdas to `List<String>` eagerly, which *is* genuinely immutable), taken as
its own piece of work with its own test — not as a stability-annotation fix.

## If you are here looking for the original

It is in this file's history. The substance was: *check whether `@Immutable` is a promise this class
can keep, rather than assuming it.* The answer turned out to be yes, cheaply, and the class is fine.
