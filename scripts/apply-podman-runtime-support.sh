#!/usr/bin/env bash
# apply-podman-runtime-support.sh
#
# Adds Docker/Podman runtime abstraction to the acceptance-test harness (Option B).
# Run once on a machine, then use FWMT_RUNTIME=podman when Docker is not installed.
#
# Usage:
#   ./apply-podman-runtime-support.sh
#   ./apply-podman-runtime-support.sh --dry-run
#
# After applying on a Podman-only host:
#   export FWMT_RUNTIME=podman
#   ./start-infra.sh

set -euo pipefail

DRY_RUN=false

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run) DRY_RUN=true; shift ;;
    -h|--help)
      sed -n '2,14p' "$0"
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
MARKER="FWMT_CONTAINER_RUNTIME_INITIALIZED"

if [[ ! -f "$SCRIPT_DIR/local-test-env.sh" ]]; then
  echo "ERROR: missing $SCRIPT_DIR/local-test-env.sh" >&2
  exit 1
fi

if grep -q 'ensure_fwmt_container_runtime_ready' "$SCRIPT_DIR/local-test-env.sh" 2>/dev/null; then
  echo "Already applied (ensure_fwmt_container_runtime_ready found in local-test-env.sh). Nothing to do."
  exit 0
fi

if grep -q "$MARKER" "$SCRIPT_DIR/local-test-env.sh" 2>/dev/null; then
  echo "Partial apply detected ($MARKER without macOS readiness). Re-run after pulling latest, or patch local-test-env.sh manually." >&2
  exit 1
fi

BACKUP_SUFFIX=".bak.$(date +%Y%m%d%H%M%S)"

backup() {
  local f="$1"
  if [[ "$DRY_RUN" == true ]]; then
    echo "[dry-run] would backup: $f -> ${f}${BACKUP_SUFFIX}"
  else
    cp -a "$f" "${f}${BACKUP_SUFFIX}"
    echo "Backed up: $f -> ${f}${BACKUP_SUFFIX}"
  fi
}

write_file() {
  local path="$1"
  if [[ "$DRY_RUN" == true ]]; then
    echo "[dry-run] would write: $path"
    return 0
  fi
  cat >"$path"
  echo "Wrote: $path"
}

echo "Applying Podman/Docker runtime support under: $SCRIPT_DIR"

for f in \
  local-test-env.sh \
  start-infra.sh \
  drop-infra.sh \
  setup-pubsub.sh \
  prepare-job-service-db.sh
do
  backup "$SCRIPT_DIR/$f"
done

if [[ "$DRY_RUN" != true ]]; then
  cat >>"$SCRIPT_DIR/local-test-env.sh" <<'RUNTIME_BLOCK'

# --- Container runtime (Docker / Podman) — added by apply-podman-runtime-support.sh ---
# Override examples:
#   export FWMT_RUNTIME=podman          # force Podman
#   export FWMT_RUNTIME=docker          # force Docker
#   export FWMT_COMPOSE_PROJECT_NAME=my-project
#   export FWMT_POSTGRES_CONTAINER=my-project-postgres-1
FWMT_COMPOSE_PROJECT_NAME="${FWMT_COMPOSE_PROJECT_NAME:-${FWMT_DOCKER_PROJECT_NAME:-census31-fwmt-acceptance-tests}}"
FWMT_RUNTIME="${FWMT_RUNTIME:-auto}"

init_fwmt_container_runtime() {
  if [[ -n "${FWMT_CONTAINER_RUNTIME_INITIALIZED:-}" ]]; then
    return 0
  fi
  FWMT_CONTAINER_RUNTIME_INITIALIZED=1

  local runtime="$FWMT_RUNTIME"
  case "$runtime" in
    auto)
      if command -v docker >/dev/null 2>&1; then
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
      FWMT_COMPOSE_CMD=(docker compose)
      ;;
    podman)
      FWMT_CONTAINER_CMD=(podman)
      FWMT_COMPOSE_CMD=(podman compose)
      ;;
  esac
  export FWMT_RUNTIME
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
  echo "${FWMT_POSTGRES_CONTAINER:-${FWMT_COMPOSE_PROJECT_NAME}-postgres-1}"
}

