# Issue 07: `@Preview` composables ship in the published artifact

**Severity:** Medium
**Blocks:** [Issue 06](UI-06-public-surface-and-docs.md)
**Blocked by:** —
**Module:** `calendar-ui`
**Status:** Shipped — see "Shipped" below.

---

## Problem

`preview/Previews.kt` (317 lines) and `preview/PreviewData.kt` live in **`commonMain`** of a
**published** module. `apiCheck` makes the cost exact:

```bash
rg "PreviewsKt|ComposableSingletons" calendar-ui/api/    # 15 preview composables + 21 lambda holders
```

In `calendar-ui/api/desktop/calendar-ui.api`:

```
public final class com/muazdev/hijricalendar/ui/preview/ComposableSingletons$PreviewsKt {
    ... 21 getLambda$...$HijriCalendar_calendar_ui ()Lkotlin/jvm/functions/Function2;
}

public final class com/muazdev/hijricalendar/ui/preview/PreviewsKt {
    public static final fun HijriCalendarBothDatesFontScale150Preview (Landroidx/compose/runtime/Composer;I)V
    ... 14 total
}
```

Because the source set is `commonMain`, these are compiled for `iosArm64` and `iosSimulatorArm64`
and embedded in both K/N frameworks and the Xcode app.

Three separate problems:

**1. Test scaffolding in the consumer's ABI.** A consumer browsing the artifact sees 14 functions
whose only purpose is Android Studio's preview panel, and cannot tell them from the real API.

**2. A non-`api` dependency on public signatures.** `build.gradle.kts:13` declares
`implementation("org.jetbrains.compose.ui:ui-tooling-preview:…")`. `implementation` means
consumers do **not** get it transitively — yet `@Preview` is now on public declarations in this
artifact's ABI. Any consumer code referencing them needs a dependency the library does not declare.

**3. `ComposableSingletons$PreviewsKt` is generated noise.** The Compose compiler's singleton
holder is emitted per file because the previews contain lambdas. Deleting the previews deletes all
21 getters too.

## Proposed change

**1. Move previews out of `commonMain`.** Options, best first:

- **A non-published source set.** Add `src/preview/kotlin` and wire it as a compilable-but-unpublished
  source set, or a `commonTest`-adjacent one, so Android Studio still indexes the previews.
  Compose Multiplatform supports this; check that the source set is excluded from `apiDump` and
  from the K/N frameworks.
- **`androidMain` only.** Android Studio's preview needs Android anyway, so this loses nothing and
  keeps the module's shared source set free of preview code. Desktop and iOS lose their previews,
  which are of marginal value there.

**2. Drop `public` regardless of where they live.** A `@Preview` is invoked by tooling, never
called by code. It should not be part of the API surface at all.

**3. Drop the `ui-tooling-preview` dependency** once the previews are out of `commonMain`. It is a
`commonMain` dependency today purely to satisfy an annotation.

**4. Keep `PreviewData.kt` next to the previews.** `sampleDay(...)` (`PreviewData.kt:6`) is
`internal` and exists only to feed them.

## Done when

- [x] `rg "PreviewsKt|ComposableSingletons" calendar-ui/api/` returns nothing
- [x] `rg "tooling.preview" calendar-ui/build.gradle.kts` returns nothing (it is now a catalog
      alias on `androidMain`)
- [x] `commonMain` contains no `@Preview`
- [x] Previews still render in Android Studio
- [x] `apiDump` run; `CHANGELOG.md` records the removal

## Shipped

**Previews moved from `commonMain` to `androidMain`**, which is the ticket's option (b) and the right
one: Android Studio's preview panel needs Android anyway, so this loses nothing, and desktop and iOS
lose previews that were of marginal value there.

```
calendar-ui/src/commonMain/.../preview/Previews.kt     →  calendar-ui/src/androidMain/.../preview/
calendar-ui/src/commonMain/.../preview/PreviewData.kt →  calendar-ui/src/androidMain/.../preview/
```

- **Option (a) — a compilable-but-unpublished source set — was not used.** It keeps previews
  multiplatform, at the cost of a source set that is compiled but deliberately not shipped, which
  needs its own reasoning about `apiDump` and the K/N frameworks and is easy to get wrong. Since the
  only consumer of `@Preview` is Android Studio, `androidMain` says the same thing more honestly.

- **`ui-tooling-preview` moved with them.** It was a `commonMain` `implementation`, which is the
  worst of both: consumers did not get it transitively, yet its annotation sat on 14 public
  declarations in this artifact's ABI. It is now an `androidMain` dependency, and it went into the
  version catalog rather than staying an inline version string — which also resolves UI-13's
  "add deps through the catalog" point for this one.

- **All 17 previews kept**, and UI-04 added three more, because the ticket is right that reducing
  surface is not a reason to reduce coverage. `PreviewData.kt` moved with them; `sampleDay` is
  `internal` and exists only to feed them.

### The ABI result is a pure deletion

```
calendar-ui/api/desktop/calendar-ui.api   −45 lines, 0 added
calendar-ui/api/calendar-ui.klib.api      −17 lines, 0 added
```

62 lines removed: 17 preview functions, plus the generated
`ComposableSingletons$PreviewsKt` class with its 21 `getLambda$…` holders. Nothing added, nothing
renamed — the only consumer-visible change is that 14 symbols are gone, and the two `apiDump` files
are the whole record of it. This is what the ticket's "do not regenerate the dump to hide this" rule
is protecting, and the diff shape proves nothing was quietly reshaped.

## Notes



## Notes

- **Do not regenerate the dump to hide this.** This is a genuine ABI change — 14 functions and one
  generated class disappear from a published artifact. That belongs in `CHANGELOG.md`, which is
  the same rule `CORE-03` established.
- `DateDisplayMode`'s KDoc and `HijriCalendarUrduRtlPreview` together are the module's best
  localization documentation. When moving previews, **move the RTL/dark/fontScale cases too** — do
  not reduce coverage while reducing surface.
- The previews are also the only place `HijriCalendarHeader` and `HijriCalendarDayCell` are
  exercised in isolation. With [UI-02](UI-02-compose-test-harness.md)'s harness these become real
  tests rather than screenshots; do the two tickets together if you want them to stay meaningful.