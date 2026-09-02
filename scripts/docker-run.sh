#!/usr/bin/env bash
set -euo pipefail

# Prefer CUCUMBER_TAGS_1 and CUCUMBER_TAGS_2. Fall back to CUCUMBER_TAGS/TEST_TAG for compatibility.
cucumber_tags_1="${CUCUMBER_TAGS_1:-}"
cucumber_tags_2="${CUCUMBER_TAGS_2:-}"
if [[ -z "${cucumber_tags_1}" && -n "${CUCUMBER_TAGS:-}" ]]; then
  cucumber_tags_1="${CUCUMBER_TAGS}"
fi
if [[ -z "${cucumber_tags_1}" && -n "${TEST_TAG:-}" ]]; then
  cucumber_tags_1="@${TEST_TAG#@}"
fi

declare -a executed_run_keys=()
declare -a executed_run_labels=()

overall_mvn_failure=0
overall_mvn_exit_code=0
overall_artifact_failure=0

sanitize_tag_fragment() {
  local raw="$1"
  local sanitized
  sanitized="$(echo "${raw}" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9]+/-/g; s/^-+//; s/-+$//; s/-+/-/g')"
  if [[ -z "${sanitized}" ]]; then
    sanitized="all"
  fi
  echo "${sanitized}"
}

prepare_report_dirs() {
  rm -rf target/jsonReports target/surefire-reports target/cucumber-reports target/cucumber-html-reports target/performance-investigation
}

summarize_current_reports() {
  bad_cucumber_steps=0
  json_report_count=0
  scenario_total=0
  scenario_passed=0
  scenario_failed=0
  scenario_skipped=0
  scenario_other=0
  feature_total=0
  junit_xml_count=0
  junit_tests=0
  junit_failures=0
  junit_errors=0
  junit_skipped=0
  artifact_failure=0

  if compgen -G "target/jsonReports/*.json" >/dev/null; then
    if command -v python3 >/dev/null 2>&1; then
      # Build a scenario-level summary so logs reflect real cucumber outcomes.
      read -r bad_cucumber_steps json_report_count feature_total scenario_total scenario_passed scenario_failed scenario_skipped scenario_other < <(
        python3 - <<'PY'
import glob
import json

failing_statuses = {"failed", "ambiguous", "undefined", "pending"}
skipped_statuses = {"skipped"}
bad = 0
files = 0
features = 0
scenarios = 0
passed = 0
failed = 0
skipped = 0
other = 0

for report in glob.glob("target/jsonReports/*.json"):
    files += 1
    with open(report, "r", encoding="utf-8") as f:
        data = json.load(f)

    for feature in data if isinstance(data, list) else []:
        features += 1
        for element in feature.get("elements", []) or []:
            scenarios += 1
            statuses = []
            for step in element.get("steps", []) or []:
                step_status = ((step.get("result") or {}).get("status") or "").lower()
                if step_status:
                    statuses.append(step_status)
                if step_status in failing_statuses:
                    bad += 1

            for hook in (element.get("before", []) or []) + (element.get("after", []) or []):
                hook_status = ((hook.get("result") or {}).get("status") or "").lower()
                if hook_status:
                    statuses.append(hook_status)
                if hook_status in failing_statuses:
                    bad += 1

            if any(s in failing_statuses for s in statuses):
                failed += 1
            elif statuses and all(s in skipped_statuses for s in statuses):
                skipped += 1
            elif any(s == "passed" for s in statuses):
                passed += 1
            else:
                other += 1

print(f"{bad} {files} {features} {scenarios} {passed} {failed} {skipped} {other}")
PY
      )
    else
      echo "Warning: python3 not found; skipping cucumber JSON summary"
    fi
  fi

  if [[ "${json_report_count}" -eq 0 ]]; then
    echo "Warning: no cucumber JSON reports found under target/jsonReports"
  else
    echo "Acceptance summary: json_reports=${json_report_count} features=${feature_total} scenarios_total=${scenario_total} scenarios_passed=${scenario_passed} scenarios_failed=${scenario_failed} scenarios_skipped=${scenario_skipped} scenarios_other=${scenario_other} failing_steps_or_hooks=${bad_cucumber_steps}"
  fi

  if compgen -G "target/surefire-reports/TEST-*.xml" >/dev/null; then
    if command -v python3 >/dev/null 2>&1; then
      read -r junit_xml_count junit_tests junit_failures junit_errors junit_skipped < <(
        python3 - <<'PY'
import glob
import xml.etree.ElementTree as ET

files = 0
tests = 0
failures = 0
errors = 0
skipped = 0

for report in glob.glob("target/surefire-reports/TEST-*.xml"):
    files += 1
    root = ET.parse(report).getroot()
    tests += int(root.attrib.get("tests", "0"))
    failures += int(root.attrib.get("failures", "0"))
    errors += int(root.attrib.get("errors", "0"))
    skipped += int(root.attrib.get("skipped", "0"))

print(f"{files} {tests} {failures} {errors} {skipped}")
PY
      )
      echo "JUnit summary: xml_reports=${junit_xml_count} tests=${junit_tests} failures=${junit_failures} errors=${junit_errors} skipped=${junit_skipped}"
    else
      echo "Warning: python3 not found; skipping JUnit XML summary"
    fi
  else
    echo "Warning: no JUnit XML files found under target/surefire-reports"
  fi

  if [[ "${scenario_failed}" -gt 0 || "${bad_cucumber_steps}" -gt 0 || "${junit_failures}" -gt 0 || "${junit_errors}" -gt 0 ]]; then
    artifact_failure=1
  fi
}

