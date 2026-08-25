#!/usr/bin/env bash
set -euo pipefail

# Prefer CUCUMBER_TAGS. Fall back to TEST_TAG for backward compatibility.
cucumber_tags="${CUCUMBER_TAGS:-}"
if [[ -z "${cucumber_tags}" && -n "${TEST_TAG:-}" ]]; then
  cucumber_tags="@${TEST_TAG#@}"
fi

tag_args=""
if [[ -n "${cucumber_tags}" ]]; then
  tag_args="-Dcucumber.filter.tags=${cucumber_tags}"
  echo "Running acceptance tests with filter: ${cucumber_tags}"
else
  echo "Running all acceptance tests"
fi

mvn_exit_code=0
set +e
mvn --batch-mode verify ${tag_args}
mvn_exit_code=$?
set -e

bad_cucumber_steps=0
json_report_count=0
scenario_total=0
scenario_passed=0
scenario_failed=0
scenario_skipped=0
scenario_other=0
feature_total=0
if compgen -G "target/jsonReports/*.json" >/dev/null; then
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
fi

if [[ "${json_report_count}" -eq 0 ]]; then
  echo "Warning: no cucumber JSON reports found under target/jsonReports"
else
  echo "Acceptance summary: json_reports=${json_report_count} features=${feature_total} scenarios_total=${scenario_total} scenarios_passed=${scenario_passed} scenarios_failed=${scenario_failed} scenarios_skipped=${scenario_skipped} scenarios_other=${scenario_other} failing_steps_or_hooks=${bad_cucumber_steps}"
fi

echo "Finished running acceptance tests"
ls -l target/cucumber-reports 2>/dev/null || echo "Warning: cucumber-reports not found"
ls -l target/cucumber-html-reports 2>/dev/null || true

if [[ -n "${REPORTS_BUCKET:-}" ]]; then
  echo "Uploading reports to ${REPORTS_BUCKET}"

  if compgen -G "target/surefire-reports/TEST-*.xml" >/dev/null; then
    gcloud storage cp target/surefire-reports/TEST-*.xml "${REPORTS_BUCKET}/surefire-reports/"
    xml_count=$(find target/surefire-reports -maxdepth 1 -name 'TEST-*.xml' | wc -l | tr -d ' ')
    echo "Uploaded ${xml_count} JUnit XML report(s)"
  else
    echo "Warning: no JUnit XML files found under target/surefire-reports"
  fi

  if [[ -d target/cucumber-reports ]]; then
    gcloud storage cp -r target/cucumber-reports "${REPORTS_BUCKET}/"
  else
    echo "Warning: cucumber report directory not found"
  fi

  if [[ -d target/cucumber-html-reports ]]; then
    gcloud storage cp -r target/cucumber-html-reports "${REPORTS_BUCKET}/"
  fi
else
  echo "REPORTS_BUCKET not set - skipping report upload"
fi

if [[ "${mvn_exit_code}" -ne 0 ]]; then
  echo "Maven verify failed with exit code ${mvn_exit_code}"
  exit "${mvn_exit_code}"
fi
