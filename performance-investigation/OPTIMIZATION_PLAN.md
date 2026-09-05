# FWMT Acceptance Tests - Queue Reset Performance Optimization Plan

**Document**: Phase 2 Performance Investigation  
**Branch**: FMT-128_performance-investigation  
**Date**: 2026-09-04  
**Objective**: Reduce queue-reset hook duration from 16.5s (current) to <3s per scenario

---

## Executive Summary

The acceptance test suite's queue-reset hook is a critical performance bottleneck, dominating per-scenario runtime. Current implementation (subscription delete-recreate) runs at 16.5s per scenario. Previous optimization attempt (eb67ce0) achieved 6.74s via drain-based strategy, then 6-thread parallelism (cf0a161) achieved 4.25s. The **Pub/Sub Seek API** experiment (5648cc8 → da874b87) proved seek works but is **performance-neutral** here: seek is eventually consistent, so the residual drain re-pulls the same backlog (4.24s median — parity, worse tail). Seek was reverted. The current approach pipelines pull/ack within each drain loop to halve per-batch round trips, targeting <3.5s.

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

### ✅ Commits 5648cc8 → 28d6d4a: Pub/Sub Seek-Based Purge (Experiment — Reverted)
- **What**: Replaced the linear pull/ack drain loop with the Pub/Sub **Seek API** (single `seekCallable()` RPC per queue to a future timestamp, plus bounded residual drain).
- **Why (hypothesis)**: Drain time scaled linearly with backlog size (N pull+ack RTTs); seek promised O(1) purge per queue.
- **Cloud result (build da874b87, see `PHASE3_SEEK_ANALYSIS_BUILD_da874b87.md`)**:
  - Seek **worked** (all seeks succeeded, no fallbacks) but **performance-neutral**: median 4.24s vs Phase 2 4.25s; **worse tail** (max 10.4s vs ~6.4s).
  - Root cause: seek is **eventually consistent** — acked messages remain deliverable for up to ~1 minute, so the immediate residual drain re-pulls the same backlog it just purged. Seek + residual drain ≡ linear drain (parity is expected, not a tuning bug).
  - Verdict: **reverted** in favour of attacking the RTT-per-batch cost directly.
- **Details preserved for reference**: `drainSubscription()` → `seekPurge()` (+60s, `SEEK_PURGE_FUTURE_MILLIS`), `drainResidualMessages()` (max `MAX_RESIDUAL_DRAIN_BATCHES=50`), `drainByPullLoop()` fallback on IAM-denied seek.

### ✅ Uncommitted: Pipelined Pull/Ack Drain (Current Strategy)
- **What**: Each queue's drain loop now overlaps the ack of batch *k* with the pull of batch *k+1* (ack on a single background thread; gRPC stubs are thread-safe). Sequential pull-then-ack = 2 RTTs/batch → pipelined = ~1 RTT/batch.
- **Why**: The seek experiment proved there is no O(1) purge for this workload (continuous publishing + eventual consistency); the real cost is per-batch RTT count in the drain loop (RM.Field ≈ 3.6s ≈ dozens of batch cycles).
- **Impact**: Expected queue-reset 4.25s → ~2.5s (RM.Field 3.6s → ~1.8-2s); no API/IAM/retention dependencies.
- **Details** (GcpPubSubMessaging.java): `drainSubscription()` → `drainByPipelinedPull()` with `DRAIN_PULL_BATCH_SIZE=1000` (API max), daemon `drain-ack-<queue>` thread, `awaitAck()` surfaces ack failures. Pull batch size cannot exceed 1000 (API cap) — the "increase to 5000" idea from the seek analysis is invalid.
- **Tests**: All 6 affected unit tests pass (GcpPubSubMessagingTest 5, QueueClientTest 1).

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
**Expected benefit**: Removes the remaining per-queue channel setup during reset and per-call channel churn across every getMessage in the suite; likely the next measurable win after pipelined drain  
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
- Seek experiment (5648cc8+, reverted): parity with Phase 2 (median 4.24s) — expected given seek eventual consistency
- Pipelined drain (current): Should show queue-reset at **~2.5 seconds** (halved per-batch RTTs)

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

**Current status**: ✅ All pass after fdebef1 + cf0a161; seek experiment reverted; pipelined drain implemented (6 affected tests green)

---

## Execution Plan