fwmt_pubsub_container() {
  echo "${FWMT_PUBSUB_CONTAINER:-${FWMT_COMPOSE_PROJECT_NAME}-pubsub-1}"
}

fwmt_infra_container_names() {
  printf '%s\n' \
    "${FWMT_COMPOSE_PROJECT_NAME}-postgres-1" \
    "${FWMT_COMPOSE_PROJECT_NAME}-redis-1" \
    "${FWMT_COMPOSE_PROJECT_NAME}-pubsub-1"
}
# --- end container runtime block ---
RUNTIME_BLOCK
  echo "Patched: $SCRIPT_DIR/local-test-env.sh"
else
  echo "[dry-run] would append runtime block to local-test-env.sh"
fi

write_file "$SCRIPT_DIR/start-infra.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=local-test-env.sh
source "$SCRIPT_DIR/local-test-env.sh"

COMPOSE_FILE="${COMPOSE_FILE:-$SCRIPT_DIR/docker-compose-infra.yml}"

echo "Starting FWMT acceptance-test infrastructure (runtime=${FWMT_RUNTIME:-auto})..."
fwmt_compose -f "$COMPOSE_FILE" up -d

mapfile -t containers < <(fwmt_infra_container_names)

infra_timeout="${FWMT_INFRA_TIMEOUT_SECONDS:-120}"
[[ -z "$infra_timeout" ]] && infra_timeout=120
start_epoch=$(date +%s)
deadline=$((start_epoch + infra_timeout))

while true; do
  all_ready=true
  for container in "${containers[@]}"; do
    status="$(fwmt_container inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || true)"
    if [[ "$status" != "healthy" && "$status" != "running" ]]; then
      all_ready=false
      break
    fi
  done

  if [[ "$all_ready" == "true" ]]; then
    break
  fi

  if (( $(date +%s) >= deadline )); then
    echo "Timed out waiting for infrastructure to become healthy." >&2
    fwmt_compose -f "$COMPOSE_FILE" ps >&2
    exit 1
  fi

  sleep 2
done

fwmt_compose -f "$COMPOSE_FILE" ps
echo "Infrastructure is ready."
EOF

write_file "$SCRIPT_DIR/drop-infra.sh" <<'EOF'
#!/usr/bin/env bash
# Stop acceptance-test container infrastructure (Postgres, Pub/Sub emulator, Redis).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=local-test-env.sh
source "$SCRIPT_DIR/local-test-env.sh"

COMPOSE_FILE="${COMPOSE_FILE:-$SCRIPT_DIR/docker-compose-infra.yml}"
REMOVE_VOLUMES=false

usage() {
  cat <<'USAGE'
Usage: ./drop-infra.sh [options]

Stops containers from docker-compose-infra.yml (project: census31-fwmt-acceptance-tests).

Options:
  -v, --volumes   Also remove named volumes (Postgres / Redis data)
  -h, --help      Show this help

Examples:
  ./drop-infra.sh
  ./drop-infra.sh --volumes
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -v|--volumes) REMOVE_VOLUMES=true; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1" >&2; usage; exit 1 ;;
  esac
done

echo "Stopping FWMT acceptance-test infrastructure..."
if [[ "$REMOVE_VOLUMES" == true ]]; then
  fwmt_compose -f "$COMPOSE_FILE" down -v
  echo "Infrastructure stopped (volumes removed)."
else
  fwmt_compose -f "$COMPOSE_FILE" down
  echo "Infrastructure stopped (volumes retained; use --volumes to remove)."
fi
EOF

write_file "$SCRIPT_DIR/setup-pubsub.sh" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

# Pub/Sub emulator bootstrap (topics + subscriptions) via the emulator HTTP API.
#
# Host HTTP bootstrap: runs from the host against the published emulator port.
# No local gcloud install required.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=local-test-env.sh
source "$SCRIPT_DIR/local-test-env.sh"