stage_run_artifacts() {
  local run_key="$1"
  local run_dir="target/runs/${run_key}"
  mkdir -p "${run_dir}"

  if [[ -d target/jsonReports ]]; then
    cp -R target/jsonReports "${run_dir}/"
  fi

  if [[ -d target/performance-investigation ]]; then
    cp -R target/performance-investigation "${run_dir}/"
  fi

  if [[ -d scripts/logs ]]; then
    cp -R scripts/logs "${run_dir}/"
  fi

  if compgen -G "target/surefire-reports/TEST-*.xml" >/dev/null; then
    cp target/surefire-reports/TEST-*.xml "${run_dir}/"
  fi

  if compgen -G "target/artifacts*.xml" >/dev/null; then
    cp target/artifacts*.xml "${run_dir}/"
  fi

  if [[ -d target/surefire-reports ]]; then
    cp -R target/surefire-reports "${run_dir}/"
  fi

  if [[ -d target/cucumber-reports ]]; then
    cp -R target/cucumber-reports "${run_dir}/"
  fi

  if [[ -d target/cucumber-html-reports ]]; then
    cp -R target/cucumber-html-reports "${run_dir}/"
  fi
}

run_suite() {
  local run_label="$1"
  local run_key="$2"
  local run_tags="$3"
  local tag_args=""

  prepare_report_dirs

  if [[ -n "${run_tags}" ]]; then
    tag_args="-Dcucumber.filter.tags=${run_tags}"
    echo "Running ${run_label} with filter: ${run_tags}"
  else
    echo "Running ${run_label} with all acceptance tests"
  fi

  local run_timeout_seconds="${RUN_TIMEOUT_SECONDS:-7200}"
  local maven_log="target/runs/${run_key}/maven.log"
  mkdir -p "target/runs/${run_key}"

  mvn_exit_code=0
  set +e
  timeout --signal=TERM --kill-after=30 "${run_timeout_seconds}" \
    mvn --batch-mode \
      -Dfwmt.performance.run-id="${run_key}" \
      -Dfwmt.performance.timings.file="target/performance-investigation/timings.ndjson" \
      verify ${tag_args} 2>&1 | tee "${maven_log}"
  mvn_exit_code=${PIPESTATUS[0]}
  set -e

  summarize_current_reports
  stage_run_artifacts "${run_key}"

  echo "Finished ${run_label} (${run_key})"
  ls -l target/cucumber-reports 2>/dev/null || echo "Warning: cucumber-reports not found"
  ls -l target/cucumber-html-reports 2>/dev/null || true

  if [[ "${mvn_exit_code}" -eq 124 ]]; then
    overall_mvn_failure=1
    if [[ "${overall_mvn_exit_code}" -eq 0 ]]; then
      overall_mvn_exit_code="${mvn_exit_code}"
    fi
    echo "Maven verify timed out for ${run_label} after ${run_timeout_seconds}s"
  elif [[ "${mvn_exit_code}" -ne 0 ]]; then
    overall_mvn_failure=1
    if [[ "${overall_mvn_exit_code}" -eq 0 ]]; then
      overall_mvn_exit_code="${mvn_exit_code}"
    fi
    echo "Maven verify failed for ${run_label} with exit code ${mvn_exit_code}"
  fi

  if [[ "${artifact_failure}" -ne 0 ]]; then
    overall_artifact_failure=1
    echo "Parsed report failures found for ${run_label}"
  fi
}

