#!/usr/bin/env bash
# Builds and installs census31-fwmt library jars to ~/.m2 (Maven only).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=local-test-env.sh
source "$SCRIPT_DIR/local-test-env.sh"

CENSUS31_FWMT_ROOT="${CENSUS31_FWMT_ROOT:-$(cd "$SCRIPT_DIR/../.." && pwd)}"
FWMT_PARENT_DIR="${FWMT_PARENT_DIR:-$CENSUS31_FWMT_ROOT/census31-fwmt-parent}"

if [[ ! -f "$FWMT_PARENT_DIR/pom.xml" ]]; then
  echo "Missing census31-fwmt-parent POM: $FWMT_PARENT_DIR/pom.xml" >&2
  exit 1
fi

echo "Installing census31-fwmt-parent BOM to local Maven repository"
run_maven_in_repo "$FWMT_PARENT_DIR" -q install -N

for lib in \
  census31-fwmt-canonical \
  census31-fwmt-common \
  census31-fwmt-events \
  census31-fwmt-storage-utils; do
  echo "Installing $lib from $CENSUS31_FWMT_ROOT/$lib"
  run_maven_in_repo "$CENSUS31_FWMT_ROOT/$lib" -q install -Dmaven.test.skip=true
done

echo "FWMT library artifacts installed to local Maven repository."
