# Cloud Acceptance-Test Performance Investigation Plan

**Date:** 2026-09-02  
**Scope:** Establish why the acceptance suite is about 25 minutes locally but exceeds two hours in cloud execution. The focus is test orchestration, event observation, message propagation, service endpoints, and database calls.

## Current Evidence

- The captured local run executed 233 tests in 24m 36.457s; the Cucumber runner executed 225 scenarios in 24m 36s. See [baseline.md](baseline.md).
- The Cucumber runner is a single suite, `RunCucumberTest`, and writes a Cucumber JSON report to `target/jsonReports/cucumber.json`.
- Many step definitions synchronously wait for events with the shared 10-second timeout in `CommonUtils.TIMEOUT`.
- Before each scenario, `CommonUtils.setup()` enables, drains, and resets the event monitor; teardown stops it.
- In cloud mode, `GcpGatewayEventMonitor` drains the shared `acceptance-tests-Gateway-Events` subscription for each scenario, then repeatedly calls Pub/Sub Pull with `returnImmediately=true` in a 100 ms loop. Each Pull and acknowledgement creates a new gRPC stub.

## Hypotheses and Discriminating Measurements

| Hypothesis | Evidence that confirms it | Evidence that rules it out |
| --- | --- | --- |
| Event assertions spend time polling or reach their full timeout | High aggregate event-wait time, high p95/p99, or many waits near 10,000 ms | Wait time is a small fraction of scenario time |
| Event delivery or downstream processing is slow | Large publish-to-event latency for the same correlation ID; services are busy between event stages | Events are available promptly but monitor detects them late |
| The monitor is inefficient in cloud mode | Scenario setup/drain or Pull RPC time is high; many failed/slow Pulls; client creation dominates | Pull and drain time remain small |
| REST/database dependencies are slow or contended | Endpoint/database spans explain scenario time and coincide with service resource saturation | These spans are low while scenarios are slow |
| The suite has test-runner overhead or a small set of pathological scenarios | Cucumber scenario timing shows a concentrated slow tail unrelated to external spans | Scenario time is broadly distributed across the suite |

## Phase 1: Establish Comparable Baselines

1. Run the same commit, service versions, test selection, and configuration locally and in cloud at least three times. Do not compare `clean verify` with a cloud run that has a different service set, test tag filter, retry policy, or suite mode.
2. Capture the Maven output, `target/jsonReports/cucumber.json`, test container logs, and all involved service logs for every run in a uniquely named run directory.
3. Record environment facts beside each run: commit SHA, image digests, region, node/pod resources and limits, Pub/Sub project/subscription, database instance, parallelism, timeout settings, and test start/end timestamps in UTC.
4. Use median and p95 across successful, comparable runs. Keep failed runs, but label them separately because retries and timeout failures distort elapsed time.

**Deliverable:** `run-manifest.json` and a comparison table showing total suite time, per-scenario time, failures, retries, and environment metadata.

## Phase 2: Add Timing Instrumentation

Add structured, machine-readable timing records (JSON lines) rather than relying on free-text logs. Each record MUST include `runId`, Cucumber scenario ID/name, feature, example row where applicable, case ID or transaction ID when known, timestamp in UTC, duration, outcome, and error details.

Instrument these boundaries:

| Boundary | Measurements |
| --- | --- |
| Cucumber scenario lifecycle | Scenario start/end, status, total duration, attempt/retry number |
| Scenario setup/teardown | Pre-flight, feature-flag reset, TM mock reset, database clear-down, queue create/reset, monitor enable, subscription drain, monitor reset, monitor shutdown |
| Event wait | Expected event type, case/correlation ID, wait start/end, duration, found/not found, number of polling checks |
| Pub/Sub monitor | Drain duration and message count; Pull RPC duration/result count/error; acknowledgement duration/count; event received/indexed timestamp |
| REST and database helpers | Target operation, duration, status/result, timeout/error; never log credentials or message bodies containing sensitive data |
| Service processing | Event received, processing started/completed, output event published, with a propagated case ID/transaction ID |

