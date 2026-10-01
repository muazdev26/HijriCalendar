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

# read_publish_version -> the version `hijri.publish` stamps on every published module.
# It is a findProperty default inside a convention plugin rather than a catalog entry, which is
# exactly why the claims below went unchecked until this was added: the script only knew about
# libs.versions.toml and the wrapper, so AGENTS.md sat on "1.0.0-alpha08" for a long while
# after v1.0.0 was actually tagged.
read_publish_version() {
  sed -n 's/.*publishing\.version.*as? String ?: "\([^"]*\)".*/\1/p' \
    "build-logic/src/main/kotlin/hijri.publish.gradle.kts" | head -1
}

# Compose artifacts must not sit on their own version inside one release train.
#
# The failure this exists to catch: material-icons-extended was pinned to 1.7.3 while everything else
# resolved from Compose 1.12.0, five minors apart, in a *published* artifact -- and it still compiled,
# so nothing noticed until the ticket asked. See UI-13.
#
# A version-less entry is legitimate only when it is always consumed alongside a platform/BOM, which
# is how the androidx.compose.* entries below are used. Those are allow-listed rather than inferred,
# so adding a new version-less entry is a deliberate act rather than something the check silently
# tolerates.
BOM_MANAGED_COMPOSE_ENTRIES=(
  androidx-compose-bom          # the BOM itself
  androidx-compose-material3    # version from compose-bom
  androidx-compose-ui           # version from compose-bom
  androidx-compose-foundation   # version from compose-bom
  androidx-compose-ui-test-junit4    # version from compose-bom
  androidx-compose-ui-test-manifest  # version from compose-bom
)

is_bom_managed() {
  local candidate="$1" entry
  for entry in "${BOM_MANAGED_COMPOSE_ENTRIES[@]}"; do
    [[ "$candidate" == "$entry" ]] && return 0
  done
  return 1
}

# The Compose *platform* train is `composeMultiplatform`. Every org.jetbrains.compose.* artifact
# must resolve from it, either directly or through a shared `version.ref = "composeMultiplatform"`
# indirection (the `compose.*` aliases in the catalog). Anything else means a second train on the
# graph, which is the defect UI-13 describes.
#
# Note what is deliberately NOT allowed: a bare `version = "1.7.3"`, or a `version.ref` to some
# other key in this file. Both resolve, both compile, and both were invisible to a check that only
# asked "is there a version at all" -- the pin this check was written for had exactly that shape.
diverging_compose_entries() {
  LC_ALL=C grep -E "^[a-zA-Z0-9-]+[[:space:]]*=.*org\.jetbrains\.compose" "$CATALOG" \
    | LC_ALL=C grep -v 'version\.ref[[:space:]]*=[[:space:]]*"composeMultiplatform"' \
    | LC_ALL=C sed -E 's/^([a-zA-Z0-9-]+).*/\1/'
}

check_catalog_coherence() {
  local offenders="" line name

  # 1. org.jetbrains.compose.* on its own version.
  while IFS= read -r line; do
    [[ -z "$line" ]] && continue
    offenders+="  $line  -- org.jetbrains.compose artifact not on the composeMultiplatform train"$'\n'
  done < <(diverging_compose_entries)

  # 2. A Compose artifact with no version at all, beyond the BOM allow-list.
  while IFS= read -r line; do
    name=$(printf '%s' "$line" | LC_ALL=C sed -E 's/^([a-zA-Z0-9-]+).*/\1/')
    is_bom_managed "$name" && continue
    offenders+="  $name  -- no version, and not on the BOM allow-list"$'\n'
  done < <(LC_ALL=C grep -E "^[a-zA-Z0-9-]+[[:space:]]*=.*(org\.jetbrains\.compose|androidx\.compose)" "$CATALOG" \
             | LC_ALL=C grep -v "version\.ref\|version =\|version\.toml")

  if [[ -n "$offenders" ]]; then
    echo "error: $CATALOG has Compose artifacts outside the single Compose release train:" >&2
    printf '%s' "$offenders" >&2
    echo "  -- point it at version.ref = \"composeMultiplatform\" (directly or via a compose.*" >&2
    echo "     alias), or add it to BOM_MANAGED_COMPOSE_ENTRIES with a note on why the BOM covers" >&2
    echo "     it. A second train in a published artifact is a consumer's problem; see UI-13." >&2
    status=1
  else
    echo "ok:   every Compose artifact in $CATALOG is on the composeMultiplatform train or BOM-managed"
  fi
}

echo "Checking hand-written version claims against $CATALOG, $WRAPPER and build-logic"

check_doc_claim "AGENTS.md" "Kotlin" "$(read_catalog kotlin)" \
  "Kotlin $(read_catalog kotlin | sed 's/\./\\./g')"

check_doc_claim "AGENTS.md" "Compose Multiplatform" "$(read_catalog composeMultiplatform)" \
  "Compose Multiplatform $(read_catalog composeMultiplatform | sed 's/\./\\./g')"

check_doc_claim "AGENTS.md" "AGP" "$(read_catalog agp)" \
  "AGP $(read_catalog agp | sed 's/\./\\./g')"

check_doc_claim "AGENTS.md" "Gradle wrapper" "$(read_wrapper)" \
  "Gradle $(read_wrapper | sed 's/\./\\./g')"

check_doc_claim "AGENTS.md" "publish version" "$(read_publish_version)" \
  "version \`$(read_publish_version | sed 's/\./\\./g')\`"

check_catalog_coherence

exit "$status"
