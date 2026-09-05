# Build bf252017 Timeout Analysis

**Build ID:** `bf252017-beba-405d-a552-ea0a92b2ea0d`  
**Trigger Project:** `c31-fwmtg-ci-prod`  
**Environment:** `c31-fwmtg-dev`  
**Status:** ❌ FAILURE (`TIMEOUT`)  
**Cloud Build Exit:** `124`  
**Created:** 2026-09-05 10:27:14 UTC  
**Finished:** 2026-09-05 10:59:03 UTC  
**Duration:** ~31m 50s  
**Acceptance Image:** `europe-west2-docker.pkg.dev/c31-fwmtg-ci-prod/fwmtg-docker-snapshot/census31-fwmt-acceptance-tests@sha256:333289c0921436cad45c737bda7dc5a1846779f1b920e2ec08d5e0281b28ba4f`  
**Reports Bucket:** `gs://c31-fwmtg-ci-prod-acceptance-test-details/bf252017-beba-405d-a552-ea0a92b2ea0d`  

---

## Executive Summary

This build failed because the acceptance-test step timed out, not because the HH scenarios failed assertions. The bucket artifacts show a severe **queue-reset regression** during scenario setup:

- **Queue-reset mean:** `108792 ms`
- **Queue-reset P50:** `114045 ms`
- **Queue-reset P95:** `121246 ms`
- **Regression vs previous fast run (`1ced6799`):** `3894 ms -> 108792 ms` = **+104898 ms** (**+2694%**, ~`27.9x` slower)

The slowdown is concentrated in three subscription drains:

- `queue-reset-drain-RM.FieldDLQ`
- `queue-reset-drain-Field.refusals`
- `queue-reset-drain-Field.other`

The previously optimized hot lane, `RM.Field`, is **not** the bottleneck in this run.

**Key Result:** this is a queue-drain/backlog regression, not evidence of a broken subscriber pull path.

---

## Build Outcome

The Cloud Build log ends with:

- `status:"TIMEOUT"`
- `wait_status:124`
- `ERROR: step exited with non-zero status: 124`

This means the build wrapper timed out before its orchestration step completed successfully.

However, the raw test artifacts tell a more complete story:

- `cucumber.json` contains `20` scenario elements
- All recorded Cucumber step statuses are `passed`
- `timings.ndjson` contains `20` scenario starts and `20` scenario finishes

So the run progressed much further than the manifest summary implies. The raw timing and Cucumber artifacts are the reliable source for performance analysis.

An additional signal from the Cloud Build log is that the runtime did not stay on the intended StreamingPull path:

- `Streaming pull drain failed for subscription acceptance-tests-RM-Field, falling back to pipelined pull drain: Streaming pull not available`
- subsequent retries for the same subscription reported `Cannot invoke "com.google.api.gax.rpc.BidiStreamingCallable.splitCall(...)" because "callable" is null`

That matters because the fallback path has queue-specific parallelism, while the intended StreamingPull path does not depend on that map.

---

## Performance Metrics

### Queue-Reset Overall Timing

| Metric | Current Build | Previous Fast Run (`1ced6799`) | Delta |
|--------|---------------|---------------------------------|-------|
| **Mean** | 108792 ms | 3894 ms | +104898 ms (+2694%) |
| **Median (P50)** | 114045 ms | 4049 ms | +109996 ms |
| **P95** | 121246 ms | 4807 ms | +116439 ms |
| **Max** | 121281 ms | 4807 ms | +116474 ms |
| **Runs** | 20 | 20 | matched sample size |

**Status:** ❌ catastrophic regression in scenario setup/reset time

---

## Per-Queue Drain Performance

Queue drain operations ranked by mean duration:

| Queue | Mean (ms) | P50 (ms) | P95 (ms) | Max (ms) | Assessment |
|-------|-----------|----------|----------|----------|------------|
| **RM.FieldDLQ** | 93663 | 87547 | 121063 | 121063 | ❌ dominant bottleneck |
| **Field.refusals** | 91858 | 89750 | 116078 | 116078 | ❌ dominant bottleneck |
| **Field.other** | 91852 | 91713 | 121096 | 121096 | ❌ dominant bottleneck |
| **Outcome.Preprocessing** | 16010 | 577 | 107410 | 107410 | ⚠ highly bimodal |
| **Outcome.PreprocessingDLQ** | 14360 | 603 | 95099 | 95099 | ⚠ highly bimodal |
| **RM.Field** | 605 | 548 | 1148 | 1148 | ✅ healthy |

