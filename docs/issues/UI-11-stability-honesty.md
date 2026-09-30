# Issue 11: `@Immutable` on `HijriCalendarLabels` is unsound

**Severity:** Medium
**Blocks:** —
**Blocked by:** —
**Module:** `calendar-ui`

---

## Problem

`HijriCalendarLabels` is annotated `@Immutable` (`HijriCalendarLabels.kt:17`) and its four function
fields are `kotlin.Function1` / `kotlin.Function2`, which the Compose stability inference rates
**unstable**. The explicit annotation overrides the inference, so the compiler believes the class is
stable when its fields are not.

Verified in the compiled bytecode:

```bash
javap -v calendar-ui/build/classes/kotlin/desktop/main/\
  com/muazdev/hijricalendar/ui/HijriCalendarLabels.class | grep -i immutable
#   d2=[... "HijriCalendar:calendar-ui","Landroidx/compose/runtime/Immutable;"]
javap -v .../HijriCalendarColors.class | grep -i 'stable\|Immutable'
#   no Immutable annotation — $stable is emitted by *inference*
```

The asymmetry is the tell. `HijriCalendarColors` has **no** annotation and is nonetheless stable,
because `Color` and `Dp` are inline value classes over primitives and the compiler infers
stability. `HijriCalendarLabels` has an annotation that *overrides* inference in the wrong
direction.

## Why it matters

`@Immutable` promises the compiler "if the instance is equal, nothing observable has changed".
With function fields, `data class` `equals` falls back to **reference** equality for lambdas, so
there are two ways to be wrong:

- **The label object is rebuilt each composition.** A consumer writing
  `HijriCalendarLabels(hijriMonthName = { _, m -> … })` inline produces a new lambda identity every
  recomposition, so the object is never equal to its predecessor and nothing skips. The class is
  marked stable, so the compiler does not even warn.
- **A lambda captures changing state.** A consumer writes
  `HijriCalendarLabels(hijriMonthName = { _, m -> names[currentLocale] })` where `currentLocale`
  is a snapshot state. The lambda identity never changes, so the label object compares equal, so the
  consumer is skipped — and the month name goes stale until something *else* invalidates it.

Neither is a compiler error and neither is a crash. Both are "the header shows the wrong month".

There is also a cost in the other direction: `remember(currentMonth, labels)` at
`HijriCalendar.kt:37, 45, 85` uses `labels` as a key, so an unstable-by-identity `labels` throws
away those caches on every recomposition.

## Proposed change

**1. Remove `@Immutable` from `HijriCalendarLabels`** and let the compiler infer. Four `Function`
fields → unstable → the compiler correctly refuses to skip, and warns at the consumer's call site
where the fix belongs.

**2. Leave `HijriCalendarColors` alone.** It is stable by inference and needs no annotation. Adding
one is harmless but redundant; removing nothing is fine. Do **not** add `@Immutable` to
`HijriCalendarDefaults` either — `colors()` is `@Composable` and its stability is about the
composable, not the object.

**3. If you want the skipping back, fix the type, not the annotation.** Offer a
`@Composable`-free immutable variant for the common case — e.g. a `HijriCalendarLabels.of(...)`
that resolves the lambdas to `String` tables eagerly:

```kotlin
public data class HijriCalendarLabels(
    val hijriMonthNames: List<String> = CalendarNames.englishHijriMonths,
    val gregorianMonthNames: List<String> = CalendarNames.englishGregorianMonths,
    val weekdayShortNames: Map<WeekDay, String> = …,
    val dayContentDescription: (CalendarDay) -> String = …,   // still a function
)
```

A `List<String>` is stable, so a fully-table-driven label object is genuinely `@Immutable` and skips.
Keep the lambda form for callers who need per-call computation, as a separate type or a documented
escape hatch.

**4. Document the caching implication** at the `HijriCalendarLabels` KDoc: this object is used as
a `remember` key at three sites, so **construct it once and hold it** — do not build it inline in a
composable if you care about recomposition cost.

## Done when

- [ ] `rg "@Immutable" calendar-ui/src/commonMain` shows the annotation only on types whose fields
      are genuinely stable (or is gone)
- [ ] A Compose stability report (`./gradlew :calendar-ui:compileKotlinDesktop
      -Pplugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=…`) shows
      `HijriCalendarLabels` as unstable
- [ ] The KDoc states the `remember`-key consequence

## Notes

- **Verify with the compiler, not by reading.** The Compose compiler plugin emits a stability report;
  use it rather than reasoning from the annotation. This finding came from `javap`, and an earlier
  reading of the source ("`HijriCalendarColors` has no `@Immutable`, so it is unstable and 42 cells
  never skip") turned out to be **wrong** — inference makes it stable. Getting this backwards would
  have produced a ticket for a bug that does not exist.
- `CalendarDay` in `calendar-core` has the same annotation pattern (`CalendarDay.kt:3`) and is a
  better-behaved case: its fields are `HijrahDate?` / `PakistanHijriDate?` / `ObservedHijriDate?` /
  `LocalDate` / `Boolean` / `Int`, all stable. Worth a note there too, but it is not this module's
  ticket.
- Low blast radius, but it is the kind of thing that produces an unreproducible "the month name is
  wrong" report two releases later.