Keep the existing human-readable timeout diagnostics, but make the structured timing output independently parseable. Apply sampling only to high-volume successful Pull records; do not sample slow calls, errors, or timeouts.

**Deliverable:** A per-run `timings.ndjson` and an event-latency dataset joinable to service logs by case ID/transaction ID.

## Phase 3: Analyze Event-Centric Latency

For each scenario, construct a timeline:

1. Test sends REST request or publishes input message.
2. Service receives input.
3. Each expected gateway event is published.
4. Event monitor receives and indexes it.
5. The assertion completes.

Calculate these distributions globally and grouped by feature, event type, service, and cloud run:

- Input-to-service-receive latency.
- Service-receive-to-event-published latency.
- Event-published-to-monitor-received latency.
- Monitor-received-to-assertion-complete latency.
- Event wait duration, timeout rate, and the count of assertions per scenario.
- Scenario setup, subscription-drain, and teardown duration.

> Do not infer event publication time from a log ingestion timestamp. Use application timestamps or OpenTelemetry span timestamps, normalized to UTC. If clocks cannot be trusted, report only same-process durations and explicitly note the limitation.

**Deliverable:** A ranked table of the slowest scenarios, event types, event paths, and setup operations, with p50/p95/p99 and the percentage of total suite time.

## Phase 4: Isolate the Dominant Cause

Run controlled A/B experiments one variable at a time against the same environment and test subset.

| Experiment | Purpose | Decision rule |
| --- | --- | --- |
| Instrumentation-only run | Confirm measurement overhead is negligible | Instrumented runtime changes by less than 5% |
| One known-fast scenario | Separate fixed startup/dependency cost from per-scenario cost | Large fixed cost points to setup, drain, or environment readiness |
| Representative slow feature only | Reproduce the cloud delay cheaply | Same event-wait or service-latency signature confirms the target slice |
| Event monitor disabled or replaced by a known event fixture, where test validity permits | Quantify monitor contribution | Material runtime drop isolates monitor/polling overhead |
| Reuse a Pub/Sub subscriber client and measure before/after | Test gRPC client-creation overhead | Reduced Pull latency or CPU/network churn supports the hypothesis |
| Drain once per isolated run or use a run-specific subscription | Quantify per-scenario drain/backlog cost without cross-run event contamination | Setup time drop confirms drain/subscription contention |
| Fixed service/DB resource allocation | Check resource throttling, saturation, and connection contention | Span latency falls alongside CPU throttling/queue depth |

Run each selected experiment at least three times. Preserve correctness: tests must retain event isolation and continue to fail when expected events are absent.

## Phase 5: Remediate and Prove the Improvement

Choose a fix only after one experiment shows a repeatable, material improvement. Possible remediation areas are monitor client lifecycle/polling, subscription isolation and backlog handling, timeout strategy, service resource/connection configuration, or a genuinely slow service operation.

The change MUST retain event assertion correctness and produce the same expected event coverage. Re-run the comparable baseline three times and report median, p95, failure rate, and per-event wait time against the pre-change cloud baseline.

## Success Criteria

- Every cloud scenario has a duration and a correlation key that can be joined to its event observations.
- At least 95% of suite time is attributed to scenario lifecycle, setup/teardown, event waits, REST calls, database calls, or service-processing spans.
- The top contributors to cloud elapsed time are supported by measured p95/p99 values, not log impressions.
- A controlled experiment identifies or rules out the event monitor, subscription drain, event propagation, and service/database latency as material contributors.
- The final recommendation demonstrates a repeatable cloud improvement across three comparable runs with no regression in acceptance outcomes.

## Initial Priorities

1. Preserve the current artifacts and obtain three comparable cloud runs with Cucumber JSON.
2. Add scenario, event-wait, subscription-drain, Pull, and acknowledgement timings first; these directly test the highest-risk cloud-only behavior.
3. Produce the event timeline and ranked latency report before changing timeout values or parallelism.