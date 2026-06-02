#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/local-test-env.sh"

ACCEPTANCE_REPO="$(cd "$SCRIPT_DIR/.." && pwd)"
RUNNER="CreateTestRunner"
PREPARE=false
CLEAN=false
SETUP_RABBIT=true
SETUP_PUBSUB=true

usage() {
  cat <<'EOF'
Usage: ./run-acceptance-test.sh [options] [RunnerName|all]

Runs acceptance tests (Maven) without rebuilding local dependency artifacts by default.
Run ./prepare-local-artifacts.sh when dependency repos change.

Options:
  --prepare            Run local dependency artifact preparation first.
  --clean              Run clean before test.
  --messaging MODE     rabbit | pubsub | both (default: rabbit, or FWMT_MESSAGING)
  --no-setup-rabbitmq  Skip RabbitMQ queue/bootstrap (when mode includes rabbit).
  --no-setup-pubsub    Skip Pub/Sub topic/bootstrap (when mode includes pubsub).
  --no-setup-messaging Skip all messaging bootstrap steps.

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
    --messaging)
      FWMT_MESSAGING="$2"
      shift 2
      ;;
    --no-setup-rabbitmq)
      SETUP_RABBIT=false
      shift
      ;;
    --no-setup-pubsub)
      SETUP_PUBSUB=false
      shift
      ;;
    --no-setup-messaging)
      SETUP_RABBIT=false
      SETUP_PUBSUB=false
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

export SETUP_RABBIT SETUP_PUBSUB FWMT_MESSAGING
"$SCRIPT_DIR/setup-messaging.sh"

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
test_messaging_provider="${FWMT_MESSAGING:-rabbit}"
if [[ "$test_messaging_provider" == "both" ]]; then
  test_messaging_provider=rabbit
fi

run_maven_in_repo "$ACCEPTANCE_REPO" "${mvn_args[@]}" \
  -Dservice.rabbit.port="$RM_RABBIT_PORT" \
  -Dspring.rabbitmq.port="$RM_RABBIT_PORT" \
  -Dservice.tm.url="http://localhost:${TM_MOCK_PORT}" \
  -Dservice.mocktm.url="http://localhost:${TM_MOCK_PORT}" \
  -Dfwmt.messaging.provider="$test_messaging_provider" \
  -Dfwmt.pubsub.emulatorHost="localhost:${PUBSUB_EMULATOR_PORT}" \
  -Dfwmt.pubsub.project="${FWMT_PUBSUB_PROJECT:-fwmt-local}"
