# Issue 3: API stability gate for the published artifact

**Severity:** Critical
**Blocks:** —
**Blocked by:** [Issue 1](CORE-01-mode-aware-month-range.md), [Issue 5](CORE-05-exception-semantics.md), [Issue 6](CORE-06-core-purity.md)
**Module:** build-logic + all published modules
**Do this LAST** — `explicitApi()` freezes whatever the others decide.

---

## Problem

`calendar-core` is published (`hijri.publish` → JitPack, currently at `1.0.0`) and re-exports
three dependencies as `api` (`build.gradle.kts:8-12`):

```kotlin
commonMain.dependencies {
    implementation(libs.kotlinx.datetime)
    api(libs.kotlinx.serialization.core)
    api(libs.kotlinx.collections.immutable)
    api(libs.hijrah.datetime)          // third-party: HijrahDate is in our public signatures
}
```

So `HijrahDate`, `ImmutableList` and the serialization annotations are all part of our
compatibility surface, and there is **nothing** preventing a commit from changing any of it
silently. Confirmed absent from the whole repo: `explicitApi`, `binary-compatibility-validator`,
`apiDump`, any `.api` file, any detekt/ktlint/spotless config.

### Why this is the highest-severity finding here

The state saver encodes a **positional** format tightly coupled to the public constructor:

```kotlin
// calendar-core/.../HijriCalendarState.kt:414-453
internal fun hijriCalendarStateSaver(config: HijriCalendarStateConfig) = Saver(
    save = { state -> buildList {
        add(state.currentMonth.year)
        add(state.currentMonth.month.number)
        add(if (config.pakistanDates) 1 else 0)
        // then 0-4 more positional ints, indexed by a selection-type tag
    } },
    restore = { saved -> HijriCalendarState(
        initialMonth = HijrahYearMonth(saved[0], saved[1]),
        ...
    ) },
)
```

This is a **persisted format on someone else's device**. Consequences of an unguarded change:

- Reorder or remove a constructor parameter → every saved `rememberSaveable` state restores
  garbage, and `saved[4]` throws `IndexOutOfBoundsException` on restore.
- Reorder a `WeekDay` enum entry (see [Issue 6](CORE-06-core-purity.md)) → saved
  widget options reinterpret silently, with no exception to grep for.
- Reorder a `PakistanHijriDate` or `ObservedHijriDate` field → the same, via the
  `SELECTION_PAKISTAN` / `SELECTION_OBSERVED` tags at `:489-492`.

Every one of these **fails silently or at restore time, not at compile time.** A consumer on
1.0.0 cannot detect it, and neither can we. That is the failure class that costs a release.

## Proposed change

**1. `explicitApi()`.** Add to `build-logic/src/main/kotlin/hijri.multiplatform.library.gradle.kts`
so every KMP module gets it:

```kotlin
kotlin {
    explicitApi()
    ...
}
```

This forces every public declaration to state its visibility and return type explicitly. In a
KMP module it also disallows public declarations that leak `internal` types. Expect a
first-pass diff that adds `public` — mechanical, but it will surface genuinely accidental public
declarations, which is the point.

Apply it to `calendar-widget-data` too (it declares its own `kotlin { }` block rather than using
the convention plugin — see AGENTS.md).

**2. Binary-compatibility validator.** Add `org.jetbrains.kotlinx.binary-compatibility-validator`,
run `apiDump` once, and commit the `.api` files. Wire `apiCheck` into CI so any public signature
change fails the build instead of shipping.

For a KMP module the dump is per-target (`.api` files under `api/`), which is fine — it also
means an accidental change to the *iOS* surface is caught, which matters here because
`HijriCalendarState` and `CalendarMonth` both cross into Swift.

**3. Document the saver format as a versioned contract.** The positional `List<Int>` is fine as
an encoding; what is missing is a stated promise. Add to the KDoc at `:402-411`: the exact
layout, the meaning of each `SELECTION_*` tag, and that **appending is safe while reordering or
removing is not**. A reader who knows that will not casually reorder the list.

If you want belt and braces, add a leading format-version int so a future change can migrate
rather than break — but do this *before* `apiCheck` freezes, not after.

**4. Consider a CHANGELOG.** The batch of breaking changes from Issues 1, 2 and 6 needs to be
discoverable. AGENTS.md notes publishing is deferred to JitPack, so a `CHANGELOG.md` with an
`Unreleased` section is enough for now.

## Shipped

