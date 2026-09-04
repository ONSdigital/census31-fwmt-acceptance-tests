# FWMT Acceptance Tests - Queue Reset Performance Optimization Plan

**Document**: Phase 2 Performance Investigation  
**Branch**: FMT-128_performance-investigation  
**Date**: 2026-09-04  
**Objective**: Reduce queue-reset hook duration from 16.5s (current) to <3s per scenario

---

## Executive Summary

The acceptance test suite's queue-reset hook is a critical performance bottleneck, dominating per-scenario runtime. Current implementation (subscription delete-recreate) runs at 16.5s per scenario. Previous optimization attempt (eb67ce0) achieved 6.74s via drain-based strategy, then 6-thread parallelism (cf0a161) targeted 3-4s. Investigation revealed a better, O(1) approach: the **Pub/Sub Seek API** bulk-purges the entire retained backlog in a single RPC per queue, independent of message volume. This replaces the linear pull/ack drain loop and is expected to take queue-reset well below 3s, comfortably beating the success criterion.

---

## Current Baseline (Before Any Changes)

| Metric | Value | Notes |
|--------|-------|-------|
| queue-reset hook | 16.5s per scenario | Delete-recreate subscription strategy |
| Full HH suite (20 scenarios) | ~330s (~5.5 min) | queue-reset dominates total time |
| Strategy | Recreate (delete + create) | Ensures clean isolation but slow |
| Thread pool | N/A | Sequential recreation |
| Instrumentation | ✅ Exists | timings.ndjson + Cucumber JSON |

---

## Implemented Changes

### ✅ Commit fdebef1: Revert to Optimized Drain Strategy
- **What**: Removed subscription deletion, restored pull/ack-based drain loop
- **Why**: Drain is faster than recreate; stub reuse within loop eliminates per-call gRPC overhead
- **Impact**: 16.5s → 6.74s (58.2% improvement)
- **Details**:
  - GcpPubSubMessaging.java: Remove recreateTestSubscription(), restore drainSubscription()
  - Drain loop: Pull 1000-message batches, reuse SubscriberStub across loop iterations
  - Tests: QueueClientTest, GcpPubSubMessagingTest updated for drain-only path

### ✅ Commit cf0a161: Optimize Queue Reset Parallelism
- **What**: Thread pool increased from hardcoded 3 to RESET_QUEUES.length (6)
- **Why**: 6 queues + 3 threads = 2 serial waves; 6 threads = 1 parallel wave
- **Impact**: Expected 6.74s → 3-4s (50-60% additional improvement)
- **Change**: Single-line modification in QueueClient.drainQueuesInParallel()
  ```java
  ExecutorService executor = Executors.newFixedThreadPool(RESET_QUEUES.length);
  ```
- **Queues affected**: RM.Field, RM.FieldDLQ, Field.refusals, Field.other, Outcome.Preprocessing, Outcome.PreprocessingDLQ

### ✅ Uncommitted: Pub/Sub Seek-Based Purge (Primary Solution)
- **What**: Replaced the linear pull/ack drain loop with the Pub/Sub **Seek API**, which marks the entire retained backlog as acknowledged in a single RPC per queue (O(1), independent of message count).
- **Why**: Drain time previously scaled linearly with backlog size (N pull+ack RTTs). Seek collapses this to 1 RTT per queue, eliminating message volume from the critical path.
- **Impact**: Expected queue-reset to drop well below 3s (dominated by residual drain + listener HTTP calls + channel setup, not volume).
- **Details** (GcpPubSubMessaging.java):
  - `drainSubscription()` calls `seekPurge()` → issues a single `subscriber.seekCallable().call()` seeking to `now + 60s` (constant `SEEK_PURGE_FUTURE_MILLIS`).
  - Followed by `drainResidualMessages()` — a bounded pull/ack pass (max `MAX_RESIDUAL_DRAIN_BATCHES=50` × 1000 messages) to clear stragglers, since seek is **eventually consistent** (docs warn of up to ~1 minute for full effect).
  - Fail-safe: if `seekCallable()` throws (e.g. missing `pubsub.subscriptions.seek` IAM permission), falls back to the original `drainByPullLoop()` — no behaviour or coverage regression.
  - Verified in the pinned client (google-cloud-pubsub 1.150.2) via `SubscriberStub.seekCallable()` (returns `UnaryCallable<SeekRequest, SeekResponse>`).