### Phase 1: ✅ Complete - Revert to Drain Strategy
- [x] Revert subscription recreation logic
- [x] Restore concurrent drain with 3-thread pool
- [x] Update tests for drain operations
- [x] Commit: fdebef1

### Phase 2: ✅ Complete - Full Parallelism with 6-Thread Pool
- [x] Increase thread pool to RESET_QUEUES.length (6)
- [x] Commit: cf0a161
- [x] Cloud run confirmed: median 4.25s (Phase 2 baseline)

### Phase 3: ✅ Complete - Gateway Event Monitor Optimization
- [x] Long-lived SubscriberStub in GcpGatewayEventMonitor (commits d4797db, 25c0ea9)
- [x] Cloud run: parity with Phase 2 (median 4.45s; stub reuse was not the bottleneck)

### Phase 4: Future - Shared Stub Cache (If Phase 3 gains insufficient improvement)
- [ ] Build NDJSON analyzer to identify remaining hot spots
- [ ] Determine if single-stub pooling adds measurable benefit
- [ ] Weigh against connection lifecycle complexity

### Phase 5: ❌ Cancelled - returnImmediately Flag Investigation
- [x] Investigation complete - long-polling is counter-productive for the empty-tail drain check
- [x] Decision: drop from scope (kept returnImmediately=true)

### Phase 6: ❌ Reverted - Seek-Based Purge (Experiment)
- [x] Implemented (commit 5648cc8) + diagnostic logging (6c899af) + analysis (28d6d4a)
- [x] Cloud run da874b87: seek works but performance-neutral (median 4.24s, max 10.4s) — eventual consistency forces residual drain to re-pull the purged backlog
- [x] Decision: revert (code removed) — parity is mathematically expected for seek + immediate residual drain

### Phase 7: 🔄 In Progress - Pipelined Pull/Ack Drain
- [x] Overlap ack(batch k) with pull(batch k+1) on a single background thread per queue
- [x] Remove seek code paths and constants; `DRAIN_PULL_BATCH_SIZE=1000` (API max)
- [x] Compile + unit tests pass (8 affected tests: GcpPubSubMessagingTest 7, QueueClientTest 1)
- [x] **Phase 7b**: parallel pause/resume (2 concurrent listener HTTP calls, was sequential)
- [x] **Phase 7c**: RM.Field gets 3 pullers (default 1; configurable via `pullerParallelismFor` on subscription prefix); each puller pipelines its acks on shared ack threads — projected RM.Field 3.6s → ~1.2-1.8s
- [x] **Phase 7d**: StreamingPull prototype behind `fwmt.pubsub.streaming-pull.enabled` (default false). `drainSubscription()` switches to a persistent streaming-pull drain (single bidirectional stream, acks on the same stream) when enabled; any stream failure falls back to the pipelined-pull path. Unit-tested on/off + fallback.
- [ ] Cloud run: confirm queue-reset median ~2.5s on `analyse-cucumber-timings.py` + `analyse-ndjson-timings.py`
- [ ] A/B test: run with flag off (baseline) then flag on (StreamingPull prototype) and compare with the NDJSON analyzer
- [ ] If insufficient: apply Phase 4 (shared stub) and/or 2 pullers per queue (12-thread pool)

---

## StreamingPull A/B Test Plan (Phase 7d prototype)

### 1. Baseline run (flag OFF - current production path)
- **Build**: commit `2bf6eb4`, property `fwmt.pubsub.streaming-pull.enabled=false` (default)
- **What it validates**: multi-puller pipelined pull (RM.Field = 3 pullers), parallel pause/resume
- **Expected**: queue-reset median ~4.4s → ~2.5s (Phase 7 projection)
- **Artifacts**: `gs://c31-fwmtg-ci-prod-acceptance-test-details/<build>/run1-hh/performance-investigation/timings.ndjson`

### 2. StreamingPull run (flag ON)
- **Same commit** `2bf6eb4`, property `fwmt.pubsub.streaming-pull.enabled=true`
- **What it validates**: streaming-pull drain with pipelined-pull fallback on failure
- **Expected**: queue-reset median < baseline if StreamingPull is effective
- **Artifacts**: same bucket, different build ID

