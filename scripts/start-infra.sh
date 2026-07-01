#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=local-test-env.sh
source "$SCRIPT_DIR/local-test-env.sh"

COMPOSE_FILE="${COMPOSE_FILE:-$SCRIPT_DIR/docker-compose-infra.yml}"

ensure_fwmt_container_runtime_ready
echo "Starting FWMT acceptance-test infrastructure (runtime=$FWMT_RUNTIME, compose=${FWMT_COMPOSE_CMD[*]})..."
fwmt_compose -f "$COMPOSE_FILE" up -d

containers=()
while IFS= read -r line; do
  containers+=("$line")
done < <(fwmt_infra_container_names)

infra_timeout="${FWMT_INFRA_TIMEOUT_SECONDS:-120}"
[[ -z "$infra_timeout" ]] && infra_timeout=120
start_epoch=$(date +%s)
deadline=$((start_epoch + infra_timeout))

while true; do
  all_ready=true
  for container in "${containers[@]}"; do
    status="$(fwmt_container inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || true)"
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
    fwmt_compose -f "$COMPOSE_FILE" ps >&2
    exit 1
  fi

  sleep 2
done

fwmt_compose -f "$COMPOSE_FILE" ps
echo "Infrastructure is ready."