- **`explicitApi()`** is on in `hijri.multiplatform.library`, and hand-added to
  `calendar-widget-data` and `calendar-widget-glance`. ~120 declarations gained an explicit
  `public` plus a return type. A first-pass `sed` would have been wrong in two places that this
  exercise made obvious: `const val DAYS_IN_WEEK = 7` needs an explicit `Int`, and a delegating
  `fun setMonthLength(...) = default.setMonthLength(...)` inherits a type that the compiler will
  not infer for you. Both were silent under the old build and loud now, which is the point.

  `sample-shared` reuses the same convention for its Compose/iOS wiring but is not published, so
  it opts out with `hijri.explicitApi=false` in its `gradle.properties` rather than paying for
  ceremony.

- **Binary-compatibility validator 0.18.0**, applied once at the root (per-module application is
  a double-apply error, since the root application propagates). Dumps committed for
  `calendar-core`, `calendar-ui` and `calendar-widget-data`, each as both a JVM
  `api/desktop/*.api` and a `api/*.klib.api`.

- **KLib validation is enabled** (`apiValidation { klib { enabled = true } }`), which is opt-in
  and experimental upstream. Without it the dump is JVM-only and the iOS surface — where
  `HijriCalendarState` and `CalendarMonth` cross into Swift through the `calendar` and
  `WidgetCalendar` frameworks — would be entirely ungated. That was the point of the ticket.

- **`apiCheck` runs in CI on the macOS job**, not the ubuntu one: the KLib dump spans the iOS
  targets and Kotlin/Native cannot produce those klibs on a Linux host. Verified both ways — a
  deliberate signature break (`fromIndex(Int)` → `fromIndex(Long)`) fails with the expected diff,
  and reverting it goes green.

  Worth recording what this does **not** catch: my first verification attempt changed
  `ordinal` to `this.ordinal + 1`, and `apiCheck` passed. That is correct — the validator
  compares signatures, not implementations. A behavioural regression needs a test, not this gate.

- **Saver KDoc** now states the index-by-index layout, what each `SELECTION_*` tag means, and the
  append-only contract, with the reason (a `rememberSaveable` bundle is a persisted format on
  someone else's device that no compiler checks). The migration advice points at the existing
  precedent, `WidgetOptionsJson.decodeLegacyOrdinalJson`.

- **`CHANGELOG.md`** created with an `Unreleased` section recording every breaking change from
  Issues 1, 2 and 6, including the two user-visible ones that are easy to miss in a diff: the six
  in-app Hijri month names that changed spelling, and `minDate`/`maxDate` now actually applying in
  Pakistan and observed mode.

### Deliberately not done

- **`calendar-widget-glance` has no API gate.** It is a plain `com.android.library` on AGP 9's
  built-in Kotlin support, and the validator does not recognise that plugin — it registers no dump
  task and fails silently. Applying the standalone `org.jetbrains.kotlin.android` plugin would
  conflict with AGP's built-in one ("already on the classpath with an unknown version"), so
  closing this means moving the module off built-in Kotlin, which is a larger change than this
  ticket. It is listed in `apiValidation.ignoredProjects` with the reason, so the gap reads as a
  gap rather than as coverage.

- **No leading format-version int in the saver.** The KDoc contract and `apiCheck` are in place, and
  the existing format has no migration need yet. Adding a version int now would be a change to a
  format with no failing reader, in the same commit that freezes the API.

## Done when

- [x] `explicitApi()` is on; all modules compile with no `explicitApi` errors
- [x] `apiDump` output is committed for all published KMP modules, JVM **and** KLib (iOS) targets
- [x] `./gradlew apiCheck` runs in CI and fails on a deliberately broken public signature
      (verified, then reverted)
- [x] The saver KDoc states the format and the append-only contract
- [x] `CHANGELOG.md` records the breaking changes from Issues 1, 2 and 6
- [ ] All four test suites + `:sample-android-app:assembleDebug` + the iOS CI job green (final
      verification pass, with Issue 7)

## Notes

- **Sequence matters.** Issues 1, 2, 5 and 6 all change public signatures. Running
  `apiDump`/`apiCheck` first would mean re-dumping after each and training everyone to ignore
  the failures. Run it once, at the end, on the settled API.
- `explicitApi()` is a **strict** mode — it also rejects implicit types in public signatures.
  `HijriCalendarState`'s private helpers and the `Saver<..., List<Int>>` chain are the likely
  friction. That friction is the feature.
- The three `api` dependency re-exports are the real hazard, not our own declarations: we
  cannot control their evolution. If a future `hijrah-datetime` release changes `HijrahDate`,
  `apiCheck` will not catch it. Worth a note in the KDoc that consumers inherit that coupling.
- `calendar-widget-glance` is a plain `com.android.library` (per AGENTS.md) and does not use the
  convention plugin, so it needs the plugin applied separately or explicit exclusion. It is
  published, so it should be in scope.
