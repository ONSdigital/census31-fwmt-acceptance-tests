#!/usr/bin/env bash
# Stop local port-forward tunnels to GKE services.
# Usage: ./stop-gcp-port-forwards.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PID_DIR="${SCRIPT_DIR}/.pids"

echo "Stopping port-forward tunnels..."

for service in job-service outcome-service csv-service tm-mock; do
  pid_file="$PID_DIR/port-forward-$service.pid"
  if [[ -f "$pid_file" ]]; then
    pid=$(cat "$pid_file")
    if kill -0 "$pid" 2>/dev/null; then
      echo "Stopping $service (PID $pid)..."
      kill "$pid" 2>/dev/null || true
      sleep 1
      kill -9 "$pid" 2>/dev/null || true
    fi
    rm -f "$pid_file"
  fi
done

echo "Port-forwards stopped."

