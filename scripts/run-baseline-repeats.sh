#!/usr/bin/env bash
# Phase 0 baseline harness: repeats an unchanged `mvn clean verify` and archives each run
# so run-to-run variance and the flaky failure set can be measured.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
source "$SCRIPT_DIR/local-test-env.sh"

RUNS="${1:-3}"
LABEL="${2:-baseline}"
ARCHIVE_ROOT="$REPO_DIR/performance-investigation/runs"
MVN="$(resolve_maven_bin)"

cd "$REPO_DIR"

for i in $(seq 1 "$RUNS"); do
  run_dir="$ARCHIVE_ROOT/${LABEL}-run${i}"
  mkdir -p "$run_dir"

  # Truncate service logs so each run's service activity is separable.
  : > "$LOG_DIR/tm-mock.log"
  : > "$LOG_DIR/job-service.log"
  : > "$LOG_DIR/outcome-service.log"

  echo "=== ${LABEL} run ${i}/${RUNS} starting at $(date -Iseconds) ==="
  started_at="$(date -Iseconds)"
  SECONDS=0
  "$MVN" clean verify > "$run_dir/output.log" 2>&1
  exit_code=$?
  elapsed=$SECONDS
  echo "=== ${LABEL} run ${i}/${RUNS} finished in ${elapsed}s (mvn exit ${exit_code}) ==="

  cp target/jsonReports/cucumber.json "$run_dir/" 2>/dev/null \
    || echo "WARNING: no cucumber.json produced for run ${i}"
  cp "$LOG_DIR/tm-mock.log" "$LOG_DIR/job-service.log" "$LOG_DIR/outcome-service.log" "$run_dir/" 2>/dev/null || true

  cat > "$run_dir/run.json" <<EOF
{
  "label": "${LABEL}",
  "run": ${i},
  "order": ${i},
  "startedAt": "${started_at}",
  "finishedAt": "$(date -Iseconds)",
  "elapsedSeconds": ${elapsed},
  "mvnExitCode": ${exit_code},
  "command": "mvn clean verify",
  "gitBranch": "$(git rev-parse --abbrev-ref HEAD)",
  "gitCommit": "$(git rev-parse HEAD)",
  "gitDirty": $(if [[ -n "$(git status --porcelain)" ]]; then echo true; else echo false; fi)
}
EOF
done

echo "All ${RUNS} runs complete. Archives under $ARCHIVE_ROOT"
