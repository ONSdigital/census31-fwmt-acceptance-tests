#!/usr/bin/env bash
set -euo pipefail

RM_MANAGEMENT_PORT="${FWMT_RM_RABBIT_MANAGEMENT_PORT:-15674}"
GW_MANAGEMENT_PORT="${FWMT_GW_RABBIT_MANAGEMENT_PORT:-15673}"
RABBIT_USER="${FWMT_RABBIT_USER:-guest}"
RABBIT_PASSWORD="${FWMT_RABBIT_PASSWORD:-guest}"

rabbit_api() {
  local method="$1"
  local port="$2"
  local path="$3"
  local body="${4:-}"

  if [[ -n "$body" ]]; then
    curl -fsS -u "$RABBIT_USER:$RABBIT_PASSWORD" \
      -H "content-type: application/json" \
      -X "$method" \
      "http://localhost:$port/api/$path" \
      -d "$body" >/dev/null
  else
    curl -fsS -u "$RABBIT_USER:$RABBIT_PASSWORD" \
      -X "$method" \
      "http://localhost:$port/api/$path" >/dev/null
  fi
}

queue() {
  local port="$1"
  local name="$2"
  rabbit_api PUT "$port" "queues/%2F/$name" '{"durable":true,"auto_delete":false,"arguments":{}}'
}

auto_delete_queue() {
  local port="$1"
  local name="$2"
  rabbit_api DELETE "$port" "queues/%2F/$name" || true
  rabbit_api PUT "$port" "queues/%2F/$name" '{"durable":false,"auto_delete":true,"arguments":{}}'
}

exchange() {
  local port="$1"
  local name="$2"
  local type="${3:-direct}"
  rabbit_api PUT "$port" "exchanges/%2F/$name" "{\"type\":\"$type\",\"durable\":true,\"auto_delete\":false,\"internal\":false,\"arguments\":{}}"
}

recreate_exchange() {
  local port="$1"
  local name="$2"
  local type="${3:-direct}"
  rabbit_api DELETE "$port" "exchanges/%2F/$name" || true
  exchange "$port" "$name" "$type"
}

binding() {
  local port="$1"
  local exchange="$2"
  local queue="$3"
  local routing_key="$4"
  rabbit_api POST "$port" "bindings/%2F/e/$exchange/q/$queue" "{\"routing_key\":\"$routing_key\",\"arguments\":{}}"
}

echo "Bootstrapping RM RabbitMQ queues on management port $RM_MANAGEMENT_PORT"
queue "$RM_MANAGEMENT_PORT" "RM.Field"
queue "$RM_MANAGEMENT_PORT" "RM.FieldDLQ"
queue "$RM_MANAGEMENT_PORT" "GW.Field"
queue "$RM_MANAGEMENT_PORT" "GW.Permanent.ErrorQ"
queue "$RM_MANAGEMENT_PORT" "GW.Transient.ErrorQ"
queue "$RM_MANAGEMENT_PORT" "Outcome.Preprocessing"
queue "$RM_MANAGEMENT_PORT" "Outcome.PreprocessingDLQ"
queue "$RM_MANAGEMENT_PORT" "Outcome.PreprocesingDLQ"
auto_delete_queue "$RM_MANAGEMENT_PORT" "Field.refusals"
auto_delete_queue "$RM_MANAGEMENT_PORT" "Field.other"
exchange "$RM_MANAGEMENT_PORT" "events"
recreate_exchange "$RM_MANAGEMENT_PORT" "Gateway.Events.Exchange" "direct"
exchange "$RM_MANAGEMENT_PORT" "GW.Error.Exchange"
exchange "$RM_MANAGEMENT_PORT" "adapter-outbound-exchange"
exchange "$RM_MANAGEMENT_PORT" "Outcome.Preprocessing.Exchange"
binding "$RM_MANAGEMENT_PORT" "Outcome.Preprocessing.Exchange" "Outcome.Preprocessing" "Outcome.Preprocessing.Request"
binding "$RM_MANAGEMENT_PORT" "events" "Field.refusals" "event.respondent.refusal"
binding "$RM_MANAGEMENT_PORT" "events" "Field.other" "event.case.address.update"
binding "$RM_MANAGEMENT_PORT" "events" "Field.other" "event.fulfilment.request"
binding "$RM_MANAGEMENT_PORT" "events" "Field.other" "event.questionnaire.update"
binding "$RM_MANAGEMENT_PORT" "events" "Field.other" "event.ccs.propertylisting"
binding "$RM_MANAGEMENT_PORT" "events" "Field.other" "event.fieldcase.update"

echo "Bootstrapping GW RabbitMQ queues on management port $GW_MANAGEMENT_PORT"
queue "$GW_MANAGEMENT_PORT" "GW.Field"
queue "$GW_MANAGEMENT_PORT" "GW.Permanent.ErrorQ"
queue "$GW_MANAGEMENT_PORT" "GW.Transient.ErrorQ"
queue "$GW_MANAGEMENT_PORT" "GW.ErrorQ"
queue "$GW_MANAGEMENT_PORT" "Outcome.Preprocessing"
queue "$GW_MANAGEMENT_PORT" "Outcome.PreprocessingDLQ"
queue "$GW_MANAGEMENT_PORT" "Outcome.PreprocesingDLQ"
exchange "$GW_MANAGEMENT_PORT" "GW.Error.Exchange"
exchange "$GW_MANAGEMENT_PORT" "Outcome.Preprocessing.Exchange"
exchange "$GW_MANAGEMENT_PORT" "events"
recreate_exchange "$GW_MANAGEMENT_PORT" "Gateway.Events.Exchange" "direct"
binding "$GW_MANAGEMENT_PORT" "GW.Error.Exchange" "GW.Permanent.ErrorQ" "gw.permanent.error"
binding "$GW_MANAGEMENT_PORT" "GW.Error.Exchange" "GW.Transient.ErrorQ" "gw.transient.error"
binding "$GW_MANAGEMENT_PORT" "Outcome.Preprocessing.Exchange" "Outcome.Preprocessing" "Outcome.Preprocessing.Request"

echo "RabbitMQ queues and exchanges are ready."