- **References**: https://cloud.google.com/pubsub/docs/replay-overview ("Replay and purge messages with seek") and https://cloud.google.com/pubsub/docs/reference/rest/v1/projects.subscriptions/seek.
- **Caveats**:
  - Timestamp seek requires subscription message retention (default **on**, 7 days) — confirm once on the `acceptance-tests-*` subscriptions.
  - IAM needs `pubsub.subscriptions.seek` (included in `pubsub.editor`); fallback keeps tests safe if absent.
  - Works on DLQ subscriptions; seek on the test subscription does not touch service subscriptions.
- **Tests**: All 6 affected unit tests pass (GcpPubSubMessagingTest 5, QueueClientTest 1). Full suite: 19 run, 1 pre-existing `RunCucumberTest` error (requires live services — unrelated).

---

## Planned Future Optimizations (Phase 3+)

### 3. Gateway Event Monitor Stub Reuse (Phase 3)
**Status**: ✅ Already Implemented (verified during seek investigation — `GcpGatewayEventMonitor` already creates a long-lived `subscriberStub` in `enableEventMonitor()` and reuses it across pull/ack calls)  
**Objective**: Reduce gateway-event-monitor-enable hook time  
**Change**: Long-lived SubscriberStub in GcpGatewayEventMonitor, reused across multiple pull() calls  
**Measurable**: Yes - shows in Cucumber JSON hook timing  
**Risk**: Low - background thread, no hot-path changes  

### 4. Shared Stub/Channel Optimization (Phase 4)
**Status**: Recommended Follow-Up  
**Objective**: Reuse a single gRPC channel/stub across all Pub/Sub operations  
**Change**: Introduce a singleton `SubscriberStub` cache in `GcpPubSubMessaging.GooglePubSubOperations`; gRPC stubs/channels are thread-safe and built for concurrent use, so one shared stub can serve all 6 drain threads plus pull()/acknowledge()/release() and eliminate the per-queue + per-call channel construction  
**Expected benefit**: Removes the remaining per-queue channel setup during reset and per-call channel churn across every getMessage in the suite; this is now the primary remaining variable cost after seek  
**Measurable**: Medium - requires NDJSON parsing (not yet automated)  
**Risk**: Medium - connection lifecycle management  
**Blocker**: No automated NDJSON analyzer exists yet  

### 5. Remove returnImmediately Flag (Phase 5)
**Status**: ❌ Cancelled / Do Not Implement  
**Objective**: ~~Evaluate batch-pull efficiency vs deprecated API pattern~~  
**Change**: ~~Set returnImmediately=false in pull requests~~  
**Outcome**: Investigation found this is **counter-productive**. With long-polling (returnImmediately=false), the synchronous pull blocks until the RPC deadline when no messages are present. The drain's empty-tail check (`pull() → empty → stop`) would stall for up to the RPC deadline on every near-empty queue, and assertion-timing behaviour would change. `returnImmediately=true` is deprecated but is the correct choice here.  
**Risk**: High - changes assertion timing behavior  
**Blocker**: Not applicable - dropped from scope.  

---

## Measurement Strategy

### Primary Metric: Cucumber JSON Hook Timing
**File**: `target/performance-investigation/cucumber.json`  
**Tool**: `./scripts/analyse-cucumber-timings.py`  
**What we measure**:
- `queue-reset` hook total duration (sum of all drain operations + orchestration)
- Per-scenario average
- Percentile distribution (50th, 75th, 95th)

**Expected visibility**:
- Commit fdebef1: Should show queue-reset at ~6-7 seconds
- Commit cf0a161: Should show queue-reset at ~3-4 seconds (if parallelism fully effective)
- Seek-based purge (uncommitted): Should show queue-reset at **<3 seconds**, independent of backlog size

