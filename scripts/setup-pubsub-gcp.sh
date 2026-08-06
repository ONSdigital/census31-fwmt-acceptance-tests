#!/usr/bin/env bash
set -euo pipefail

# Bootstrap Pub/Sub topics/subscriptions in a real GCP project.
#
# Defaults are safe for shared environments:
# - Creates acceptance subscriptions only
# - Does not create service subscriptions unless explicitly enabled

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=local-test-env.sh
source "$SCRIPT_DIR/local-test-env.sh"

PUBSUB_PROJECT="${FWMT_PUBSUB_PROJECT:-c31-fwmtg-dev}"
DRY_RUN="${FWMT_PUBSUB_DRY_RUN:-false}"
INCLUDE_SERVICE_SUBSCRIPTIONS="${FWMT_PUBSUB_INCLUDE_SERVICE_SUBSCRIPTIONS:-false}"

require_gcloud() {
  if [[ "$DRY_RUN" == "true" ]]; then
    return 0
  fi
  if ! command -v gcloud >/dev/null 2>&1; then
    echo "gcloud is required for GCP Pub/Sub bootstrap" >&2
    exit 1
  fi
}

run_or_echo() {
  if [[ "$DRY_RUN" == "true" ]]; then
    printf '[dry-run] %s\n' "$*"
    return 0
  fi
  "$@"
}

topic_exists() {
  gcloud pubsub topics describe "$1" --project "$PUBSUB_PROJECT" >/dev/null 2>&1
}

subscription_exists() {
  gcloud pubsub subscriptions describe "$1" --project "$PUBSUB_PROJECT" >/dev/null 2>&1
}

ensure_topic() {
  local topic="$1"
  if [[ "$DRY_RUN" == "false" ]] && topic_exists "$topic"; then
    return 0
  fi
  run_or_echo gcloud pubsub topics create "$topic" --project "$PUBSUB_PROJECT"
}

ensure_subscription() {
  local subscription="$1"
  local topic="$2"
  if [[ "$DRY_RUN" == "false" ]] && subscription_exists "$subscription"; then
    return 0
  fi
  run_or_echo \
    gcloud pubsub subscriptions create "$subscription" \
    --topic "$topic" \
    --project "$PUBSUB_PROJECT"
}

ensure_subscription_with_dlq() {
  local subscription="$1"
  local topic="$2"
  local dlq_topic="$3"
  local max_attempts="${4:-5}"
  if [[ "$DRY_RUN" == "false" ]] && subscription_exists "$subscription"; then
    return 0
  fi
  run_or_echo \
    gcloud pubsub subscriptions create "$subscription" \
    --topic "$topic" \
    --dead-letter-topic "$dlq_topic" \
    --max-delivery-attempts "$max_attempts" \
    --project "$PUBSUB_PROJECT"
}

require_gcloud

echo "Bootstrapping Pub/Sub in GCP project=$PUBSUB_PROJECT"
if [[ "$DRY_RUN" == "true" ]]; then
  echo "DRY RUN enabled (no gcloud mutations will be applied)."
fi

TOPICS=(
  "RM.Field"
  "RM.FieldDLQ"
  "GW.Field"
  "GW.Permanent.ErrorQ"
  "GW.Transient.ErrorQ"
  "Outcome.Preprocessing"
  "Outcome.PreprocessingDLQ"
  "Field.refusals"
  "Field.other"
  "events"
  "Gateway.Events.Exchange"
)

for topic in "${TOPICS[@]}"; do
  ensure_topic "$topic"
done

# Acceptance-test-only subscriptions (safe default in shared env)
ACCEPTANCE_TEST_SUBS=(
  "acceptance-tests-RM-Field:RM.Field"
  "acceptance-tests-RM-FieldDLQ:RM.FieldDLQ"
  "acceptance-tests-GW-Transient-ErrorQ:GW.Transient.ErrorQ"
  "acceptance-tests-GW-Permanent-ErrorQ:GW.Permanent.ErrorQ"
  "acceptance-tests-Outcome-Preprocessing:Outcome.Preprocessing"
  "acceptance-tests-Outcome-PreprocessingDLQ:Outcome.PreprocessingDLQ"
  "acceptance-tests-Field-refusals:Field.refusals"
  "acceptance-tests-Field-other:Field.other"
  "acceptance-tests-Gateway-Events:Gateway.Events.Exchange"
)

for pair in "${ACCEPTANCE_TEST_SUBS[@]}"; do
  subscription="${pair%%:*}"
  topic="${pair#*:}"
  ensure_subscription "$subscription" "$topic"
done

if [[ "$INCLUDE_SERVICE_SUBSCRIPTIONS" == "true" ]]; then
  echo "FWMT_PUBSUB_INCLUDE_SERVICE_SUBSCRIPTIONS=true, creating service subscriptions as well"
  SERVICE_SUBS=(
    "job-service-RM-Field:RM.Field"
    "job-service-GW-Field:GW.Field"
    "job-service-GW-Transient-ErrorQ:GW.Transient.ErrorQ"
    "job-service-GW-Permanent-ErrorQ:GW.Permanent.ErrorQ"
    "outcome-service-Outcome-PreprocessingDLQ:Outcome.PreprocessingDLQ"
    "outcome-service-events:events"
    "fulfilment-event-service-events:events"
  )

  for pair in "${SERVICE_SUBS[@]}"; do
    subscription="${pair%%:*}"
    topic="${pair#*:}"
    ensure_subscription "$subscription" "$topic"
  done

  ensure_subscription_with_dlq \
    "outcome-service-Outcome-Preprocessing" \
    "Outcome.Preprocessing" \
    "Outcome.PreprocessingDLQ" \
    "5"
fi

echo "GCP Pub/Sub topics/subscriptions are ready."