### Non-Drain Reset Components

| Component | Mean (ms) | Assessment |
|----------|-----------|------------|
| **queue-reset-pause-inbound-adapters** | 82 | ✅ normal |
| **queue-reset-resume-inbound-adapters** | 99 | ✅ normal |

**Interpretation:** the timeout is driven by subscription drain cost, not by pause/resume orchestration and not by the optimized `RM.Field` lane.

---

## Critical Findings

### 0. Current Live Backlog Is Clear On The Three Problem Queues

Current Cloud Monitoring samples from `c31-fwmtg-dev` show:

| Subscription | Undelivered Messages | Oldest Unacked Age | Sample Time |
|-------------|----------------------|--------------------|-------------|
| **acceptance-tests-RM-FieldDLQ** | 0 | 0 | 2026-09-05 18:14-18:15 UTC |
| **acceptance-tests-Field-refusals** | 0 | 0 | 2026-09-05 18:14-18:15 UTC |
| **acceptance-tests-Field-other** | 0 | 0 | 2026-09-05 18:14-18:15 UTC |
| **acceptance-tests-RM-Field** | 27 | 39268 s | 2026-09-05 18:14-18:15 UTC |

This does not prove what backlog existed during the timeout run, but it does show the three slow subscriptions are not currently carrying residual backlog. In other words, the bad behavior was transient to the run or its immediate environment, not a still-persisting live blockage.

### 1. Subscriber Pull Is Not The Primary Failure Mode

The bucket data does not support a stuck subscriber-pull hypothesis:

- `RM.Field` drain completes in `~0.6s` mean
- Gateway event monitor activity was observed in the live run
- Cucumber scenarios recorded in `cucumber.json` are all `passed`

If the subscriber pull path were broadly broken, `RM.Field` and event-monitor driven scenarios would also be expected to stall or fail. That is not what the artifacts show.

### 2. The Bottleneck Has Moved To Other Queues

The queues consuming almost all reset time are:

- `RM.FieldDLQ`
- `Field.refusals`
- `Field.other`

This suggests one of two classes of root cause:

1. **Backlog volume regression** on those subscriptions
2. **Drain strategy mismatch** where those subscriptions still use a slow path that `RM.Field` no longer uses

The current code path makes that second explanation plausible:

- all queue resets call the same `drainSubscription(testSubscription)` path
- on StreamingPull failure, the code falls back to `drainByPipelinedPull(..., pullerParallelismFor(subscriptionId))`
- `pullerParallelismFor(...)` gives `3` pullers only to `acceptance-tests-RM-Field`
- every other acceptance subscription falls back to the default parallelism of `1`

That asymmetry matches the observed timing profile almost exactly: `RM.Field` stays fast, while `RM.FieldDLQ`, `Field.refusals`, and `Field.other` become dominant.

### 3. Outcome Queues Show Bimodal Behavior

`Outcome.Preprocessing` and `Outcome.PreprocessingDLQ` have low medians (`577 ms`, `603 ms`) but very high P95/max values (`95s` to `107s`). That usually points to intermittent large backlogs rather than a uniformly slow code path.

The per-scenario series supports that interpretation:

- `Outcome.Preprocessing` is slow in the first few resets (`96817`, `105332`, `107410` ms) then drops to sub-`1s`
- `Outcome.PreprocessingDLQ` shows the same pattern (`90166`, `88867`, `95099` ms) then drops to sub-`1.3s`
- `RM.FieldDLQ`, `Field.refusals`, and `Field.other` remain slow across the full run rather than just the start

So there are two different behaviors in the same build:

1. transient early-run backlog on outcome queues
2. persistent all-run slowness on `RM.FieldDLQ`, `Field.refusals`, and `Field.other`

---

## Build Summary Inconsistencies

The generated manifest is not internally consistent with the raw run artifacts:

- Manifest says `TIMEOUT`
- Manifest reports `tests_total = 0`
- Manifest uses `run1-all` / `run2-all`
- Actual artifact folder contains `run1-hh`
- `cucumber.json` clearly contains `20` executed scenarios
- `timings.ndjson` clearly contains `20` scenario finishes

This indicates the reporting/aggregation wrapper timed out or emitted a partial summary after the main test process had already produced usable artifacts.

**Conclusion:** use `timings.ndjson`, `cucumber.json`, and the build log for analysis; do not trust the manifest totals for this run.

---

## Comparison With Previous Fast Run

