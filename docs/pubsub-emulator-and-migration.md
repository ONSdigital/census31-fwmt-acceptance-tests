# Pub/Sub emulator landing zone + RabbitMQ migration guide

This repo boots local infrastructure via:

- `scripts/docker-compose-infra.yml` (Postgres, Redis, RM/GW RabbitMQ, **Pub/Sub emulator**)
- `scripts/start-infra.sh`
- `scripts/setup-messaging.sh` (Rabbit and/or Pub/Sub bootstrap, controlled by `FWMT_MESSAGING`)
- `scripts/start-services.sh` / `scripts/run-acceptance-test.sh`

The goal of this document is to outline:

1. How to introduce a **local Google Pub/Sub emulator** into the existing infra harness as a **non-invasive “landing zone”** (no service refactors required yet).
2. A staged migration plan from **RabbitMQ → Pub/Sub**, including an **abstraction layer** approach that fits the current code layout.
3. **Stage 4 (`FMT-47_Remove-RabbitMQ`)** — removing RabbitMQ entirely from the FWMT workspace once Pub/Sub parity is proven (see [Stage 4 — Remove RabbitMQ](#stage-4--remove-rabbitmq-fmt-47_remove-rabbitmq)).

---

## What “landing zone” means here

The emulator landing zone is considered complete when:

- The infra compose can optionally run a Pub/Sub emulator container locally.
- A bootstrap step can create required **topics/subscriptions** in the emulator.
- The harness has a toggle to run either RabbitMQ bootstrap or Pub/Sub bootstrap (or both), without breaking existing acceptance-test flows.
- No production code is refactored yet; the emulator is simply available for incremental adoption.

---

## Pub/Sub emulator basics (local dev)

The Pub/Sub emulator is a local process that exposes an API on a TCP port (commonly **8085**).

The standard way to point client libraries at the emulator is the environment variable:

- `PUBSUB_EMULATOR_HOST=host:port` (example: `localhost:8085`)

Unlike RabbitMQ, Pub/Sub does not use queues/exchanges/routing keys; instead it uses:

- **Topics**: where producers publish messages
- **Subscriptions**: how consumers receive messages from a topic

During migration it is normal to use a **temporary mapping** from “Rabbit queue names” to “Pub/Sub topics/subscriptions”, then later simplify once everything runs on Pub/Sub.

---

## Recommended temporary mapping (fastest + lowest risk)

Start with **topic-per-existing-queue-name** (and subscription-per-consumer-group).

Examples (from `scripts/setup-rabbitmq.sh`):

- Topic `RM.Field`
- Topic `GW.Field`
- Topic `Outcome.Preprocessing`
- Topics for DLQs / error queues: `RM.FieldDLQ`, `GW.Permanent.ErrorQ`, `GW.Transient.ErrorQ`, etc.

This is not “ideal Pub/Sub design”, but it is the most migration-friendly because:

- It keeps message routes stable while refactoring code.
- It allows toggling lane-by-lane later.

---

## Local harness (implemented)

The acceptance-test infra landing zone is in place. Services still use RabbitMQ by default; the emulator is ready for incremental migration.

| Piece | File / script |
| --- | --- |
| Emulator container | `scripts/docker-compose-infra.yml` → service `pubsub` |
| Infra startup + wait | `scripts/start-infra.sh` (includes `…-pubsub-1`) |
| Topic/subscription bootstrap | `scripts/setup-pubsub.sh` |
| Messaging toggle | `scripts/setup-messaging.sh` + `FWMT_MESSAGING` |
| Wired into harness | `start-services.sh`, `run-acceptance-test.sh`, `run-all.sh` |

**Bootstrap host mode (standardised):** `setup-pubsub.sh` calls the emulator **HTTP API** on `localhost:8085` (same pattern as `setup-rabbitmq.sh` using curl). No `gcloud` on the host or in `docker exec` is required.

The `pubsub` container image is `gcr.io/google.com/cloudsdktool/google-cloud-cli:emulators` (includes Java; `google/cloud-sdk:slim` exits immediately without a JRE).

**Services on the host** (future Pub/Sub clients) should use:

```bash
export PUBSUB_EMULATOR_HOST=localhost:${FWMT_PUBSUB_EMULATOR_PORT:-8085}
export GOOGLE_CLOUD_PROJECT=fwmt-local   # or FWMT_PUBSUB_PROJECT
```

---

## How to run the Pub/Sub emulator locally

All commands below assume:

```bash
cd census31-fwmt-acceptance-tests/scripts
```

### 1. Start infrastructure (includes emulator)

```bash
./start-infra.sh
```

This starts Postgres, Redis, `rabbit-rm`, `rabbit-gw`, and `pubsub` (Google Cloud SDK image running the Pub/Sub emulator on port **8085**).

### 2. Bootstrap Pub/Sub topics/subscriptions

After infra is up:

```bash
# Pub/Sub only
FWMT_MESSAGING=pubsub ./setup-messaging.sh

# Or Rabbit + Pub/Sub (useful while bridging)
FWMT_MESSAGING=both ./setup-messaging.sh
```

Equivalent via service start / tests:

```bash
./start-services.sh --messaging pubsub --build-missing   # also starts apps (Rabbit-backed today)
./run-all.sh --messaging both --no-tests                 # infra + both bootstraps, no Cucumber
```

### 3. Verify (optional)

List topics via the emulator HTTP API:

```bash
curl -s "http://localhost:8085/v1/projects/fwmt-local/topics" | python3 -m json.tool
```

### 4. Default acceptance flow (unchanged)

Rabbit-only remains the default — no behaviour change unless you set `FWMT_MESSAGING`:

```bash
./run-all.sh
# same as: FWMT_MESSAGING=rabbit ./run-all.sh
```

### Ports (host → container)

| Component | Env override | Default host port | Container port |
| --- | --- | --- | --- |
| Postgres | `FWMT_POSTGRES_PORT` | 5432 | 5432 |
| Redis | `FWMT_REDIS_PORT` | 6379 | 6379 |
| RM RabbitMQ AMQP | `FWMT_RM_RABBIT_PORT` | 5674 | 5672 |
| RM RabbitMQ management | `FWMT_RM_RABBIT_MANAGEMENT_PORT` | 15674 | 15672 |
| GW RabbitMQ AMQP | `FWMT_GW_RABBIT_PORT` | 5673 | 5672 |
| GW RabbitMQ management | `FWMT_GW_RABBIT_MANAGEMENT_PORT` | 15673 | 15672 |
| **Pub/Sub emulator** | `FWMT_PUBSUB_EMULATOR_PORT` | **8085** | **8085** |

If host port **8085** is already in use, set `FWMT_PUBSUB_EMULATOR_PORT` before `start-infra.sh`.

### Environment variables

| Variable | Default | Purpose |
| --- | --- | --- |
| `FWMT_MESSAGING` | `rabbit` | `rabbit` \| `pubsub` \| `both` — which bootstrap scripts run |
| `FWMT_PUBSUB_EMULATOR_PORT` | `8085` | Host port mapped to the emulator |
| `FWMT_PUBSUB_PROJECT` | `fwmt-local` | Emulator project id (namespace only) |
| `FWMT_PUBSUB_CONTAINER` | `census31-fwmt-acceptance-tests-pubsub-1` | Container name for `setup-pubsub.sh` |
| `FWMT_PUBSUB_EMULATOR_HOST` | `localhost:8085` | Used **inside** the emulator container by `setup-pubsub.sh` |
| `PUBSUB_EMULATOR_HOST` | *(unset)* | Set on the **host** when running Java/services against the emulator: `localhost:8085` |
| `FWMT_RM_RABBIT_PORT` | `5674` | RM broker (services/tests) |
| `FWMT_GW_RABBIT_PORT` | same as RM unless set | GW broker |

Harness script flags (override `FWMT_MESSAGING` for one invocation):

- `--messaging rabbit|pubsub|both` on `start-services.sh`, `run-acceptance-test.sh`, `run-all.sh`
- `--no-setup-rabbitmq`, `--no-setup-pubsub`, `--no-setup-messaging`

### Troubleshooting

| Symptom | Likely fix |
| --- | --- |
| `Pub/Sub emulator container not found` | Run `./start-infra.sh` first |
| `pubsub-1 status=exited` / “Java 7+ JRE must be installed” | Recreate infra after upgrading compose: use `gcr.io/google.com/cloudsdktool/google-cloud-cli:emulators` (not `cloud-sdk:slim`). Run `./drop-infra.sh` then `./start-infra.sh` |
| `gcloud pubsub` errors on bootstrap | Wait for `pubsub` container to stay `running`; re-run `setup-messaging.sh` |
| Port 8085 in use | `export FWMT_PUBSUB_EMULATOR_PORT=18085` then recreate infra |
| `curl: (22) ... 404` during Rabbit bootstrap | Often harmless DELETE of a missing queue/exchange on first run; bootstrap should still complete. If the script exits non-zero, check RM management port `15674` is reachable |
| Acceptance tests fail with Pub/Sub (Outcomes / parent case) | Run **one** sequential suite only (no overlapping `run-acceptance-test` jobs). Services must be rebuilt after listener-pause changes (`start-services.sh --replace --build-missing job-service outcome-service`). Use `FWMT_TM_MOCK_PORT=18000`. See [Acceptance tests: Rabbit ↔ Pub/Sub](#acceptance-tests-rabbit--pubsub-switch) |
| PreFlight / tests hang on `localhost:8000` | Another process may own port 8000. Use `FWMT_TM_MOCK_PORT=18000` for tm-mock, job-service, and test runs |
| `Found multiple occurrences of org.json.JSONObject` in test logs | Harmless classpath clash (`org.json:json` vs `jsonassert`’s `android-json`). Fixed by excluding `android-json` from `spring-boot-starter-test` in `pom.xml` |

Further local-run context: [run-acceptance-tests-locally-census31.md](run-acceptance-tests-locally-census31.md) and the repo [README.md](../README.md).

---

## Migration plan (staged)

| Stage | Jira / branch | Status | Summary |
| --- | --- | --- | --- |
| 0 | — | ✅ | Pub/Sub emulator landing zone in harness |
| 1 | `FMT-10_Introduce_Google_Pub_Sub` | ✅ | Per-service messaging abstraction (dual-mode) |
| 2 | FMT-10 | ✅ | `Outcome.Preprocessing` lane end-to-end |
| 3 | FMT-10 | ✅ | Remaining service lanes + acceptance harness |
| 4 | `FMT-47_Remove-RabbitMQ` | in progress (4.1 harness done) | Delete Rabbit infra, deps, toggles — [detail](#stage-4--remove-rabbitmq-fmt-47_remove-rabbitmq) |
| 5 | — | ongoing | Sync fedora ↔ Mac (`cen-mac-cp`) per feature branch |

### Stage 0 — Emulator landing zone (infra only) ✅ harness

Delivered in the acceptance-test harness:

- Pub/Sub emulator runs via `scripts/start-infra.sh`
- Topics/subscriptions created via `scripts/setup-pubsub.sh` / `FWMT_MESSAGING=pubsub|both`
- No FWMT service code changes yet — emulator is opt-in for bootstrap and future adapters

### Stage 1 — Introduce an abstraction layer (per service) ✅ (local)

Current services use RabbitMQ directly via:

- `@RabbitListener(...)` consumers
- `RabbitTemplate.convertAndSend(...)` producers

Goal:

- isolate messaging concerns behind interfaces so that business code stops depending on Rabbit concepts

Suggested pattern inside each service:

- **Ports (interfaces)**: `XxxPublisher`, `XxxConsumer` (or `XxxMessageHandler`)
- **Rabbit adapters**: implement ports using existing Spring AMQP machinery
- **Pub/Sub adapters**: implement ports using Pub/Sub client + emulator config

Use Spring conditions to switch implementations:

- `@ConditionalOnProperty(name = "app.messaging.provider", havingValue = "rabbit", matchIfMissing = true)` for Rabbit adapters
- `@ConditionalOnProperty(name = "app.messaging.provider", havingValue = "pubsub")` for Pub/Sub adapters (implemented per lane in Stages 2–3)

Keep routing/topic names in config (`app.messaging.destinations.*`), not hard-coded in business code.

#### Stage 1 implementation map (local fedora tree)

| Module | Port(s) | Rabbit adapter | Pub/Sub stub | Consumers |
| --- | --- | --- | --- | --- |
| `census31-fwmt-common` | `MessagingProperties`, `QueueMessagePublisher`, `ExchangeMessagePublisher` | — | — | — |
| `census31-fwmt-job-service` | `RmFieldMessagePublisher` | `RabbitRmFieldMessagePublisher` | `PubSubRmFieldMessagePublisher` | Rabbit: `RmReceiver` / `GWReceiver`; Pub/Sub: `JobServicePubSubConfig` (Stage 3) |
| `census31-fwmt-csv-service` | `GatewayActionPublisher` | `RabbitGatewayActionPublisher` | `PubSubGatewayActionPublisher` | publish-only (Stage 3) |
| `census31-fwmt-outcome-service` | `OutcomePreprocessingPublisher` | `RabbitOutcomePreprocessingPublisher` | `PubSubOutcomePreprocessingPublisher` | Rabbit: SMLC; Pub/Sub: inbound adapter (Stage 2) |
| `census31-fwmt-fulfillment-event-service` | `RmFieldPausePublisher` | `RabbitRmFieldPausePublisher` | `PubSubRmFieldPausePublisher` | `FulfilmentEventReceiver` still `@RabbitListener` |
| `census31-fwmt-events` | `GatewayEventProducer` (existing) | `RabbitMQGatewayEventProducer` | `PubSubGatewayEventProducer` | publish-only (Stage 3) |

Default property (all services above):

```yaml
app:
  messaging:
    provider: rabbit   # rabbit | pubsub
```

Mac git branches named `FMT-10_Introduce_Google_Pub_Sub` were created off each repo’s current branch (ready for a later sync — see Stage 5).

### Stage 2 — Migrate one “lane” end-to-end ✅ `Outcome.Preprocessing` (outcome-service)

The first migrated lane is **`Outcome.Preprocessing`** inside **`census31-fwmt-outcome-service`** (same service produces and consumes — HTTP → publish → async preprocess).

| Piece | Rabbit (`app.messaging.provider=rabbit`, default) | Pub/Sub (`app.messaging.provider=pubsub`) |
| --- | --- | --- |
| Publish | `RabbitOutcomePreprocessingPublisher` → GW exchange + routing key | `PubSubOutcomePreprocessingPublisher` → topic `Outcome.Preprocessing` |
| Consume | `SimpleMessageListenerContainer` on queue `Outcome.Preprocessing` | `PubSubInboundChannelAdapter` on subscription `outcome-service-Outcome-Preprocessing` |
| Payload | `Jackson2JsonMessageConverter` + `__TypeId__` | Same JSON + `__TypeId__` attribute via `OutcomePreprocessingJsonCodec` |

**Harness (outcome-service on Pub/Sub for this lane):**

```bash
cd census31-fwmt-acceptance-tests/scripts
./start-infra.sh
FWMT_MESSAGING=pubsub ./setup-messaging.sh   # or both
FWMT_MESSAGING=pubsub ./start-services.sh --build-missing outcome-service
# or explicit override:
FWMT_OUTCOME_MESSAGING_PROVIDER=pubsub ./start-services.sh outcome-service
```

Requires `PUBSUB_EMULATOR_HOST=localhost:8085` (set automatically by `local-test-env.sh` when provider is `pubsub`).

**Caveats (historical — superseded on Pub/Sub by Stage 3+):**

- Gateway outcomes, DLQ republish, feedback → `RM.Field`, and acceptance inject/assert all work on Pub/Sub when `app.messaging.provider=pubsub` — see [Additional lanes converted](#additional-lanes-converted-after-stage-3).

### Stage 3 — Remaining service lanes ✅ (local fedora)

Migrated lanes (topic name = former Rabbit queue/exchange where noted):

| Lane | Service | Publish (pubsub) | Consume (pubsub) | Subscription |
| --- | --- | --- | --- | --- |
| `RM.Field` | job-service | `PubSubRmFieldMessagePublisher` | `JobServicePubSubConfig` (RM) | `job-service-RM-Field` |
| `GW.Field` | job-service | — (tests / external inject) | `JobServicePubSubConfig` (GW) | `job-service-GW-Field` |
| `RM.Field` pause | fulfilment-event-service | `PubSubRmFieldPausePublisher` | — | — |
| `Gateway.Actions.Exchange` | csv-service | `PubSubGatewayActionPublisher` | — (no FWMT consumer) | — |
| `Gateway.Events.Exchange` | events (lib) | `PubSubGatewayEventProducer` | — | — |
| `Outcome.Preprocessing` | outcome-service | (Stage 2) | (Stage 2) | `outcome-service-Outcome-Preprocessing` |

Shared codec: `FieldWorkerInstructionJsonCodec` in `census31-fwmt-common` (`__TypeId__` + `timestamp` attributes, aligned with job-service Rabbit JSON).

**Harness (per-service overrides, or all via `FWMT_MESSAGING=pubsub`):**

```bash
cd census31-fwmt-acceptance-tests/scripts
./start-infra.sh
FWMT_MESSAGING=pubsub ./setup-messaging.sh
FWMT_MESSAGING=pubsub ./start-services.sh --build-missing job-service
# or per service:
FWMT_JOB_MESSAGING_PROVIDER=pubsub ./start-services.sh job-service
FWMT_CSV_MESSAGING_PROVIDER=pubsub ./start-services.sh csv-service
```

### Additional lanes converted (after Stage 3)

These work when `app.messaging.provider=pubsub` (local fedora, May 2026):

| Lane | Service | Notes |
| --- | --- | --- |
| **Fulfilment pause events** | `census31-fwmt-fulfillment-event-service` | `FulfilmentPausePubSubConfig` on topic `fulfilment-event-service-events`; filters `routingKey == event.fulfilment.request`. Rabbit `FulfilmentEventReceiver` gated with `@ConditionalOnProperty(rabbit)`. |
| **Outcome gateway outcomes** | `census31-fwmt-outcome-service` | `GatewayOutcomeProducer` publishes to topic `events` with routing-key attributes; maps `event.respondent.refusal` → `Field.refusals`, other gateway keys → `Field.other`. |
| **Outcome preprocessing DLQ** | `census31-fwmt-outcome-service` | `OutcomeProcessPreprocessingDlq` pulls `outcome-service-Outcome-PreprocessingDLQ`, republishes to `Outcome.Preprocessing`. |
| **Job GW error / retry** | `census31-fwmt-job-service` | `MessageExceptionHandler` republishes to `GW.Transient.ErrorQ` / `GW.Permanent.ErrorQ` on Pub/Sub failure paths. |
| **Outcome feedback → RM.Field** | `census31-fwmt-outcome-service` | `RmFieldRepublishProducer` publishes feedback follow-ups to topic `RM.Field` via `FieldWorkerInstructionJsonCodec`; `FeedbackQueueConfig` (Rabbit template) is rabbit-only. |
| **Acceptance inject/assert** | `census31-fwmt-acceptance-tests` | `MessagingTestClient`, `DelegatingMessagingTestClient`, `PubSubEmulatorMessaging`; test subscriptions in `setup-pubsub.sh`. |
| **Acceptance gateway events** | `census31-fwmt-acceptance-tests` | `AcceptanceGatewayEventMonitor` / `PubSubGatewayEventMonitor` on `acceptance-tests-Gateway-Events`. |
| **Outcomes acceptance** | `census31-fwmt-acceptance-tests` | Six `Outcomes*` runners on Pub/Sub — `pullMessageWithEventType` on `Field.refusals` / `Field.other`; `QueueClient.reset()` pauses Pub/Sub listeners and drains `job-service-RM-Field` / `outcome-service-Outcome-Preprocessing` between scenarios. |

### Still Rabbit-only (dual-mode scaffolding) {#still-rabbit-only-blocks-full-suite}

**Functional parity:** all 11 Cucumber runners pass on Pub/Sub (May 2026). No acceptance scenario is blocked by missing Pub/Sub lanes.

**What remains on Rabbit today** is **dual-mode scaffolding**, not missing features:

- `@ConditionalOnProperty` Rabbit adapters and `@RabbitListener` consumers still ship alongside Pub/Sub beans.
- `FWMT_MESSAGING=rabbit` is still the harness default; Rabbit brokers still start in `docker-compose-infra.yml`.
- `spring-boot-starter-amqp` remains on service and acceptance-test POMs.

Stage 4 (`FMT-47_Remove-RabbitMQ`) deletes this scaffolding — see [below](#stage-4--remove-rabbitmq-fmt-47_remove-rabbitmq).

Avoid long-lived “dual publish” unless you absolutely need it (it increases the risk of duplicates).

---

## Acceptance tests: Rabbit ↔ Pub/Sub switch

This section documents the **test harness** switch. Service lanes are listed in [Additional lanes converted](#additional-lanes-converted-after-stage-3) and [Still Rabbit-only](#still-rabbit-only-blocks-full-suite) above.

Goal: Cucumber can run against the Pub/Sub emulator the same way it runs against Rabbit today, switchable via one harness flag.

### Current state (what works today)

| Layer | Rabbit | Pub/Sub |
| --- | --- | --- |
| Infra bootstrap | `setup-rabbitmq.sh` | `setup-pubsub.sh` |
| `FWMT_MESSAGING` | `rabbit` (default), `pubsub`, `both` | same |
| Service JVM (`start-services.sh`) | default `app.messaging.provider=rabbit` | `APP_MESSAGING_PROVIDER=pubsub` + `PUBSUB_EMULATOR_HOST` when `FWMT_MESSAGING=pubsub` or per-service overrides (`FWMT_JOB_MESSAGING_PROVIDER`, `FWMT_OUTCOME_MESSAGING_PROVIDER`, `FWMT_CSV_MESSAGING_PROVIDER`) |
| **Cucumber / Maven test JVM** | full suite (~217 scenarios, `BUILD SUCCESS` on local Rabbit run) | **Full suite ✅** — all 11 runners on Pub/Sub (May 2026) |

**Verified Rabbit run (local):** all 11 Cucumber runners pass with `FWMT_MESSAGING=rabbit` (`Cancel`, `Create`, `Feedback`, six `Outcomes*`, `Resilience`, `Update`). Surefire reports `Tests run: 0` because scenarios run inside JUnit runner classes, not as separate JUnit test methods.

**Verified Pub/Sub MVP + Feedback (local, May 2026):** `CreateTestRunner`, `CancelTestRunner`, `UpdateTestRunner`, `ResilienceTestRunner`, `FeedbackTestRunner` — `BUILD SUCCESS`, 0 failures. Typical command:

```bash
cd census31-fwmt-acceptance-tests/scripts
FWMT_MESSAGING=pubsub FWMT_TM_MOCK_PORT=18000 ./setup-messaging.sh
FWMT_MESSAGING=pubsub FWMT_TM_MOCK_PORT=18000 ./start-services.sh --no-setup-messaging job-service outcome-service
for r in CreateTestRunner CancelTestRunner UpdateTestRunner ResilienceTestRunner FeedbackTestRunner; do
  FWMT_MESSAGING=pubsub FWMT_TM_MOCK_PORT=18000 ./run-acceptance-test.sh "$r" || exit 1
done
```

Log examples: `scripts/logs/run-mvp-pubsub-20260528-150727.log`, `scripts/logs/run-feedback-pubsub-6.log`.

**Verified Pub/Sub full Outcomes (local, May 2026):** all six `Outcomes*` runners in one sequential pass — `ALL OUTCOMES RUNNERS PASSED`, exit 0 (~13 min). Log: `scripts/logs/run-outcomes-pubsub-20260528-165229.log`.

```bash
for r in OutcomesHardRefusalTestRunner OutcomesTestRunner OutcomesSwitchRunner \
  OutcomesSwitchCeSIteRunner OutcomesNewAddressReportedRunner OutcomesAddressTypeChangeTestRunner; do
  FWMT_MESSAGING=pubsub FWMT_TM_MOCK_PORT=18000 ./run-acceptance-test.sh "$r" || exit 1
done
```

**Test harness:** `MessagingTestClient` / `AcceptanceGatewayEventMonitor` with `fwmt.messaging.provider` (`rabbit` default, `pubsub` when `FWMT_MESSAGING=pubsub`). `QueueClient.reset()` pauses job/outcome Pub/Sub inbound adapters (same as Rabbit `stopListener`) before draining subscriptions.

### Rules for a sane switch

1. **One messaging provider per run** — every service started for the scenario must use the same `app.messaging.provider` (all `rabbit` or all `pubsub`). Do not mix job on Pub/Sub and outcome on Rabbit in one test run.
2. **Same logical names** — keep topic names aligned with former queue names (`RM.Field`, `Outcome.Preprocessing`, …) via `app.messaging.destinations.*` and `setup-pubsub.sh`.
3. **No dual publish** in services during tests.
4. **`FWMT_MESSAGING=both`** is for bootstrap/debug only (both topologies exist); services should still use a single provider.

### Harness flow (implemented)

```text
FWMT_MESSAGING=rabbit|pubsub
        │
        ├─ setup-messaging.sh     → Rabbit and/or Pub/Sub topology
        ├─ start-services.sh      → JAVA_TOOL_OPTIONS -Dapp.messaging.provider=pubsub (via local-test-env.sh)
        └─ run-acceptance-test.sh
               → -Dfwmt.messaging.provider=rabbit|pubsub
               → -Dfwmt.pubsub.emulatorHost=localhost:8085
               → -Dfwmt.pubsub.project=fwmt-local
               → -Dservice.rabbit.port / -Dspring.rabbitmq.port (still set for mixed bootstrap)
               → MessagingTestClient (Rabbit | Pub/Sub implementation)
```

Additional harness knobs already useful on Rabbit runs:

| Variable | Default | Purpose |
| --- | --- | --- |
| `FWMT_TM_MOCK_PORT` | `8000` | tm-mock / job-service / tests. **Use `18000` if port 8000 is taken** (common on fedora — connections to 8000 can hang with no HTTP response) |
| `FWMT_JOB_MESSAGING_PROVIDER` | inherits `FWMT_MESSAGING` | Override job-service only |
| `FWMT_OUTCOME_MESSAGING_PROVIDER` | inherits `FWMT_MESSAGING` | Override outcome-service only |
| `FWMT_CSV_MESSAGING_PROVIDER` | inherits `FWMT_MESSAGING` | Override csv-service only |

Test JVM properties (passed from `run-acceptance-test.sh` when `FWMT_MESSAGING=pubsub`):

| Property | Example | Purpose |
| --- | --- | --- |
| `fwmt.messaging.provider` | `rabbit` \| `pubsub` | Select test client implementation |
| `fwmt.pubsub.emulatorHost` | `localhost:8085` | Emulator endpoint |
| `fwmt.pubsub.project` | `fwmt-local` | Emulator project id |

### Cucumber messaging (historical vs current)

**Before:** steps used **`QueueClient` → `QueueUtils`** (Rabbit only).

**Now:** `QueueClient` delegates to **`MessagingTestClient`** (`QueueUtils` for Rabbit, `PubSubEmulatorMessaging` for Pub/Sub). Implemented mappings:

| Test operation | Rabbit | Pub/Sub (implemented) |
| --- | --- | --- |
| Inject RM create/cancel/update | `basicPublish` to `RM.Field` | Publish to topic `RM.Field` (`__TypeId__` + `timestamp`) |
| Reset between scenarios | `queuePurge` | Drain acceptance test subscriptions |
| Assert / poll queues | `getMessage` / count | Pull from `acceptance-tests-*` subscriptions |
| Preflight | Rabbit purge + HTTP health | Emulator check + HTTP timeouts on service URLs |
| Gateway events | `GatewayEventMonitor` | `PubSubGatewayEventMonitor` |
| Listener control | HTTP stop Rabbit listeners | Skipped when `fwmt.messaging.provider=pubsub` |

**OutcomeSteps on Pub/Sub:** asserts on `Field.refusals` / `Field.other` via test subscriptions and `pullMessageWithEventType`; gateway events via `PubSubGatewayEventMonitor`.

### Recommended implementation order

#### 1. Test-side messaging abstraction — **done**

- **`MessagingTestClient`** + **`DelegatingMessagingTestClient`** (`rabbit` → `QueueUtils`, `pubsub` → emulator HTTP pull/publish).
- **`QueueClient`** delegates inject/purge/poll to the client; skips Rabbit listener HTTP when `pubsub`.
- **`AcceptanceGatewayEventMonitor`** + **`PubSubGatewayEventMonitor`** (poll `acceptance-tests-Gateway-Events`).
- `run-acceptance-test.sh` passes `fwmt.messaging.provider` and Pub/Sub host/project when `FWMT_MESSAGING=pubsub`.

**Pub/Sub “purge” patterns:**

- Delete and recreate subscription in `@Before` / `setup-pubsub.sh`, or
- Pull-and-ack until empty before each scenario, or
- Dedicated acceptance-only subscriptions (e.g. `acceptance-tests-RM-Field`).

#### 2. Gateway events in tests — **done**

See `AcceptanceGatewayEventMonitor` / `PubSubGatewayEventMonitor` under `src/main/java/.../messaging/`.

#### 3. Test subscriptions in bootstrap — **done**

`setup-pubsub.sh` creates acceptance-only subscriptions (in addition to service subscriptions):

| Logical queue (Rabbit) | Topic | Suggested test subscription |
| --- | --- | --- |
| `RM.Field` | `RM.Field` | `acceptance-tests-RM-Field` |
| `Outcome.Preprocessing` | `Outcome.Preprocessing` | `acceptance-tests-Outcome-Preprocessing` |
| `Field.refusals` | `events` (filter / routing strategy TBD) | `acceptance-tests-Field-refusals` |
| `Field.other` | `events` | `acceptance-tests-Field-other` |
| Gateway events | `Gateway.Events.Exchange` | `acceptance-tests-Gateway-Events` |

Service subscriptions remain as today (`job-service-RM-Field`, `outcome-service-Outcome-Preprocessing`, …). Tests should use **separate** subscriptions so draining does not steal messages from running services.

#### 4. Remaining service lanes (blocks full 217-scenario suite)

Outcomes runners validated on Pub/Sub — see [Verified Pub/Sub full Outcomes](#acceptance-tests-rabbit--pubsub-switch) in the harness section.

#### 5. CI and docs

- **Today (FMT-10):** main CI path may still use `FWMT_MESSAGING=rabbit`; Pub/Sub full suite verified locally.
- **After FMT-47:** CI and local defaults become Pub/Sub-only; Rabbit bootstrap and toggles are removed.

### Minimum viable vs full parity

| Scope | Runners (examples) | Status |
| --- | --- | --- |
| **MVP Pub/Sub acceptance** | `CreateTestRunner`, `CancelTestRunner`, `UpdateTestRunner`, `ResilienceTestRunner` | ✅ Verified local (May 2026) |
| **MVP + Feedback** | Above + `FeedbackTestRunner` (2 scenarios) | ✅ Verified local — outcome `RmFieldRepublishProducer` → `RM.Field` on Pub/Sub |
| **Full parity** | All 11 runners (~217 scenarios) | ✅ Verified local (May 2026) — single `run-all.sh all`: `scripts/logs/run-all-pubsub-20260528-171537.log` (`BUILD SUCCESS`, ~8 min tests) |

### Commands today vs target

**Today (supported):**

```bash
cd census31-fwmt-acceptance-tests/scripts
FWMT_MESSAGING=rabbit ./run-all.sh all
# if port 8000 is busy:
FWMT_MESSAGING=rabbit FWMT_TM_MOCK_PORT=18000 ./run-all.sh all
```

**Pub/Sub MVP (verified):**

```bash
FWMT_MESSAGING=pubsub FWMT_TM_MOCK_PORT=18000 ./run-acceptance-test.sh CreateTestRunner
# … CancelTestRunner, UpdateTestRunner, ResilienceTestRunner
```

**Pub/Sub full suite (verified May 2026):**

```bash
FWMT_MESSAGING=pubsub FWMT_TM_MOCK_PORT=18000 ./run-all.sh all
```

**Manual service debugging on Pub/Sub:**

```bash
./start-infra.sh
FWMT_MESSAGING=pubsub ./setup-messaging.sh
FWMT_MESSAGING=pubsub FWMT_TM_MOCK_PORT=18000 ./start-services.sh --build-missing job-service outcome-service
```

**Manual service debugging on Pub/Sub:**

```bash
./start-infra.sh
FWMT_MESSAGING=pubsub ./setup-messaging.sh
FWMT_MESSAGING=pubsub FWMT_TM_MOCK_PORT=18000 ./start-services.sh --build-missing job-service outcome-service
```

**Target after FMT-47 (no `FWMT_MESSAGING` flag):**

```bash
cd census31-fwmt-acceptance-tests/scripts
./start-infra.sh
./setup-messaging.sh
FWMT_TM_MOCK_PORT=18000 ./start-services.sh --build-missing job-service outcome-service
./run-all.sh all
```

---

### Stage 4 — Remove RabbitMQ (`FMT-47_Remove-RabbitMQ`) {#stage-4--remove-rabbitmq-fmt-47_remove-rabbitmq}

**Status:** in progress — branch `FMT-47_Remove-RabbitMQ` off `FMT-10_Introduce_Google_Pub_Sub` in each affected repo. **Stage 4.1–4.3 done**; **Stage 4.4 (performance-tests) done** on fedora; **4.5+ not started**.

**Prerequisite:** FMT-10 merged or stable on all repos. Pub/Sub functional parity is already proven locally (full acceptance suite + job-service perf on Pub/Sub). FMT-47 is **deletion and simplification**, not new Pub/Sub features.

#### Goal

- No RabbitMQ containers, bootstrap scripts, or AMQP dependencies in FWMT workspace projects.
- Services, acceptance tests, and performance tests use **Pub/Sub only** (local emulator in dev; real GCP Pub/Sub in deployed environments is a separate cutover).
- Remove `FWMT_MESSAGING`, `app.messaging.provider=rabbit`, and all `@ConditionalOnProperty` dual-mode branching.

#### Branch strategy

On each affected repo:

```bash
git checkout FMT-10_Introduce_Google_Pub_Sub
git pull
git checkout -b FMT-47_Remove-RabbitMQ
```

Open coordinated PRs per repo; align artifact versions via `census31-fwmt-parent` as for FMT-10.

#### Repos in scope

| Repo | Rabbit removal scope |
| --- | --- |
| `census31-fwmt-acceptance-tests` | Infra compose, harness scripts, test client collapse |
| `census31-fwmt-common` | Simplify `MessagingProperties` (drop `rabbit` provider) |
| `census31-fwmt-job-service` | Largest — listeners, config, controllers, error handler |
| `census31-fwmt-outcome-service` | Queue configs, DLQ, feedback, Rabbit controllers |
| `census31-fwmt-csv-service` | `RabbitMQConfig`, health indicator, Rabbit publisher |
| `census31-fwmt-events` | `GatewayRabbitConfig`, `RabbitMQGatewayEventProducer` |
| `census31-fwmt-fulfillment-event-service` | `RabbitMqConfig`, `FulfilmentEventReceiver` |
| `census31-fwmt-performance-tests` | Drop `RabbitPublisher`, `pika`, rabbit preflight |
| `census31-fwmt-parent` | Remove AMQP from BOM / dependency management if centralized |

Repos with **no messaging** (`tm-mock`, `canonical`, `storage-utils`, etc.) need no FMT-47 branch unless parent POM changes force a version bump.

**Out of scope (unless explicitly added):** `census31-int-common-test-framework` (shared ONS `spring-rabbit` helpers); GCP/K8s deployment manifests outside these repos; production GCP Pub/Sub cutover (FMT-47 targets **local/dev workspace** Rabbit removal).

#### Implementation order

```text
1. Infra harness     → drop Rabbit compose + setup-rabbitmq.sh
2. job-service       → then outcome-service (acceptance suite depends on both)
3. csv + events + fulfilment-event-service
4. Acceptance tests  → Pub/Sub-only MessagingTestClient
5. Performance tests → Pub/Sub-only publisher + run-jobservice-perf.sh
6. Docs + CI         → defaults, troubleshooting, pipeline snippets
7. Verify            → run-all.sh + perf smoke (no FWMT_MESSAGING)
```

#### 4.1 Local infra harness (`census31-fwmt-acceptance-tests`) — **done**

| Action | Target | Status |
| --- | --- | --- |
| Remove `rabbit-rm`, `rabbit-gw` | `scripts/docker-compose-infra.yml` | done |
| Remove Rabbit from wait list | `scripts/start-infra.sh` | done |
| Delete Rabbit bootstrap | `scripts/setup-rabbitmq.sh` | done (deleted) |
| Pub/Sub-only bootstrap | `scripts/setup-messaging.sh` (drop `rabbit` / `both`) | done |
| Remove messaging toggle | `start-services.sh`, `run-all.sh`, `run-acceptance-test.sh`, `local-test-env.sh` | done |
| Remove Rabbit JVM props | `run-acceptance-test.sh` (`-Dservice.rabbit.port`, etc.) | done |
| Retire or rewrite legacy stacks | `docker-compose.yml`, `docker-compose-rm-integration.yml` | **removed** (FMT-47 4.3) |
| Simplify preflight | `PreFlightCheck` — emulator reachability only | done |

**Keep:** `pubsub` service, `setup-pubsub.sh`, `PUBSUB_EMULATOR_HOST`, `FWMT_PUBSUB_*`.

#### 4.2 Service Java (six services + events lib) — **done** (fedora)

Pattern per service: **delete Rabbit adapters and config**, **remove `@ConditionalOnProperty`**, make Pub/Sub beans unconditional, **drop `spring-boot-starter-amqp`**.

| Repo | Status |
| --- | --- |
| `census31-fwmt-common` | done — `PROVIDER_RABBIT` removed, `spring-amqp` dropped |
| `census31-fwmt-events` | done — Rabbit producer/config/monitor deleted |
| `census31-fwmt-job-service` | done — largest; Pub/Sub-only processors and controllers |
| `census31-fwmt-outcome-service` | done — preprocessing, DLQ, gateway outcomes Pub/Sub-only |
| `census31-fwmt-csv-service` | done — publish-only Pub/Sub |
| `census31-fwmt-fulfillment-event-service` | done — pause events Pub/Sub-only |

**Verification gate (after Mac sync + local install):**

```bash
FWMT_TM_MOCK_PORT=18000 ./run-all.sh all
```

Detail (original plan):

**`census31-fwmt-job-service`**

| Delete / gut | Refactor (shared logic — do not blind-delete) |
| --- | --- |
| `JSRabbitConfig`, `RmReceiver`, `GWReceiver` | `GWMessageProcessor`, `FieldWorkerInstructionMessageDispatcher` — move out of `rabbit/` package |
| `messaging/rabbit/RabbitRmFieldMessagePublisher` | `MessageExceptionHandler` — today autowires `RabbitTemplate`; must be Pub/Sub-only (`PubSubTemplate` → `GW.Transient.ErrorQ` / `GW.Permanent.ErrorQ`) |
| `RabbitQueueController`, `RabbitQueuesHealthIndicator` | `RmListenerController` — keep Pub/Sub adapter stop/start; drop `RabbitListenerEndpointRegistry` |
| `application.yml` `app.rabbitmq.*` | Unit tests: `RabbitTestUtils`, `RabbitQueueControllerTest` |

**`census31-fwmt-outcome-service`**

| Delete | Keep / promote |
| --- | --- |
| `RabbitMqConfig`, Rabbit SMLC in `OutcomePreprocessingQueueConfig` | `OutcomePreprocessingPubSubConfig`, emulator config |
| `RabbitOutcomePreprocessingPublisher`, `FeedbackQueueConfig` | `DlqController` Pub/Sub path |
| `RabbitQueueController`, `QueueMigrator`, `RabbitQueuesHealthIndicator` | Pub/Sub branches in `GatewayOutcomeProducer`, `RmFieldRepublishProducer`, `OutcomeProcessPreprocessingDlq` (already exist — remove Rabbit branches) |

**`census31-fwmt-csv-service`:** delete `RabbitMQConfig`, `QueueConfig`, `RabbitGatewayActionPublisher`, `RabbitQueuesHealthIndicator`.

**`census31-fwmt-events`:** delete `GatewayRabbitConfig`, `RabbitMQGatewayEventProducer`, Rabbit `GatewayEventMonitor` if unused after harness cleanup.

**`census31-fwmt-fulfillment-event-service`:** delete `RabbitMqConfig`, `PausePublishQueueConfig`, `FulfilmentEventReceiver`, `RabbitRmFieldPausePublisher`; promote `FulfilmentPausePubSubConfig`.

**`census31-fwmt-common`:** simplify `MessagingProperties` — remove `PROVIDER_RABBIT` / provider toggle; codecs (`FieldWorkerInstructionJsonCodec`, etc.) stay.

#### 4.3 Acceptance tests — **done**

Collapse dual client to Pub/Sub only:

| Remove | Keep / rename |
| --- | --- |
| `QueueUtils.java` | `PubSubEmulatorMessaging` as sole `MessagingTestClient` |
| `DelegatingMessagingTestClient` | Direct `PubSubEmulatorMessaging` `@Component` |
| `spring-boot-starter-amqp` in `pom.xml` | Emulator HTTP client |
| Rabbit properties / `fwmt.messaging.provider` toggle | Pub/Sub drain + adapter pause (`job-service-RM-Field`, `outcome-service-Outcome-Preprocessing`) |
| Legacy `docker-compose.yml` stacks | `scripts/docker-compose-infra.yml` only |

**Verification gate:**

```bash
FWMT_TM_MOCK_PORT=18000 ./run-all.sh all   # no FWMT_MESSAGING
```

#### 4.4 Performance tests (`census31-fwmt-performance-tests`) — **done**

FMT-10 added dual `publisher.py` (`RabbitPublisher` + `PubSubPublisher`). FMT-47:

- Deleted `RabbitPublisher`, `rabbit_connection_parameters()`, `FWMT_MESSAGING`, `--messaging rabbit`, rabbit preflight/purge in `run-jobservice-perf.sh`.
- Removed `pika` from `Pipfile` / README legacy Docker Rabbit stack.
- Default perf run publishes to topic `RM.Field` via emulator HTTP API.

Locust (`fwmtg-locust/load_test.py`) is HTTP-only — docs updated only.

#### 4.5 Dependencies

Remove `spring-boot-starter-amqp` from: job-service, outcome-service, csv-service, events, fulfillment-event-service, acceptance-tests.

Ensure `spring-cloud-gcp-pubsub` (or existing emulator wiring) is present wherever AMQP is removed.

#### 4.6 Risks and gotchas

1. **Shared code under `rabbit/` packages** — business processors are not Rabbit-only; plan package rename, not mass deletion.
2. **`MessageExceptionHandler`** — fails Pub/Sub-only boot until `RabbitTemplate` autowire is removed.
3. **RM vs GW brokers** — already mapped to separate Pub/Sub topics in `setup-pubsub.sh`; no new topology design needed.
4. **Test listener pause** — `RmListenerController` / `DlqController` Pub/Sub paths must remain for scenario isolation.
5. **Coordinated multi-repo PRs** — same Mac sync flow as FMT-10 Stage 5.

#### Definition of done (FMT-47)

- [ ] No `rabbit-rm` / `rabbit-gw` in `docker-compose-infra.yml`
- [ ] No `setup-rabbitmq.sh`; no `FWMT_MESSAGING` or `app.messaging.provider=rabbit` in FWMT code/scripts
- [ ] No `spring-boot-starter-amqp` in FWMT service POMs
- [ ] `./run-all.sh all` green on Pub/Sub only (no messaging flag)
- [ ] `census31-fwmt-performance-tests/Python/run-jobservice-perf.sh --local --count 10 --scenario create` green without Rabbit
- [ ] This document updated: Stage 4 marked complete; Rabbit toggle sections archived or removed

#### Rough effort

| Area | Estimate |
| --- | --- |
| Infra + harness scripts | 1–2 days |
| job-service + outcome-service | 3–5 days |
| csv + events + fulfilment | 1–2 days |
| Acceptance test collapse | 2–3 days |
| Perf tests + docs | 0.5–1 day |
| CI / multi-repo PRs | 1–2 days |
| **Total** | ~2–3 weeks (one developer, multi-repo) |

Much less than FMT-10 because behaviour already exists on Pub/Sub.

---

### Stage 5 — Sync local work to Mac (`~/dev/sourcecode/census31`)

After Stage 1+ changes are committed locally (fedora / Cursor tree), align the Mac clones **without** mixing unrelated work:

1. On each Mac repo, checkout the prepared branch:
   ```bash
   cd ~/dev/sourcecode/census31/<repo>
   git checkout FMT-10_Introduce_Google_Pub_Sub
   ```
2. Copy or push commits from the machine where Stage 1 was developed (fedora → Mac via your usual flow: `git push` from a clone, `cen-mac-cp`, or patch — **only when you explicitly choose to sync**).
3. Verify on Mac:
   ```bash
   mvn -q -DskipTests compile   # per service / common
   ```
4. Push Mac branches and open PRs per repo (`FMT-10` / Pub/Sub).

Repos with `FMT-10_Introduce_Google_Pub_Sub` branch created on Mac (off current branch at creation time):

- `census31-fwmt-acceptance-tests` (was on `FMT-10_Introducing-Google-PubSub`)
- `census31-fwmt-common`, `census31-fwmt-job-service`, `census31-fwmt-csv-service`, `census31-fwmt-events`, `census31-fwmt-outcome-service`, `census31-fwmt-fulfillment-event-service`, `census31-fwmt-canonical`, `census31-fwmt-tm-mock`, `census31-fwmt-storage-utils`, `census31-fwmt-gateway-version-tracker`, `census31-fwmt-perf-msg-builder`

Harness files to keep in sync when committing FMT-10 work: `docs/pubsub-emulator-and-migration.md`, `scripts/setup-pubsub.sh`, `scripts/setup-messaging.sh`, `pom.xml` (android-json exclusion).

For **FMT-47**, repeat the same sync pattern on branch `FMT-47_Remove-RabbitMQ` (branched off `FMT-10_Introduce_Google_Pub_Sub`) — see [macos-integration/commands-and-skills.md](../../census31-fwmt-docs/macos-integration/commands-and-skills.md) (`cen-mac`, `cen-mac-cp`).

---

## Notes specific to this harness

- **FMT-47 (harness):** `scripts/docker-compose-infra.yml` runs Postgres, Redis, and the Pub/Sub emulator only; `./run-all.sh` bootstraps Pub/Sub and starts services with `app.messaging.provider=pubsub`. See [Stage 4](#stage-4--remove-rabbitmq-fmt-47_remove-rabbitmq).
- The acceptance-test runner (`scripts/run-acceptance-test.sh`) passes:
  - `-Dfwmt.pubsub.emulatorHost`, `-Dfwmt.pubsub.project`
  - `-Dservice.tm.url` / `-Dservice.mocktm.url` (aligned with `FWMT_TM_MOCK_PORT`)
- Prefer `FWMT_TM_MOCK_PORT=18000` when host port 8000 is occupied; see [Troubleshooting](#troubleshooting).
- `install-local-decryption-key.sh` accepts an existing `~/.fwmt/keys/decryption.private` without requiring a git clone of `census31-fwmt-job-service` (useful on fedora working copies).

