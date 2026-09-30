# Issue 07: `@Preview` composables ship in the published artifact

**Severity:** Medium
**Blocks:** [Issue 06](UI-06-public-surface-and-docs.md)
**Blocked by:** —
**Module:** `calendar-ui`

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

- [ ] `rg "PreviewsKt|ComposableSingletons" calendar-ui/api/` returns nothing
- [ ] `rg "tooling.preview" calendar-ui/build.gradle.kts` returns nothing
- [ ] `commonMain` contains no `@Preview`
- [ ] Previews still render in Android Studio
- [ ] `apiDump` run; `CHANGELOG.md` records the removal

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