### Secondary Metric: Operation-Level Timings
**File**: `target/performance-investigation/timings.ndjson`  
**Format**: Line-delimited JSON, one record per operation
**Sample record**:
```json
{"operationName": "queue-reset-drain-RM.Field", "durationMs": 450, "startTime": "2026-09-04T12:30:15Z"}
```

**Limitations**:
- No automated analyzer yet (requires custom parsing)
- Useful for post-hoc analysis of bottleneck queues
- Future work: Build NDJSON aggregator script

### Verification: Unit Tests
**Files**:
- QueueClientTest.java (1 test)
- GcpPubSubMessagingTest.java (5 tests)

**Current status**: ✅ All pass after fdebef1 + cf0a161 + seek-based purge

---

## Execution Plan

### Phase 1: ✅ Complete - Revert to Drain Strategy
- [x] Revert subscription recreation logic
- [x] Restore concurrent drain with 3-thread pool
- [x] Update tests for drain operations
- [x] Commit: fdebef1

### Phase 2: ✅ In Progress - Full Parallelism with 6-Thread Pool
- [x] Increase thread pool to RESET_QUEUES.length (6)
- [x] Commit: cf0a161
- [x] Push to cloud build: Triggered, awaiting results
- [ ] **Cloud run in progress** - Analyzing cucumber.json + timings.ndjson
- [ ] Confirm queue-reset timing: expect 3-4 seconds average

### Phase 3: Planned - Gateway Event Monitor Optimization
- [ ] Modify GcpGatewayEventMonitor to create long-lived SubscriberStub
- [ ] Reduce pullMessages() stub creation overhead
- [ ] Measure gateway-event-monitor-enable hook time
- [ ] Commit and cloud test

### Phase 4: Future - Shared Stub Cache (If Phase 3 gains insufficient improvement)
- [ ] Build NDJSON analyzer to identify remaining hot spots
- [ ] Determine if single-stub pooling adds measurable benefit
- [ ] Weigh against connection lifecycle complexity

### Phase 5: ❌ Cancelled - returnImmediately Flag Investigation
- [x] Investigation complete - long-polling is counter-productive for the empty-tail drain check
- [x] Decision: drop from scope (kept returnImmediately=true)

### Phase 6: 🔄 In Progress - Seek-Based Purge (Primary Solution)
- [x] Add `seekPurge()` using `SubscriberStub.seekCallable()` to a future timestamp
- [x] Add bounded `drainResidualMessages()` for seek eventual-consistency stragglers
- [x] Add pull/ack `drainByPullLoop()` fallback if seek is unavailable
- [x] Constants: `SEEK_PURGE_FUTURE_MILLIS=60_000`, `MAX_RESIDUAL_DRAIN_BATCHES=50`
- [x] Compile + unit tests pass (6 affected tests; full suite only pre-existing RunCucumberTest env error)
- [ ] **Cloud run** - verify `pubsub.subscriptions.seek` IAM and message-retention on test subscriptions
- [ ] Confirm queue-reset timing: expect <3 seconds on the `analyse-cucumber-timings.py` report
- [ ] Commit and push to cloud build

---

## Success Criteria

### Primary (Must Have)
- [ ] queue-reset hook duration: **<3.5 seconds per scenario** (minimum 60% improvement over 16.5s baseline)
- [ ] All acceptance tests pass with new strategy
- [ ] Cucumber JSON reports confirm timing improvement
- [ ] Unit tests cover drain-only path (including seek fallback)

### Secondary (Should Have)
- [ ] Total HH suite runtime reduced by >2 minutes (20 scenarios × 6+ seconds saved)
- [ ] No regression in test assertions or coverage
- [ ] Code changes are maintainable and documented

### Tertiary (Nice to Have)
- [ ] NDJSON analyzer tool created for future debugging
- [ ] Per-queue drain timing statistics published
- [ ] ADR documented for future developers

---

## Risk Mitigation