upload_reports() {
  if [[ -z "${REPORTS_BUCKET:-}" ]]; then
    echo "REPORTS_BUCKET not set - skipping report upload"
    return 0
  fi

  echo "Uploading reports to ${REPORTS_BUCKET}"
  for run_key in "${executed_run_keys[@]}"; do
    run_dir="target/runs/${run_key}"
    dest="${REPORTS_BUCKET}/${run_key}"
    echo "Uploading ${run_key} artifacts to ${dest}"

    if compgen -G "${run_dir}/TEST-*.xml" >/dev/null; then
      gcloud storage cp "${run_dir}/"TEST-*.xml "${dest}/"
      xml_count=$(find "${run_dir}" -maxdepth 1 -name 'TEST-*.xml' | wc -l | tr -d ' ')
      echo "Uploaded ${xml_count} JUnit XML report(s) for ${run_key}"
    else
      echo "Warning: no JUnit XML files found for ${run_key}"
    fi

    if compgen -G "${run_dir}/artifacts*.xml" >/dev/null; then
      gcloud storage cp "${run_dir}/"artifacts*.xml "${dest}/"
    fi

    if [[ -d "${run_dir}/surefire-reports" ]]; then
      gcloud storage cp -r "${run_dir}/surefire-reports" "${dest}/"
    fi

    if [[ -d "${run_dir}/cucumber-reports" ]]; then
      gcloud storage cp -r "${run_dir}/cucumber-reports" "${dest}/"
    else
      echo "Warning: cucumber report directory not found for ${run_key}"
    fi

    if [[ -d "${run_dir}/cucumber-html-reports" ]]; then
      gcloud storage cp -r "${run_dir}/cucumber-html-reports" "${dest}/"
    fi
  done
}

mkdir -p target/runs

run1_key="run1-$(sanitize_tag_fragment "${cucumber_tags_1:-all}")"
run2_key="run2-$(sanitize_tag_fragment "${cucumber_tags_2:-all}")"

executed_run_keys+=("${run1_key}")
executed_run_labels+=("run_1")
run_suite "run_1" "${run1_key}" "${cucumber_tags_1}"

if [[ -n "${cucumber_tags_2}" ]]; then
  executed_run_keys+=("${run2_key}")
  executed_run_labels+=("run_2")
  run_suite "run_2" "${run2_key}" "${cucumber_tags_2}"
else
  echo "CUCUMBER_TAGS_2 is empty - skipping run_2"
fi

upload_reports

if [[ "${overall_mvn_failure}" -ne 0 ]]; then
  exit "${overall_mvn_exit_code}"
fi

if [[ "${overall_artifact_failure}" -ne 0 ]]; then
  echo "Failing job due to test failures found in generated reports"
  exit 1
fi
