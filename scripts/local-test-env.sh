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
PUBSUB_EMULATOR_PORT="${FWMT_PUBSUB_EMULATOR_PORT:-8085}"
TM_MOCK_PORT="${FWMT_TM_MOCK_PORT:-8000}"

resolve_maven_bin() {
  # Check if MAVEN_BIN is directly available
  if command -v "$MAVEN_BIN" >/dev/null 2>&1; then
    echo "$MAVEN_BIN"
    return 0
  fi

  # Try to resolve via mise
  if command -v mise >/dev/null 2>&1; then
    local mvn_exec
    mvn_exec="$(mise which mvn 2>/dev/null || true)"
    if [[ -n "$mvn_exec" && -x "$mvn_exec" ]]; then
      echo "$mvn_exec"
      return 0
    fi

    # Compatibility fallback for older/non-standard mise layouts.
    local maven_root
    maven_root="$(mise where maven 2>/dev/null || true)"
    if [[ -n "$maven_root" && -x "$maven_root/bin/mvn" ]]; then
      echo "$maven_root/bin/mvn"
      return 0
    fi

    local nested_mvn
    nested_mvn="$(find "$maven_root" -maxdepth 3 -type f -path '*/bin/mvn' 2>/dev/null | head -n 1 || true)"
    if [[ -n "$nested_mvn" && -x "$nested_mvn" ]]; then
      echo "$nested_mvn"
      return 0
    fi
  fi

  # Fallback
  echo "$MAVEN_BIN"
  return 1
}

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

  local resolved_maven_bin
  resolved_maven_bin="$(resolve_maven_bin)"
  if ! command -v "$resolved_maven_bin" >/dev/null 2>&1; then
    echo "Unable to find Maven executable. Set FWMT_MAVEN_BIN or install Maven (or use 'mise install maven')." >&2
    exit 1
  fi

  require_runtime_java

  (
    cd "$repo_dir"
    JAVA_HOME="$JAVA_HOME_TO_USE" \
    PATH="$JAVA_HOME_TO_USE/bin:$PATH" \
    "$resolved_maven_bin" "$@"
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
    tm-mock) echo "http://localhost:${TM_MOCK_PORT}/swagger-ui.html" ;;
    job-service) echo "http://localhost:8025/swagger-ui.html" ;;
    outcome-service) echo "http://localhost:8030/swagger-ui.html" ;;
    csv-service) echo "http://localhost:8060/swagger-ui.html" ;;
  esac
}

service_env() {
  case "$1" in
    tm-mock)
      echo "SERVER_PORT=$TM_MOCK_PORT"
      ;;
    job-service)
      echo "APP_TESTING=true"
      echo "TOTALMOBILE_BASEURL=http://localhost:${TM_MOCK_PORT}/"
      echo "RMAPI_BASEURL=http://localhost:${TM_MOCK_PORT}/"
      # Test key passphrase (see install-local-decryption-key.sh / gitguardian-pgp-private-key.md)
      echo "DECRYPTION_PASSWORD=${DECRYPTION_PASSWORD:-testJobService}"
      echo "APP_MESSAGING_PROVIDER=pubsub"
      echo "PUBSUB_EMULATOR_HOST=localhost:${PUBSUB_EMULATOR_PORT}"
      echo "FWMT_PUBSUB_PROJECT=${FWMT_PUBSUB_PROJECT:-fwmt-local}"
      echo "SPRING_CLOUD_GCP_PROJECT_ID=${FWMT_PUBSUB_PROJECT:-fwmt-local}"
      echo "SPRING_CLOUD_GCP_CREDENTIALS_ENABLED=false"
      echo "SPRING_CLOUD_GCP_FIRESTORE_ENABLED=false"
      echo "SPRING_CLOUD_GCP_PUBSUB_EMULATOR_HOST=localhost:${PUBSUB_EMULATOR_PORT}"
      echo "SPRING_CLOUD_GCP_PUBSUB_ENABLED=true"
      echo "JAVA_TOOL_OPTIONS=-Dapp.messaging.provider=pubsub"
      ;;
    outcome-service)
      local outcome_flags_default="true"
      if [[ "${FWMT_ACCEPTANCE_SUITE_MODE:-main}" == "feature-flag-negative" ]]; then
        outcome_flags_default="false"
      fi
      echo "APP_TESTING=true"
      echo "APP_MESSAGING_PROVIDER=pubsub"
      echo "PUBSUB_EMULATOR_HOST=localhost:${PUBSUB_EMULATOR_PORT}"
      echo "FWMT_PUBSUB_PROJECT=${FWMT_PUBSUB_PROJECT:-fwmt-local}"
      echo "SPRING_CLOUD_GCP_PROJECT_ID=${FWMT_PUBSUB_PROJECT:-fwmt-local}"
      echo "SPRING_CLOUD_GCP_CREDENTIALS_ENABLED=false"
      echo "SPRING_CLOUD_GCP_FIRESTORE_ENABLED=false"
      echo "SPRING_CLOUD_GCP_PUBSUB_EMULATOR_HOST=localhost:${PUBSUB_EMULATOR_PORT}"
      echo "SPRING_CLOUD_GCP_PUBSUB_ENABLED=true"
      echo "JAVA_TOOL_OPTIONS=-Dapp.messaging.provider=pubsub"
      # Feature flags for outcome processing
      echo "FEATURE_OUTCOME_HH=${FEATURE_OUTCOME_HH:-$outcome_flags_default}"
      echo "FEATURE_OUTCOME_SPG=${FEATURE_OUTCOME_SPG:-$outcome_flags_default}"
      echo "FEATURE_OUTCOME_CE=${FEATURE_OUTCOME_CE:-$outcome_flags_default}"
      echo "FEATURE_OUTCOME_CCS=${FEATURE_OUTCOME_CCS:-$outcome_flags_default}"
      echo "FEATURE_OUTCOME_NC=${FEATURE_OUTCOME_NC:-$outcome_flags_default}"
      ;;
    csv-service)
      echo "APP_TESTING=true"
      echo "APP_MESSAGING_PROVIDER=pubsub"
      echo "PUBSUB_EMULATOR_HOST=localhost:${PUBSUB_EMULATOR_PORT}"
      echo "FWMT_PUBSUB_PROJECT=${FWMT_PUBSUB_PROJECT:-fwmt-local}"
      echo "SPRING_CLOUD_GCP_PROJECT_ID=${FWMT_PUBSUB_PROJECT:-fwmt-local}"
      echo "SPRING_CLOUD_GCP_CREDENTIALS_ENABLED=false"
      echo "SPRING_CLOUD_GCP_FIRESTORE_ENABLED=false"
      echo "SPRING_CLOUD_GCP_PUBSUB_EMULATOR_HOST=localhost:${PUBSUB_EMULATOR_PORT}"
      echo "SPRING_CLOUD_GCP_PUBSUB_ENABLED=true"
      echo "JAVA_TOOL_OPTIONS=-Dapp.messaging.provider=pubsub"
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

