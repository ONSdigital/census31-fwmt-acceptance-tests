#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

export FWMT_PUBSUB_MODE="${FWMT_PUBSUB_MODE:-emulator}"
export FWMT_PUBSUB_HOST="${FWMT_PUBSUB_HOST:-pubsub}"
export FWMT_PUBSUB_EMULATOR_PORT="${FWMT_PUBSUB_EMULATOR_PORT:-8085}"
export FWMT_PUBSUB_PROJECT="${FWMT_PUBSUB_PROJECT:-fwmt-local}"

# Call via bash so executable bit is not required for setup-pubsub.sh.
bash "$SCRIPT_DIR/setup-pubsub.sh"
