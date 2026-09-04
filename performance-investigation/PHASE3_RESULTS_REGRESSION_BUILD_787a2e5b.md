# Phase 3 Performance Analysis - Build 787a2e5b-c334-4948-bb6b-cce36e1362b5

## Overview
- **Build Status**: ✅ SUCCESS
- **Test Coverage**: 20 HH scenarios
- **All Tests**: ✅ PASSED (0 failures)
- **Duration**: 7 minutes (14:43-14:50)
- **Image**: sha256:21f64a0ce5757124ab3cbca2d286fda264e211d6131a093965a202f4282088c9 ✅ (commit 25c0ea9 - stub reuse fix)

## Queue-Reset Performance Results

### Summary Statistics
| Metric | Value | Status |
|--------|-------|--------|
| **Mean** | 4340.2 ms | ⚠️ -2.1% (vs baseline 4250ms) |
| **Median** | 4450 ms | ⚠️ -4.7% (vs baseline 4250ms) |
| **Min** | 2867 ms | ✅ Good |
| **Max** | 6106 ms | ⚠️ High outlier |
| **P95** | 6106 ms | ⚠️ High variance |
| **P99** | 6106 ms | ⚠️ High variance |

### Individual Queue Drain Times (Mean)
- **RM.Field**: 3607.6 ms (SLOWEST - highest variance)
- **Outcome.Preprocessing**: 2850.8 ms
- **Outcome.PreprocessingDLQ**: 2452.8 ms
- **Field.other**: 2353.8 ms
- **Field.refusals**: 2270.2 ms
- **RM.FieldDLQ**: 2060.7 ms (fastest)

**Pause/Resume Overhead**: ~325 ms total (155ms pause + 170ms resume)

## Performance Regression Analysis

### Key Findings

#### 1. ❌ Performance is WORSE than Phase 2
- Phase 3 Mean: **4340.2 ms**
- Phase 2 Mean: **4250 ms** (from PHASE2_RESULTS_POOL6_PARALLELISM.md)
- **Regression**: +90.2 ms (+2.1%)

#### 2. ⚠️ High Variance in Results
- Standard deviation suggests significant variability across scenarios
- P95/P99 at 6106ms indicates outliers
- Some scenarios hitting 5.5-6.1 seconds

#### 3. ✅ No Hung Scenarios
- No scenario exceeding 6.2 seconds (Phase 2 had one at 317.88s)
- All scenarios completing normally

#### 4. ⚠️ RM.Field Queue Slowest
- RM.Field mean: 3607.6ms (exhibits highest variance)
- Range: 2062ms to 5773ms (variance of 3711ms!)
- This is the bottleneck in parallelized drain

## Root Cause Investigation

### Hypothesis 1: Code Not Actually Deployed ❌ RULED OUT
- ✅ Verified correct image SHA (21f64a0c...) built from commit 25c0ea9
- ✅ Docker snapshot build completed successfully
- ✅ Image contains fixed code with stub reuse

### Hypothesis 2: Stub Reuse Not Effective ⚠️ POSSIBLE
The Phase 3 fix should:
- Create subscriberStub once per scenario (not per pull)
- Reuse stub across all 6 concurrent drain operations
- Expected savings: 500-800ms per scenario

**Actual Result**: No measurable improvement, slight regression

**Possible Explanations**:
1. Stub initialization cost was not the bottleneck in actual cloud deployment
2. Network/GCP latency dominates, not stub creation
3. Other factors (Pub/Sub queue sizes, throughput limits) are limiting performance
4. Test environment variability making results noisy

### Hypothesis 3: Regression from Code Changes ⚠️ POSSIBLE
Changes in commit 25c0ea9:
- Moved `subscriberStub = subscriber.getStub()` to beginning of `enableEventMonitor()`
- Changed `pullMessages()` to reuse stub instead of creating new one each time
- Changed `acknowledge()` to reuse stub

**Potential Issue**: If the reused stub becomes stale or has connection issues, this could cause retries and degradation.

## Performance Targets

| Phase | Baseline | Achieved | Target | Status |
|-------|----------|----------|--------|--------|
| Phase 1 | 16.5s | 6.74s | <16s | ✅ 58.2% improvement |
| Phase 2 | 6.74s | 4.25s | <5s | ✅ 37.5% improvement |
| Phase 3 | 4.25s | 4.34s | <3.5s | ❌ +2.1% regression |
| Phase 4 | 4.34s | TBD | <3.0s | ⏳ Not started |

## Recommendations

### Immediate Actions
1. **Revert Phase 3** and investigate why stub reuse isn't helping
   - The fix may have hidden issues or may not address actual bottleneck
   - Performance regression suggests something went wrong

2. **Profile GCP Pub/Sub Operations** in cloud environment
   - Determine actual bottleneck: network, API latency, or queue depletion
   - Check if stub creation time was ever the issue

3. **Analyze RM.Field Queue Variance**
   - Why 2062-5773ms range for same operation?
   - Indicates instability or resource contention

### Alternative Approaches
- **Phase 4**: Instead of stub reuse per scenario, consider shared cache across all scenarios
- **Phase 5**: Connection pooling at GCP level
- **Phase 6**: Batch drain operations differently (hybrid parallel/sequential)

## Test Execution Details
- Build Trigger: `census31-fwmtg-ci-acceptance-tests-run`
- Region: europe-west2
- Cluster: c31-fwmtg-dev
- Namespace: fwmt
- Image Used: CORRECT ✅
- Build Logs: https://console.cloud.google.com/cloud-build/builds;region=europe-west2/787a2e5b-c334-4948-bb6b-cce36e1362b5

## Data Sources
- Bucket: gs://c31-fwmtg-ci-prod-acceptance-test-details/787a2e5b-c334-4948-bb6b-cce36e1362b5/
- Timings: run1-hh/performance-investigation/timings.ndjson (166.4 KiB, 20 scenarios)
- Cucumber: run1-hh/jsonReports/cucumber.json (91.5 KiB)
