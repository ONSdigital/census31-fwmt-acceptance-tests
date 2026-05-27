#!/usr/bin/env bash
# Bootstrap messaging topology for local acceptance tests (RabbitMQ and/or Pub/Sub).
#
# Controlled by:
#   FWMT_MESSAGING=rabbit|pubsub|both   (default: rabbit)
#   SETUP_RABBIT=true|false               skip Rabbit bootstrap when false
#   SETUP_PUBSUB=true|false               skip Pub/Sub bootstrap when false
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

FWMT_MESSAGING="${FWMT_MESSAGING:-rabbit}"
SETUP_RABBIT="${SETUP_RABBIT:-true}"
SETUP_PUBSUB="${SETUP_PUBSUB:-true}"

validate_messaging_mode() {
  case "$FWMT_MESSAGING" in
    rabbit|pubsub|both) ;;
    *)
      echo "Invalid FWMT_MESSAGING=$FWMT_MESSAGING (expected rabbit, pubsub, or both)" >&2
      exit 1
      ;;
  esac
}

bootstrap_rabbitmq() {
  if [[ "$SETUP_RABBIT" != "true" ]]; then
    return 0
  fi
  if [[ ! -x "$SCRIPT_DIR/setup-rabbitmq.sh" ]]; then
    echo "RabbitMQ bootstrap script is not executable: $SCRIPT_DIR/setup-rabbitmq.sh" >&2
    exit 1
  fi
  "$SCRIPT_DIR/setup-rabbitmq.sh"
}

bootstrap_pubsub() {
  if [[ "$SETUP_PUBSUB" != "true" ]]; then
    return 0
  fi
  if [[ ! -x "$SCRIPT_DIR/setup-pubsub.sh" ]]; then
    echo "Pub/Sub bootstrap script is not executable: $SCRIPT_DIR/setup-pubsub.sh" >&2
    exit 1
  fi
  "$SCRIPT_DIR/setup-pubsub.sh"
}

validate_messaging_mode

case "$FWMT_MESSAGING" in
  rabbit)
    bootstrap_rabbitmq
    ;;
  pubsub)
    bootstrap_pubsub
    ;;
  both)
    bootstrap_rabbitmq
    bootstrap_pubsub
    ;;
esac
