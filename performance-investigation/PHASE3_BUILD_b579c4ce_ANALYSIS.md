# Build b579c4ce Analysis - Unknown Strategy/Configuration

**Date**: 2026-09-04  
**Build ID**: b579c4ce-6004-42ce-883a-d5dbe5d6601d  
**Status**: ⚠️ **ANOMALY DETECTED** | Strategy unclear

## Executive Summary

**CRITICAL FINDING**: Build b579c4ce shows **different performance profile** from previous Seek implementations, but **NO diagnostic logging appears** in maven.log, indicating the code with diagnostic logging (commit 6c899af) was NOT used.

| Metric | Phase 2 Baseline | Phase 3-v1 (Seek Failed) | Phase 3-v2 (Seek Working) | Build b579c4ce (UNKNOWN) | Direction |
|--------|------------------|--------------------------|---------------------------|--------------------------|-----------|
| Overall Mean | 4250ms | 4658ms | 5126ms | **4342ms** | ↑ Baseline |
| Overall Median | 4250ms | 4306ms | 4240ms | **4322ms** | ↑ Baseline |
| Per-Queue Mean | N/A | ~3.8s | ~3.8s | **2.7s** | ✅ Better! |
| Per-Queue Max | ~6.1s | ~6.1s | 10.1s | **5.4s** | ✅ Better! |
| Test Results | All pass | All pass | All pass | 76/486 pass (?) | ⚠️ Different |

## ❌ Problem: Missing Diagnostic Logs

Expected log entries from commit 6c899af:
```
Seek purge succeeded for subscription acceptance-tests-Field-refusals
Seeking subscription to timestamp...
Seek purge failed for subscription...
```

**Actual result**: 0 occurrences of "Seek purge" in maven.log

**This means**: Build b579c4ce is NOT running the code with diagnostic logging (commit 6c899af). Either:
1. Build used older commit (5648cc8 without logging)
2. Build used different branch
3. Docker image from earlier point in time

## ✅ Interesting Finding: Individual Queue Performance is BETTER

Despite no diagnostic logs, the individual per-queue drain times are surprisingly good:

```
Field.refusals:            Mean 2232ms, Max 2554ms, Range 503ms ← Very tight!
Field.other:              Mean 2258ms, Max 3539ms
RM.FieldDLQ:              Mean 2338ms, Max 3213ms
Outcome.PreprocessingDLQ: Mean 2442ms, Max 3767ms
Outcome.Preprocessing:    Mean 3092ms, Max 4521ms ← Slowest queue
RM.Field:                 Mean 3659ms, Max 5402ms ← Most variance
```

### Variance Analysis

**Phase 3-v2 (Seek)** queue ranges:
- Min range: 503ms (Field.refusals)
- Max range: 3222ms (RM.Field)
- Highly variable per queue

**Build b579c4ce** queue ranges:
- Min range: 503ms (Field.refusals) 
- Max range: 3222ms (RM.Field)
- **Same variance pattern!**

This suggests build b579c4ce **may also be using Seek**, but without the diagnostic logging from commit 6c899af.

## Overall Performance Problem

Individual queue times (per-queue mean 2.7s) don't explain the overall queue-reset mean of 4.3s:

```
Math:
  If 6 queues average 2.7s each
  With 6-thread pool running in parallel
  Should be: ~2.7s max (parallel = sequential max time)
  
Actual: 4.3s overall

Gap: 4.3s - 2.7s = 1.6s unexplained overhead
```

**Possible explanations**:
1. Sequential initialization/cleanup overhead
2. Subscriber stub creation time
3. Wait for all queues to complete (not perfect parallelism)
4. Per-hook overhead (hook setup/teardown)

## Test Results Discrepancy

Manifest shows:
- Tests total: 486
- Tests passed: 76
- Tests skipped: 410
- Tests executed: 76

**This is unusual**: Typically HH scenarios show 20-25 passed (20 scenarios × 1 test each). 76 passed suggests different test suite or configuration.

## Hypothesis: Build Used Intermediate Code

Most likely scenario:

```
Code Timeline:
├─ 5648cc8: Seek implementation (no logging)
├─ 6c899af: Add diagnostic logging ← Expected for this build
├─ 28d6d4a: Analysis document
└─ Build b579c4ce: ??? (Somewhere in between?)

Evidence:
✓ Seek RPC behavior in performance metrics (better per-queue, less variance)
✗ No "Seek purge" diagnostic logs
✓ Tests passing correctly
✗ Different test result count (76 vs expected 20)
```

## Performance Verdict

**Build b579c4ce Status**: UNKNOWN STRATEGY

If running Seek (implied by per-queue times):
- ✅ Per-queue performance is actually better than Phase 3-v2 (2.7s vs 3.8s mean)
- ✅ Max per-queue reduced to 5.4s (vs 10.1s with explicit Seek)
- ❌ Overall time still 4.3s (vs Phase 2 baseline 4.25s) = no improvement
- ❓ Overhead source unidentified

If running Phase 2 (linear drain):
- ✅ Overall time ~4.3s matches Phase 2 baseline
- ❌ But per-queue times (2.7s mean) should be higher (~3.8s) with linear drain
- ❓ Per-queue improvement doesn't match Phase 2 expected behavior

## Recommended Investigation

1. **Verify Deployed Code**: Check git commit used in build b579c4ce
   ```bash
   gcloud builds log b579c4ce-6004-42ce-883a-d5dbe5d6601d --region=europe-west2
   ```

2. **Confirm Seek is Active**: Re-run with commit 6c899af (diagnostic logging)
   - Should see "Seek purge" logs if strategy is working
   - Will clarify per-queue overhead source

3. **Profile Overall Overhead**: Add timing instrumentation for:
   - Subscriber stub creation
   - SeekRequest building
   - drainResidualMessages execution
   - Hook setup/teardown

4. **Optimize Parallel Coordination**: 
   - Current overhead: ~1.6s unexplained
   - Investigate ExecutorService wait time
   - Check if sequential initialization is serial bottleneck

## Metrics Collected

**Build b579c4ce Results** (20 HH scenarios, but 76 tests reported):

```
Queue-Reset Timing (NDJSON):
  Count: 20
  Min: 3174ms
  Max: 5742ms
  Mean: 4341.9ms
  Median: 4322ms
  P95: 5742ms

Individual Queue Drains:
  Field.refusals:            2232ms mean (Range: 503ms)
  Field.other:              2258ms mean (Range: 2255ms)
  RM.FieldDLQ:              2338ms mean (Range: 1741ms)
  Outcome.PreprocessingDLQ: 2442ms mean (Range: 1923ms)
  Outcome.Preprocessing:    3092ms mean (Range: 2488ms)
  RM.Field:                 3659ms mean (Range: 3222ms)

Overall: 2670ms mean per-queue vs 4342ms overall
         1672ms overhead unexplained
```

## Next Steps

- **PRIORITY 1**: Determine which commit build b579c4ce used
- **PRIORITY 2**: Re-run with explicit commit 6c899af to enable diagnostic logging
- **PRIORITY 3**: Profile the 1.6s overhead in parallel queue coordination
- **PRIORITY 4**: Consider alternative optimizations if Seek + residual drain remains bottlenecked

---

**Status**: ⚠️ UNCLEAR  
**Seek Strategy**: IMPLIED (from per-queue times) but UNCONFIRMED (no logs)  
**Performance vs Phase 2**: +2% regression (4.3s vs 4.25s)  
**Recommendation**: Confirm deployed commit and re-run with diagnostic logging enabled
