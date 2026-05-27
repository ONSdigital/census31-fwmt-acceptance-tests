#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/local-test-env.sh"

PREPARE=false
WITH_CSV=false
services=()

usage() {
  cat <<'EOF'
Usage: ./build-service.sh [--prepare] [--with-csv] <service> [...]

Builds Spring Boot executable jars (Maven) for selected services.

Known services:
  tm-mock
  job-service
  outcome-service
  csv-service

Examples:
  ./build-service.sh job-service
  ./build-service.sh --prepare tm-mock job-service outcome-service
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --prepare)
      PREPARE=true
      shift
      ;;
    --with-csv)
      WITH_CSV=true
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
  echo "At least one service name is required." >&2
  usage >&2
  exit 1
fi

if [[ "$WITH_CSV" == "true" ]]; then
  services+=("csv-service")
fi

require_java17

if [[ "$PREPARE" == "true" ]]; then
  "$SCRIPT_DIR/prepare-local-artifacts.sh"
fi

for service in "${services[@]}"; do
  repo="$(service_repo "$service")"
  service_dir="$CENSUS31_FWMT_ROOT/$repo"

  echo "Building $service boot jar from $service_dir"
  run_maven_in_repo "$service_dir" -B clean package \
    -Dmaven.test.skip=true \
    -Dskip.integration.tests=true

  if jar_path="$(latest_boot_jar "$service_dir")"; then
    echo "$service jar ready: $jar_path"
  else
    echo "Built $service but could not find a boot jar under $service_dir/target" >&2
    exit 1
  fi
done
