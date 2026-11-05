#!/usr/bin/env bash
#
# Consumer-resolution gate.
#
# Publishes this repo's four modules into a scratch local repository (build/consumer-repo) and then
# compiles three throwaway consumers against *only* those published artifacts. Nothing inside the main
# build can see a dependency-scope mistake: a module's `implementation` dependencies are on its own
# compile classpath and on the sample app's (which declares its own), so every in-repo check stays
# green while the published POM is wrong. Only a consumer resolving the POM sees the defect.
#
# Three consumers cover the three installable artifacts:
#   consumer-glance  — hijri-calendar-widget-glance (widgets)
#   consumer-ui      — hijri-calendar-compose (the artifact the README tells people to install)
#   consumer-core    — hijri-calendar-core (pure data model, no Compose plugin)
#
# To exercise a new public API, add a line naming it in the matching consumer's source file.
# If the library declares the dependency that type comes from as `implementation`, this script fails.

set -euo pipefail

cd "$(dirname "$0")/../.."

REPO="build/consumer-repo"

# The version `hijri.publish` stamps, read from the same source `check-doc-versions.sh` reads.
# Without this the consumer pins a literal and every release silently breaks the gate (it resolves
# the previous version's POM, or nothing): read it rather than hard-code it.
LIBRARY_VERSION=$(sed -n 's/.*publishing\.version.*as? String ?: "\([^"]*\)".*/\1/p' \
    build-logic/src/main/kotlin/hijri.publish.gradle.kts | head -1)
if [[ -z "$LIBRARY_VERSION" ]]; then
    echo "could not read the publish version from build-logic/src/main/kotlin/hijri.publish.gradle.kts" >&2
    exit 1
fi


# Wipe first: a stale artifact from a previous run would satisfy the consumer and hide a
# regression in the current one.
rm -rf "$REPO"
rm -rf .github/consumer-check/build .github/consumer-check/.gradle
rm -rf .github/consumer-check/consumer-glance/build .github/consumer-check/consumer-glance/.gradle
rm -rf .github/consumer-check/consumer-ui/build .github/consumer-check/consumer-ui/.gradle
rm -rf .github/consumer-check/consumer-core/build .github/consumer-check/consumer-core/.gradle

echo "==> Publishing the four modules to $REPO"
./gradlew --console=plain publishAllPublicationsToConsumerCheckRepository

echo "==> Compiling consumer-glance against the published artifacts only"
./gradlew --console=plain -p .github/consumer-check -PlibraryVersion="$LIBRARY_VERSION" :consumer-glance:compileDebugKotlin

echo "==> Compiling consumer-ui against the published artifacts only"
./gradlew --console=plain -p .github/consumer-check -PlibraryVersion="$LIBRARY_VERSION" :consumer-ui:compileDebugKotlin

echo "==> Compiling consumer-core against the published artifacts only"
./gradlew --console=plain -p .github/consumer-check -PlibraryVersion="$LIBRARY_VERSION" :consumer-core:compileDebugKotlin

echo "==> Consumer resolution OK"