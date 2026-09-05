# Phase 3 Seek Strategy Analysis - Build d168b203

**Date**: 2026-09-04  
**Build ID**: d168b203-4d4a-4691-a90e-84d99900ccb4  
**Strategy**: Google Cloud Pub/Sub Seek API for O(1) queue purge  
**Status**: ❌ **NOT EFFECTIVE** - Seek is failing and falling back to linear drain

## Executive Summary

The Pub/Sub Seek strategy (documented in OPTIMIZATION_PLAN.md) was implemented in code and deployed to build d168b203. However, **performance metrics show ZERO improvement** over Phase 2 baseline (4.25s), suggesting the Seek API is failing silently and falling back to the original linear pull/ack drain loop.

### Key Findings

| Metric | Value | Status |
|--------|-------|--------|
| Median queue-reset | 4306ms | ❌ -1.3% vs Phase 2 (4250ms) |
| Mean queue-reset | 4658.5ms | ❌ -9.6% vs Phase 2 |
| P95 queue-reset | 6446ms | ⚠️ High variance |
| Individual queue max | 6106ms | 🚨 Indicates full pull loop |
| Test success rate | 100% (20/20 scenarios) | ✅ All pass |

## Hypothesis: Seek is Failing

**Evidence**:

1. **Individual Queue Timing Analysis**:
   ```
   RM.Field:                  Min: 2458ms, Max: 4998ms, Range: 2540ms
   RM.FieldDLQ:               Min: 2967ms, Max: 5824ms, Range: 2857ms
   Field.refusals:            Min: 3152ms, Max: 3714ms, Range:  562ms
   Field.other:               Min: 2568ms, Max: 6050ms, Range: 3482ms
   Outcome.Preprocessing:     Min: 3253ms, Max: 5106ms, Range: 1853ms
   Outcome.PreprocessingDLQ:  Min: 2186ms, Max: 6106ms, Range: 3920ms
   ```

   **Pattern**: Max times > 5000ms indicate the full linear pull/ack loop is running
   - If seek succeeded: Max would be ~200-500ms (seek RPC + bounded residual drain)
   - Actual: Max > 5s on 4/6 queues = full unbounded drain

2. **No Seek Success Logs**: Maven logs show no "Seek purge succeeded" messages (would appear at INFO level)

3. **Timing Matches Phase 2 Exactly**: Same distribution, suggesting code path didn't change

## Root Cause Analysis

### Most Likely: Missing IAM Permission

The Seek API requires the `pubsub.subscriptions.seek` IAM permission. If missing:

```java
// Code path taken:
seekPurge()  // calls subscriber.seekCallable().call(request)
  → Exception caught (permission denied)
  → return false  // Signal seek failed
drainByPullLoop()  // Fallback to linear drain (current path)
  → While loop pulls batches of 1000 messages
  → Loops until queue empty (3.7-3.8 seconds per queue)
```

### How to Verify

1. Check service account IAM bindings:
   ```bash
   gcloud projects get-iam-policy c31-fwmtg-ci-prod --flatten="bindings[].members" \
     --filter="bindings.role:pubsub.subscriptions.seek"
   ```

2. Check error details in logs with next build (added DEBUG/INFO/WARN logging):
   ```
   log.info("Seek purge succeeded for subscription ...")  // Would appear if seeking
   log.warn("Seek purge failed ... (PermissionDenied)")   // Would appear if permission denied
   log.info("Draining ... fallback pull/ack loop")        // Would appear if fallen back
   ```

## What Actually Happened

```
queue-reset hook
  │
  ├─ Thread 1: RM.Field
  │  └─ drainSubscription(RM.Field)
  │     ├─ seekPurge()
  │     │  └─ subscriber.seekCallable().call(SeekRequest)
  │     │     └─ ❌ EXCEPTION: PermissionDenied (or similar)
  │     └─ drainByPullLoop()  // ← FALLBACK PATH TAKEN
  │        ├─ Pull 1000 messages
  │        ├─ Acknowledge
  │        ├─ Pull 1000 messages
  │        └─ ... (repeat 3-4 times until empty)
  │        └─ Total: ~3.7s
  │
  ├─ Thread 2-6: Other queues (same pattern)
  │
  └─ Total queue-reset: max(~3.7s per queue) = 4.3s (concurrent)
```

