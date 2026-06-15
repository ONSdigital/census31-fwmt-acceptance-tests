#!/usr/bin/env bash
# Bootstrap Pub/Sub topology for local acceptance tests.
#
# Controlled by:
#   SETUP_PUBSUB=true|false   skip Pub/Sub bootstrap when false
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

SETUP_PUBSUB="${SETUP_PUBSUB:-true}"

if [[ "$SETUP_PUBSUB" != "true" ]]; then
  exit 0
fi

if [[ ! -x "$SCRIPT_DIR/setup-pubsub.sh" ]]; then
  echo "Pub/Sub bootstrap script is not executable: $SCRIPT_DIR/setup-pubsub.sh" >&2
  exit 1
fi

"$SCRIPT_DIR/setup-pubsub.sh"