# --- Container runtime (Docker / Podman) ---
# Override examples:
#   export FWMT_RUNTIME=podman          # force Podman
#   export FWMT_RUNTIME=docker          # force Docker
#   export FWMT_PODMAN_AUTO_START_MACHINE=0   # do not auto-start podman machine on macOS
#   export FWMT_COMPOSE_CMD_OVERRIDE="podman-compose"   # force compose CLI
FWMT_COMPOSE_PROJECT_NAME="${FWMT_COMPOSE_PROJECT_NAME:-${FWMT_DOCKER_PROJECT_NAME:-census31-fwmt-acceptance-tests}}"
FWMT_RUNTIME="${FWMT_RUNTIME:-auto}"

fwmt_runtime_usable() {
  case "$1" in
    docker) docker info >/dev/null 2>&1 ;;
    podman) podman info >/dev/null 2>&1 ;;
    *) return 1 ;;
  esac
}

fwmt_compose_version_check() {
  fwmt_compose version >/dev/null 2>&1
}

resolve_fwmt_compose_cmd() {
  local runtime="$1"

  if [[ -n "${FWMT_COMPOSE_CMD_OVERRIDE:-}" ]]; then
    # shellcheck disable=SC2206
    FWMT_COMPOSE_CMD=($FWMT_COMPOSE_CMD_OVERRIDE)
    return 0
  fi

  if [[ "$runtime" == "docker" ]]; then
    if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
      FWMT_COMPOSE_CMD=(docker compose)
      return 0
    fi
    if command -v docker-compose >/dev/null 2>&1; then
      FWMT_COMPOSE_CMD=(docker-compose)
      return 0
    fi
    echo "Need 'docker compose' or docker-compose on PATH." >&2
    exit 1
  fi

  # Podman: `podman compose` delegates to podman-compose or docker-compose.
  if command -v podman-compose >/dev/null 2>&1; then
    FWMT_COMPOSE_CMD=(podman-compose)
    return 0
  fi
  if command -v docker-compose >/dev/null 2>&1; then
    export PODMAN_COMPOSE_PROVIDER=docker-compose
    FWMT_COMPOSE_CMD=(podman compose)
    return 0
  fi
  if PODMAN_COMPOSE_PROVIDER=podman-compose podman compose version >/dev/null 2>&1; then
    export PODMAN_COMPOSE_PROVIDER=podman-compose
    FWMT_COMPOSE_CMD=(podman compose)
    return 0
  fi

  echo "Podman is running but no compose provider is installed." >&2
  echo "On macOS with Podman only, install one of:" >&2
  echo "  brew install podman-compose    # recommended" >&2
  echo "  brew install docker-compose" >&2
  echo "Then re-run ./start-infra.sh" >&2
  exit 1
}

