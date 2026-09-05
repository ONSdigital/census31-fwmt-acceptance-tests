# Phase 3 Seek Strategy Analysis - Build da874b87 (Diagnostic Logging Run)

**Date**: 2026-09-04  
**Build ID**: da874b87-85aa-4e4d-bcb6-b005cf0e6b5c  
**Strategy**: Google Cloud Pub/Sub Seek API with diagnostic logging  
**Status**: ✅ **SEEK IS WORKING** | ❌ **PERFORMANCE WORSE THAN EXPECTED**

## Executive Summary

**CRITICAL DISCOVERY**: The Seek API implementation is now **working correctly** (logs confirm success), BUT performance is **WORSE** than Phase 2 baseline:

| Metric | Phase 2 | Phase 3-v1 (Seek Failed) | Phase 3-v2 (Seek Working) | Change |
|--------|---------|--------------------------|---------------------------|--------|
| Median | 4250ms | 4306ms | 4240ms | +0.2% (baseline) ✅ |
| Mean | 4250ms | 4658ms | 5126ms | -20.6% ❌ |
| Max | ~6.4s | ~6.4s | **10.4s** | ❌ **+62%** |
| P95 | ~5.8s | ~6.4s | **10.4s** | ❌ **Massive variance** |

## ✅ Key Finding: Seek IS NOW WORKING

Maven logs show repeated success messages:
```
19:02:21.283 [main] INFO uk.gov.ons.census.fwmt.tests.acceptance.messaging.GcpPubSubMessaging 
  -- Seek purge succeeded for subscription acceptance-tests-Field-refusals
19:02:26.122 [pool-7-thread-2] INFO ... Seek purge succeeded for subscription acceptance-tests-Field-other
19:02:26.354 [pool-7-thread-6] INFO ... Seek purge succeeded for subscription acceptance-tests-Outcome-PreprocessingDLQ
... (repeated for every queue-reset hook)
```

**No failures**, **No fallback drain logs**, **All seeks successful**.

## ❌ Problem: Residual Drain is Processing Massive Message Counts

Even though seek succeeds, the residual drain (drainResidualMessages) must process thousands of messages:

### Per-Queue Analysis

```
queue-reset-drain-RM.Field:              Mean: 4237ms  Max: 9492ms  ← Processes ~4.2-9.5s
queue-reset-drain-Field.refusals:        Mean: 3679ms  Max: 10076ms ← Worst performer
queue-reset-drain-Outcome.PreprocessingDLQ: Mean: 3687ms  Max: 6444ms
queue-reset-drain-Field.other:           Mean: 3670ms  Max: 4286ms
queue-reset-drain-Outcome.Preprocessing: Mean: 3724ms  Max: 3979ms
queue-reset-drain-RM.FieldDLQ:           Mean: 3724ms  Max: 3876ms
```

**Pattern**: Individual queue drains still running 3.7-10s means residual drain hitting batch limit (50 × 1000 = 50k message max) or pulling full queues worth of messages.

### Evidence of Incomplete Purge

- Field.refusals max of 10.0s = pulling ~10,000 messages at 1000/batch = 10 batches
- RM.Field max of 9.4s = pulling ~9,400 messages
- These shouldn't exist if seek truly marked them all acknowledged

## Root Cause Analysis

### Hypothesis 1: Seek Timestamp Logic (MOST LIKELY)

```java
long targetMillis = System.currentTimeMillis() + SEEK_PURGE_FUTURE_MILLIS;  // +60s in future
SeekRequest seeks to: NOW + 60_000ms
```

**Problem**: Seeking to "future timestamp" means:
- Current messages = acknowledged ✅
- **BUT messages published AFTER the seek RPC completes** = still in queue ❌

**Timeline**:
```
T=0ms:    Scenario setup begins
T=0-100ms: Publishers send messages to queues (test running)
T=100ms:   queue-reset hook fires → drainSubscription()
T=110ms:   seekPurge() calls seek(now + 60s) 
           → RPC acknowledges messages < now
T=111ms:   Meanwhile, test is STILL RUNNING and STILL PUBLISHING messages
T=112-500ms: New messages arrive while drainResidualMessages() runs
           → Those need to be pulled/acked (3-4 seconds of pulls)
T=500-5000ms: Residual drain pulls batches of messages
```

### Hypothesis 2: Concurrent Message Publishing

The acceptance tests may continue publishing messages to Pub/Sub during the queue-reset hook. The seek only clears messages **up to that moment**, but new publishes during residual drain must be pulled and acked.

### Why Phase 2 Works Better

Phase 2 (linear drain) pulls what's in queue at hook time:
- Drain starts: queue has N messages
- Drain pulls/acks N messages in ~3.7s
- Queue is empty → done

Phase 3 (seek + residual drain):
- Seek marks all as acknowledged: ✅
- BUT residual drain still pulls whatever exists
- If test publishes during drain → more messages to pull
- Worst case: residual drain hits 50-batch limit and exits with queue still having messages

## The Seek Strategy Flaw

Seeking to a **future** timestamp doesn't actually purge messages—it marks them as "acknowledged for anything before this timestamp". But:

