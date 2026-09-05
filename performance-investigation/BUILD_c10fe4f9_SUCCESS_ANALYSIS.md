# Build c10fe4f9 Success Analysis

**Build ID:** `c10fe4f9-8722-4a9a-be56-702c34756207`  
**Trigger Project:** `c31-fwmtg-ci-prod`  
**Status:** ✅ SUCCESS  
**Created:** 2026-09-05 19:06:23 UTC  
**Finished:** 2026-09-05 19:13:23 UTC  
**Duration:** ~7m 00s  
**Commit Reported By Build:** `a8976a85141fc763fb1bc7f34b9ec5f936c2940e`  
**Reports Bucket:** `gs://c31-fwmtg-ci-prod-acceptance-test-details/c10fe4f9-8722-4a9a-be56-702c34756207`  

---

## Executive Summary

This run is healthy. The bucket artifacts, manifest, JUnit reports, Cucumber JSON, and timing data are all internally consistent and show a normal HH-only execution with fast queue resets.

Key outcomes:

- Build completed successfully in about `7 minutes`
- `20` HH scenarios executed and all `190` recorded Cucumber steps passed
- Queue-reset mean was `4060 ms`, which is close to the earlier fast reference run (`3894 ms`)
- No `Streaming pull drain failed` warnings appear in the build log

The one important caveat is that the **updated test reports do not match the source currently present in this workspace**. The built `GcpPubSubMessagingTest` report references test names and behaviors that are not present in the checked-out source files.

---

## Build Outcome

The build and manifest align cleanly this time:

- Build status: `SUCCESS`
- Manifest status: `SUCCESS`
- `run_1` executed as `run1-hh`
- `run_2` is explicitly `SKIPPED`
- Manifest totals show `494` total JUnit test entries, `84` executed, `410` skipped, `0` failures, `0` errors

Unlike the earlier timeout build, there is no sign here of a partial or misleading summary layer. The reporting pipeline completed normally.

---

## Updated Reports

### Cucumber Results

From `run1-hh/jsonReports/cucumber.json`:

- `20` scenarios
- `190` steps recorded
- all step statuses `passed`

This matches the HH-only expectation and confirms the acceptance run itself succeeded end to end.

### JUnit Results

From `run1-hh/surefire-reports/TEST-uk.gov.ons.census.fwmt.tests.acceptance.runners.RunCucumberTest.xml`:

- `225` JUnit testcase entries
- `20` executed
- `205` skipped
- `0` failures
- `0` errors

From `run1-hh/surefire-reports/TEST-uk.gov.ons.census.fwmt.tests.acceptance.messaging.GcpPubSubMessagingTest.xml`:

- `9` tests run
- `0` failures
- `0` errors
- `0` skipped

The messaging test text report confirms the same result:

- `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0`

These reports are materially better than the earlier timeout build because they are complete and consistent with the manifest.

---

## Queue-Reset Performance

From `run1-hh/performance-investigation/timings.ndjson`:

| Metric | Value |
|--------|-------|
| **Mean** | 4060 ms |
| **P50** | 4248 ms |
| **P95** | 4666 ms |
| **Max** | 5783 ms |
| **Count** | 20 |

This is effectively a normal run.

### Comparison Against Previous Runs

| Build | Status | Queue-Reset Mean |
|------|--------|------------------|
| **1ced6799** | SUCCESS | 3894 ms |
| **c10fe4f9** | SUCCESS | 4060 ms |
| **bf252017** | TIMEOUT | 108792 ms |

Interpretation:

- `c10fe4f9` is only `166 ms` slower than the earlier fast reference build `1ced6799`
- `c10fe4f9` is `104732 ms` faster than the timeout run `bf252017`
- relative to the timeout build, queue-reset time improved by about **96.3%**

### Per-Queue Drain Means

| Queue | Mean (ms) | Max (ms) |
|------|-----------|----------|
| **RM.Field** | 3327 | 4430 |
| **Outcome.Preprocessing** | 3185 | 5596 |
| **Outcome.PreprocessingDLQ** | 2698 | 5551 |
| **Field.refusals** | 2431 | 2545 |
| **Field.other** | 2311 | 3289 |
| **RM.FieldDLQ** | 2244 | 3578 |

