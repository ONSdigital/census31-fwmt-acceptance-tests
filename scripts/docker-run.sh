#!/usr/bin/env bash
set -euo pipefail

echo "Running acceptance tests with @Feedback tag"
mvn --batch-mode --offline verify -Dcucumber.filter.tags="@Feedback"
echo "Finished running acceptance tests with @Feedback tag"
ls -l target/cucumber-reports 2>/dev/null || echo "Warning: cucumber-reports not found"

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
else
  echo "REPORTS_BUCKET not set - skipping report upload"
fi
