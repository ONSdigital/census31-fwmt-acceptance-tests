#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

export CENSUS31_FWMT_ROOT="${CENSUS31_FWMT_ROOT:-/workspace/census31}"
export FWMT_POSTGRES_HOST="${FWMT_POSTGRES_HOST:-postgres}"
export FWMT_POSTGRES_PORT="${FWMT_POSTGRES_PORT:-5432}"
export FWMT_POSTGRES_DB="${FWMT_POSTGRES_DB:-postgres}"
export FWMT_POSTGRES_USER="${FWMT_POSTGRES_USER:-postgres}"
export FWMT_POSTGRES_PASSWORD="${FWMT_POSTGRES_PASSWORD:-postgres}"
export FWMT_RUN_LIQUIBASE_UPDATE="${FWMT_RUN_LIQUIBASE_UPDATE:-true}"
export FWMT_MAVEN_BIN="${FWMT_MAVEN_BIN:-mvn}"
FWMT_RESET_DB_SCHEMA="${FWMT_RESET_DB_SCHEMA:-false}"

if [[ "$FWMT_RESET_DB_SCHEMA" == "true" ]]; then
	echo "Resetting schema fwmtg on ${FWMT_POSTGRES_HOST}:${FWMT_POSTGRES_PORT}..."
	PGPASSWORD="$FWMT_POSTGRES_PASSWORD" psql \
		-h "$FWMT_POSTGRES_HOST" \
		-p "$FWMT_POSTGRES_PORT" \
		-U "$FWMT_POSTGRES_USER" \
		-d "$FWMT_POSTGRES_DB" \
		-c "DROP SCHEMA IF EXISTS fwmtg CASCADE; CREATE SCHEMA fwmtg;"
fi

# prepare-job-service-db.sh performs schema setup and Liquibase migration.
bash "$SCRIPT_DIR/prepare-job-service-db.sh"