### 3. Analysis & decision
```bash
# Per run:
python3 scripts/analyse-ndjson-timings.py <build-a>/.../timings.ndjson <build-b>/.../timings.ndjson
# Compare (queue-reset mean/p50/p95/max, RM.Field per-queue mean/max, scenario failures)
```
| Metric | Baseline (flag off) | StreamingPull (flag on) | Outcome |
|--------|---------------------|-------------------------|---------|
| queue-reset median | tbd | tbd | target < 3.5s |
| RM.Field drain mean | tbd | tbd | target < ~1.8s |
| queue-reset p95 | tbd | tbd | lower variance desired |
| scenario failures | tbd | tbd | must stay 0 |

### 4. Go / No-Go
- **GO** (keep flag on by default): StreamingPull median < pipelined median by > noise (σ≈880ms → require ≥ ~500ms gain) AND zero scenario failures AND fallback never observed in logs
- **NO-GO** (default stays OFF / revert prototype): no gain, worse tail, or any stream errors forcing fallback — the pipelined-pull path remains the production drain
- Rollback is always a one-line property flip (`fwmt.pubsub.streaming-pull.enabled=false`); no code change required

### 5. Preconditions & risks
- Confirm `pubsub.subscriptions.consume` on the test subscriptions (already used by pull; StreamPull uses the same permission)
- Two consecutive empty responses end the stream-drain; a queue that keeps receiving during the hook still drains correctly (non-empty keeps it alive)
- Noise: run each config **3×** per the plan's measurement strategy, report median + percentiles (not single runs)

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
| Pipelined ack reordering (out-of-order acks) | Low | Low | Acks are idempotent per ack-id; messages re-delivered only if deadline (10s) exceeded |
| Ack thread failure masked until next batch | Low | Medium | `awaitAck()` rethrows into drain thread; drain fails fast |
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
| Phase 6 (Seek purge) | ❌ Reverted | 2026-09-04 | Performance-neutral (eventual consistency) |
| Phase 7 (Pipelined drain) | 🔄 In Progress | 2026-09-04 (cloud results) | Cloud build |

---

## Artifacts & Tracking

### Code Changes
- Branch: FMT-128_performance-investigation
- Commits:
  - fdebef1: Revert to optimized drain strategy with stub reuse (58.2% faster)
  - cf0a161: Optimize queue reset parallelism: use all 6 queues concurrently
  - 5648cc8: Seek-based purge experiment (REVERTED in working tree)
  - 6c899af: Diagnostic logging for seek analysis (REVERTED in working tree)
  - 28d6d4a: Phase 3 Seek API analysis doc (kept for reference)
- Uncommitted: Pipelined pull/ack drain (Phase 7) in GcpPubSubMessaging.java

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
  │  ├─ Thread 1: drain(RM.Field)             → pipelined pull/ack (ack k ∥ pull k+1)
  │  ├─ Thread 2: drain(RM.FieldDLQ)          → pipelined pull/ack (ack k ∥ pull k+1)
  │  ├─ Thread 3: drain(Field.refusals)       → pipelined pull/ack (ack k ∥ pull k+1)
  │  ├─ Thread 4: drain(Field.other)          → pipelined pull/ack (ack k ∥ pull k+1)
  │  ├─ Thread 5: drain(Outcome.Preprocessing) → pipelined pull/ack (ack k ∥ pull k+1)
  │  └─ Thread 6: drain(Outcome.PreprocessingDLQ) → pipelined pull/ack (ack k ∥ pull k+1)
  │  [All 6 queues concurrent; each uses 1 background ack thread → ~1 RTT/batch instead of 2]
  └─ resumeInboundAdapters() [Hooks: resume adapters]
```

**Key optimization**: Phases 1 & 2 move from sequential recreation (one subscription at a time) to parallel drain (all 6 subscriptions at once). Phase 7 overlaps each queue's acks with its next pull, halving per-batch round trips. (Phase 6 seek experiment — single O(1) purge RPC — was reverted: eventual consistency made the residual drain re-pull the same backlog, so it was exactly as fast as a linear drain with a worse tail.)

---

## Related Documentation

- **Commit eb67ce0**: Original high-performance implementation (baseline for this work)
- **SEEDING.md**: Queue population strategy and acceptance test setup
- **analyse-cucumber-timings.py**: Cucumber JSON analyzer (detailed hook breakdown)
- **run-baseline-repeats.sh**: Test harness for repeated runs with measurement
- **Pub/Sub Seek/purge docs**: https://cloud.google.com/pubsub/docs/replay-overview and https://cloud.google.com/pubsub/docs/reference/rest/v1/projects.subscriptions/seek

