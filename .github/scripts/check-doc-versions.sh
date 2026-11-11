#!/usr/bin/env bash
#
# Fails when a version claim written by hand in the docs drifts from the build's own sources of
# truth (gradle/libs.versions.toml and the publish version in build-logic).
#
# The 2026-09-15 review caught Kotlin 2.4.0 / Compose 1.11.1 / alpha08 in prose while the catalog
# said 2.4.20 / 1.12.0. Hand-syncing does not hold, so this checks instead.
#
# Deliberately narrow: it only asserts the two things that can drift without anyone noticing —
# the README's publish version, and the toolchain floor it promises consumers. The catalog's own
# internal coherence (every Compose artifact on the composeMultiplatform train) is asserted below.
# It is not a general docs linter — see "Known limitations" in docs/issues/CORE-07-static-analysis.md.

set -euo pipefail

cd "$(dirname "$0")/../.."

CATALOG="gradle/libs.versions.toml"
status=0

# read_publish_version -> the version `hijri.publish` stamps on every published module.
# It is a findProperty default inside a convention plugin rather than a catalog entry, which is
# exactly why claims about it go unchecked without this: the script can read the catalog, but that
# file does not hold the published version.
read_publish_version() {
  sed -n 's/.*publishing\.version.*as? String ?: "\([^"]*\)".*/\1/p' \
    "build-logic/src/main/kotlin/hijri.publish.gradle.kts" | head -1
}

# check_file_contains <file> <label> <extended regex that must appear in the file>
check_file_contains() {
  local file="$1" label="$2" pattern="$3"

  if [[ ! -f "$file" ]]; then
    echo "error: $file does not exist — required file is missing" >&2
    status=1
    return
  fi

  if grep -Eq "$pattern" "$file"; then
    echo "ok:   $file documents $label"
  else
    echo "error: $file does not document $label -- update the doc or this script" >&2
    status=1
  fi
}

# Compose artifacts must not sit on their own version inside one release train.
#
# The failure this exists to catch: material-icons-extended was pinned to 1.7.3 while everything else
# resolved from Compose 1.12.0, five minors apart, in a *published* artifact -- and it still compiled,
# so nothing noticed until the ticket asked. See docs/issues/UI-13-dependency-hygiene.md.
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
# graph, which is the defect described in docs/issues/UI-13-dependency-hygiene.md.
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
    echo "     it. A second train in a published artifact is a consumer's problem." >&2
    status=1
  else
    echo "ok:   every Compose artifact in $CATALOG is on the composeMultiplatform train or BOM-managed"
  fi
}

echo "Checking the README's publish version and consumer toolchain floor"

# The published artifact version. A consumer pinning the JitPack coordinate gets exactly this
# version; a drift here means the README it tells people how to install is stale.
check_file_contains "README.md" "publish version hijri-calendar-compose:$(read_publish_version)" \
  "hijri-calendar-compose:$(read_publish_version | sed 's/\./\\./g')"

# The consumer floor. The README documents the *minimum* each tool may be, which is a contract with
# consumers and deliberately older than the catalog's build toolchain — so the guard pins the
# floor, not the catalog. Bump all three together or the README lies about what it supports.
check_file_contains "README.md" "consumer floor Kotlin 2.4.10" "Kotlin 2\\.4\\.10"
check_file_contains "README.md" "consumer floor Compose Multiplatform 1.11.0" "Compose Multiplatform 1\\.11\\.0"
check_file_contains "README.md" "consumer floor AGP 9.4.0" "AGP 9\\.4\\.0"

check_catalog_coherence

exit "$status"
