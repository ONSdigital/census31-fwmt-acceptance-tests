# Local Acceptance-Test Performance Investigation Plan

**Date:** 2026-09-02
**Scope:** Local `mvn clean verify` only. Cloud execution is out of scope — see [investigation-plan.md](investigation-plan.md) for that.
**Baseline:** 233 tests / 24m 36s. See [baseline.md](baseline.md).

## Why This Plan Differs From The Cloud Plan

The cloud plan assumes we must instrument before we can attribute time. Locally we already have
the attribution: `target/jsonReports/cucumber.json` carries per-step and per-hook durations for all
225 scenarios. That has been parsed, so this plan starts from measured hotspots rather than
hypotheses, and the early phases are confirmation and fixing, not discovery.

## Measured Starting Point

Parsed from `target/jsonReports/cucumber.json` (1.2 MB, 225 scenarios, all `before`/`after` hooks
and all steps).

| Segment | Time | Share of suite |
| --- | ---: | ---: |
| Step execution | 16.5 min | 67% |
| `@Before` / `@After` hooks | 8.0 min | 33% |
| **Total** | **24.6 min** | **100%** |

Scenario duration distribution: p50 **3.87s**, p90 **14.39s**, p95 **23.56s**, max **24.56s**.
The distribution is strongly bimodal, not a broad slowdown. A minority of scenarios carry the suite.

### Hotspot 1 — one step definition holds 50% of suite time

| Step definition | Calls | Total | Avg |
| --- | ---: | ---: | ---: |
| `OutcomeSteps.create_the_following_messages_to_RM` | 132 | **12.25 min** | 5.57s |
| `OutcomeSteps.it_will_run_the_following_processors` | 132 | 1.14 min | 0.52s |
| `OutcomeSteps.it_will_create_the_following_messages_to_JobService` | 132 | 1.14 min | 0.52s |
| `OutcomeSteps.gateway_processes_the_outcome` | 132 | 0.56 min | 0.26s |

**53 invocations of `create_the_following_messages_to_RM` reached 9s or more and still passed.** These
are not failures reaching a timeout; they are successful assertions that waited close to the full
`CommonUtils.TIMEOUT` of 10,000 ms before the expected message appeared.

Across the whole suite, 58 steps sit at or above 9s and account for 12.40 min — **50% of total suite
time in 58 steps**. The breakdown is 53 `create_the_following_messages_to_RM` (11.56 min, passed),
2 `UpdateSteps.an_associated_a_Pause_is_deleted` (passed), 2
`FeatureFlagSteps.theRequestIsIgnoredDueToFeatureFlagForCaseId` (passed), and 1
`OutcomeSteps.gateway_processes_the_hidden_outcome` (failed).

### Hotspot 2 — `CommonUtils.setup()` runs four times per scenario

Four step classes each declare an active `@Before` that calls the same `commonUtils.setup()`:

