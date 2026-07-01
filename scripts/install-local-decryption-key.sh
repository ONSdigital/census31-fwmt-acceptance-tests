#!/usr/bin/env bash
# Install the historical *test* PGP key for local job-service runs (never re-committed).
# Source: census31-fwmt-job-service commit e695484 (parent of FMT-4 GitGuardian removal).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=local-test-env.sh
source "$SCRIPT_DIR/local-test-env.sh"

JOB_SERVICE_DIR="${CENSUS31_FWMT_ROOT}/census31-fwmt-job-service"
KEY_DIR="${FWMT_KEYS_DIR:-$HOME/.fwmt/keys}"
KEY_FILE="${FWMT_DECRYPTION_KEY_FILE:-$KEY_DIR/decryption.private}"
# Passphrase from seeded application.yml before key removal (test-only).
TEST_KEY_COMMIT="${FWMT_TEST_PGP_COMMIT:-e695484}"
TEST_KEY_GIT_PATH="src/main/resources/testPrivateKey.private"

usage() {
  cat <<'EOF'
Usage: ./install-local-decryption-key.sh [--force]

Extracts the test PGP private key from git history in census31-fwmt-job-service
(commit e695484 by default) into ~/.fwmt/keys/decryption.private.

Does not modify the job-service repo or recommit the key. For local harness only.
Passphrase for this test key: testJobService (exported as DECRYPTION_PASSWORD by start-services.sh).

See census31-fwmt-job-service/docs/gitguardian-pgp-private-key.md.
EOF
}

FORCE=false
while [[ $# -gt 0 ]]; do
  case "$1" in
    --force) FORCE=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage >&2; exit 1 ;;
  esac
done

if [[ -f "$KEY_FILE" && "$FORCE" != "true" ]]; then
  echo "Decryption key already present: $KEY_FILE (use --force to replace)"
  exit 0
fi

if [[ ! -d "$JOB_SERVICE_DIR/.git" ]]; then
  echo "Missing git repo: $JOB_SERVICE_DIR" >&2
  echo "Clone census31-fwmt-job-service with history, or copy decryption.private manually." >&2
  exit 1
fi

if ! git -C "$JOB_SERVICE_DIR" cat-file -e "${TEST_KEY_COMMIT}:${TEST_KEY_GIT_PATH}" 2>/dev/null; then
  echo "Commit ${TEST_KEY_COMMIT} in $JOB_SERVICE_DIR does not contain ${TEST_KEY_GIT_PATH}" >&2
  exit 1
fi

mkdir -p "$KEY_DIR"
git -C "$JOB_SERVICE_DIR" show "${TEST_KEY_COMMIT}:${TEST_KEY_GIT_PATH}" >"$KEY_FILE"
chmod 600 "$KEY_FILE"
echo "Installed test decryption key from ${TEST_KEY_COMMIT} to $KEY_FILE"
echo "Passphrase: testJobService (set DECRYPTION_PASSWORD when starting job-service)"
