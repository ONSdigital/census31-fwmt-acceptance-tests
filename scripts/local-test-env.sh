#!/usr/bin/env bash
# Shared env for Census 31 acceptance harness (Maven + Java 25 only).

if [[ -n "${FWMT_LOCAL_TEST_ENV_LOADED:-}" ]]; then
  return 0
fi

FWMT_LOCAL_TEST_ENV_LOADED=true

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ACCEPTANCE_REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CENSUS31_FWMT_ROOT="${CENSUS31_FWMT_ROOT:-$(cd "$ACCEPTANCE_REPO_ROOT/.." && pwd)}"
CENSUS31_INTEGRATION_COMMON_ROOT="${CENSUS31_INTEGRATION_COMMON_ROOT:-$CENSUS31_FWMT_ROOT}"
LOG_DIR="${FWMT_LOG_DIR:-$SCRIPT_DIR/logs}"
PID_DIR="${FWMT_PID_DIR:-$SCRIPT_DIR/.pids}"
MAVEN_BIN="${FWMT_MAVEN_BIN:-mvn}"
RM_RABBIT_PORT="${FWMT_RM_RABBIT_PORT:-5674}"
GW_RABBIT_PORT="${FWMT_GW_RABBIT_PORT:-$RM_RABBIT_PORT}"

resolve_java_home() {
  local candidates=()

  if [[ -n "${FWMT_JAVA_HOME:-}" ]]; then
    candidates+=("$FWMT_JAVA_HOME")
  fi
  candidates+=(
    "$HOME/.sdkman/candidates/java/25.0.2-open"
    "/usr/lib/jvm/java-25-openjdk"
  )

  local candidate
  for candidate in "${candidates[@]}"; do
    if [[ -x "$candidate/bin/java" ]]; then
      echo "$candidate"
      return
    fi
  done

  for candidate in /usr/lib/jvm/java-25-openjdk-*; do
    if [[ -x "$candidate/bin/java" ]]; then
      echo "$candidate"
      return
    fi
  done

  echo "Unable to find Java 25. Set FWMT_JAVA_HOME." >&2
  exit 1
}

require_runtime_java() {
  JAVA_HOME_TO_USE="$(resolve_java_home)"
  local java_major
  java_major="$("$JAVA_HOME_TO_USE/bin/java" -version 2>&1 | awk -F '[\".]' '/version/ {print $2; exit}')"

  if [[ "$java_major" != "25" ]]; then
    echo "Expected Java 25 but FWMT_JAVA_HOME resolved to Java $java_major at $JAVA_HOME_TO_USE" >&2
    exit 1
  fi

  echo "Using Java 25 from $JAVA_HOME_TO_USE"
}

# Back-compat alias for harness scripts
require_java17() {
  require_runtime_java
}

run_maven_in_repo() {
  local repo_dir="$1"
  shift

  if [[ ! -f "$repo_dir/pom.xml" ]]; then
    echo "Missing Maven POM: $repo_dir/pom.xml" >&2
    exit 1
  fi

  if ! command -v "$MAVEN_BIN" >/dev/null 2>&1; then
    echo "Unable to find Maven executable: $MAVEN_BIN" >&2
    exit 1
  fi

  require_runtime_java

  (
    cd "$repo_dir"
    JAVA_HOME="$JAVA_HOME_TO_USE" \
    PATH="$JAVA_HOME_TO_USE/bin:$PATH" \
    "$MAVEN_BIN" "$@"
  )
}

service_repo() {
  case "$1" in
    tm-mock) echo "census31-fwmt-tm-mock" ;;
    job-service) echo "census31-fwmt-job-service" ;;
    outcome-service) echo "census31-fwmt-outcome-service" ;;
    csv-service) echo "census31-fwmt-csv-service" ;;
    *)
      echo "Unknown service: $1" >&2
      echo "Known services: tm-mock job-service outcome-service csv-service" >&2
      exit 1
      ;;
  esac
}

service_health_url() {
  case "$1" in
    tm-mock) echo "http://localhost:8000/swagger-ui.html" ;;
    job-service) echo "http://localhost:8025/swagger-ui.html" ;;
    outcome-service) echo "http://localhost:8030/swagger-ui.html" ;;
    csv-service) echo "http://localhost:8060/swagger-ui.html" ;;
  esac
}

service_env() {
  case "$1" in
    tm-mock)
      echo "RABBITMQ_PORT=$RM_RABBIT_PORT"
      ;;
    job-service)
      echo "APP_TESTING=true"
      echo "APP_RABBITMQ_RM_PORT=$RM_RABBIT_PORT"
      echo "APP_RABBITMQ_GW_PORT=$GW_RABBIT_PORT"
      # Test key passphrase (see install-local-decryption-key.sh / gitguardian-pgp-private-key.md)
      echo "DECRYPTION_PASSWORD=${DECRYPTION_PASSWORD:-testJobService}"
      ;;
    outcome-service|csv-service)
      echo "APP_TESTING=true"
      echo "APP_RABBITMQ_RM_PORT=$RM_RABBIT_PORT"
      echo "APP_RABBITMQ_GW_PORT=$GW_RABBIT_PORT"
      echo "APP_RABBITMQ_GW_QUEUES_ERRORPER=GW.Permanent.ErrorQ"
      ;;
  esac
}

latest_boot_jar() {
  local service_dir="$1"
  local jars=()
  local jar

  shopt -s nullglob
  for jar in "$service_dir"/target/*.jar; do
    case "$(basename "$jar")" in
      *-plain.jar|*-sources.jar|*-javadoc.jar|original-*) ;;
      *) jars+=("$jar") ;;
    esac
  done
  shopt -u nullglob

  if (( ${#jars[@]} == 0 )); then
    return 1
  fi

  printf '%s\n' "${jars[@]}" | sort | tail -n 1
}

# Bash 3.2 (macOS /bin/bash): readarray is unavailable; caller must declare env_args=().
load_service_env_args() {
  local name="$1"
  local line

  env_args=()
  while IFS= read -r line; do
    env_args+=("$line")
  done < <(service_env "$name")
}

# Background start in service_dir; uses setsid on Linux when present.
start_detached_in_dir() {
  local service_dir="$1"
  local log_file="$2"
  shift 2

  local launcher=(bash -c 'cd "$1" && shift && exec "$@"' _ "$service_dir" "$@")
  if command -v setsid >/dev/null 2>&1; then
    setsid "${launcher[@]}" >"$log_file" 2>&1 &
  else
    "${launcher[@]}" >"$log_file" 2>&1 &
  fi
}
