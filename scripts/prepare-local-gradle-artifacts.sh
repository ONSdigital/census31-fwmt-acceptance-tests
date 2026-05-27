#!/usr/bin/env bash
# Deprecated name — forwards to Maven-only prepare-local-fwmt-libs.sh
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "$SCRIPT_DIR/prepare-local-fwmt-libs.sh" "$@"