- [CreateSteps.java](../src/test/java/uk/gov/ons/census/fwmt/tests/acceptance/steps/inbound/create/CreateSteps.java#L72-L79)
- [FeedbackSteps.java](../src/test/java/uk/gov/ons/census/fwmt/tests/acceptance/steps/inbound/feedback/FeedbackSteps.java#L57-L59)
- [ResilienceSteps.java](../src/test/java/uk/gov/ons/census/fwmt/tests/acceptance/steps/inbound/resilience/ResilienceSteps.java#L55-L61)
- [OutcomeSteps.java](../src/test/java/uk/gov/ons/census/fwmt/tests/acceptance/steps/outcomes/OutcomeSteps.java#L133-L136)

Cucumber applies unconditional hooks to every scenario, so each of the 225 scenarios performs the
full [CommonUtils.setup()](../src/test/java/uk/gov/ons/census/fwmt/tests/acceptance/steps/inbound/common/CommonUtils.java#L37-L46)
sequence four times: pre-flight check, job-service feature-flag refresh, TM mock recorder enable,
TM mock reset, database clear-down, queue create, queue reset, monitor enable, monitor reset.

Measured: 2.22 + 1.94 + 1.94 + 1.93 = **8.0 min**, ~0.52–0.59s per hook per scenario. Three of the
four runs are redundant, so roughly **6 min (24% of the suite) is duplicated setup**.
`CancelSteps` and `UpdateSteps` already have their `commonUtils.setup()` calls commented out, which
suggests this duplication was recognised previously but only partly addressed.

### Hotspot 3 — republish-and-repoll loop in the emulator client

[`PubSubEmulatorMessaging.pullMessageWithEventType`](../src/main/java/uk/gov/ons/census/fwmt/tests/acceptance/messaging/PubSubEmulatorMessaging.java#L121-L147)
pulls a batch of 10, and for every message whose event type does not match it **republishes the
message back to the topic**, acknowledges the original, then sleeps `msInterval` (50 ms from
`OutcomeSteps`). It sleeps even when the batch was non-empty.

Consequences to quantify:

- Each non-matching message costs a pull + publish + acknowledge HTTP round trip per 50 ms tick.
- `collectRmMessages` iterates expected types **sequentially**, so when arrival order differs from
  the expected-list order, earlier-arriving messages are round-tripped repeatedly for the whole
  wait.
- Republishing changes message attributes and ordering, so the churn is a correctness risk as well
  as a cost.

### Non-factor — test failures

18 scenarios failed, totalling 0.85 min (**3%** of suite time), average 2.83s. Failing scenarios are
*faster* than passing ones (6.88s average). Fixing the failures will not materially change runtime,
and runtime work should not be blocked behind them.

### Execution model

Surefire runs with default `forkCount=1` / `reuseForks=true`, no `parallel` setting, and
[junit-platform.properties](../src/test/resources/junit-platform.properties) sets no
`cucumber.execution.parallel.*` properties. All 225 scenarios run sequentially in one JVM.

## Phase 0 — Lock The Measurement Loop (do first)

1. **Done.** [`scripts/analyse-cucumber-timings.py`](../scripts/analyse-cucumber-timings.py) reads
   `target/jsonReports/cucumber.json` and prints the step-vs-hook split, top step definitions and
   hooks by total time, per-feature totals, scenario p50/p90/p95/max, the pass/fail time split, and
   the steps at or above the near-timeout threshold. Every later phase is judged by re-running it.

   ```bash
   ./scripts/analyse-cucumber-timings.py                     # default report path
   ./scripts/analyse-cucumber-timings.py --top 8
   ./scripts/analyse-cucumber-timings.py performance-investigation/runs/<ts>/cucumber.json
   ./scripts/analyse-cucumber-timings.py --json              # for run-to-run diffing
   ```

   Standard library only, no dependencies. `--json` emits per-definition counts and seconds so runs
   can be compared mechanically rather than by eye.
2. Archive **before** each new run, not after. `target/` is gitignored and `mvn clean verify` deletes
   it, so an un-archived `cucumber.json` is destroyed by the next run. Each run is stored as
   `performance-investigation/runs/<timestamp>/` holding `cucumber.json`, `output.log`, and the
   service logs.
3. Record the machine state that actually moves local numbers: CPU model, whether services run as
   `java -jar` or in Docker, Docker Desktop CPU/memory allocation, Postgres and Pub/Sub emulator
   container settings, and whether anything else heavy was running.
4. Re-run the unchanged baseline **three** times to establish local variance. Locally this matters
   more than in cloud, because a single-JVM sequential suite on a laptop is sensitive to thermal
   throttling and background load. Record the run order, because thermal drift tends to slow later
   runs and must not be misread as a regression. Treat any later improvement smaller than the
   observed baseline spread as unproven.
5. **Enumerate the flaky failure set.** Across the three runs, classify every failing scenario as
   *deterministic* (fails in all three) or *flaky* (fails in some). This is a prerequisite for the
   decision rules in Phases 1, 3 and 4, which compare failure sets between runs: an unstable
   baseline failure set makes "the failure set is unchanged" untestable. Prior observation supports
   expecting flakiness — `OutcomeNewAddressReported` CE new-unit receipt failures have been seen to
   pass on rerun.

   Where the failure set proves unstable, the decision rule used by later phases becomes:

   > The set of deterministic failures is unchanged, and every failure outside it is a member of the
   > enumerated flaky set. Any new deterministic failure blocks the change.

**Exit criteria:** three archived baseline runs, a known variance band, an enumerated
deterministic-versus-flaky failure set, and a one-command timing report.

### Phase 0 Results (complete)

Three unchanged `mvn clean verify` runs on branch `FMT-128_performance-investigation`, executed
back-to-back on 2026-09-02, archived under `performance-investigation/runs/`. Environment recorded in
`runs/machine-state.txt`: Apple M4 Max, 14 CPU, 36 GB, macOS 26.6.2, Java 25.0.2, Maven 3.9.16;
services run natively as `java -jar`; Postgres 9.6, Pub/Sub emulator and Redis run in podman on a VM
capped at 7 CPU / 6 GB.

| Measure | Run 1 | Run 2 | Run 3 | Median | Spread |
| --- | ---: | ---: | ---: | ---: | ---: |
| Maven wall time | 1516s | 1540s | 1529s | **1529s** | 24s (1.6%) |
| Cucumber total | 25.08 min | 25.31 min | 25.17 min | **25.17 min** | 0.23 min (0.9%) |
| Hooks | 8.81 min | 8.70 min | 8.57 min | **8.70 min** | 0.24 min |
| Steps | 16.27 min | 16.61 min | 16.60 min | **16.60 min** | 0.34 min |
| `create_the_following_messages_to_RM` | 11.96 min | 12.27 min | 12.27 min | **12.27 min** | 0.31 min |
| Steps at or above 9s | 56 | 58 | 58 | **58** | 2 |
| Failures | 18 | 18 | 18 | **18** | 0 |

**Variance band: approximately 1.6% of wall time (~25s).** No thermal drift was observed — run 3 was
not slower than run 1 — so run order is not a confound on this machine. Any claimed improvement below
about 0.5 min should be treated as noise.

**Failure set is fully deterministic. The flaky set is empty.** All 18 failures occurred in all three
runs, and the set is identical to the original `run0` baseline. Distribution: 17 in `Create Tests`,
1 in `Outcomes Feature Flag Tests`. This means the stricter decision rule applies to later phases:

> The failure set must remain exactly these 18 scenarios. Any change to the failure set blocks the
> performance change.

The earlier concern about intermittent `OutcomeNewAddressReported` failures did not reproduce here;
that feature passed cleanly in all three runs.

**Two notes on the run:**

- `mvn` exits 1 in every run, but not from Surefire — `testFailureIgnore` is `true`. The failure comes
  from `maven-cucumber-reporting:5.11.0:generate`, which fails the build when the report contains
  failures. Exit code is therefore not a usable signal; the failure set must be read from
  `cucumber.json`.
- Hook time rose slightly against `run0` (8.70 min vs 8.04 min) while step time fell. The four
  duplicated `@Before` hooks remain 34–35% of the suite, so the Phase 1 conclusion is unchanged.

## Phase 1 — Eliminate Duplicated Setup (highest certain return)

This is a known-quantity change: the work is provably repeated, and the measurement already exists.

1. Confirm the redundancy empirically before changing code. Add temporary counter logging inside
   `CommonUtils.setup()` recording invocation count per scenario and the duration of each of its
   nine operations. Expect four invocations per scenario for all 225.
2. Break down which of the nine operations dominate the ~0.52s. Candidates in likely order:
   job-service feature-flag refresh (HTTP + health poll at 250 ms interval), database clear-down
   (four DELETE statements), queue reset (purge of six subscriptions), monitor enable (subscription
   drain).
3. Collapse to a single scenario-scoped hook. Options, in order of preference:
   - Move `setup()`/`clearDown()` into one dedicated hooks class in the glue path and remove the
     `@Before`/`@After` from the four step classes.
   - Failing that, make `CommonUtils.setup()` idempotent per scenario by guarding on a
     scenario-scoped flag reset in a single `@Before` with a lower order.
4. Verify isolation is preserved. The four hooks currently mask ordering assumptions; after
   collapsing, each scenario must still start with a clean database, reset TM mock, drained queues,
   and a reset event monitor. Run the full suite and confirm the failure set is still exactly the 18
   deterministic failures enumerated in Phase 0.

**Expected:** hook time from 8.70 min to roughly 2.2 min. **Predicted saving ~6.5 min (26%).**
**Decision rule:** accept only if the failure set is exactly the Phase 0 set of 18 and the saving
exceeds the 1.6% variance band.

### Phase 1 Results (applied)

Implemented as a single glue class, [`ScenarioHooks`](../src/test/java/uk/gov/ons/census/fwmt/tests/acceptance/steps/ScenarioHooks.java),
holding one `@Before(order = 0)` / `@After(order = 0)` pair calling `commonUtils.setup()` /
`clearDown()`. Removed the duplicated hooks (and now-unused `CommonUtils` fields) from `CreateSteps`,
`ResilienceSteps`, `OutcomeSteps` and `FeedbackSteps`; cleaned up the already-commented-out hooks in
`UpdateSteps` and `CancelSteps`. `order = 0` preserves `OutcomeSteps`' prior relative ordering (setup
before its own feature-flag refresh), since Cucumber does not guarantee ordering between same-order
hooks in different classes.

Three verification runs of `mvn clean verify` (unchanged infra/services from Phase 0):

| Measure | Baseline (median) | Run 1 | Run 2 | Run 3 |
| --- | ---: | ---: | ---: | ---: |
| Wall time | 1529s | 926s | 1162s | 1168s |
| Cucumber total | 25.17 min | 15.28 min | 19.07 min | 19.16 min |
| Hooks | 8.70 min | 2.44 min | 2.40 min | 2.42 min |
| Steps \u2265 9s (RM wait, Hotspot 1, untouched) | 58 | 44 | 58 | 58 |
| Failures | 18 | 20 | 18 | 18 |

**Run 1 is an outlier and is excluded from the accept/reject decision.** Its 2 extra failures are
`freemarker.template.TemplateNotFoundException: Template not found for name "FULFILMENT_REQUESTED-out.ftl"`
in `OutcomeSteps.createExpectedRmMessage` \u2014 a classpath resource lookup failure, not a timeout or
assertion failure, and unrelated in kind to the hook change. It coincides with run 1 also having 14
fewer near-timeout RM waits and a wall time far outside the other two runs' agreement, and it
overlapped with a VS Code workspace reload during the run. The likely cause is environmental
interference during that specific run rather than a regression; this is not proven, only judged
consistent with the evidence, so a confirmatory re-run is recommended before treating run 1 as fully
explained.

**Runs 2 and 3 satisfy the decision rule:**

- Failure set is exactly the Phase 0 deterministic set of 18 in both runs.
- Hooks: 8.70 min \u2192 2.40\u20132.42 min, saving **~6.3 min**, ahead of the ~6.5 min prediction band
  and far outside the 1.6% variance band.
- Suite total: 25.17 min \u2192 19.07\u201319.16 min, saving **~24%**.
- Hotspot 1 (the RM-wait step) is unchanged at 58 near-timeout steps / ~12.27 min in both clean runs,
  confirming the change is isolated to hook overhead as intended and did not perturb Phase 2/3's
  target.

**Status: accepted**, pending an optional confirmatory run to resolve the run 1 anomaly. Proceed to
Phase 2.

## Phase 2 — Attribute The 5.57s Average In `create_the_following_messages_to_RM`

This step is 50% of the suite. The 53 near-timeout passes must be explained before anything is
tuned, because the two candidate explanations demand opposite fixes.

Add temporary structured timing (NDJSON, one record per wait) capturing: scenario name, expected
event type list, the type being awaited, wait start/end, duration, number of poll iterations,
number of messages pulled, number of messages republished, and whether the match was found on the
first pull.

Then discriminate:

| Explanation | Confirming evidence | Refuting evidence | Implied fix |
| --- | --- | --- | --- |
| The service genuinely takes ~10s to produce the message | Few poll iterations return anything; the message's first appearance in the outcome-service/job-service log is ~10s after the trigger | The message is present in the queue early | Investigate outcome-service processing, not the test |
| The republish loop starves the matcher | High republish counts; the awaited message is pulled and republished before it is matched; matching type is present in the subscription throughout | Republish counts near zero | Match against a buffered index instead of destructive pull |
| Sequential per-type collection blocks on arrival order | Waits are long only when expected-list order differs from arrival order; the last type resolves instantly once reached | Long waits occur for single-type expectations too | Collect all expected types in one pass over a buffered stream |
| 50 ms poll interval plus HTTP round-trip cost | Wall time far exceeds sum of measured service latencies; high emulator HTTP call counts | HTTP call time is small | Reduce interval and/or batch, or switch to a push/buffered subscriber |

Cross-reference every long wait against `performance-investigation/outcome-service.log` and
`job-service.log` using the case ID. Because the tests already interrogate and log every gateway
event, the case ID is available as a natural join key — use application timestamps, not log
ingestion order.

**Deliverable:** for the 53 near-timeout waits, a stated cause per wait and a ranked cause
distribution. Do not change timeout values before this deliverable exists; raising `TIMEOUT` hides
the signal and lowering it converts slow passes into failures.

## Phase 3 — Fix The Dominant Cause From Phase 2

Apply one change at a time, re-running the Phase 0 report after each. Use the same **three-run**
protocol as Phase 0 and Phase 1: one run is not enough to distinguish a real effect from the 1.6%
variance band or from a one-off environmental anomaly (Phase 1's run 1 is the concrete example \u2014
see its Results section above). A cheap single smoke run is a reasonable gate before committing to
the full three: if it does not compile, crashes, or shows no plausible improvement, stop before
spending the extra ~40 minutes. If it looks promising, run the remaining two and judge the change on
all three together, exactly as in Phase 1.

Likely candidates, to be selected by evidence rather than assumption:

- **Buffer instead of destructively re-pull.** Run a single background consumer per test lane that
  indexes messages by event type into a map, as `PubSubGatewayEventMonitor` already does for gateway
  events. `collectRmMessages` then waits on the index rather than republishing. This removes the
  republish round-trips, removes the arrival-order coupling, and removes the ordering/attribute
  mutation risk in one change.
- **Collect expected types concurrently** rather than looping types sequentially.
- **Tune the poll interval** only after the above, and only if measurement shows interval-bound
  waits.
- **Address a genuine outcome-service delay** if the logs show the ~10s is real processing time.

Each candidate must retain assertion strength: the suite must still fail when an expected RM message
is genuinely absent. Prove this by deliberately suppressing one expected message and confirming the
relevant scenario fails within the timeout.

**Target:** reduce the 12.25 min step total. The 53 near-timeout calls account for 11.56 min of that
12.25 min, so if they prove test-side the realistic ceiling for this phase is close to 11 min. If
they prove to be genuine outcome-service processing time, the saving here is near zero and the work
moves to the service instead.

## Phase 4 — Parallelism, Only After Phases 1–3

Parallelism is deliberately last. The suite currently relies on global shared state — one database
cleared between scenarios, shared TM mock state, and shared Pub/Sub subscriptions — so enabling
`cucumber.execution.parallel.enabled` now would produce flakiness that masks the real wins and is
hard to attribute.

Once serial time is reduced and setup is single-pass, evaluate in this order:

1. Quantify the remaining serial floor. If the suite is already well under target, stop here.
2. Assess isolation honestly: per-scenario database isolation, per-scenario Pub/Sub subscriptions or
   correlation-ID-scoped filtering, and TM mock state scoping. Without these, parallel execution is
   not safe at any thread count.
3. If isolation is achievable, trial `cucumber.execution.parallel` at low thread counts (2, then 4)
   and measure both wall time and failure-set stability across three runs each.
4. Confirm local services and the Pub/Sub emulator are not themselves the bottleneck under
   concurrency, otherwise parallelism converts test time into contention.

**Decision rule:** adopt parallelism only if the failure set remains exactly the Phase 0 set of 18
across three runs at the chosen thread count.

## Phase 5 — Environment And Build Overhead

Lower priority; approximately 15.5s of the 24m 52s build sits outside Surefire, so these are small
relative to Phases 1–3. Worth checking only once the large items land.

- Whether local services run natively or in Docker, and whether Docker CPU/memory limits throttle
  Postgres or the Pub/Sub emulator.
- JVM warm-up cost in the single test fork, and whether service JVMs are still warming during the
  first scenarios.
- Whether the database clear-down cost grows across the run (missing index, table bloat) — measure
  clear-down duration as a function of scenario index.
- Cucumber HTML report generation in the `verify` phase.

## Success Criteria

- A single command reproduces the timing report from `cucumber.json` for any run. **Done.**
- Local variance across three unchanged runs is known (1.6%) and quoted alongside every claimed
  saving. **Done.**
- Baseline failures are classified as deterministic or flaky, so later phases have a testable
  regression rule. **Done — all 18 are deterministic, flaky set is empty.**
- The near-timeout-but-passing waits have a documented, evidence-backed cause.
- Setup work executes once per scenario, not four times.
- No performance change alters the failure set.
- Improvements are demonstrated as median across three runs, not a single fast run.

## Priority Order

| # | Action | Predicted saving | Confidence |
| --- | --- | ---: | --- |
| 1 | Phase 0 measurement loop, variance band, flaky-set enumeration | — | **Done** |
| 2 | Phase 1 collapse four `@Before` hooks into one | ~6.5 min | High — duplication is proven |
| 3 | Phase 2 attribute the ~53 near-timeout RM waits | — | Prerequisite for 4 |
| 4 | Phase 3 fix the dominant RM-wait cause | up to ~11 min | Medium — depends on Phase 2 |
| 5 | Phase 4 parallelism | Large but risky | Low until isolation exists |
| 6 | Phase 5 environment and build overhead | < 1 min | Low |

Phases 2 and 3 together address the single step definition that accounts for half the suite; Phase 1
addresses the largest change that can be made with existing evidence and no new instrumentation.
