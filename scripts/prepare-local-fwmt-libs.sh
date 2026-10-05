#!/usr/bin/env bash
# Builds and installs census31-fwmt library jars to ~/.m2 (Maven only).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=local-test-env.sh
source "$SCRIPT_DIR/local-test-env.sh"

CENSUS31_FWMT_ROOT="${CENSUS31_FWMT_ROOT:-$(cd "$SCRIPT_DIR/../.." && pwd)}"
FWMT_COMMON_DIR="${FWMT_COMMON_DIR:-$CENSUS31_FWMT_ROOT/census31-fwmt-common}"

if [[ ! -f "$FWMT_COMMON_DIR/pom.xml" || ! -f "$FWMT_COMMON_DIR/census31-fwmt-common/pom.xml" ]]; then
  echo "Missing census31-fwmt-common reactor POMs under $FWMT_COMMON_DIR" >&2
  exit 1
fi

echo "Installing census31-fwmt-common parent and library from $FWMT_COMMON_DIR"
run_maven_in_repo "$FWMT_COMMON_DIR" -B install -Dmaven.test.skip=true

echo "FWMT library artifacts installed to local Maven repository."