PUBSUB_HOST="${FWMT_PUBSUB_HOST:-localhost}"
PUBSUB_PORT="${FWMT_PUBSUB_EMULATOR_PORT:-8085}"
PUBSUB_PROJECT="${FWMT_PUBSUB_PROJECT:-fwmt-local}"
PUBSUB_API_BASE="http://${PUBSUB_HOST}:${PUBSUB_PORT}/v1/projects/${PUBSUB_PROJECT}"

PUBSUB_CONTAINER="$(fwmt_pubsub_container)"

pubsub_api_path_segment() {
  # Encode topic/subscription id for URL path (dots are fine; other chars encoded).
  python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1], safe=""))' "$1"
}

ensure_emulator_reachable() {
  if ! curl -fsS "${PUBSUB_API_BASE}/topics" -H "Content-Type: application/json" >/dev/null 2>&1; then
    if fwmt_container inspect "$PUBSUB_CONTAINER" >/dev/null 2>&1; then
      local status
      status="$(fwmt_container inspect -f '{{.State.Status}}' "$PUBSUB_CONTAINER" 2>/dev/null || true)"
      if [[ "$status" != "running" ]]; then
        echo "Pub/Sub emulator container is not running ($PUBSUB_CONTAINER status=$status)" >&2
        fwmt_container logs "$PUBSUB_CONTAINER" 2>&1 | tail -20 >&2 || true
        echo "Recreate infra: $SCRIPT_DIR/drop-infra.sh && $SCRIPT_DIR/start-infra.sh" >&2
        exit 1
      fi
    fi
    echo "Cannot reach Pub/Sub emulator at ${PUBSUB_HOST}:${PUBSUB_PORT}" >&2
    echo "Start infra first: $SCRIPT_DIR/start-infra.sh" >&2
    exit 1
  fi
}

create_topic_if_missing() {
  local topic="$1"
  local segment
  segment="$(pubsub_api_path_segment "$topic")"
  if curl -fsS "${PUBSUB_API_BASE}/topics/${segment}" >/dev/null 2>&1; then
    return 0
  fi
  curl -fsS -X PUT "${PUBSUB_API_BASE}/topics/${segment}" \
    -H "Content-Type: application/json" \
    -d '{}' >/dev/null
}

create_subscription_if_missing() {
  local subscription="$1"
  local topic="$2"
  local sub_segment topic_segment topic_resource
  sub_segment="$(pubsub_api_path_segment "$subscription")"
  topic_segment="$(pubsub_api_path_segment "$topic")"
  topic_resource="projects/${PUBSUB_PROJECT}/topics/${topic}"

  if curl -fsS "${PUBSUB_API_BASE}/subscriptions/${sub_segment}" >/dev/null 2>&1; then
    return 0
  fi
  curl -fsS -X PUT "${PUBSUB_API_BASE}/subscriptions/${sub_segment}" \
    -H "Content-Type: application/json" \
    -d "{\"topic\":\"${topic_resource}\"}" >/dev/null
}

create_subscription_with_dlq_if_missing() {
  local subscription="$1"
  local topic="$2"
  local dlq_topic="$3"
  local max_attempts="${4:-5}"
  local sub_segment topic_resource dlq_resource
  sub_segment="$(pubsub_api_path_segment "$subscription")"
  topic_resource="projects/${PUBSUB_PROJECT}/topics/${topic}"
  dlq_resource="projects/${PUBSUB_PROJECT}/topics/${dlq_topic}"

  if curl -fsS "${PUBSUB_API_BASE}/subscriptions/${sub_segment}" >/dev/null 2>&1; then
    return 0
  fi
  curl -fsS -X PUT "${PUBSUB_API_BASE}/subscriptions/${sub_segment}" \
    -H "Content-Type: application/json" \
    -d "{\"topic\":\"${topic_resource}\",\"deadLetterPolicy\":{\"deadLetterTopic\":\"${dlq_resource}\",\"maxDeliveryAttempts\":${max_attempts}}}" >/dev/null
}

safe_sub_name() {
  local raw="$1"
  raw="${raw//[^a-zA-Z0-9]/-}"
  raw="$(echo "$raw" | tr -s '-')"
  echo "$raw"
}