| Risk | Probability | Impact | Mitigation |
|------|------------|--------|-----------|
| Cloud build fails | Low | High | Revert commits, debug locally first |
| Thread pool over-parallelism | Low | Medium | Start with 6, fall back to 4-5 if contention observed |
| Seek not permitted (IAM) | Low | Medium | Fallback to pull/ack drain (automatic on exception) |
| Seek eventual consistency (stragglers) | Medium | Low | Bounded residual drain (50×1000) after seek; adapters paused meanwhile |
| Drain hangs on queue | Low | High | Timeout added in pull loop; fallback to delete-recreate if deadlock |
| Measurement noise (variability) | Medium | Low | Run 3+ iterations, report median + percentiles |
| Regression in test stability | Low | High | Comprehensive unit test suite in place |

---

## Timeline

| Phase | Status | Expected Completion | Blocker |
|-------|--------|-------------------|---------|
| Phase 1 (Drain revert) | ✅ Complete | 2026-09-04 | None |
| Phase 2 (6-thread pool) | ✅ Complete | 2026-09-04 | None |
| Phase 3 (Gateway monitor) | ✅ Complete | 2026-09-04 | Already implemented |
| Phase 4 (Shared stub) | 📅 Planned | 2026-09-06 | NDJSON analyzer |
| Phase 5 (returnImmediately) | ❌ Cancelled | 2026-09-04 | Counter-productive |
| Phase 6 (Seek purge) | 🔄 In Progress | 2026-09-04 (cloud results) | Cloud build / IAM + retention check |

---

## Artifacts & Tracking

### Code Changes
- Branch: FMT-128_performance-investigation
- Commits:
  - fdebef1: Revert to optimized drain strategy with stub reuse (58.2% faster)
  - cf0a161: Optimize queue reset parallelism: use all 6 queues concurrently
- Uncommitted: Seek-based purge (Primary Solution) in GcpPubSubMessaging.java

### Test Results
- Location: `gs://c31-fwmtg-ci-prod-acceptance-test-details/` (Cloud Build artifacts)
- Local cache: `performance-investigation/runs/pool-6-parallel-run1/`
- Key files: cucumber.json, timings.ndjson, output.log

### Documentation
- This plan: `performance-investigation/OPTIMIZATION_PLAN.md`
- Future ADR: `census31-fwmt-docs/.github/adrs/queue-reset-optimization.md`

---

## Appendix: Queue Reset Flow

```
QueueClient.reset() [Hooks: queue-reset orchestration]
  ├─ pauseInboundAdapters() [Hooks: pause adapters]
  ├─ drainQueuesInParallel() [Hooks: queue-reset-drain-* for each queue]
  │  ├─ Thread 1: drain(RM.Field)            → seek(+60s) → residual pull/ack (bounded)
  │  ├─ Thread 2: drain(RM.FieldDLQ)         → seek(+60s) → residual pull/ack (bounded)
  │  ├─ Thread 3: drain(Field.refusals)      → seek(+60s) → residual pull/ack (bounded)
  │  ├─ Thread 4: drain(Field.other)         → seek(+60s) → residual pull/ack (bounded)
  │  ├─ Thread 5: drain(Outcome.Preprocessing) → seek(+60s) → residual pull/ack (bounded)
  │  └─ Thread 6: drain(Outcome.PreprocessingDLQ) → seek(+60s) → residual pull/ack (bounded)
  │  [All 6 threads run concurrently; fallback to full pull/ack drain if seek fails]
  └─ resumeInboundAdapters() [Hooks: resume adapters]
```

**Key optimization**: Phases 1 & 2 move from sequential recreation (one subscription at a time) to parallel drain (all 6 subscriptions at once). Phase 6 replaces the per-message pull/ack loop with a single bulk **seek** RPC per queue (O(1)), so drain time no longer scales with backlog size.

---

## Related Documentation

- **Commit eb67ce0**: Original high-performance implementation (baseline for this work)
- **SEEDING.md**: Queue population strategy and acceptance test setup
- **analyse-cucumber-timings.py**: Cucumber JSON analyzer (detailed hook breakdown)
- **run-baseline-repeats.sh**: Test harness for repeated runs with measurement
- **Pub/Sub Seek/purge docs**: https://cloud.google.com/pubsub/docs/replay-overview and https://cloud.google.com/pubsub/docs/reference/rest/v1/projects.subscriptions/seek

