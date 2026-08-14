#!/usr/bin/env zsh
set -euo pipefail

# Run two tagged acceptance suites with isolated reports, bounded runtime, and optional GCS upload.
# Usage:
#   ./scripts/run-tagged-acceptance.sh
#   TAG_1=@Feedback KEY_1=feedback TAG_2=@Feedback KEY_2=feedback-retry \
#     REPORTS_BUCKET=gs://my-bucket MAX_SECONDS=600 ./scripts/run-tagged-acceptance.sh
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="${REPO_DIR:-$(cd "${SCRIPT_DIR}/.." && pwd)}"
MAX_SECONDS="${MAX_SECONDS:-300}"
TAG_1="${TAG_1:-@Census27Test}"
KEY_1="${KEY_1:-census27}"
TAG_2="${TAG_2:-@Regression}"
KEY_2="${KEY_2:-regression}"

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

  # Use mise when available (local dev); fall back to plain mvn in Docker/CI.
  local mvn_cmd
  if command -v mise >/dev/null 2>&1; then
    mvn_cmd=(mise exec -- mvn)
  else
    mvn_cmd=(mvn)
  fi

  set +e
  (
    cd "${REPO_DIR}"
    "${mvn_cmd[@]}" --batch-mode --offline clean test \
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

upload_reports() {
  local bucket="${REPORTS_BUCKET:-}"
  if [[ -z "${bucket}" ]]; then
    echo "REPORTS_BUCKET not set — skipping upload"
    return 0
  fi
  local dest="${bucket}/$(date -u +%Y%m%dT%H%M%SZ)"
  echo "Uploading reports to ${dest}"
  # Upload all cucumber-reports-* directories and the status file.
  gcloud storage cp -r "${REPO_DIR}/target/cucumber-reports-"* "${dest}/" \
    || echo "WARNING: report directory upload failed (non-fatal)"
  [[ -f "${REPO_DIR}/target/tag-run-status.env" ]] && \
    gcloud storage cp "${REPO_DIR}/target/tag-run-status.env" "${dest}/tag-run-status.env" \
    || true
  echo "Reports uploaded to ${dest}"
}

rm -f "${REPO_DIR}/target/tag-run-status.env"

run_tag "${TAG_1}" "${KEY_1}"
run_tag "${TAG_2}" "${KEY_2}"

upload_reports

echo "Status file: ${REPO_DIR}/target/tag-run-status.env"