ensure_emulator_reachable

echo "Bootstrapping Pub/Sub emulator at ${PUBSUB_HOST}:${PUBSUB_PORT} (project=${PUBSUB_PROJECT})"

TOPICS=(
  "RM.Field"
  "RM.FieldDLQ"
  "GW.Field"
  "GW.Permanent.ErrorQ"
  "GW.Transient.ErrorQ"
  "GW.ErrorQ"
  "Outcome.Preprocessing"
  "Outcome.PreprocessingDLQ"
  "Outcome.PreprocesingDLQ"
  "Field.refusals"
  "Field.other"
  "events"
  "Gateway.Events.Exchange"
  "GW.Error.Exchange"
  "adapter-outbound-exchange"
  "Outcome.Preprocessing.Exchange"
  "Gateway.Actions.Exchange"
)

for topic in "${TOPICS[@]}"; do
  create_topic_if_missing "$topic"
done

# Publishers only (no subscription): Gateway.Actions.Exchange (csv-service), Gateway.Events.Exchange (events lib)
SUBS=(
  "job-service:RM.Field"
  "job-service:GW.Field"
  "job-service:GW.Transient.ErrorQ"
  "job-service:GW.Permanent.ErrorQ"
  "outcome-service:Outcome.Preprocessing"
  "outcome-service:Outcome.PreprocessingDLQ"
  "outcome-service:events"
  "fulfilment-event-service:events"
)

for pair in "${SUBS[@]}"; do
  service="${pair%%:*}"
  topic="${pair#*:}"
  subscription="$(safe_sub_name "$service-$topic")"
  if [[ "$service" == "outcome-service" && "$topic" == "Outcome.Preprocessing" ]]; then
    create_subscription_with_dlq_if_missing "$subscription" "$topic" "Outcome.PreprocessingDLQ" 5
  else
    create_subscription_if_missing "$subscription" "$topic"
  fi
done

# Acceptance-test-only subscriptions (drain in Cucumber without stealing service traffic)
ACCEPTANCE_TEST_SUBS=(
  "acceptance-tests-RM-Field:RM.Field"
  "acceptance-tests-RM-FieldDLQ:RM.FieldDLQ"
  "acceptance-tests-GW-Transient-ErrorQ:GW.Transient.ErrorQ"
  "acceptance-tests-GW-Permanent-ErrorQ:GW.Permanent.ErrorQ"
  "acceptance-tests-Outcome-Preprocessing:Outcome.Preprocessing"
  "acceptance-tests-Outcome-PreprocessingDLQ:Outcome.PreprocessingDLQ"
  "acceptance-tests-Field-refusals:Field.refusals"
  "acceptance-tests-Field-other:Field.other"
  "acceptance-tests-Gateway-Events:Gateway.Events.Exchange"
)

for pair in "${ACCEPTANCE_TEST_SUBS[@]}"; do
  subscription="${pair%%:*}"
  topic="${pair#*:}"
  create_subscription_if_missing "$subscription" "$topic"
done

echo "Pub/Sub emulator topics/subscriptions are ready."
EOF

write_file "$SCRIPT_DIR/prepare-job-service-db.sh" <<'EOF'
#!/usr/bin/env bash
# Apply job-service Liquibase migrations to local Postgres (harness docker-compose-infra).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=local-test-env.sh
source "$SCRIPT_DIR/local-test-env.sh"

JOB_SERVICE_DIR="${CENSUS31_FWMT_ROOT}/census31-fwmt-job-service"
POSTGRES_PORT="${FWMT_POSTGRES_PORT:-5432}"
POSTGRES_USER="${FWMT_POSTGRES_USER:-postgres}"
POSTGRES_PASSWORD="${FWMT_POSTGRES_PASSWORD:-postgres}"
POSTGRES_DB="${FWMT_POSTGRES_DB:-postgres}"
MAVEN_BIN="${FWMT_MAVEN_BIN:-mvn}"
LIQUIBASE_PLUGIN_VERSION="${FWMT_LIQUIBASE_PLUGIN_VERSION:-4.31.1}"
POSTGRES_CONTAINER="$(fwmt_postgres_container)"

