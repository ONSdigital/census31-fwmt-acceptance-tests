#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/local-test-env.sh"

ACCEPTANCE_REPO="$(cd "$SCRIPT_DIR/.." && pwd)"
RUNNER="CreateTestRunner"
PREPARE=false
CLEAN=false
SETUP_PUBSUB=true
SUITE_MODE="default"

FEATURE_FLAG_RUNNERS="FeatureFlagTestRunner,OutcomeFeatureFlagTestRunner"

usage() {
  cat <<'EOF'
Usage: ./run-acceptance-test.sh [options] [RunnerName|all]

Runs acceptance tests (Maven) without rebuilding local dependency artifacts by default.
Run ./prepare-local-artifacts.sh when dependency repos change.

Options:
  --prepare            Run local dependency artifact preparation first.
  --clean              Run clean before test.
  --no-setup-pubsub    Skip Pub/Sub topic/bootstrap.
  --no-setup-messaging Skip all messaging bootstrap steps.
  --suite <mode>       Suite mode: default | main | feature-flag

Examples:
  ./run-acceptance-test.sh CreateTestRunner
  ./run-acceptance-test.sh OutcomesTestRunner
  ./run-acceptance-test.sh --clean CreateTestRunner
  ./run-acceptance-test.sh --prepare all
  ./run-acceptance-test.sh --suite main all
  ./run-acceptance-test.sh --suite feature-flag all
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
    --no-setup-pubsub)
      SETUP_PUBSUB=false
      shift
      ;;
    --no-setup-messaging)
      SETUP_PUBSUB=false
      shift
      ;;
    --suite)
      if [[ $# -lt 2 ]]; then
        echo "Missing value for --suite (expected: default|main|feature-flag)" >&2
        exit 1
      fi
      SUITE_MODE="$2"
      shift 2
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

case "$SUITE_MODE" in
  default|main|feature-flag)
    ;;
  *)
    echo "Invalid --suite value '$SUITE_MODE' (expected: default|main|feature-flag)" >&2
    exit 1
    ;;
esac

require_java17

if [[ "$PREPARE" == "true" ]]; then
  "$SCRIPT_DIR/prepare-local-artifacts.sh"
fi

export SETUP_PUBSUB
if [[ "$SUITE_MODE" == "feature-flag" ]]; then
  export FWMT_ACCEPTANCE_SUITE_MODE="feature-flag-negative"
else
  export FWMT_ACCEPTANCE_SUITE_MODE="main"
fi
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
  mvn_args+=(-Dtest="${RUNNER}")
elif [[ "$SUITE_MODE" == "main" ]]; then
  mvn_args+=(-Dtest="!${FEATURE_FLAG_RUNNERS}")
elif [[ "$SUITE_MODE" == "feature-flag" ]]; then
  mvn_args+=(-Dtest="${FEATURE_FLAG_RUNNERS}")
fi

run_maven_in_repo "$ACCEPTANCE_REPO" "${mvn_args[@]}" \
  -Dservice.tm.url="http://localhost:${TM_MOCK_PORT}" \
  -Dservice.mocktm.url="http://localhost:${TM_MOCK_PORT}" \
  -Dfwmt.pubsub.emulatorHost="localhost:${PUBSUB_EMULATOR_PORT}" \
  -Dfwmt.pubsub.project="${FWMT_PUBSUB_PROJECT:-fwmt-local}"
