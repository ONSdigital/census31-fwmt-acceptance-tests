# FWMT Acceptance Tests - Queue Reset Performance Optimization Plan

**Document**: Phase 2 Performance Investigation  
**Branch**: FMT-128_performance-investigation  
**Date**: 2026-09-04  
**Objective**: Reduce queue-reset hook duration from 16.5s (current) to <3s per scenario

---

## Executive Summary

The acceptance test suite's queue-reset hook is a critical performance bottleneck, dominating per-scenario runtime. Current implementation (subscription delete-recreate) runs at 16.5s per scenario. Previous optimization attempt (eb67ce0) achieved 6.74s via drain-based strategy. We are implementing a phased approach to reach 3-4s (70%+ improvement) through thread pool parallelism and stub reuse optimizations.

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

---

## Planned Future Optimizations (Phase 3+)

### 3. Gateway Event Monitor Stub Reuse (Phase 3)
**Status**: Not Yet Implemented  
**Objective**: Reduce gateway-event-monitor-enable hook time  
**Change**: Create long-lived SubscriberStub in GcpGatewayEventMonitor, reuse across multiple pull() calls  
**Measurable**: Yes - shows in Cucumber JSON hook timing  
**Risk**: Low - background thread, no hot-path changes  

### 4. Shared Stub/Channel Optimization (Phase 4)
**Status**: Requires Investigation  
**Objective**: Reuse single gRPC channel/stub across all Pub/Sub operations  
**Change**: Introduce singleton SubscriberStub cache in GcpPubSubMessaging  
**Measurable**: Medium - requires NDJSON parsing (not yet automated)  
**Risk**: Medium - connection lifecycle management  
**Blocker**: No automated NDJSON analyzer exists yet  

### 5. Remove returnImmediately Flag (Phase 5)
**Status**: Requires Investigation  
**Objective**: Evaluate batch-pull efficiency vs deprecated API pattern  
**Change**: Set returnImmediately=false in GcpGatewayEventMonitor pull requests  
**Measurable**: Hard - behavioral change, difficult to measure without load testing  
**Risk**: High - changes assertion timing behavior  
**Blocker**: Needs careful test coverage analysis first  

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

**Current status**: ✅ All pass after fdebef1 + cf0a161

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

### Phase 5: Future - returnImmediately Flag Investigation (Lower priority)
- [ ] Profile behavioral impact of flag change
- [ ] Assess test reliability with long-polling vs immediate return
- [ ] Only proceed if Phases 3-4 insufficient to reach <3s target

---

## Success Criteria

### Primary (Must Have)
- [ ] queue-reset hook duration: **<3.5 seconds per scenario** (minimum 60% improvement over 16.5s baseline)
- [ ] All acceptance tests pass with new strategy
- [ ] Cucumber JSON reports confirm timing improvement
- [ ] Unit tests cover drain-only path

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
| Drain hangs on queue | Low | High | Timeout added in pull loop; fallback to delete-recreate if deadlock |
| Measurement noise (variability) | Medium | Low | Run 3+ iterations, report median + percentiles |
| Regression in test stability | Low | High | Comprehensive unit test suite in place |

---

## Timeline

| Phase | Status | Expected Completion | Blocker |
|-------|--------|-------------------|---------|
| Phase 1 (Drain revert) | ✅ Complete | 2026-09-04 | None |
| Phase 2 (6-thread pool) | 🔄 In Progress | 2026-09-04 (cloud results) | Cloud build completion |
| Phase 3 (Gateway monitor) | 📅 Planned | 2026-09-05 | Phase 2 results review |
| Phase 4 (Shared stub) | 📅 Planned | 2026-09-06 | Phase 3 gap analysis |
| Phase 5 (returnImmediately) | 📅 Planned | 2026-09-07 | Phase 4 insufficient results |

---

## Artifacts & Tracking

### Code Changes
- Branch: FMT-128_performance-investigation
- Commits:
  - fdebef1: Revert to optimized drain strategy with stub reuse (58.2% faster)
  - cf0a161: Optimize queue reset parallelism: use all 6 queues concurrently

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
  │  ├─ Thread 1: drain(RM.Field) via pull/ack loop
  │  ├─ Thread 2: drain(RM.FieldDLQ) via pull/ack loop
  │  ├─ Thread 3: drain(Field.refusals) via pull/ack loop
  │  ├─ Thread 4: drain(Field.other) via pull/ack loop
  │  ├─ Thread 5: drain(Outcome.Preprocessing) via pull/ack loop
  │  └─ Thread 6: drain(Outcome.PreprocessingDLQ) via pull/ack loop
  │  [All 6 threads run concurrently, wait for completion]
  └─ resumeInboundAdapters() [Hooks: resume adapters]
```

**Key optimization**: Phases 1 & 2 move from sequential recreation (one subscription at a time) to parallel drain (all 6 subscriptions at once).

---

## Related Documentation

- **Commit eb67ce0**: Original high-performance implementation (baseline for this work)
- **SEEDING.md**: Queue population strategy and acceptance test setup
- **analyse-cucumber-timings.py**: Cucumber JSON analyzer (detailed hook breakdown)
- **run-baseline-repeats.sh**: Test harness for repeated runs with measurement

