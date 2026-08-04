#!/usr/bin/env bash
# Start local Cloud SQL Proxy for manual local-against-GCP workflow.
# Usage: ./start-cloudsql-proxy.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

CLOUDSQL_INSTANCE=${FWMT_CLOUDSQL_INSTANCE:-c31-fwmtg-dev:europe-west2:c31-fwmtg-dev-postgres}
CLOUDSQL_LOCAL_PORT=${FWMT_CLOUDSQL_LOCAL_PORT:-15432}

PID_DIR="${SCRIPT_DIR}/.pids"
mkdir -p "$PID_DIR"

LOG_FILE="$SCRIPT_DIR/logs/cloudsql-proxy.log"
mkdir -p "$(dirname "$LOG_FILE")"

echo "Starting Cloud SQL Proxy: localhost:$CLOUDSQL_LOCAL_PORT -> $CLOUDSQL_INSTANCE"

# Check if cloud-sql-proxy is available
if ! command -v cloud-sql-proxy >/dev/null 2>&1; then
  echo "Error: cloud-sql-proxy not found on PATH" >&2
  echo "Install with: curl https://dl.google.com/cloudsql/cloud-sql-proxy.linux.amd64 -o cloud-sql-proxy && chmod +x cloud-sql-proxy" >&2
  exit 1
fi

# Start cloud-sql-proxy
cloud-sql-proxy --private-ip --port "$CLOUDSQL_LOCAL_PORT" "$CLOUDSQL_INSTANCE" >"$LOG_FILE" 2>&1 &
pid=$!
echo $pid >"$PID_DIR/cloudsql-proxy.pid"

echo "Cloud SQL Proxy started (PID $pid)."
echo "Check logs at: $LOG_FILE"
echo "To stop: ./stop-cloudsql-proxy.sh"

# Wait a moment for the proxy to be ready
sleep 2

# Verify connection
if nc -z localhost "$CLOUDSQL_LOCAL_PORT" >/dev/null 2>&1; then
  echo "✓ Cloud SQL Proxy is ready on localhost:$CLOUDSQL_LOCAL_PORT"
else
  echo "⚠ Warning: Cloud SQL Proxy may not be ready yet. Check logs: tail -f $LOG_FILE"
fi

