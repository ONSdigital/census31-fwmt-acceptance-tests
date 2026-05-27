#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-$SCRIPT_DIR/docker-compose-infra.yml}"
PROJECT_NAME="census31-fwmt-acceptance-tests"

echo "Starting FWMT acceptance-test infrastructure..."
docker compose -f "$COMPOSE_FILE" up -d

containers=(
  "$PROJECT_NAME-postgres-1"
  "$PROJECT_NAME-redis-1"
  "$PROJECT_NAME-rabbit-rm-1"
  "$PROJECT_NAME-rabbit-gw-1"
)

infra_timeout="${FWMT_INFRA_TIMEOUT_SECONDS:-120}"
[[ -z "$infra_timeout" ]] && infra_timeout=120
start_epoch=$(date +%s)
deadline=$((start_epoch + infra_timeout))

while true; do
  all_ready=true
  for container in "${containers[@]}"; do
    status="$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || true)"
    if [[ "$status" != "healthy" && "$status" != "running" ]]; then
      all_ready=false
      break
    fi
  done

  if [[ "$all_ready" == "true" ]]; then
    break
  fi

  if (( $(date +%s) >= deadline )); then
    echo "Timed out waiting for infrastructure to become healthy." >&2
    docker compose -f "$COMPOSE_FILE" ps >&2
    exit 1
  fi

  sleep 2
done

docker compose -f "$COMPOSE_FILE" ps
echo "Infrastructure is ready."
