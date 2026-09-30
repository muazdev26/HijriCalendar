# Issue 6: Core purity — move presentation/i18n out, re-type the date bounds

**Severity:** Medium
**Blocks:** [Issue 3](CORE-03-api-stability.md)
**Blocked by:** [Issue 1](CORE-01-mode-aware-month-range.md)
**Module:** `calendar-core` → `calendar-ui`, `calendar-widget-data`, sample

---

## Problem

Four unrelated things, all the same root cause: `calendar-core`'s boundary was never drawn
deliberately, so types accreted into it.

### a. `DateDisplayMode` is presentation

```kotlin
// calendar-core/.../DateDisplayMode.kt — the entire file
enum class DateDisplayMode { HIJRI_ONLY, GREGORIAN_ONLY, BOTH }
```

Seven lines, zero KDoc, and **no consumer in core**. Every user is in `calendar-ui` or a sample.
It has no business in a module whose stated job is data models and the state holder.

### b. The **English** name lists were duplicated, and had already drifted

`calendar-core/.../UrduCalendarNames.kt` holds Urdu month, Gregorian-month and weekday names, and
its KDoc claims that "month names, weekday names and the Gregorian month list can never drift
apart". That claim was **true for Urdu** — `WidgetLocalization` delegates to `UrduCalendarNames`
rather than copying it, so Urdu had exactly one owner all along. This part of the finding was
wrong.

The English defaults were a different story. Each consumer module held its **own private copy**:

- `calendar-ui/.../HijriCalendarLabels.kt:6-17` — `DefaultHijriMonthNames`, `DefaultGregorianMonthNames`
- `calendar-widget-data/.../WidgetDataApi.kt:17-35` — `DefaultHijriMonthNames`, `DefaultGregorianMonthNames`

and six of the twelve Hijri names had already diverged:

| Month | `calendar-ui` | `calendar-widget-data` |
|---|---|---|
| Rabi al-Awwal | `Rabi al-Awwal` | `Rabi' al-awwal` |
| Rabi al-Thani | `Rabi al-Thani` | `Rabi' al-thani` |
| Jumada al-Ula | `Jumada al-Ula` | `Jumada al-ula` |
| Jumada al-Akhirah | `Jumada al-Akhirah` | `Jumada al-akhirah` |
| Shaban | `Shaban` | `Sha'ban` |
| Dhul-Qadah | `Dhu al-Qadah` | `Dhu al-Qa'dah` |

So an in-app month header and a placed widget header rendered different text for the same month.
The anti-drift guarantee the KDoc made was true where it did not need to be and false where the
bug was.

### c. `minDate` / `maxDate` are evaluated in the wrong space

```kotlin
// calendar-core/.../HijriCalendarState.kt:43-44
val minDate: HijrahDate? = null,
val maxDate: HijrahDate? = null,
```

`HijrahDate` is the **Umm al-Qura** type. In Pakistan mode the grid is built from the `FIXES`
table; in observed mode from the override-shifted chain. Range clamping therefore compares
UAQ-space bounds against Pakistan- or observed-space cells, and silently means something
different in each of the three modes. `selectDate` even applies the range check
(`:205`, `:320-324`) only on the UAQ path — `selectPakistanDate` (`:139-145`) and
`selectObservedDate` (`:152-158`) skip it entirely, so the bounds do not apply consistently even
within one mode.

### d. Dead code in the state holder

```kotlin
// calendar-core/.../HijriCalendarState.kt:317-318
private val hasObservedOverrides: Boolean
    get() = _overridesRevision >= 0 && HijriMonthOverrides.all().isNotEmpty()
```

`_overridesRevision >= 0` is always true — the counter starts at `0L` and only increments. A
leftover from a removed branch that now reads as a guard.

### e. `WeekDay.index` is an undocumented persisted wire format — a latent bug

