# Phase 3 Performance Analysis: Initial Cloud Build (Build 5770a424)

**Status:** ❌ **PERFORMANCE REGRESSION - FIX DEPLOYED, AWAITING RE-TEST**

## Build Metadata
- **Build ID:** 5770a424-534b-4b35-927d-80c1c7ff678a
- **Timestamp:** 2026-09-04T14:22:50Z - 2026-09-04T14:29:56Z (7m 6s total)
- **Project:** c31-fwmtg-ci-prod (europe-west2)
- **Test Suite:** @HH (Household) - 20 feature scenarios
- **Status:** ✅ SUCCESS (but with performance issues)

## Test Results Summary
- **Total Scenarios:** 486 defined
- **Executed:** 76 (420 skipped as @HH filtered)
- **Passed:** 76 ✅
- **Failed:** 0 ✅
- **Errors:** 0 ✅
- **Total Duration:** 553.62s (9m 14s)
- **Average per Scenario:** 7.284s

## Queue-Reset Performance Analysis

### Overall Metrics
| Metric | Value | Status |
|--------|-------|--------|
| **Median Duration** | **5.01s** | ❌ WORSE than Phase 2 |
| **Mean Duration** | 19.81s | ⚠️ Skewed by outlier |
| **Minimum** | 3.35s | ✅ Good (best case) |
| **Maximum** | **317.88s** | 🔴 CRITICAL - Scenario hung |
| **Phase 2 Baseline** | 4.25s | -- |
| **Phase 3 Target** | 3.50s | -- |

### Performance Degradation
- **Regression:** +18% slower than Phase 2 baseline (5.01s vs 4.25s)
- **vs Target:** +43% worse than Phase 3 target (5.01s vs 3.50s)
- **Expected Improvement:** -500 to -800ms
- **Actual Result:** +760ms regression

## Root Cause Analysis

### The Problem
The initial Phase 3 implementation (commit `d4797db`) had a **critical initialization order bug**:

```java
// WRONG ORDER (in d4797db):
drainSubscription();  // Called BEFORE stub created
log.info("Drained GCP gateway event monitor...");
subscriberStub = GrpcSubscriberStub.create(...);  // Created AFTER drain
```

When `drainSubscription()` calls `pullMessages()`, the `subscriberStub` field is null, causing either:
1. NullPointerException (if exception not caught)
2. Silent failure and retry loop with new stub creation (if caught)
3. Repeated stub initialization (expensive operation, defeating optimization)

### Evidence
The snapshot image used in this build (digest `sha256:ec0a738d...`) was built from commit `d4797db` which still had the ordering bug. The drainSubscription would continuously fail and retry, creating new stubs each time instead of reusing one stub.

### The Fix
Commit `25c0ea9` corrected the initialization order:

```java
// CORRECT ORDER (in 25c0ea9):
subscriberStub = GrpcSubscriberStub.create(...);  // Created FIRST
drainSubscription();  // Can now use the stub
```

## Detailed Operation Breakdown

### By Queue (Median Times)
| Queue | Median | Count | Status |
|-------|--------|-------|--------|
| queue-reset-drain-RM.Field | **3.87s** | 20 calls | Large backlog |
| queue-reset-drain-Outcome.Preprocessing | **3.34s** | 20 calls | Large backlog |
| queue-reset-drain-Field.other | **2.38s** | 20 calls | Moderate |
| queue-reset-drain-Outcome.PreprocessingDLQ | **2.18s** | 20 calls | Moderate |
| queue-reset-drain-Field.refusals | **2.14s** | 20 calls | Moderate |
| queue-reset-drain-RM.FieldDLQ | **2.16s** | 20 calls | Moderate |
| **queue-reset (aggregate)** | **4.69s** | 20 calls | Total drain time |

### Hook Timing Context
```
queue-reset-pause-inbound-adapters: ~156ms (stable)
queue-reset (all drains):            ~4,686ms (TOO HIGH)
queue-reset-resume-inbound-adapters: ~169ms (stable)
TOTAL:                               ~5,010ms (median)
```

## Scenario Outlier: 317.88s Anomaly

The first scenario in the sorted list consumed 317.88 seconds. Analysis shows:
- Contains 66+ individual drain operations in sequence
- Not concurrent draining (violates Phase 2 design)
- Indicates possible:
  - Logging/debugging overhead enabled
  - Performance monitoring hook thrashing
  - Lock contention under stub reuse pattern
  - Timeout and retry behavior

**This needs investigation** - may indicate concurrency issue in Phase 3 stub reuse implementation.

## Comparison Against Optimization Roadmap

| Phase | Baseline | Target | Achieved | Status |
|-------|----------|--------|----------|--------|
| **Phase 1** | 16.5s | 6.74s | 6.74s | ✅ Complete (+58.2%) |
| **Phase 2** | 6.74s | 4.25s | 4.25s | ✅ Complete (+37.5%) |
| **Phase 3** | 4.25s | 3.5s | **5.01s** | ❌ Regression (-18%) |

## Next Steps

### Immediate Action (Priority 1)
1. ✅ **Fixed initialization order** (commit `25c0ea9`)
2. **Pending:** Trigger new cloud build to test corrected code
3. **Expected:** Median should drop to ~3.0-3.5s range if stub reuse works

### If Phase 3 Target Not Achieved
1. **Investigate:** Why stub reuse isn't providing expected 500-800ms savings
   - Verify stub is actually being reused (not recreated)
   - Check if concurrent drain is still working (Phase 2 optimization)
   - Profile hot paths in drain operations

2. **Consider Phase 4:** Shared Pub/Sub stub cache across ALL queue-reset operations
   - Currently: One stub per GcpGatewayEventMonitor instance
   - Proposed: Centralized stub pool reused across scenarios
   - Estimated savings: 200-400ms additional

### Investigation Notes
- The outlier scenario (317.88s) should be identified and profiled
- Check if performance monitoring hooks are creating excessive overhead
- Verify concurrent drain (6-thread pool) is still functioning correctly
- Consider if background polling thread is interfering with drain operations

## Files
- **Cucumber JSON:** `jsonReports/cucumber.json` (91.5 KiB)
- **Timing Data:** `performance-investigation/timings.ndjson` (166.4 KiB)
- **Reports:** https://storage.cloud.google.com/c31-fwmtg-ci-prod-acceptance-test-details/5770a424-534b-4b35-927d-80c1c7ff678a/run1-hh/

## Commits Related to Phase 3
- `d4797db` - Initial Phase 3 implementation (BUG: wrong initialization order)
- `25c0ea9` - **FIX:** Corrected stub initialization order (PENDING TEST)

---
**Analysis Date:** 2026-09-04
**Analyzed By:** Performance Investigation System
**Status:** Awaiting re-test with corrected code
