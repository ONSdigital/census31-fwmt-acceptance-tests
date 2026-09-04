# Phase 6 (Seek Experiment) Verdict + Phase 7 (Pipelined Drain)

**Date**: 2026-09-04  
**Branch**: FMT-128_performance-investigation  
**Status**: ✅ Decision made - seek reverted, pipelined pull/ack drain implemented

---

## TL;DR

The Pub/Sub **Seek API** experiment (commits `5648cc8` → `28d6d4a`, cloud build `da874b87`)
**works but is performance-neutral** for the acceptance-test workload: median 4.24s vs the
Phase 2 baseline of 4.25s, with a worse tail (max 10.4s vs ~6.4s). The seek code has been
reverted and replaced with a **pipelined pull/ack drain** that overlaps each queue's
acknowledgements with its next pull, halving per-batch round trips.

## Why seek failed to help

1. **Seek is eventually consistent** (Google docs: up to ~1 minute for full effect).
   Messages already acked by the seek remain deliverable in the window after the RPC.
2. The implementation ran `drainResidualMessages()` **immediately** after seek, so it
   re-pulled the very backlog the seek had just marked acknowledged.
3. Result: **seek + residual drain ≡ linear drain** — identical work, plus one extra RPC
   (and a worse tail when the residual drain hit its batch cap mid-publishing).

This is mathematically expected, not a tuning bug. There is no O(1) purge for this
workload because the tests keep publishing while the reset hook drains.

## What was reverted

- `drainSubscription()` seek path (`seekPurge()`, `drainResidualMessages()`,
  `drainByPullLoop()` fallback)
- Constants `SEEK_PURGE_FUTURE_MILLIS`, `MAX_RESIDUAL_DRAIN_BATCHES`
- Diagnostic logging (`Seek purge succeeded for subscription ...`)

## What replaced it (Phase 7)

`drainByPipelinedPull()` in `GcpPubSubMessaging.GooglePubSubOperations`:

- Pulls batch *k+1* while batch *k* is acked on a single background thread
  (`drain-ack-<queue>`, daemon).
- gRPC stubs are thread-safe, so concurrent pull/ack share one channel per queue drain.
- `awaitAck()` rethrows ack failures into the drain thread (fail-fast).
- `DRAIN_PULL_BATCH_SIZE = 1000` (Pub/Sub API maximum).

### Why not "increase batch size to 5000"?

The Phase 3 seek analysis suggested this, but **Pub/Sub caps pull `maxMessages` at 1000**.
The real lever is RTT count, which pipelining halves.

### Projection

| Queue | Phase 2 mean | Pipelined projection |
|-------|-------------|----------------------|
| RM.Field | ~3.6s | ~1.8-2.0s |
| Others | ~2.0-2.9s | ~1.0-1.5s |
| **queue-reset total** | **4.25s** | **~2.5s** |

## Follow-ups (if pipelining alone is insufficient)

1. **Phase 4 — shared stub cache**: one `SubscriberStub` for all queues/operations,
   eliminating per-queue channel construction + per-call churn in getMessage().
2. **2 pullers per queue** (12-thread pool) — Pub/Sub allows concurrent pulls on one
   subscription; paired with pipelined acks this could roughly double drain throughput
   on the hot RM.Field lane.
3. **NDJSON aggregator** — automated per-queue analysis is still the missing measurement
   tool (plan's standing Phase 4 blocker).

---

**References**: PHASE3_SEEK_ANALYSIS_BUILD_da874b87.md (original analysis),
PHASE3_RESULTS_REGRESSION_BUILD_787a2e5b.md (stub-reuse parity),
https://cloud.google.com/pubsub/docs/replay-overview (seek eventual consistency).