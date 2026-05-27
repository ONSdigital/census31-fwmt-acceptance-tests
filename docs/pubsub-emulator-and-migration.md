# Pub/Sub emulator landing zone + RabbitMQ migration guide

This repo boots local infrastructure via:

- `scripts/docker-compose-infra.yml` (Postgres, Redis, RM/GW RabbitMQ, **Pub/Sub emulator**)
- `scripts/start-infra.sh`
- `scripts/setup-messaging.sh` (Rabbit and/or Pub/Sub bootstrap, controlled by `FWMT_MESSAGING`)
- `scripts/start-services.sh` / `scripts/run-acceptance-test.sh`

The goal of this document is to outline:

1. How to introduce a **local Google Pub/Sub emulator** into the existing infra harness as a **non-invasive “landing zone”** (no service refactors required yet).
2. A staged migration plan from **RabbitMQ → Pub/Sub**, including an **abstraction layer** approach that fits the current code layout.

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
| Acceptance tests still fail with Pub/Sub only | Expected today — **services and Cucumber still use Rabbit**. Use `FWMT_MESSAGING=rabbit` until code migration (Stage 1+) |

Further local-run context: [run-acceptance-tests-locally-census31.md](run-acceptance-tests-locally-census31.md) and the repo [README.md](../README.md).

---

## Migration plan (staged)

### Stage 0 — Emulator landing zone (infra only) ✅ harness

Delivered in the acceptance-test harness:

- Pub/Sub emulator runs via `scripts/start-infra.sh`
- Topics/subscriptions created via `scripts/setup-pubsub.sh` / `FWMT_MESSAGING=pubsub|both`
- No FWMT service code changes yet — emulator is opt-in for bootstrap and future adapters

### Stage 1 — Introduce an abstraction layer (per service) 🚧 in progress (local)

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
- `@ConditionalOnProperty(name = "app.messaging.provider", havingValue = "pubsub")` for Pub/Sub adapters (Stage 1 stubs throw `UnsupportedOperationException` until Stage 2)

Keep routing/topic names in config (`app.messaging.destinations.*`), not hard-coded in business code.

#### Stage 1 implementation map (local fedora tree)

| Module | Port(s) | Rabbit adapter | Pub/Sub stub | Consumers |
| --- | --- | --- | --- | --- |
| `census31-fwmt-common` | `MessagingProperties`, `QueueMessagePublisher`, `ExchangeMessagePublisher` | — | — | — |
| `census31-fwmt-job-service` | `RmFieldMessagePublisher` | `RabbitRmFieldMessagePublisher` | `PubSubRmFieldMessagePublisher` | `RmReceiver` / `GWReceiver` still `@RabbitListener` (handler = `GWMessageProcessor`) |
| `census31-fwmt-csv-service` | `GatewayActionPublisher` | `RabbitGatewayActionPublisher` | `PubSubGatewayActionPublisher` | — |
| `census31-fwmt-outcome-service` | `OutcomePreprocessingPublisher` | `RabbitOutcomePreprocessingPublisher` | `PubSubOutcomePreprocessingPublisher` | — |
| `census31-fwmt-fulfillment-event-service` | `RmFieldPausePublisher` | `RabbitRmFieldPausePublisher` | `PubSubRmFieldPausePublisher` | `FulfilmentEventReceiver` still `@RabbitListener` |
| `census31-fwmt-events` | `GatewayEventProducer` (existing) | `RabbitMQGatewayEventProducer` | `PubSubGatewayEventProducer` | — |

Default property (all services above):

```yaml
app:
  messaging:
    provider: rabbit   # rabbit | pubsub
```

Mac git branches named `FMT-10_Introduce_Google_Pub_Sub` were created off each repo’s current branch (ready for a later sync — see Stage 5).

### Stage 2 — Migrate one “lane” end-to-end

Pick a single, low-blast-radius flow and migrate producer+consumer together.

Good early candidates are flows with:

- 1 producer + 1 consumer
- minimal fanout/routing complexity

In this codebase, `Outcome.Preprocessing` is a common candidate because it already behaves like a dedicated queue/exchange/routing-key lane.

### Stage 3 — Iterate lane-by-lane

For each lane:

- map Rabbit queue/exchange names to a topic/subscription scheme (temporary mapping is fine)
- run acceptance tests with `FWMT_MESSAGING=pubsub` for migrated lanes
- keep Rabbit on for the rest

Avoid long-lived “dual publish” unless you absolutely need it (it increases the risk of duplicates).

### Stage 4 — Remove RabbitMQ

When all lanes are migrated:

- remove `rabbit-rm` and `rabbit-gw` from `scripts/docker-compose-infra.yml`
- delete `setup-rabbitmq.sh` and any rabbit-only harness flags
- remove Rabbit dependencies/config from services

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

Harness-only files on fedora not yet on Mac (sync when committing FMT-10 harness work): `docs/pubsub-emulator-and-migration.md`, `scripts/setup-pubsub.sh`, `scripts/setup-messaging.sh`.

---

## Notes specific to this harness

- The harness currently uses **two brokers** (RM + GW) and bootstraps topology via the RabbitMQ **management API** in `scripts/setup-rabbitmq.sh`.
- The acceptance-test runner (`scripts/run-acceptance-test.sh`) forces:
  - `-Dservice.rabbit.port="$RM_RABBIT_PORT"`
  - `-Dspring.rabbitmq.port="$RM_RABBIT_PORT"`
  which means tests currently assume “one Rabbit port”.
  When you start moving consumers to Pub/Sub, expect acceptance tests to need a new configuration path (e.g. `service.pubsub.emulatorHost`) to avoid coupling to Rabbit ports.

