#!/usr/bin/env bash
#
# Consumer-resolution gate.
#
# Publishes this repo's four modules into a scratch local repository (build/consumer-repo) and then
# compiles `.github/consumer-check` against *only* those published artifacts. Nothing inside the main
# build can see a dependency-scope mistake: a module's `implementation` dependencies are on its own
# compile classpath and on the sample app's (which declares its own), so every in-repo check stays
# green while the published POM is wrong. Only a consumer resolving the POM sees the defect.
#
# Caught so far: `calendar-widget-glance` declared `androidx.compose.ui` as `implementation` while
# four `public` composables take `Modifier` and `DpSize`, so the documented settings-screen
# integration path did not compile for anyone who added the dependency
# (docs/widgets/glance/WG-01-compose-runtime-scope.md).
#
# To exercise a new public API, add a line naming it in
# `.github/consumer-check/src/main/kotlin/.../ConsumerResolutionCheck.kt`. If the library declares
# the dependency that type comes from as `implementation`, this script fails.

set -euo pipefail

cd "$(dirname "$0")/../.."

REPO="build/consumer-repo"

# Wipe first: a stale artifact from a previous run would satisfy the consumer and hide a
# regression in the current one.
rm -rf "$REPO"
rm -rf .github/consumer-check/build .github/consumer-check/.gradle

echo "==> Publishing the four modules to $REPO"
./gradlew --console=plain publishAllPublicationsToConsumerCheckRepository

echo "==> Compiling the throwaway consumer against the published artifacts only"
./gradlew --console=plain -p .github/consumer-check compileDebugKotlin

echo "==> Consumer resolution OK"