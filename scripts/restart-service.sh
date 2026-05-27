#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if (( $# == 0 )); then
  cat <<'EOF' >&2
Usage: ./restart-service.sh [start-services options] <service> [...]

Examples:
  ./restart-service.sh job-service
  ./restart-service.sh --build-missing outcome-service
EOF
  exit 1
fi

services=()
start_args=()

for arg in "$@"; do
  case "$arg" in
    --with-csv|--build-missing|--prepare|--boot-run)
      start_args+=("$arg")
      ;;
    -*)
      echo "Unknown option: $arg" >&2
      exit 1
      ;;
    *)
      services+=("$arg")
      start_args+=("$arg")
      ;;
  esac
done

if (( ${#services[@]} == 0 )); then
  echo "At least one service name is required." >&2
  exit 1
fi

"$SCRIPT_DIR/stop-services.sh" "${services[@]}"
"$SCRIPT_DIR/start-services.sh" "${start_args[@]}"