Folded in from the [KDoc audit](INDEX.md#kdoc-audit-data-class-fields). This one is not a style
issue:

```kotlin
// calendar-core/.../WeekDay.kt:24
val index: Int get() = ordinal
```

That ordinal is **persisted on devices**, in two places:

- `WidgetOptions.firstDayOfWeekIndex` (`WidgetOptions.kt:26`), stored as JSON in the Glance
  preferences store *and* the iOS app group
- Glance `SharedPreferences` (`HijriWidgetConfig.kt:252-253`), guarded only by
  `.coerceIn(0, 6)`

`coerceIn` catches an out-of-range value but **cannot** detect that ordinal `3` now names a
different day. Reordering the `WeekDay` enum would silently change the first day of week on
every already-placed widget. That is exactly the failure `WidgetOptionsJson` was designed to
prevent — AGENTS.md: *"enums by name (`"URDU"`), never by ordinal — reordering an enum entry
must not reinterpret a stored widget"* — and `firstDayOfWeekIndex` reintroduces it one layer
down. `WeekDay` has exactly one KDoc block, on `WEEKEND_DAYS`; the ordinal carries no warning at
all.

## Proposed change

**1. `DateDisplayMode` → `calendar-ui`.** It is a rendering option. Add a deprecated typealias in
core for one release if you want a soft migration.

**2. Consolidate calendar names** behind one owner: `CalendarNames` in core holds the English
lists, `UrduCalendarNames` keeps the Urdu ones, and both consumers delegate. Not one
parameterized type — the lists are compiled-in constants, so there is nothing to parameterize
and a lookup table would only add indirection on a path that runs once per month header.

**3. Re-type `minDate` / `maxDate`.** Make them space-aware. Cleanest is to make the bound a
small value type carrying what it bounds, or to have them follow the active mode the way
`CalendarDay`'s three date slots do. Either way, `selectPakistanDate` and `selectObservedDate`
must apply the same range check `selectDate` does.

**4. Delete the dead conjunct** at `HijriCalendarState.kt:318`.

**5. Fix `WeekDay`.** Stop persisting the ordinal. Store the day *name*
(`"SATURDAY"`) and resolve it back, matching what `WidgetOptionsJson` already does for every
other enum. Add a decode fallback for already-stored ordinals (a fenced migration, like
`decodeLegacyOrdinalJson` in `HijriWidgetConfig.kt`) so existing widgets do not reset. Then
document the field on `WeekDay.index` regardless — and consider `@Deprecated`-ing it once the
migration ships.

## Shipped

- **`b` — names.** New `CalendarNames` in core owns the English Hijri, Gregorian and weekday
  lists. `HijriCalendarLabels`' default lambdas and `WidgetLocalization`'s three defaults now read
  from it; both private copies are deleted. The widget variant won the transliteration
  disagreement, since it is the published `WidgetLocalization.englishHijriMonthNames` surface and
  matches the common ALA-LC style. **This changes six in-app month names**, which is the point of
  a single owner; it is called out here because it is a user-visible string change, not just a
  refactor.

- **`d` — dead conjunct.** `hasObservedOverrides` lost `_overridesRevision >= 0`, which was always
  true (the counter starts at `0L` and only increments). The revision read that made it a
  snapshot dependency was a side effect of that dead conjunct, so it is now stated explicitly in
  the KDoc rather than left to look accidental.

- **`e` — `WeekDay`.** `WeekDay.index` is a persisted wire format spanning `WidgetOptions`,
  the Glance preferences store, a `SharedPreferences` legacy path and two Swift call sites.
  Renaming the field to store a *name* is a schema migration across four modules plus Swift —
  disproportionate for a hazard that is currently under control. Instead the failure mode is
  closed off directly: `WeekDayOrdinalTest` pins all seven ordinals, so **reordering the enum
  fails the build** rather than silently changing the first day of week on every already-placed
  widget. That is a stronger guarantee than migrating the wire format, which would still need
  the pinned order to read existing values. `WeekDay.fromIndex` was added so callers no longer
  index `entries` directly, and both the class and the property now document the constraint.

- **`a` — `DateDisplayMode` → `calendar-ui`.** Moved to
  `calendar-ui/.../ui/DateDisplayMode.kt`. There was no way to soften this with a typealias:
  core cannot depend on ui, so core cannot keep naming the type even as an alias. Seven
  `calendar-ui` files lost their import (they share the package now) and six sample files
  repointed to the `ui` package. Both sample platforms persist it with `valueOf(name)`, so the
  move breaks no stored preference — verified before moving rather than after.

- **`c` — space-aware `minDate`/`maxDate`.** This was worse than the finding said. `minDate` and
  `maxDate` were not merely *inconsistent* across modes, they were **ignored outright** on two of
  the three: `toPakistanCalendarMonth` and `toObservedCalendarMonth` never received the bounds and
  hardcoded `isDisabled = false`. `HijriCalendarState.selectPakistanDate` and
  `selectObservedDate` did not consult them either. So a bounded calendar silently became an
  unbounded one whenever Pakistan mode or a month-length override was active.

  The fix keeps `minDate`/`maxDate` as `HijrahDate?` — public API unchanged — but stops
  *comparing* them as Hijri dates, which is what made the other two spaces inexpressible. New
  internal `DateWindow` resolves both bounds into the real-world Gregorian days (shifted by
  `adjustmentDays`, so the window travels with the grid) and every cell is tested against
  `CalendarDay.localDate`, the one real-world day each cell already knows and all three spaces
  already agree on. A forced 30th of a 29-day month — which has no Umm al-Qura coordinate to
  compare against — is now bounded like any other cell.

  Net effect: the bounds mean the same interval in all three modes. The *count* of selectable
  cells still differs between modes, because the modes disagree about which real-world day the
  "1 of the month" is; that is correct, not a leak.

## Done when

- [x] `a` — `DateDisplayMode` has no remaining reference in `calendar-core`
- [x] `b` — exactly one module owns each language's name lists; the other call sites delegate
- [x] `c` — a test asserts `minDate`/`maxDate` behave consistently across all three modes, and
      that they actually apply on the Pakistan and observed selection paths
- [x] `d` — no always-true conjunct in the state holder
- [x] `e` — a test decodes stored ordinals `0`..`6` to the correct day, and a reorder fails CI

## Tests added

- `WeekDayOrdinalTest` (4) — pins the persisted ordinal mapping; a reorder fails the build.
- `DateWindowTest` (8) — the bounds bite in all three modes, an unbounded calendar stays
  unbounded, `adjustmentDays` shifts the window with the grid, and out-of-window selection is
  rejected in each selection space.
- `MonthLengthOverridesTest` (+3) — `ObservedHijriDate.localDate` belongs to the table that
  produced it, not the process default.
- [ ] All four test suites + `:sample-android-app:assembleDebug` + the iOS CI job green
- [ ] `DateDisplayMode`, `WeekDay`, `ObservedHijriDate` and `PakistanHijriDate` have the KDoc
      called for in the [audit](INDEX.md#kdoc-audit-data-class-fields)

## Notes

- These are **breaking changes**, accepted deliberately. `DateDisplayMode` and `UrduCalendarNames`
  are on the published surface of `calendar-core`; both are `api` in a 1.0.0 artifact.
- Item 5 needs both the Android and iOS sides: `HijriWidgetConfig.kt` and
  `iosApp/Shared/HijriSharedOptions.swift` both read `firstDayOfWeekIndex`, and the Swift side
  constructs it as `Int32` at `WidgetCatalogView.swift:61`.
- `ObservedHijriDate.monthLength` needs KDoc explaining that it is why the saver persists a
  fourth integer. Without it, a future reader will "simplify" the saver and break restore.
- `PakistanHijriDate` should document its `MIN_YEAR..MAX_YEAR` range on the type; right now the
  only guard is a `require` several functions away.
