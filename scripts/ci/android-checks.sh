#!/usr/bin/env bash
set -euo pipefail
mode="${1:-pr}"
case "$mode" in pr|main) ;; *) echo "Expected pr or main" >&2; exit 2 ;; esac
args=(testDebugUnitTest)
while IFS= read -r filter; do
  [[ -z "$filter" || "$filter" == \#* ]] && continue
  args+=(--tests "$filter")
done < .github/ci/android-test-filters.txt
# One Gradle invocation shares configuration and task outputs for PR validation.
if [[ "$mode" == pr ]]; then args+=(assembleDebug); fi
./gradlew "${args[@]}" --stacktrace
