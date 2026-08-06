#!/usr/bin/env bash
# Start local port-forward tunnels to GKE services for manual local-against-GCP workflow.
# Usage: ./start-gcp-port-forwards.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Port mapping (local -> GKE service:port)
JOB_SERVICE_LOCAL_PORT=18025
OUTCOME_SERVICE_LOCAL_PORT=18030
CSV_SERVICE_LOCAL_PORT=18060
TM_MOCK_LOCAL_PORT=18000

# GKE namespace and service names
K8S_NAMESPACE=${FWMT_K8S_NAMESPACE:-fwmt}
K8S_CONTEXT=${FWMT_K8S_CONTEXT:-gke_c31-fwmtg-dev_europe-west2_c31-fwmtg-dev}

PID_DIR="${SCRIPT_DIR}/.pids"
mkdir -p "$PID_DIR"

echo "Starting port-forward tunnels to GKE services (namespace=$K8S_NAMESPACE)..."

# Start job-service port-forward
echo "job-service: localhost:$JOB_SERVICE_LOCAL_PORT -> service/job-service:80"
kubectl --context="$K8S_CONTEXT" -n "$K8S_NAMESPACE" port-forward svc/job-service "$JOB_SERVICE_LOCAL_PORT":80 >"$SCRIPT_DIR/logs/port-forward-job-service.log" 2>&1 &
echo $! >"$PID_DIR/port-forward-job-service.pid"

# Start outcome-service port-forward
echo "outcome-service: localhost:$OUTCOME_SERVICE_LOCAL_PORT -> service/outcome-service:80"
kubectl --context="$K8S_CONTEXT" -n "$K8S_NAMESPACE" port-forward svc/outcome-service "$OUTCOME_SERVICE_LOCAL_PORT":80 >"$SCRIPT_DIR/logs/port-forward-outcome-service.log" 2>&1 &
echo $! >"$PID_DIR/port-forward-outcome-service.pid"

# Start csv-service port-forward
echo "csv-service: localhost:$CSV_SERVICE_LOCAL_PORT -> service/csv-service:80"
kubectl --context="$K8S_CONTEXT" -n "$K8S_NAMESPACE" port-forward svc/csv-service "$CSV_SERVICE_LOCAL_PORT":80 >"$SCRIPT_DIR/logs/port-forward-csv-service.log" 2>&1 &
echo $! >"$PID_DIR/port-forward-csv-service.pid"

# Start tm-mock port-forward
echo "tm-mock: localhost:$TM_MOCK_LOCAL_PORT -> service/fwmtgatewaytmmock:80"
kubectl --context="$K8S_CONTEXT" -n "$K8S_NAMESPACE" port-forward svc/fwmtgatewaytmmock "$TM_MOCK_LOCAL_PORT":80 >"$SCRIPT_DIR/logs/port-forward-tm-mock.log" 2>&1 &
echo $! >"$PID_DIR/port-forward-tm-mock.pid"

echo "Port-forwards started. Check logs in $SCRIPT_DIR/logs/port-forward-*.log"
echo "To stop: ./stop-gcp-port-forwards.sh"

