#!/usr/bin/env bash
# Stop local Cloud SQL Proxy.
# Usage: ./stop-cloudsql-proxy.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PID_DIR="${SCRIPT_DIR}/.pids"
pid_file="$PID_DIR/cloudsql-proxy.pid"

if [[ -f "$pid_file" ]]; then
  pid=$(cat "$pid_file")
  if kill -0 "$pid" 2>/dev/null; then
    echo "Stopping Cloud SQL Proxy (PID $pid)..."
    kill "$pid" 2>/dev/null || true
    sleep 1
    kill -9 "$pid" 2>/dev/null || true
  fi
  rm -f "$pid_file"
  echo "Cloud SQL Proxy stopped."
else
  echo "Cloud SQL Proxy PID file not found; process may already be stopped."
fi

