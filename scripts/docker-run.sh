#!/usr/bin/env bash
set -euo pipefail

TEST_TAG="${TEST_TAG:-Feedback}"
TEST_TAG_FILTER="@${TEST_TAG#@}"
TEST_TAG_FOLDER="${TEST_TAG_FILTER#@}"
# Keep bucket paths safe and predictable.
TEST_TAG_FOLDER="$(echo "${TEST_TAG_FOLDER}" | tr -cs '[:alnum:]._-+' '-')"
TEST_TAG_FOLDER="${TEST_TAG_FOLDER#-}"
TEST_TAG_FOLDER="${TEST_TAG_FOLDER%-}"

upload_reports() {
	local bucket="${REPORTS_BUCKET:-}"
	if [[ -z "${bucket}" ]]; then
		echo "REPORTS_BUCKET not set - skipping upload"
		return 0
	fi

	local dest="${bucket}/$(date -u +%Y%m%dT%H%M%SZ)/${TEST_TAG_FOLDER:-untagged}"
	echo "Uploading reports to ${dest}"

	# Upload common report directories if present.
	local report_dirs=(target/cucumber-reports target/cucumber-html-reports target/surefire-reports)
	local existing_dirs=()
	local dir
	for dir in "${report_dirs[@]}"; do
		if [[ -d "${dir}" ]]; then
			existing_dirs+=("${dir}")
		fi
	done

	if [[ ${#existing_dirs[@]} -gt 0 ]]; then
		gcloud storage cp -r "${existing_dirs[@]}" "${dest}/" \
			|| echo "WARNING: report directory upload failed (non-fatal)"
	else
		echo "WARNING: no report directories found to upload"
	fi

	# Upload common single-file artifacts when available.
	[[ -f target/cucumber.json ]] && gcloud storage cp target/cucumber.json "${dest}/cucumber.json" || true
	[[ -f target/tag-run-status.env ]] && gcloud storage cp target/tag-run-status.env "${dest}/tag-run-status.env" || true

	echo "Reports uploaded to ${dest}"
}

echo "Running acceptance tests with ${TEST_TAG_FILTER} tag"
set +e
mvn --batch-mode --offline verify -Dcucumber.filter.tags="${TEST_TAG_FILTER}"
test_exit_code=$?
set -e

if [[ ${test_exit_code} -eq 0 ]]; then
	echo "Finished running acceptance tests with ${TEST_TAG_FILTER} tag"
else
	echo "Acceptance tests failed with exit code ${test_exit_code}"
fi

ls -l target/cucumber-reports 2>/dev/null || echo "Warning: cucumber-reports not found"
ls -l target/cucumber-html-reports 2>/dev/null || true

upload_reports

exit ${test_exit_code}
