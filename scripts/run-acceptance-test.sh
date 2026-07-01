#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/local-test-env.sh"

ACCEPTANCE_REPO="$(cd "$SCRIPT_DIR/.." && pwd)"
RUNNER="CreateTestRunner"
PREPARE=false
CLEAN=false
SETUP_RABBIT=true

usage() {
  cat <<'EOF'
Usage: ./run-acceptance-test.sh [options] [RunnerName|all]

Runs acceptance tests (Maven) without rebuilding local dependency artifacts by default.
Run ./prepare-local-artifacts.sh when dependency repos change.

Options:
  --prepare            Run local dependency artifact preparation first.
  --clean              Run clean before test.
  --no-setup-rabbitmq  Do not run the RabbitMQ queue/bootstrap step.

Examples:
  ./run-acceptance-test.sh CreateTestRunner
  ./run-acceptance-test.sh OutcomesTestRunner
  ./run-acceptance-test.sh --clean CreateTestRunner
  ./run-acceptance-test.sh --prepare all
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --prepare)
      PREPARE=true
      shift
      ;;
    --clean)
      CLEAN=true
      shift
      ;;
    --no-setup-rabbitmq)
      SETUP_RABBIT=false
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      RUNNER="$1"
      shift
      ;;
  esac
done

require_java17

if [[ "$PREPARE" == "true" ]]; then
  "$SCRIPT_DIR/prepare-local-artifacts.sh"
fi

if [[ "$SETUP_RABBIT" == "true" ]]; then
  if [[ -x "$SCRIPT_DIR/setup-rabbitmq.sh" ]]; then
    "$SCRIPT_DIR/setup-rabbitmq.sh"
  else
    echo "RabbitMQ bootstrap script is not executable: $SCRIPT_DIR/setup-rabbitmq.sh" >&2
    exit 1
  fi
fi

if [[ ! -f "$ACCEPTANCE_REPO/pom.xml" ]]; then
  echo "Missing acceptance-tests POM: $ACCEPTANCE_REPO/pom.xml" >&2
  exit 1
fi

mvn_args=(-B test)
if [[ "$CLEAN" == "true" ]]; then
  run_maven_in_repo "$ACCEPTANCE_REPO" clean
fi
if [[ "$RUNNER" != "all" ]]; then
  mvn_args+=(-Dtest="*${RUNNER}")
fi
run_maven_in_repo "$ACCEPTANCE_REPO" "${mvn_args[@]}" \
  -Dservice.rabbit.port="$RM_RABBIT_PORT" \
  -Dspring.rabbitmq.port="$RM_RABBIT_PORT"
