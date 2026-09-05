# Acceptance-Test Performance Investigation

This directory contains the local measurement tooling used by the Census 31 acceptance-test suite. The investigation plans and archived findings are maintained in the [Census 2027 documentation repository](https://github.com/ONSdigital/census31-fwmt-docs/tree/main/docs/census27/acceptance-test-investigation).

## Prerequisites

- Java 25
- Maven 3.9 or later
- The local FWMT services running with the Pub/Sub emulator, Postgres, Redis, and TM mock
- The repository's local environment configured through `scripts/local-test-env.sh`

The helper scripts resolve Maven and Java from the current environment, `JAVA_HOME`, `mise`, or the repository's documented Java 25 locations. Override values with `FWMT_MAVEN_BIN`, `FWMT_JAVA_HOME`, `FWMT_LOG_DIR`, `FWMT_PUBSUB_EMULATOR_PORT`, and `FWMT_TM_MOCK_PORT` when required.

## Run A Single Baseline

From the repository root:

```bash
mvn clean verify
```

The Cucumber report is written to `target/jsonReports/cucumber.json`. Maven may exit non-zero because the Cucumber report plugin fails the build when acceptance scenarios fail; inspect the test summary and Cucumber report rather than treating the exit code alone as the performance result.

## Run And Archive Baselines

Use the repeat harness to run the suite and archive each report and selected service logs:

```bash
./scripts/run-baseline-repeats.sh 3 baseline
```

Arguments are the number of runs and an archive label. For example, the accepted Phase 3 smoke used:

```bash
./scripts/run-baseline-repeats.sh 1 phase3-buffer-smoke
```

The harness writes archives under `performance-investigation/runs/<label>-run<number>/`. These archives are local investigation output and must not be committed. Delete them when they are no longer needed:

```bash
rm -rf performance-investigation/runs
```

Each archived run includes `cucumber.json`, `output.log`, timing NDJSON when enabled, service logs, and `run.json` metadata.

## Analyse A Report

Analyse any archived Cucumber report:

```bash
./scripts/analyse-cucumber-timings.py performance-investigation/runs/baseline-run1/cucumber.json
```

The report includes step and hook totals, scenario percentiles, feature totals, slow step definitions, and steps at or above the 9-second near-timeout threshold. Use JSON output for mechanical comparisons:

```bash
./scripts/analyse-cucumber-timings.py --json performance-investigation/runs/baseline-run1/cucumber.json
```

For a fair comparison, keep the service versions, test selection, timeout settings, machine state, and run order consistent. Establish variance with three unchanged baseline runs before treating a small improvement as real. Do not commit generated archives, service logs, credentials, or local environment files.

## Investigate A Cloud Run

The cloud runner is `scripts/docker-run.sh`. Set `REPORTS_BUCKET` to a writable `gs://` location before starting the acceptance-test container. Each configured run is uploaded below its own prefix, for example:

```text
gs://my-bucket/run1-all/
	maven.log
	jsonReports/cucumber.json
	performance-investigation/timings.ndjson
	surefire-reports/
	cucumber-reports/
	cucumber-html-reports/
	logs/
```

The runner stores the following evidence automatically:

- `maven.log`: Maven stdout and stderr captured with `tee` while preserving Maven's exit code.
- `jsonReports/`: Cucumber JSON reports used by `analyse-cucumber-timings.py`.
- `performance-investigation/timings.ndjson`: structured timing records for hook operations and RM-message waits. Records include the cloud run ID, scenario details, duration, operation names, polling counts, message counts, event type, and correlation IDs.
- `logs/`: logs produced locally by the acceptance-test wrapper, such as port-forward logs. These are not a substitute for application logs from the cloud services.

Example container configuration:

```bash
REPORTS_BUCKET=gs://my-bucket/acceptance/ \
CUCUMBER_TAGS_1='@Outcomes' \
CUCUMBER_TAGS_2='@Resilience' \
docker run --rm \
	-e REPORTS_BUCKET \
	-e CUCUMBER_TAGS_1 \
	-e CUCUMBER_TAGS_2 \
	acceptance-test-image:tag
```

For Cloud Build, pass `REPORTS_BUCKET` through the build substitutions or environment used by the acceptance-test container. Ensure the build service account can write to the bucket. Do not put credentials or service-account keys in the bucket artifacts.

### Download And Analyse

Download one run after the container finishes:

```bash
mkdir -p performance-investigation/cloud-runs
gcloud storage cp -r \
	gs://my-bucket/run1-all \
	performance-investigation/cloud-runs/run1-all
```

Analyse the Cucumber report locally:

```bash
./scripts/analyse-cucumber-timings.py \
	performance-investigation/cloud-runs/run1-all/jsonReports/cucumber.json
```

Inspect the structured RM wait timings with `jq`:

```bash
jq -s '
	map(select(.type == "rm-message-wait")) |
	{
		waits: length,
		total_wait_ms: (map(.durationMs) | add // 0),
		slow_waits: (map(select(.durationMs >= 9000)) | length),
		republishes: (map(.messagesRepublished) | add // 0),
		max_wait_ms: (map(.durationMs) | max // 0)
	}
' performance-investigation/cloud-runs/run1-all/performance-investigation/timings.ndjson

Inspect queue reset sub-steps with `jq`:

```bash
jq -s '
	map(select(.type == "hook-operation" and .hookName == "ScenarioHooks.setup" and (.operationName | startswith("queue-reset-")))) |
	group_by(.operationName) |
	map({
		operation: .[0].operationName,
		count: length,
		avg_ms: ((map(.durationMs) | add) / length),
		max_ms: (map(.durationMs) | max)
	}) |
	sort_by(.avg_ms) | reverse
' performance-investigation/cloud-runs/run1-all/performance-investigation/timings.ndjson
```
```

### Correlate With Service Logs

Use the `messageTransactionId`, `generatedCaseId`, and the UTC timestamps in `timings.ndjson` to investigate a slow scenario in Cloud Logging. The acceptance-test artifact does not contain the full outcome-service, job-service, gateway, or Pub/Sub service logs unless those logs are explicitly collected by the deployment.

Record the Cloud Build ID, commit SHA, test start and end times, project, region, namespace, pod names, and service image versions. Use those values to narrow the Cloud Logging query to the test window and service instances. Match service publish/process timestamps against the acceptance-test `waitStartedAtUtc`, `waitFinishedAtUtc`, and correlation IDs.

For a local Kubernetes port-forward setup, `scripts/logs/` may contain useful forwarding output. For services running in the cluster, retrieve application logs through the platform's log store or with the deployment's approved `kubectl logs` workflow, then keep those logs alongside the downloaded run directory for the investigation. The Cucumber report and NDJSON are sufficient for aggregate timing analysis; service logs are needed to attribute a delay to a downstream service rather than the test harness.
