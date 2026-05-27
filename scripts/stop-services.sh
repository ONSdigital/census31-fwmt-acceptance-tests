#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PID_DIR="${FWMT_PID_DIR:-$SCRIPT_DIR/.pids}"
services=()

usage() {
  cat <<'EOF'
Usage: ./stop-services.sh [service ...]

Stops all recorded services by default. If service names are supplied, stops only
those services.

Known service names are the PID file names:
  tm-mock
  job-service
  outcome-service
  csv-service
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help)
      usage
      exit 0
      ;;
    *)
      services+=("$1")
      shift
      ;;
  esac
done

if [[ ! -d "$PID_DIR" ]]; then
  echo "No PID directory found at $PID_DIR"
  exit 0
fi

pid_files=()
if (( ${#services[@]} == 0 )); then
  shopt -s nullglob
  pid_files=("$PID_DIR"/*.pid)
  shopt -u nullglob
else
  for service in "${services[@]}"; do
    pid_files+=("$PID_DIR/$service.pid")
  done
fi

if (( ${#pid_files[@]} == 0 )); then
  echo "No service PID files found in $PID_DIR"
  exit 0
fi

for pid_file in "${pid_files[@]}"; do
  service_name="$(basename "$pid_file" .pid)"

  if [[ ! -f "$pid_file" ]]; then
    echo "$service_name is not running; no PID file found"
    continue
  fi

  pid="$(cat "$pid_file")"

  if kill -0 "$pid" 2>/dev/null; then
    echo "Stopping $service_name with PID $pid"
    kill -- "-$pid" 2>/dev/null || kill "$pid" 2>/dev/null || true
  else
    echo "$service_name is not running; removing stale PID file"
  fi

  rm -f "$pid_file"
done

echo "Service stop requests sent."
