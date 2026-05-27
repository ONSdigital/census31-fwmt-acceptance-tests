#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CENSUS31_FWMT_ROOT="${CENSUS31_FWMT_ROOT:-/home/simon/dev/sourcecode/census31}"
CENSUS31_INTEGRATION_COMMON_ROOT="${CENSUS31_INTEGRATION_COMMON_ROOT:-$CENSUS31_FWMT_ROOT}"
CENSUS31_INT_COMMON_BACKEND="${CENSUS31_INT_COMMON_BACKEND:-$CENSUS31_FWMT_ROOT/census31-int-common-backend}"
STATE_DIR="$SCRIPT_DIR/.local-artifacts"
FINGERPRINT_FILE="$STATE_DIR/prepare.fingerprint"
FORCE=false

usage() {
  cat <<'EOF'
Usage: ./prepare-local-artifacts.sh [--force]

Builds and installs local Maven dependency artifacts required by FWMT services.
Skips when inputs are unchanged unless --force is supplied.

Steps:
  1. prepare-local-maven-artifacts.sh — CTP integration libs (census31-int-* seeds)
  2. prepare-local-fwmt-libs.sh — census31-fwmt-parent BOM + FWMT libraries
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --force)
      FORCE=true
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

hash_file() {
  local file="$1"
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$file" | awk '{print $1}'
  else
    cksum "$file" | awk '{print $1 ":" $2}'
  fi
}

fwmt_seed_fingerprint() {
  local name="$1"
  local d="$CENSUS31_FWMT_ROOT/$name"
  if [[ ! -d "$d" ]]; then
    echo "$name:missing"
    return
  fi
  echo -n "$name:"
  (
    cd "$d"
    if [[ -f version.txt ]]; then hash_file version.txt; else echo nov; fi
    if [[ -f pom.xml ]]; then hash_file pom.xml; else echo nop; fi
  ) | sha256sum 2>/dev/null | awk '{print $1}' || echo unknown
}

census31_int_common_backend_fingerprint() {
  local d="$CENSUS31_INT_COMMON_BACKEND"
  if [[ ! -f "$d/pom.xml" ]]; then
    echo "census31-int-common-backend:missing"
    return
  fi
  echo -n "census31-int-common-backend:"
  (
    hash_file "$d/pom.xml"
    for sub in framework test-framework product-reference standards; do
      if [[ -f "$d/$sub/pom.xml" ]]; then
        hash_file "$d/$sub/pom.xml"
      fi
    done
  ) | sha256sum 2>/dev/null | awk '{print $1}' || echo unknown
}

compute_fingerprint() {
  hash_file "$SCRIPT_DIR/prepare-local-maven-artifacts.sh"
  hash_file "$SCRIPT_DIR/prepare-local-fwmt-libs.sh"
  if [[ -f "$CENSUS31_FWMT_ROOT/census31-fwmt-parent/pom.xml" ]]; then
    hash_file "$CENSUS31_FWMT_ROOT/census31-fwmt-parent/pom.xml"
  fi

  fwmt_seed_fingerprint "census31-fwmt-storage-utils"
  fwmt_seed_fingerprint "census31-fwmt-common"
  fwmt_seed_fingerprint "census31-fwmt-events"
  fwmt_seed_fingerprint "census31-fwmt-canonical"

  census31_int_common_backend_fingerprint
}

mkdir -p "$STATE_DIR"
current_fingerprint="$(compute_fingerprint)"

if [[ "$FORCE" != "true" && -f "$FINGERPRINT_FILE" ]] && [[ "$(cat "$FINGERPRINT_FILE")" == "$current_fingerprint" ]]; then
  echo "Local dependency artifacts already prepared; skipping. Use --force to rebuild them."
  exit 0
fi

for script in prepare-local-maven-artifacts.sh prepare-local-fwmt-libs.sh; do
  if [[ ! -x "$SCRIPT_DIR/$script" ]]; then
    echo "Missing or not executable: $SCRIPT_DIR/$script" >&2
    exit 1
  fi
done

export CENSUS31_FWMT_ROOT CENSUS31_INTEGRATION_COMMON_ROOT CENSUS31_INT_COMMON_BACKEND

"$SCRIPT_DIR/prepare-local-maven-artifacts.sh"
"$SCRIPT_DIR/prepare-local-fwmt-libs.sh"

printf '%s\n' "$current_fingerprint" >"$FINGERPRINT_FILE"
echo "Local dependency artifacts prepared."
