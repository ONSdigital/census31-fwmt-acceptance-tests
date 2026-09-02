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