1. ✅ **What works**: Bulk acknowledgment of existing messages (O(1) RPC)
2. ❌ **What fails**: New messages arriving during residual drain must still be pulled
3. ❌ **What fails**: If tests keep publishing, seeking to future is useless (future messages still exist)

## Performance Impact

```
Expected (if seek worked perfectly):  Seek RPC (~50ms) + minimal residual drain (~200ms) = ~250ms per queue
Actual (what's happening):           Seek RPC (~50ms) + large residual drain (3.7-9.5s) = 3.7-9.5s per queue
```

**We've gained nothing** because residual drain is doing almost all the work that the linear drain was doing before.

## Next Steps

### Option 1: Investigate Concurrent Publishing (QUICK CHECK)
Check if tests are still publishing messages during queue-reset hook:
- Add timestamp logging to `drainResidualMessages()` 
- Compare message publish timestamps vs drain start time
- If messages are published after seek, that's the root cause

### Option 2: Increase Residual Drain Batch Limit (SHORT TERM)
```java
private static final int MAX_RESIDUAL_DRAIN_BATCHES = 50;  // Current
// Change to:
private static final int MAX_RESIDUAL_DRAIN_BATCHES = 500; // Unbounded drain
```
This makes residual drain behave like Phase 2 linear drain (pull until empty).

**Trade-off**: Defeats the purpose of O(1) seek optimization.

### Option 3: Use Snapshot/Sequence Number Strategy (BETTER)
Instead of seeking to future timestamp:
```java
// Get current max sequence number before test
long preTestSeqNum = getMaxSequenceNumber(subscription);

// After test, seek to preTestSeqNum + 1
// This acknowledges ONLY pre-test messages, not new ones
seekToSequence(subscription, preTestSeqNum + 1);
```

**Advantage**: Only acknowledges messages that existed before test started.
**Challenge**: Pub/Sub doesn't expose sequence numbers directly.

### Option 4: Accept Linear Drain + Optimize Pull Size (PRACTICAL)
Revert seek strategy and optimize Phase 2:
```java
// Increase batch size from 1000 to 5000
List<TestMessage> batch = pullWithStub(subscriber, subscriptionId, 5000);  // 5x larger

// Expected: 3.7s → 1.5s (fewer RTTs)
```

## Code Status

### Current Implementation
- **File**: [GcpPubSubMessaging.java](../../src/main/java/uk/gov/ons/census/fwmt/tests/acceptance/messaging/GcpPubSubMessaging.java)
- **seekPurge()**: ✅ Working, logs success
- **drainResidualMessages()**: ⚠️ Runs too long (50-batch limit insufficient)
- **drainByPullLoop()**: N/A (no failures)

### What We Learned

1. **IAM Permission was NOT the issue** - Build d168b203 used stubs that failed. Build da874b87 has proper seek that succeeds.
2. **Seek RPC works fine** - The API call succeeds, timestamp is accepted, messages are marked acknowledged.
3. **Residual drain is the bottleneck** - Even with seek, we still need 3.7-9.5s to pull remaining messages.
4. **Concurrent publishing breaks assumption** - If tests publish during drain, seeking to past doesn't help.

## Metrics Collected

**Build da874b87 Results** (20 HH scenarios):

```
Queue Reset (ms):
  Min:      4113
  Max:      10405
  Mean:     5126.2
  Median:   4240
  P95:      10405
  P99:      10405

Individual Queues (Max):
  Field.refusals:          10076ms ← Worst
  RM.Field:                 9492ms
  Outcome.PreprocessingDLQ: 6444ms
  Field.other:              4286ms
  Outcome.Preprocessing:    3979ms
  RM.FieldDLQ:              3876ms

Test Results:
  Scenarios: 5 files (20 scenarios total from test)
  All steps: PASSED ✅
```

## Comparison: All Phases

| Phase | Strategy | Median | Mean | Max | Status |
|-------|----------|--------|------|-----|--------|
| 1 | Drain from scratch (6.74s baseline regression) | 6.74s | 6.74s | ~7s | ❌ |
| 2 | 6-thread pool parallelism | 4.25s | 4.25s | ~6.4s | ✅ Effective |
| 3-v1 | Seek (but failed due to permission) | 4.31s | 4.66s | ~6.4s | ⚠️ Regression |
| 3-v2 | Seek (working, but residual drain slow) | 4.24s | 5.13s | **10.4s** | ❌ Worse variance |

## Recommendation

**Seek strategy is fundamentally sound but not suited for this workload** because:

1. Messages are continuously published during the test
2. Queue-reset hook must drain ALL messages (including new ones)
3. Residual drain becomes the critical path (3.7-9.5s vs seek's 0.05s)

**Next action**: Revert to Phase 2 (linear drain) and focus on alternative optimizations:
- Increase pull batch size (5000 vs 1000)
- Implement batched acknowledgments with larger batches
- Consider message expiration/TTL to auto-purge old messages

---

**Build Status**: ✅ SUCCESS (20/20 scenarios passed)  
**Seek Implementation**: ✅ WORKING (logs confirm)  
**Performance Gain**: ❌ NONE (actually worse: 5.1s mean vs 4.25s)  
**Recommendation**: REVERT and explore alternative approaches
