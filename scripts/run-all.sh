#!/usr/bin/env bash
# Full local acceptance-test flow: infra → deps → services → Cucumber.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RUNNER="CreateTestRunner"
FORCE_PREPARE=false
BUILD_MISSING=true
BOOT_RUN=false
INFRA_ONLY=false
NO_TESTS=false

usage() {
  cat <<'EOF'
Usage: ./run-all.sh [options] [RunnerName|all]

Runs the full local harness in order:
  1. start-infra.sh
  2. prepare-local-artifacts.sh
  3. start-services.sh (--build-missing by default)
  4. run-acceptance-test.sh

Options:
  --force-prepare    Pass --force to prepare-local-artifacts.sh
  --no-build         Do not pass --build-missing to start-services.sh
  --boot-run         Start services with Maven spring-boot:run instead of jars
  --infra-only       Stop after start-infra.sh
  --no-tests         Start stack only; skip run-acceptance-test.sh
  -h, --help

Examples:
  ./run-all.sh
  ./run-all.sh all
  ./run-all.sh --boot-run CreateTestRunner
  ./run-all.sh --infra-only
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --force-prepare) FORCE_PREPARE=true; shift ;;
    --no-build) BUILD_MISSING=false; shift ;;
    --boot-run) BOOT_RUN=true; shift ;;
    --infra-only) INFRA_ONLY=true; shift ;;
    --no-tests) NO_TESTS=true; shift ;;
    -h|--help) usage; exit 0 ;;
    -*) echo "Unknown option: $1" >&2; usage; exit 1 ;;
    *) RUNNER="$1"; shift ;;
  esac
done

log() { printf '[run-all] %s\n' "$*"; }

log "Step 1/4: infrastructure"
"$SCRIPT_DIR/start-infra.sh"

if [[ "$INFRA_ONLY" == true ]]; then
  log "Done (--infra-only)."
  exit 0
fi

log "Step 2/4: local Maven artifacts"
prepare_args=()
[[ "$FORCE_PREPARE" == true ]] && prepare_args+=(--force)
"$SCRIPT_DIR/prepare-local-artifacts.sh" ${prepare_args[@]+"${prepare_args[@]}"}

log "Step 3/4: gateway services"
start_args=()
[[ "$BUILD_MISSING" == true ]] && start_args+=(--build-missing)
[[ "$BOOT_RUN" == true ]] && start_args+=(--boot-run)
"$SCRIPT_DIR/start-services.sh" ${start_args[@]+"${start_args[@]}"}

if [[ "$NO_TESTS" == true ]]; then
  log "Done (--no-tests). Stack is up; run ./run-acceptance-test.sh when ready."
  exit 0
fi

log "Step 4/4: acceptance tests ($RUNNER)"
"$SCRIPT_DIR/run-acceptance-test.sh" "$RUNNER"

log "Complete."