usage() {
  cat <<'USAGE'
Usage: ./prepare-job-service-db.sh

Runs census31-fwmt-job-service Liquibase changelog against local Postgres
(schema fwmtg). Requires Postgres from ./start-infra.sh.

Uses the Liquibase Maven plugin by full coordinate (no liquibase prefix in pom).
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown argument: $1" >&2; usage >&2; exit 1 ;;
  esac
done

if [[ ! -f "$JOB_SERVICE_DIR/pom.xml" ]]; then
  echo "Missing job-service POM: $JOB_SERVICE_DIR/pom.xml" >&2
  exit 1
fi

if ! command -v "$MAVEN_BIN" >/dev/null 2>&1; then
  echo "Maven not found: $MAVEN_BIN" >&2
  exit 1
fi

echo "Ensuring fwmtg schema exists in Postgres on port $POSTGRES_PORT (container=$POSTGRES_CONTAINER)..."
fwmt_container exec "$POSTGRES_CONTAINER" \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -c "CREATE SCHEMA IF NOT EXISTS fwmtg;" >/dev/null 2>&1 || {
  echo "WARN: could not exec into $POSTGRES_CONTAINER; is ./start-infra.sh up?" >&2
}

echo "Running Liquibase update for job-service..."
(
  cd "$JOB_SERVICE_DIR"
  "$MAVEN_BIN" -q "org.liquibase:liquibase-maven-plugin:${LIQUIBASE_PLUGIN_VERSION}:update" \
    -Dliquibase.changeLogFile=src/main/resources/db/changelog/db.changelog-master.yml \
    -Dliquibase.url="jdbc:postgresql://localhost:${POSTGRES_PORT}/${POSTGRES_DB}" \
    -Dliquibase.defaultSchemaName=fwmtg \
    -Dliquibase.liquibaseSchemaName=fwmtg \
    -Dliquibase.username="$POSTGRES_USER" \
    -Dliquibase.password="$POSTGRES_PASSWORD"
)

echo "Verifying fwmtg tables..."
fwmt_container exec "$POSTGRES_CONTAINER" \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "\dt fwmtg.*"

echo "Job-service database ready."
EOF

README="$REPO_ROOT/README.md"
if [[ -f "$README" ]] && ! grep -q 'FWMT_RUNTIME' "$README" 2>/dev/null; then
  if [[ "$DRY_RUN" == true ]]; then
    echo "[dry-run] would append Podman section to README.md"
  else
    backup "$README"
    cat >>"$README" <<'EOF'

## Container runtime (Docker / Podman)

Infra scripts use `fwmt_compose` / `fwmt_container` from `scripts/local-test-env.sh`.
Apply once with `scripts/apply-podman-runtime-support.sh` if your checkout predates this support.

- **Auto-detect** (default): Docker if present, else Podman.
- **Force Podman**: `export FWMT_RUNTIME=podman`
- **Force Docker**: `export FWMT_RUNTIME=docker`

Requires `podman compose` (Podman 4+) or `docker compose`. Container names follow compose project `census31-fwmt-acceptance-tests`; override with `FWMT_COMPOSE_PROJECT_NAME`, `FWMT_POSTGRES_CONTAINER`, or `FWMT_PUBSUB_CONTAINER` if needed.
EOF
    echo "Appended Podman section to README.md"
  fi
fi

if [[ "$DRY_RUN" != true ]]; then
  chmod +x \
    "$SCRIPT_DIR/apply-podman-runtime-support.sh" \
    "$SCRIPT_DIR/start-infra.sh" \
    "$SCRIPT_DIR/drop-infra.sh" \
    "$SCRIPT_DIR/setup-pubsub.sh" \
    "$SCRIPT_DIR/prepare-job-service-db.sh"
fi

cat <<EOF

Done.

Verify:
  cd "$SCRIPT_DIR"
  export FWMT_RUNTIME=podman   # when both docker and podman are installed
  ./start-infra.sh
  ./setup-pubsub.sh

Revert: restore *.bak.* files next to each modified script.
EOF