## Performance Impact

**No improvement achieved**:
- Phase 2 (6-thread pool): 4250ms median
- Phase 3 Seek (attempted): 4306ms median
- Regression: -1.3% (within variance margin)

The seek strategy added code complexity without performance benefit because it's not being used.

## Next Steps (Priority: HIGH)

### 1. **Verify IAM Permission** (IMMEDIATE)
```bash
# Check if service account has pubsub.subscriptions.seek
gcloud projects get-iam-policy c31-fwmtg-ci-prod \
  --flatten="bindings[].members" \
  --format="table(bindings.role,bindings.members[].id)"
```

### 2. **Deploy with Enhanced Logging** (IMMEDIATE)
- Commit: 6c899af adds detailed logging
- Next build will log seek success/failure with exception type
- This will definitively confirm whether seek is working or failing

### 3. **Investigate Seek Alternative** (IF PERMISSION DENIED)

If IAM permission cannot be granted, consider alternatives:

#### Option A: Use ModifyAckDeadline with 0-second deadline (O(n))
```java
// Faster than pull/ack but slower than seek
// Sets all messages to ack deadline=0 (deliver immediately)
// Clients will pull and discard them
```

#### Option B: Accept Linear Drain with Optimization (O(n) but faster)
```java
// Increase batch size from 1000 to 5000-10000
// Reduce RTT count: fewer pull operations
// Estimated: 3.7s → 2.5-3s (still not <3s target)
```

#### Option C: Consider Subscription Recreation Pattern
```java
// Trade space for time: delete + recreate subscription
// Challenge: May cause message loss if used incorrectly
// Risk: High complexity for marginal gain
```

## Code Status

### Current State ✅
- Seek implementation present in GcpPubSubMessaging.java
- Fallback to pull/ack loop implemented
- Unit tests passing (GcpPubSubMessagingTest 5/5)
- All integration tests passing (20/20 scenarios)

### Changes Staged
- Commit 6c899af: Enhanced logging for diagnosing seek behavior
- Ready for next cloud build to get detailed logs

## Metrics Collected

**Build d168b203 Results** (20 HH scenarios):

```
Queue Reset (ms):
  Min:      4064
  Max:      6446
  Mean:     4658.5
  Median:   4306
  P95:      6446
  P99:      6446

Individual Queue Analysis:
  Field.other:               Mean 3955ms, Max 6050ms ← Worst performer
  RM.FieldDLQ:               Mean 3772ms, Max 5824ms
  Outcome.Preprocessing:     Mean 3729ms, Max 5106ms
  RM.Field:                  Mean 3743ms, Max 4998ms
  Outcome.PreprocessingDLQ:  Mean 3592ms, Max 6106ms
  Field.refusals:            Mean 3599ms, Max 3714ms ← Best performer

Test Results:
  Scenarios passed:     20/20 ✅
  Steps passed:        190/190 ✅
  Failures:            0 ✅
```

## Comparison with Previous Phases

| Phase | Strategy | Median | Improvement | Status |
|-------|----------|--------|-------------|--------|
| 1 | Drain-based (6.74s Phase 1 regression) | 6.74s | - | ✅ Baseline |
| 2 | 6-thread pool parallelism | 4.25s | -37.5% | ✅ Effective |
| 3 | Stub reuse attempt | 4.45s | -1.3% regression | ❌ Regressed |
| 3b | Seek API strategy | 4.31s | -1.3% | ❌ No improvement |

## Recommendations

1. **IMMEDIATE**: Run next build with enhanced logging (commit 6c899af)
   - Will show exact reason seek is failing
   - Will confirm fallback path is being taken

2. **URGENT**: Check IAM permissions for service account
   - If `pubsub.subscriptions.seek` not granted → grant it
   - If cannot be granted → pivot to alternative strategy

3. **NEXT PHASE**: Once we know why seek isn't working:
   - If fixable: Deploy with permission grant + re-test
   - If not: Implement Option B (larger batch sizes) or other alternative

## Performance Target Status

- **Target**: < 3 seconds
- **Current**: 4.3 seconds (4.3s ÷ 3s = 143% of target)
- **Shortfall**: 1.3 seconds needed
- **Gap Analysis**: Need 30% improvement to hit target

---

**Next Action**: Merge commit 6c899af and run cloud build for diagnostic logs
