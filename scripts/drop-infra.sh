#!/usr/bin/env bash
# Stop acceptance-test container infrastructure (Postgres, Pub/Sub emulator, Redis).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=local-test-env.sh
source "$SCRIPT_DIR/local-test-env.sh"

COMPOSE_FILE="${COMPOSE_FILE:-$SCRIPT_DIR/docker-compose-infra.yml}"
REMOVE_VOLUMES=false

usage() {
  cat <<'EOF'
Usage: ./drop-infra.sh [options]

Stops containers from docker-compose-infra.yml (project: census31-fwmt-acceptance-tests).

Options:
  -v, --volumes   Also remove named volumes (Postgres / Redis data)
  -h, --help      Show this help

Examples:
  ./drop-infra.sh
  ./drop-infra.sh --volumes
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -v|--volumes) REMOVE_VOLUMES=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage; exit 1 ;;
  esac
done

ensure_fwmt_container_runtime_ready
echo "Stopping FWMT acceptance-test infrastructure..."
if [[ "$REMOVE_VOLUMES" == true ]]; then
  fwmt_compose -f "$COMPOSE_FILE" down -v
  echo "Infrastructure stopped (volumes removed)."
else
  fwmt_compose -f "$COMPOSE_FILE" down
  echo "Infrastructure stopped (volumes retained; use --volumes to remove)."
fi
