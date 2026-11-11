#!/usr/bin/env bash
# Fails if the widget render path or the sample app reads the process-global override table.
# The global is deprecated for 2.x source compatibility; the widget module must never use it.
set -euo pipefail
cd "$(dirname "$0")/../.."

status=0
check() {
  local label="$1" pattern="$2" paths="$3"
  local hits
  hits=$(grep -rEn "$pattern" $paths --include=*.kt 2>/dev/null | grep -vE ':\s*\*' | grep -vE '/\*\*' || true)
  if [[ -n "$hits" ]]; then
    echo "error: $label" >&2
    echo "$hits" >&2
    status=1
  else
    echo "ok:    $label"
  fi
}

check "widget glance does not read HijriMonthOverrides" \
  "HijriMonthOverrides\b" \
  "calendar-widget-glance/src/main"

check "sample app does not read or write HijriMonthOverrides" \
  "HijriMonthOverrides" \
  "sample-android-app/src sample-shared/src"

check "no implicit default overrides in glance (overridesTable() without an argument)" \
  "overridesTable\(\)" \
  "calendar-widget-glance/src/main"

exit $status
