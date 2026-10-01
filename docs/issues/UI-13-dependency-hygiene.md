# Issue 13: `material-icons-extended` on a stale release train

**Severity:** Low
**Status:** Shipped — see "Shipped" below.
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

- [x] `rg "material.icons" calendar-ui/build.gradle.kts` returns nothing
- [x] `:calendar-ui:compileKotlinDesktop` and `:calendar-ui:compileKotlinIosArm64` succeed
- [x] `check-doc-versions.sh` fails if a `org.jetbrains.compose.*` entry diverges from
      `composeMultiplatform` without being allow-listed
- [x] `CHANGELOG.md` notes the removed dependency

## Shipped

The dependency is gone and nothing replaced it but ~100 lines of drawing code.

- **The two arrows are now `NavChevron.kt`.** Rather than depend on `material-icons-core` for two
  glyphs — which is how the 1.7.3 pin arrived in the first place, since `extended` was declared for
  icons that live in `core` — the module draws the chevron itself with `Path`/`drawLine`, mirrored
  from a single `rtl` flag. `core` is on the classpath transitively via `compose.material3` and
  would have compiled, so this is a deliberate choice over the cheaper fix: the library now has **no**
  icon dependency on either artifact, which is what the ticket's own point 2 asks for. Its KDoc
  records the RTL reasoning the `AutoMirrored` semantics used to supply.
- **`NavChevron` is a `Canvas` with a stable intrinsic size**, so the 48dp touch target from
  [UI-08](UI-08-touch-feedback.md) is unchanged; only the glyph inside it moved.
- **The catalog coherence check is real, and it was not on the first attempt.** The check shipped
  alongside the fix and was verified by re-injecting the original defect — and it **passed**. The
  filter asked "does this line mention a version at all", and the defect's two shapes both mention
  one: `version = "1.7.3"` inline, or a `version.ref` to a separate `materialIconsExtended` key.
  A check written from the ticket's wording ("every entry is *versioned*") cannot catch the bug the
  same ticket is about. It now asserts the stricter property — every `org.jetbrains.compose.*`
  entry resolves from `composeMultiplatform`, so a bare version or a foreign `version.ref` both
  fail — and both shapes were confirmed to `exit 1`. `androidx.compose.*` keeps the version-less
  BOM allow-list, which is a different mechanism and still legitimate.

**A note on the mechanism.** The version check is worth more than the dependency removal: it is the
only gate in the repo that would notice a *transitive* regression, since `apiCheck` is
signature-only, `detekt` does not read the catalog, and the `material-icons-extended` graph was
itself benign enough to compile and pass everything. It generalises the existing script rather than
adding a second one, per the ticket's point 3.

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