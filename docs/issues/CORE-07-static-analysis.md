# Issue 7: Static analysis and a docs-freshness check

**Severity:** Medium
**Blocks:** —
**Blocked by:** —
**Module:** repo-wide + CI
**Independent of the other six — can run any time.**

---

## Problem

### a. No static analysis at all

Confirmed absent from `gradle/libs.versions.toml` and every `build.gradle.kts`: no detekt, no
ktlint, no spotless. `.github/workflows/build.yml` runs two jobs — the four test suites plus
`:sample-android-app:assembleDebug` on ubuntu, and iOS compile + `xcodebuild` on macos. Nothing
checks style, complexity, or the smell classes that this module's correctness argument depends
on (long parameter lists, too many return statements, swallowed exceptions).

That last one matters here specifically. The biggest finding in this review is eleven
`catch (_: Exception)` sites ([Issue 5](CORE-05-exception-semantics.md)) — precisely
what detekt's `SwallowedException` / `TooGenericExceptionCaught` rules flag. The tooling that
would have caught it has never been run.

### b. `CONTRIBUTING.md` has already drifted

The prior review ([`PRINCIPAL-REVIEW-2026-09-15.md`](../PRINCIPAL-REVIEW-2026-09-15.md)) raised
**P0 #2: "AGENTS.md version drift"** — it stated Kotlin 2.4.0 / Compose 1.11.1 / version
`alpha03` while the catalog said 2.4.20 / 1.12.0 / alpha08. It was fixed by hand.

The class has since recurred, in a different file:

| Claim in `CONTRIBUTING.md` | Reality |
|---|---|
| "JDK 11+" | CI uses Java 21; `jvmTarget` is JVM_11 but the build JDK is 21 |
| "Android Studio Ladybug (2024.2.1) or later" | AGP 9.x requires a much newer Studio |
| Lists 5 modules | There are 8: also `calendar-widget-data`, `calendar-widget-glance`, `iosApp` |
| No mention of the test command | AGENTS.md documents it |
| No mention of iOS | An entire target platform is absent |

A hand-sync fix does not hold. The second occurrence proves it.

## Shipped

- **detekt 1.23.8 + `detekt-formatting`**, applied from the root to every subproject. Applying
  it at the root only analysed the root project, which has no Kotlin sources — the task reported
  `NO-SOURCE` and looked like a clean pass. A `detekt` extension in a `subprojects { }` block with
  `source.setFrom(files("src"))` is what makes it see Kotlin Multiplatform source sets, which have
  no `src/main`.

- **`config/detekt/detekt.yml`** builds on detekt's defaults and records only the deviations plus
  the rules this repo actually wants, each with the defect class that motivated it
  (`SwallowedException`/`TooGenericExceptionCaught` for the eleven broad catches of
  [Issue 5](CORE-05-exception-semantics.md), `LongParameterList` for the configuration-object
  pressure of [Issue 2](CORE-02-scope-month-overrides.md), and the complexity rules for the
  month-walking functions).

- **Per-module baselines** in `config/detekt/baseline-<module>.xml` (223 lines across six
  modules). The first attempt used one shared `baseline.xml` for all projects, which quietly kept
  only the last module's findings — every `detektBaseline` run overwrote the previous one. That
  is the failure mode a shared path hides, so the per-module filename is deliberate.

- **Verified in both directions**: `detekt` is green against the committed baselines, and a
  deliberately added `catch (e: Exception) {}` fails the build. Reverted after checking.

- **`./.github/scripts/check-doc-versions.sh`**, wired into the ubuntu CI job. It asserts the
  version claims in `AGENTS.md` against `gradle/libs.versions.toml` and
  `gradle/wrapper/gradle-wrapper.properties`.

  It found real drift on its first run, which is the argument for having it: `AGENTS.md` claimed
  **AGP 9.3.1 / Gradle 9.5.0** while the catalog and wrapper said 9.4.1 and 9.6.0. Fixed.

- **`CONTRIBUTING.md` rewritten.** It listed 5 modules (there are 8), claimed JDK 11+ (CI uses 21),
  recommended Android Studio Ladybug (AGP 9.x needs much newer), and did not mention the test
  command or iOS at all. Rather than re-state the corrected facts — which is how it drifted in the
  first place — it now links to `AGENTS.md` for commands and keeps only the onboarding path.

## Known limitations

- **The docs check is deliberately narrow.** It asserts the four version claims that have actually
  drifted, not the whole document. It will not catch a new wrong claim of a kind it does not know
  about, and it does not check module lists or command lines. The deeper fix is to stop restating
  facts in prose at all, which is why `CONTRIBUTING.md` now points at `AGENTS.md` instead of
  duplicating it.

- **`apiCheck` and `detekt` do not overlap in what they catch.** `apiCheck` is
  signature-only: during [Issue 3](CORE-03-api-stability.md) a deliberate change to
  `ordinal` → `this.ordinal + 1` passed it, correctly. Behavioural regressions need tests, and
  style regressions need detekt. Neither substitutes for the other.

- **`sample-shared` opts out of `explicitApi()`** (it is not published) but is still covered by
  detekt.

## Done when

- [x] detekt is applied to every module and sees Kotlin Multiplatform source sets
- [x] Per-module baselines are committed and `detekt` is green
- [x] `detekt` is in CI, and a new finding fails it (verified, then reverted)
- [x] A docs-freshness check is in CI, and it caught real drift on its first run
- [x] `CONTRIBUTING.md` corrected and no longer duplicates the commands
- [ ] All four test suites + `:sample-android-app:assembleDebug` + the iOS CI job green (final
      verification pass)