init_fwmt_container_runtime() {
  if [[ -n "${FWMT_CONTAINER_RUNTIME_INITIALIZED:-}" ]]; then
    return 0
  fi
  FWMT_CONTAINER_RUNTIME_INITIALIZED=1

  local runtime="$FWMT_RUNTIME"
  case "$runtime" in
    auto)
      if command -v docker >/dev/null 2>&1 && fwmt_runtime_usable docker; then
        runtime=docker
      elif command -v podman >/dev/null 2>&1 && fwmt_runtime_usable podman; then
        runtime=podman
      elif command -v docker >/dev/null 2>&1; then
        runtime=docker
      elif command -v podman >/dev/null 2>&1; then
        runtime=podman
      else
        echo "Need docker or podman on PATH (or set FWMT_RUNTIME)." >&2
        exit 1
      fi
      ;;
    docker|podman) ;;
    *)
      echo "Invalid FWMT_RUNTIME=$runtime (use auto, docker, or podman)." >&2
      exit 1
      ;;
  esac

  case "$runtime" in
    docker)
      FWMT_CONTAINER_CMD=(docker)
      ;;
    podman)
      FWMT_CONTAINER_CMD=(podman)
      ;;
  esac
  resolve_fwmt_compose_cmd "$runtime"
  export FWMT_RUNTIME
}

ensure_fwmt_compose_ready() {
  init_fwmt_container_runtime
  if fwmt_compose_version_check; then
    return 0
  fi
  echo "Compose provider failed: ${FWMT_COMPOSE_CMD[*]}" >&2
  if [[ "$FWMT_RUNTIME" == "podman" ]]; then
    echo "Install: brew install podman-compose" >&2
  fi
  exit 1
}

ensure_fwmt_container_runtime_ready() {
  init_fwmt_container_runtime

  if fwmt_container info >/dev/null 2>&1; then
    ensure_fwmt_compose_ready
    return 0
  fi

  if [[ "$FWMT_RUNTIME" == "podman" ]]; then
    if [[ "$(uname -s)" == "Darwin" ]]; then
      local machines=""
      machines="$(podman machine list --format '{{.Name}}' 2>/dev/null | sed '/^$/d' || true)"

      if [[ -z "$machines" ]]; then
        echo "Podman is installed on macOS but no Podman machine exists." >&2
        echo "Create and start one, then re-run ./start-infra.sh:" >&2
        echo "  podman machine init" >&2
        echo "  podman machine start" >&2
        echo "Or use Docker Desktop: export FWMT_RUNTIME=docker" >&2
        exit 1
      fi

      if [[ "${FWMT_PODMAN_AUTO_START_MACHINE:-1}" != "0" ]]; then
        echo "Podman machine is not running; starting it (FWMT_PODMAN_AUTO_START_MACHINE=0 to disable)..."
        podman machine start >/dev/null 2>&1 || podman machine start
        local waited=0
        while (( waited < 60 )); do
          if fwmt_container info >/dev/null 2>&1; then
            echo "Podman is ready."
            ensure_fwmt_compose_ready
            return 0
          fi
          sleep 2
          waited=$((waited + 2))
        done
      fi
    fi

    echo "Cannot connect to Podman." >&2
    if [[ "$(uname -s)" == "Darwin" ]]; then
      echo "On macOS, start the Podman VM:" >&2
      echo "  podman machine start" >&2
      echo "Check connections:" >&2
      echo "  podman system connection list" >&2
    fi
    exit 1
  fi

  echo "Cannot connect to Docker. Is Docker Desktop (or the docker daemon) running?" >&2
  exit 1
}

fwmt_compose() {
  init_fwmt_container_runtime
  "${FWMT_COMPOSE_CMD[@]}" "$@"
}

fwmt_container() {
  init_fwmt_container_runtime
  "${FWMT_CONTAINER_CMD[@]}" "$@"
}

fwmt_postgres_container() {
  echo "${FWMT_POSTGRES_CONTAINER:-${FWMT_COMPOSE_PROJECT_NAME}_postgres_1}"
}

fwmt_pubsub_container() {
  echo "${FWMT_PUBSUB_CONTAINER:-${FWMT_COMPOSE_PROJECT_NAME}_pubsub_1}"
}

fwmt_infra_container_names() {
  printf '%s\n' \
    "${FWMT_COMPOSE_PROJECT_NAME}_postgres_1" \
    "${FWMT_COMPOSE_PROJECT_NAME}_redis_1" \
    "${FWMT_COMPOSE_PROJECT_NAME}_pubsub_1"
}
