#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WITH_CSV=false
args=()

usage() {
  cat <<'EOF'
Usage: ./build-services.sh [--prepare] [--with-csv]

Builds boot jars for the default acceptance-test services:
  - tm-mock
  - job-service
  - outcome-service

Add --with-csv to build csv-service too.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --with-csv)
      WITH_CSV=true
      shift
      ;;
    --prepare)
      args+=("--prepare")
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 1
      ;;
  esac
done

services=(tm-mock job-service outcome-service)
if [[ "$WITH_CSV" == "true" ]]; then
  services+=(csv-service)
fi

"$SCRIPT_DIR/build-service.sh" "${args[@]}" "${services[@]}"