This is the healthy shape we expected to see:

- no queue is dominating reset time catastrophically
- the previously problematic `RM.FieldDLQ`, `Field.refusals`, and `Field.other` lanes are back in the low-`2s` range
- reset pause/resume overhead remains normal (`80 ms` / `91 ms` mean)

---

## Runtime Warning Check

The build log contains **no** matches for:

- `Streaming pull drain failed`
- `Streaming pull not available`
- `callable is null`
- `Failed to streaming-drain`

That matters because the earlier bad run showed explicit StreamingPull fallback warnings before the reset regression. This successful run does not show that failure mode.

---

## Code And Report Mismatch

This is the main non-performance finding from the finished build.

The JUnit report for `GcpPubSubMessagingTest` references test cases including:

- `shouldFallbackToPipelinedAndInvalidateStubWhenStreamingPullFails`
- `shouldUseMultiplePullersForHotSubscriptionAndTwoForBusyOnes`
- `shouldUseStreamingPullDrainWhenFlagEnabled`

The current workspace source does **not** match those names or behaviors.

Current source in `src/test/java/.../GcpPubSubMessagingTest.java` instead contains tests such as:

- `shouldFallbackToPipelinedWhenStreamingPullFails`
- `shouldUseMultiplePullersForRmFieldSubscription`
- `shouldUseStreamingPullDrainByDefault`

And current source in `src/main/java/.../GcpPubSubMessaging.java` still shows:

- fallback parallelism mapped only for `acceptance-tests-RM-Field`
- no visible busy-queue `2`-puller mapping
- no visible stub invalidation logic in the `catch` path after StreamingPull failure

So there is a real mismatch between:

1. the code implied by the successful build reports
2. the code currently checked out in this workspace

Possible explanations:

1. the build used a different revision than the local checkout
2. the acceptance image contained newer code than the local source tree
3. the workspace branch is behind the effective source used by the build, despite matching the visible branch name

What this does **not** look like:

- a reporting corruption issue inside this build
- a partial upload problem

The reports are too internally consistent for that.

---

## Interpretation

There are two important conclusions from this build:

1. **Operationally, the queue-reset regression is gone in this run.**
   The timings are back to the normal `~4s` range and the earlier problematic queues have normalized.

2. **Analytically, the built code surface appears ahead of or different from the current workspace source.**
   The updated JUnit reports strongly suggest that the successful runtime included protections or tuning not visible in the checked-out files, especially around stub invalidation and broader puller parallelism.

That makes this build a strong hint that the fix path is probably valid, but it also means the local workspace is not yet a reliable source of truth for exactly what ran.

---

## Recommended Next Steps

1. Resolve the source-of-truth mismatch by identifying where build commit `a8976a85141fc763fb1bc7f34b9ec5f936c2940e` lives and diffing its `GcpPubSubMessaging` and `GcpPubSubMessagingTest` files against the current workspace.
2. Confirm whether the successful run used acceptance image contents newer than the branch currently checked out locally.
3. If the build code really contains stub invalidation and broader fallback parallelism, port or reconcile those exact changes into the workspace branch.
4. Preserve this build as the new healthy reference point alongside `1ced6799`, since its reporting layer is cleaner and its timing profile is similarly good.

---

## Conclusion

Build `c10fe4f9` is a clean successful run with normal queue-reset timings, complete reports, and no sign of the earlier timeout pathology. The updated reports strongly indicate that the runtime path in this build avoided the StreamingPull fallback failure mode seen before.

The only notable concern is that the successful build artifacts do not line up with the code currently visible in this workspace. That discrepancy should be resolved before treating the local branch as the exact source of the successful behavior.

---

**Report Date:** 2026-09-05  
**Source Artifacts:** `manifest.json`, `cucumber.json`, `timings.ndjson`, JUnit XML, Cloud Build log  
**Status:** ✅ Successful run confirmed; queue-reset healthy; source/report mismatch requires follow-up