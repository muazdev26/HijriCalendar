#!/usr/bin/env bash
#
# Fails when a version claim written by hand in the docs drifts from the build's own sources of
# truth (gradle/libs.versions.toml and gradle/wrapper/gradle-wrapper.properties).
#
# AGENTS.md and CONTRIBUTING.md have each carried a stale version at least once — the 2026-09-15
# review caught Kotlin 2.4.0 / Compose 1.11.1 / alpha08 in prose while the catalog said 2.4.20 /
# 1.12.0. Hand-syncing does not hold, so this checks instead.
#
# Deliberately narrow: it only asserts the claims that have actually drifted. It is not a general
# docs linter and does not try to remove duplication from the prose — see "Known limitations" in
# docs/issues/CORE-07-static-analysis.md.

set -euo pipefail

cd "$(dirname "$0")/../.."

CATALOG="gradle/libs.versions.toml"
WRAPPER="gradle/wrapper/gradle-wrapper.properties"
status=0

# check_doc_claim <file> <label> <expected value> <extended regex that must appear in the file>
check_doc_claim() {
  local file="$1" label="$2" expected="$3" pattern="$4"

  if [[ -z "$expected" ]]; then
    echo "error: could not resolve the expected $label from the build files" >&2
    status=1
    return
  fi

  if [[ ! -f "$file" ]]; then
    echo "skip: $file does not exist"
    return
  fi

  if grep -Eq "$pattern" "$file"; then
    echo "ok:   $file states $label as $expected"
  else
    echo "error: $file does not state $label as $expected -- update the doc or the build file" >&2
    status=1
  fi
}

# read_catalog <key> -> the value assigned to `key = "..."` in the version catalog.
read_catalog() {
  sed -n "s/^$1[[:space:]]*=[[:space:]]*\"\\([^\"]*\\)\".*/\\1/p" "$CATALOG" | head -1
}

# read_wrapper -> the Gradle version pinned by the wrapper.
read_wrapper() {
  sed -n 's/^distributionUrl=.*gradle-\([0-9.]*\)-.*$/\1/p' "$WRAPPER" | head -1
}

echo "Checking hand-written version claims against $CATALOG and $WRAPPER"

check_doc_claim "AGENTS.md" "Kotlin" "$(read_catalog kotlin)" \
  "Kotlin $(read_catalog kotlin | sed 's/\./\\./g')"

check_doc_claim "AGENTS.md" "Compose Multiplatform" "$(read_catalog composeMultiplatform)" \
  "Compose Multiplatform $(read_catalog composeMultiplatform | sed 's/\./\\./g')"

check_doc_claim "AGENTS.md" "AGP" "$(read_catalog agp)" \
  "AGP $(read_catalog agp | sed 's/\./\\./g')"

check_doc_claim "AGENTS.md" "Gradle wrapper" "$(read_wrapper)" \
  "Gradle $(read_wrapper | sed 's/\./\\./g')"

exit "$status"