Previous analyzed fast run: `1ced6799-f354-4652-b2d6-a249276a8b38`

| Metric | Fast Run | Timeout Run | Change |
|--------|----------|-------------|--------|
| **queue-reset mean** | 3894 ms | 108792 ms | +104898 ms |
| **queue-reset P50** | 4049 ms | 114045 ms | +109996 ms |
| **queue-reset P95** | 4807 ms | 121246 ms | +116439 ms |
| **RM.Field mean** | 3153 ms | 605 ms | -2548 ms |

This is a strong signal that the regression is **not** on the originally optimized RM.Field path. The cost has shifted elsewhere.

For the three dominant queues, the regression against the fast run is extreme and sustained:

| Queue | Fast Run Mean | Timeout Run Mean | Delta |
|------|---------------|------------------|-------|
| **RM.FieldDLQ** | 2347 ms | 93663 ms | +91316 ms |
| **Field.refusals** | 2297 ms | 91858 ms | +89561 ms |
| **Field.other** | 2311 ms | 91852 ms | +89541 ms |

By contrast, `RM.Field` improved:

| Queue | Fast Run Mean | Timeout Run Mean | Delta |
|------|---------------|------------------|-------|
| **RM.Field** | 3153 ms | 605 ms | -2548 ms |

---

## Likely Root Cause Hypothesis

The most plausible local explanation is:

1. At least one early drain attempt failed off StreamingPull, as shown in the build log
2. The same shared subscriber stub was then reused for subsequent drains instead of being invalidated/recreated
3. Those later drains therefore ran on the pipelined fallback path
4. On that fallback path, only `acceptance-tests-RM-Field` gets `3` pullers; `RM.FieldDLQ`, `Field.refusals`, and `Field.other` fall back to a single puller
5. Any backlog on those three queues therefore converts directly into ~`90s` drain times across nearly every scenario reset
6. Per-scenario reset cost rose from ~`4s` to ~`110s`, exhausting the Cloud Build step budget even though scenarios kept passing

This hypothesis fits all observed evidence:

- explicit StreamingPull fallback warnings in the build log
- very slow drain timings on specific queues
- healthy `RM.Field` timing
- healthy pause/resume timing
- queue-specific fallback parallelism asymmetry in code
- passed scenario records in raw artifacts
- timeout at build-wrapper level

The main thing still not proven from artifacts alone is whether the persistent slowness on the three affected queues was caused purely by high message volume, by degraded stub state after the first StreamingPull failure, or by a combination of both. But the code-path evidence now narrows the likely control point considerably.

---

## Recommended Next Steps

### Immediate

1. Query live subscription depth for:
   - `acceptance-tests-RM.FieldDLQ`
   - `acceptance-tests-Field.refusals`
   - `acceptance-tests-Field.other`
2. Confirm whether these queues still use default single-puller drain behavior.
3. Capture the next timeout-run logs specifically for all `Streaming pull drain failed` lines, not just the first subscription.

### Short-Term Investigation

1. Add temporary instrumentation to log per-subscription message counts before draining.
2. On StreamingPull failure, close and recreate the cached `SubscriberStub` before the next subscription drain.
3. Compare next-run timings with and without expanded fallback parallelism on `RM.FieldDLQ`, `Field.refusals`, and `Field.other`.
4. Re-run a focused HH-only test after manual queue cleanup to separate backlog effects from algorithm effects.

### Remediation Candidates

1. Extend multi-puller or StreamingPull optimization to the slow non-`RM.Field` queues.
2. Introduce pre-run backlog sanity checks with explicit abort/logging when queues exceed expected levels.
3. Fix manifest/report aggregation so timeout builds still emit accurate executed-test counts.

---

## Conclusion

Build `bf252017` failed due to timeout, but the associated bucket data makes the failure mode clear: **queue-reset regressed from ~`3.9s` to ~`108.8s` per scenario**. The regression is concentrated in `RM.FieldDLQ`, `Field.refusals`, and `Field.other`, while `RM.Field` remains fast.

The evidence does **not** point to a generally broken subscriber pull implementation. It points to queue-specific backlog or drain-strategy problems that now dominate setup time and exhaust the Cloud Build step budget.

---

**Report Date:** 2026-09-05  
**Source Artifacts:** `timings.ndjson`, `cucumber.json`, Cloud Build log, `manifest.json`  
**Status:** ❌ Timeout regression confirmed; queue-reset root cause isolated to non-`RM.Field` drains