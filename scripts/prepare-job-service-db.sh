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

usage() {
  cat <<'EOF'
Usage: ./prepare-job-service-db.sh

Runs census31-fwmt-job-service Liquibase changelog against local Postgres
(schema fwmtg). Requires docker postgres from ./start-infra.sh.

Uses the Liquibase Maven plugin by full coordinate (no liquibase prefix in pom).
EOF
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

echo "Ensuring fwmtg schema exists in Postgres on port $POSTGRES_PORT..."
docker exec census31-fwmt-acceptance-tests-postgres-1 \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -c "CREATE SCHEMA IF NOT EXISTS fwmtg;" >/dev/null 2>&1 || {
  echo "WARN: could not exec into census31-fwmt-acceptance-tests-postgres-1; is ./start-infra.sh up?" >&2
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
docker exec census31-fwmt-acceptance-tests-postgres-1 \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "\dt fwmtg.*"

echo "Job-service database ready."
