# Issue 13: `material-icons-extended` on a stale release train

**Severity:** Low
**Blocks:** —
**Blocked by:** —
**Module:** `calendar-ui`, `gradle/libs.versions.toml`

---

## Problem

`build.gradle.kts:12` declares:

```kotlin
implementation(libs.material.icons.extended)
```

for exactly two icons (`HijriCalendarHeader.kt:58, 87`):

```kotlin
Icons.AutoMirrored.Filled.KeyboardArrowLeft
Icons.AutoMirrored.Filled.KeyboardArrowRight
```

Both are in **`material-icons-core`**, which is already on the compile classpath transitively via
`compose.material3` — confirmed on the resolved graph:

```
+--- org.jetbrains.compose.material:material-icons-extended:1.7.3
|    \--- org.jetbrains.compose.material:material-icons-extended-desktop:1.7.3
|         \--- org.jetbrains.compose.material:material-icons-core:1.7.3
```

So the `extended` artifact adds nothing the module uses, on a **published** artifact where
dependency weight is a consumer's problem.

## Worse: the version is on a different release train

```toml
# gradle/libs.versions.toml
composeMultiplatform    = "1.12.0"     # line 4
materialIconsExtended   = "1.7.3"      # line 10
```

Everything else — foundation, material3, ui, resources — resolves from `composeMultiplatform`
1.12.0 via `compose.*`. The icons artifact is pinned separately at **1.7.3**, five minors behind,
and it brings `material-icons-core:1.7.3` along with it. A published calendar library mixing
Compose release trains is the kind of thing that surfaces as an obscure
`NoSuchMethodError` or a duplicated-class warning in a consumer's app.

`check-doc-versions.sh` does not catch this: it asserts hand-written version claims in
`AGENTS.md` / `CONTRIBUTING.md` against the catalog, not catalog *coherence*.

## Proposed change

**1. Delete the dependency.** `material-icons-core` arrives with `compose.material3`, which the
convention plugin already declares (`hijri.multiplatform.library.gradle.kts:50`). Verify both icons
still compile.

**2. If a consumer later needs more icons, that is their `implementation`,** not the library's.

**3. Add a catalog-coherence check** to `check-doc-versions.sh`: assert every
`org.jetbrains.compose.*` version in `libs.versions.toml` is either `version.ref =
"composeMultiplatform"` or explicitly allow-listed as intentionally separate. That generalises the
existing script instead of adding a second one.

## Done when

- [ ] `rg "material.icons" calendar-ui/build.gradle.kts` returns nothing
- [ ] `:calendar-ui:compileKotlinDesktop` and `:calendar-ui:compileKotlinIosArm64` succeed
- [ ] `check-doc-versions.sh` fails if a `org.jetbrains.compose.*` entry diverges from
      `composeMultiplatform` without being allow-listed
- [ ] `CHANGELOG.md` notes the removed dependency

## Notes

- Low severity, and the 1.7.3 pin is plausibly a leftover from the alpha08 era rather than a
  deliberate choice. Confirm with `git log -p gradle/libs.versions.toml` before assuming.
- **Related finding in the same area:** `ui-tooling-preview` is `implementation` in `commonMain`
  of a published module (`build.gradle.kts:13`). That is
  [UI-07](UI-07-previews-out-of-abi.md) — same shape, different consequence, because its annotation
  ends up on public signatures rather than merely bloating the graph.
- Both dependencies are `implementation`, which is correct in principle: a Compose *library* should
  not force its consumers onto a specific Compose version. The problem is only that these two are
  unnecessary, and one of them is on the wrong train.