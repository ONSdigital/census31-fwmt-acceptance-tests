#!/usr/bin/env zsh
set -euo pipefail

# Run two tagged acceptance suites with isolated reports and bounded runtime.
# Usage:
#   ./scripts/run-tagged-acceptance.sh
#   REPO_DIR=/path/to/census31-fwmt-acceptance-tests MAX_SECONDS=600 ./scripts/run-tagged-acceptance.sh
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="${REPO_DIR:-$(cd "${SCRIPT_DIR}/.." && pwd)}"
MAX_SECONDS="${MAX_SECONDS:-300}"

if [[ ! -d "${REPO_DIR}" ]]; then
  echo "REPO_DIR does not exist: ${REPO_DIR}" >&2
  exit 1
fi

run_tag() {
  local tag="$1"
  local key="$2"

  local log_file="${REPO_DIR}/target/${key}.log"
  local report_dir="${REPO_DIR}/target/cucumber-reports-${key}"

  mkdir -p "${REPO_DIR}/target"
  rm -rf "${report_dir}"

  echo "Running tag ${tag} (max ${MAX_SECONDS}s)"

  set +e
  (
    cd "${REPO_DIR}"
    mise exec -- mvn clean verify \
      "-Dcucumber.filter.tags=${tag}" \
      "-Dcucumber.report.outputDirectory=target/cucumber-reports-${key}" \
      > "${log_file}" 2>&1 &
    local pid=$!

    (
      sleep "${MAX_SECONDS}"
      if kill -0 "${pid}" 2>/dev/null; then
        echo "TIMEOUT after ${MAX_SECONDS}s" >> "${log_file}"
        kill -TERM "${pid}" 2>/dev/null || true
        sleep 5
        kill -KILL "${pid}" 2>/dev/null || true
      fi
    ) &
    local watchdog=$!

    wait "${pid}"
    local rc=$?
    kill "${watchdog}" 2>/dev/null || true
    wait "${watchdog}" 2>/dev/null || true
    exit "${rc}"
  )
  local rc=$?
  set -e

  if [[ ${rc} -eq 0 ]]; then
    echo "${key}=PASS" >> "${REPO_DIR}/target/tag-run-status.env"
  elif grep -q "TIMEOUT after" "${log_file}"; then
    echo "${key}=TIMEOUT" >> "${REPO_DIR}/target/tag-run-status.env"
  else
    echo "${key}=FAIL" >> "${REPO_DIR}/target/tag-run-status.env"
  fi

  echo "${key} exit code: ${rc}"
  echo "Log: ${log_file}"
  echo "Report: ${report_dir}"

  return ${rc}
}

rm -f "${REPO_DIR}/target/tag-run-status.env"

run_tag "@Census27Test" "census27"
run_tag "@Regression" "regression"

echo "Status file: ${REPO_DIR}/target/tag-run-status.env"
