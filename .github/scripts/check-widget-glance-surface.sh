#!/usr/bin/env bash
#
# Public-surface gate for `calendar-widget-glance`.
#
# The module is in `apiValidation.ignoredProjects` (AGP 9's built-in Kotlin is not recognised by
# binary-compatibility-validator), so `apiCheck` registers no dump task for it. This is the
# substitute: the module's public surface is a checked-in list, so adding or renaming a public
# declaration shows up as a diff that a reviewer has to read.
#
# Deliberately not a *count* check. A count can be satisfied by removing one thing and adding
# another; a list forces each name to be looked at.
#
# What this catches that `check-consumer-resolution.sh` does not: a newly added public API. What that
# one catches that this does not: a removed one, and a dependency-scope mistake.
#
# Regenerate deliberately, after reviewing the diff:
#   ./gradlew :calendar-widget-glance:compileDebugKotlin
#   <this script> --print > calendar-widget-glance/api/public-surface.txt   (then re-add the comments)

set -euo pipefail

cd "$(dirname "$0")/../.."

SURFACE_FILE="calendar-widget-glance/api/public-surface.txt"
SRC_GLOB="calendar-widget-glance/src/main/java/com/muazdev/hijricalendar/widget/glance"

current() {
    # Every public declaration, top-level and member alike, as `Name` or `Type.member`.
    #
    # Members matter more than top-level declarations here: `HijriWidgetConfig` alone has more
    # public surface than the whole rest of the module, and a newly added *member* is exactly the
    # kind of thing that goes unnoticed when nothing diffs the surface. The enclosing type is tracked
    # by remembering the last top-level `public` declaration seen, which over-attributes a nested
    # type's members to its outer owner — deliberately, since being stricter is the safe direction.
    #
    # The type keyword list includes `data`, `sealed`, `abstract` and `open` because a top-level
    # `public data class` that this does not match is not registered as an owner at all, and its
    # members are then silently attributed to whatever class happened to precede it. That is worse
    # than over-attribution: the file asks for `HijriDaySelection.day` and the gate reports
    # `SomeEarlierClass.day`, so a reviewer sees a plausible-looking diff that is simply wrong.
    awk '
        /^public (suspend )?(const )?(data |sealed |abstract |open |value )*(class|object|interface) [A-Za-z_][A-Za-z0-9_]*/ {
            owner = $0
            sub(/^public (suspend )?(const )?(data |sealed |abstract |open |value )*(class|object|interface) /, "", owner)
            sub(/[^A-Za-z0-9_].*$/, "", owner)
            print owner
            next
        }
        /^public (suspend )?(const )?(fun|val|var) [A-Za-z_][A-Za-z0-9_]*/ {
            line = $0
            sub(/^public (suspend )?(const )?(fun|val|var) /, "", line)
            sub(/[^A-Za-z0-9_].*$/, "", line)
            print line
            next
        }
        /^    (public |@Composable$|@Composable )/ {
            line = $0
            sub(/^    /, "", line)
            if (line !~ /^public /) next
            sub(/^public (suspend )?(const )?(fun|val|var|class|object|interface) /, "", line)
            sub(/[^A-Za-z0-9_].*$/, "", line)
            if (line == "") next
            print (owner == "" ? "" : owner ".") line
        }
    ' "$SRC_GLOB"/*.kt | sort -u
}

if [[ "${1:-}" == "--print" ]]; then
    current | sort -u
    exit 0
fi

# Compare on *names*, ignoring the comment block in the checked-in file.
expected() {
    grep -vE '^\s*(#|$)' "$SURFACE_FILE" | sort -u
}

actual=$(current | sort -u)
want=$(expected)

if [[ "$actual" == "$want" ]]; then
    echo "==> calendar-widget-glance public surface unchanged"
    exit 0
fi

echo "==> calendar-widget-glance public surface changed:"
diff <(echo "$want") <(echo "$actual") || true
cat <<'EOF'

If this change is intended:
  1. Give the new declaration a reason in calendar-widget-glance/api/public-surface.txt — that file
     is the review surface, so an unexplained line defeats the point.
  2. If the new public API names a type from a new dependency, add a line naming it in
     .github/consumer-check/src/main/kotlin/.../ConsumerResolutionCheck.kt too, so a wrongly-scoped
     `implementation` fails the consumer-resolution gate.
  3. Record the change in CHANGELOG.md — this module has no apiCheck to catch the break, so the
     changelog is the only place a consumer learns about it.
EOF
exit 1