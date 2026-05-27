#!/usr/bin/env bash
set -euo pipefail

# Pub/Sub emulator bootstrap (topics + subscriptions) via the emulator HTTP API.
#
# Mirrors `setup-rabbitmq.sh`: runs from the host against the published emulator port.
# No local gcloud install required.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

PUBSUB_HOST="${FWMT_PUBSUB_HOST:-localhost}"
PUBSUB_PORT="${FWMT_PUBSUB_EMULATOR_PORT:-8085}"
PUBSUB_PROJECT="${FWMT_PUBSUB_PROJECT:-fwmt-local}"
PUBSUB_API_BASE="http://${PUBSUB_HOST}:${PUBSUB_PORT}/v1/projects/${PUBSUB_PROJECT}"

PROJECT_NAME="${FWMT_DOCKER_PROJECT_NAME:-census31-fwmt-acceptance-tests}"
PUBSUB_CONTAINER="${FWMT_PUBSUB_CONTAINER:-$PROJECT_NAME-pubsub-1}"

pubsub_api_path_segment() {
  # Encode topic/subscription id for URL path (dots are fine; other chars encoded).
  python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1], safe=""))' "$1"
}

ensure_emulator_reachable() {
  if ! curl -fsS "${PUBSUB_API_BASE}/topics" -H "Content-Type: application/json" >/dev/null 2>&1; then
    if docker inspect "$PUBSUB_CONTAINER" >/dev/null 2>&1; then
      local status
      status="$(docker inspect -f '{{.State.Status}}' "$PUBSUB_CONTAINER" 2>/dev/null || true)"
      if [[ "$status" != "running" ]]; then
        echo "Pub/Sub emulator container is not running ($PUBSUB_CONTAINER status=$status)" >&2
        docker logs "$PUBSUB_CONTAINER" 2>&1 | tail -20 >&2 || true
        echo "Recreate infra: $SCRIPT_DIR/drop-infra.sh && $SCRIPT_DIR/start-infra.sh" >&2
        exit 1
      fi
    fi
    echo "Cannot reach Pub/Sub emulator at ${PUBSUB_HOST}:${PUBSUB_PORT}" >&2
    echo "Start infra first: $SCRIPT_DIR/start-infra.sh" >&2
    exit 1
  fi
}

create_topic_if_missing() {
  local topic="$1"
  local segment
  segment="$(pubsub_api_path_segment "$topic")"
  if curl -fsS "${PUBSUB_API_BASE}/topics/${segment}" >/dev/null 2>&1; then
    return 0
  fi
  curl -fsS -X PUT "${PUBSUB_API_BASE}/topics/${segment}" \
    -H "Content-Type: application/json" \
    -d '{}' >/dev/null
}

create_subscription_if_missing() {
  local subscription="$1"
  local topic="$2"
  local sub_segment topic_segment topic_resource
  sub_segment="$(pubsub_api_path_segment "$subscription")"
  topic_segment="$(pubsub_api_path_segment "$topic")"
  topic_resource="projects/${PUBSUB_PROJECT}/topics/${topic}"

  if curl -fsS "${PUBSUB_API_BASE}/subscriptions/${sub_segment}" >/dev/null 2>&1; then
    return 0
  fi
  curl -fsS -X PUT "${PUBSUB_API_BASE}/subscriptions/${sub_segment}" \
    -H "Content-Type: application/json" \
    -d "{\"topic\":\"${topic_resource}\"}" >/dev/null
}

safe_sub_name() {
  local raw="$1"
  raw="${raw//[^a-zA-Z0-9]/-}"
  raw="$(echo "$raw" | tr -s '-')"
  echo "$raw"
}

ensure_emulator_reachable

echo "Bootstrapping Pub/Sub emulator at ${PUBSUB_HOST}:${PUBSUB_PORT} (project=${PUBSUB_PROJECT})"

TOPICS=(
  "RM.Field"
  "RM.FieldDLQ"
  "GW.Field"
  "GW.Permanent.ErrorQ"
  "GW.Transient.ErrorQ"
  "GW.ErrorQ"
  "Outcome.Preprocessing"
  "Outcome.PreprocessingDLQ"
  "Outcome.PreprocesingDLQ"
  "Field.refusals"
  "Field.other"
  "events"
  "Gateway.Events.Exchange"
  "GW.Error.Exchange"
  "adapter-outbound-exchange"
  "Outcome.Preprocessing.Exchange"
  "Gateway.Actions.Exchange"
)

for topic in "${TOPICS[@]}"; do
  create_topic_if_missing "$topic"
done

SUBS=(
  "job-service:RM.Field"
  "job-service:GW.Field"
  "outcome-service:Outcome.Preprocessing"
  "outcome-service:events"
  "csv-service:Gateway.Actions.Exchange"
  "fulfilment-event-service:events"
)

for pair in "${SUBS[@]}"; do
  service="${pair%%:*}"
  topic="${pair#*:}"
  subscription="$(safe_sub_name "$service-$topic")"
  create_subscription_if_missing "$subscription" "$topic"
done

echo "Pub/Sub emulator topics/subscriptions are ready."
