#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/local-test-env.sh"

WITH_CSV=false
BUILD_MISSING=false
BOOT_RUN=false
PREPARE=false
SETUP_RABBIT=true
SETUP_PUBSUB=true
services=()

usage() {
  cat <<'EOF'
Usage: ./start-services.sh [options] [service ...]

Starts already-built services needed by the acceptance tests. If no service is
specified, starts:
  - tm-mock
  - job-service
  - outcome-service

Known services:
  tm-mock
  job-service
  outcome-service
  csv-service

Options:
  --with-csv           Include csv-service when no explicit service list is supplied.
  --build-missing      Build a service jar only when no boot jar exists.
  --prepare            Run local dependency artifact preparation first.
  --boot-run           Use Maven spring-boot:run instead of java -jar.
  --messaging MODE     rabbit | pubsub | both (default: rabbit, or FWMT_MESSAGING)
  --no-setup-rabbitmq  Skip RabbitMQ queue/bootstrap (when mode includes rabbit).
  --no-setup-pubsub    Skip Pub/Sub topic/bootstrap (when mode includes pubsub).
  --no-setup-messaging Skip all messaging bootstrap steps.

Examples:
  ./start-services.sh
  ./start-services.sh --build-missing
  ./start-services.sh job-service
  ./start-services.sh --boot-run outcome-service
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --with-csv)
      WITH_CSV=true
      shift
      ;;
    --build-missing)
      BUILD_MISSING=true
      shift
      ;;
    --prepare)
      PREPARE=true
      shift
      ;;
    --boot-run)
      BOOT_RUN=true
      shift
      ;;
    --messaging)
      FWMT_MESSAGING="$2"
      shift 2
      ;;
    --no-setup-rabbitmq)
      SETUP_RABBIT=false
      shift
      ;;
    --no-setup-pubsub)
      SETUP_PUBSUB=false
      shift
      ;;
    --no-setup-messaging)
      SETUP_RABBIT=false
      SETUP_PUBSUB=false
      shift
      ;;
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

if (( ${#services[@]} == 0 )); then
  services=(tm-mock job-service outcome-service)
  if [[ "$WITH_CSV" == "true" ]]; then
    services+=(csv-service)
  fi
fi

mkdir -p "$LOG_DIR" "$PID_DIR"
require_java17

if [[ "$PREPARE" == "true" ]]; then
  "$SCRIPT_DIR/prepare-local-artifacts.sh"
fi

export SETUP_RABBIT SETUP_PUBSUB FWMT_MESSAGING
"$SCRIPT_DIR/setup-messaging.sh"

needs_job_service=false
for service in "${services[@]}"; do
  if [[ "$service" == "job-service" ]]; then
    needs_job_service=true
    break
  fi
done
if [[ "$needs_job_service" == "true" ]]; then
  if [[ -x "$SCRIPT_DIR/install-local-decryption-key.sh" ]]; then
    "$SCRIPT_DIR/install-local-decryption-key.sh"
  fi
  if [[ -x "$SCRIPT_DIR/prepare-job-service-db.sh" ]]; then
    "$SCRIPT_DIR/prepare-job-service-db.sh"
  fi
fi

is_running() {
  local pid_file="$1"
  [[ -f "$pid_file" ]] && kill -0 "$(cat "$pid_file")" 2>/dev/null
}

wait_for_http() {
  local name="$1"
  local url="$2"
  local log_file="$3"
  local pid_file="$PID_DIR/$name.pid"
  local timeout_secs="${FWMT_SERVICE_TIMEOUT_SECONDS:-600}"
  [[ -z "$timeout_secs" ]] && timeout_secs=600
  local start_epoch
  start_epoch=$(date +%s)
  local deadline=$((start_epoch + timeout_secs))

  while true; do
    if curl -fsS -u user:password "$url" >/dev/null 2>&1; then
      echo "$name is responding at $url"
      return
    fi

    if [[ -f "$pid_file" ]] && ! kill -0 "$(cat "$pid_file")" 2>/dev/null; then
      echo "$name exited before responding at $url" >&2
      echo "Recent log output from $log_file:" >&2
      if [[ -f "$log_file" ]]; then
        sed -n '1,200p' "$log_file" >&2 || true
      fi
      rm -f "$pid_file"
      exit 1
    fi

    if (( $(date +%s) >= deadline )); then
      echo "Timed out waiting for $name at $url" >&2
      echo "Recent log output from $log_file:" >&2
      if [[ -f "$log_file" ]]; then
        sed -n '1,200p' "$log_file" >&2 || true
      fi
      if [[ -f "$pid_file" ]]; then
        local pid
        pid="$(cat "$pid_file")"
        kill -- "-$pid" 2>/dev/null || kill "$pid" 2>/dev/null || true
        rm -f "$pid_file"
      fi
      exit 1
    fi

    sleep 3
  done
}

start_service() {
  local name="$1"
  local repo
  local service_dir
  local health_url
  local pid_file="$PID_DIR/$name.pid"
  local log_file="$LOG_DIR/$name.log"

  repo="$(service_repo "$name")"
  service_dir="$CENSUS31_FWMT_ROOT/$repo"
  health_url="$(service_health_url "$name")"
  # env_args: no 'local' — load_service_env_args must assign in this scope (Bash 3.2 / macOS).
  env_args=()
  load_service_env_args "$name"

  if is_running "$pid_file"; then
    echo "$name already appears to be running with PID $(cat "$pid_file")."
    wait_for_http "$name" "$health_url" "$log_file"
    return
  fi

  if [[ "$BOOT_RUN" == "true" ]]; then
    if [[ ! -f "$service_dir/pom.xml" ]]; then
      echo "$name has no pom.xml; Census 31 services are Maven-only ($service_dir)" >&2
      exit 1
    fi
    echo "Starting $name from source with Maven spring-boot:run"
    start_detached_in_dir "$service_dir" "$log_file" \
      env "JAVA_HOME=$JAVA_HOME_TO_USE" "PATH=$JAVA_HOME_TO_USE/bin:$PATH" \
      "${env_args[@]}" \
      mvn -q spring-boot:run
  else
    local jar_path
    if ! jar_path="$(latest_boot_jar "$service_dir")"; then
      if [[ "$BUILD_MISSING" == "true" ]]; then
        "$SCRIPT_DIR/build-service.sh" "$name"
        jar_path="$(latest_boot_jar "$service_dir")"
      else
        echo "No boot jar found for $name under $service_dir/target." >&2
        echo "Run ./build-service.sh $name, or pass --build-missing." >&2
        exit 1
      fi
    fi

    echo "Starting $name from jar $jar_path"
    start_detached_in_dir "$service_dir" "$log_file" \
      env "JAVA_HOME=$JAVA_HOME_TO_USE" "PATH=$JAVA_HOME_TO_USE/bin:$PATH" \
      "${env_args[@]}" \
      "$JAVA_HOME_TO_USE/bin/java" -jar "$jar_path"
  fi

  local pid=$!
  echo "$pid" >"$pid_file"
  echo "$name PID $pid, log $log_file"

  wait_for_http "$name" "$health_url" "$log_file"
}

for service in "${services[@]}"; do
  start_service "$service"
done

echo "Requested services are running."
