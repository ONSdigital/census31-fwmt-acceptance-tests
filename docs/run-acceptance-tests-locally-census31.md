# Running FWMT acceptance tests locally (Census 2031 / census31)

This is the **census31** variant of the harness under **`census31-fwmt-acceptance-tests/scripts/`** (wrappers remain in `census31-fwmt-docs/acceptance-tests/`). It targets seeded repos `census31-fwmt-*` under `CENSUS31_FWMT_ROOT` (default parent of the acceptance-tests repo). **`prepare-local-maven-artifacts.sh`** builds **`uk.gov.ons.ctp.integration.common:*`** from seeded **`census31-int-common-*`** and **`census31-int-product-reference`** under **`CENSUS31_INTEGRATION_COMMON_ROOT`** (default the same as `CENSUS31_FWMT_ROOT`) — Java 11 copies of the census21 integration repos.

**`census31-int-common-backend`** is the separate Census 2031 monorepo (`ons.census.int.common:*`, Java 25). It is **not** the input to `prepare-local-maven-artifacts.sh` today. `prepare-local-artifacts.sh` fingerprints that monorepo so reruns are not skipped when its POMs change.

The census21 guide (`run-acceptance-tests-locally.md` in the same folder) remains the narrative reference; differences for census31 are called out below.

## Differences vs census21

1. **`prepare-local-fwmt-libs.sh`** installs **`census31-fwmt-parent`** and Maven **`install`** for canonical, common, events, and storage-utils (no Gradle).
2. **Docker Compose** project name is **`census31-fwmt-acceptance-tests`** (`docker-compose-infra.yml` + `start-infra.sh`).
3. **Service jars** are built/started from **`$CENSUS31_FWMT_ROOT/census31-fwmt-…`**.
4. **`prepare-local-maven-artifacts.sh`** reads **`census31-int-common-config`**, **`census31-int-common-service`**, **`census31-int-common-test-framework`**, and **`census31-int-product-reference`** under **`$CENSUS31_INTEGRATION_COMMON_ROOT`** (default **`$CENSUS31_FWMT_ROOT`**), not live **`census-int-*`** checkouts under census21.

## Quick start

**One command** (from `census31-fwmt-acceptance-tests/scripts`):

```bash
cd /home/simon/dev/sourcecode/census31/census31-fwmt-acceptance-tests/scripts
./run-all.sh
# ./run-all.sh all
# ./run-all.sh --force-prepare   # after dependency changes
```

**Step by step** (same directory):

```bash
cd /home/simon/dev/sourcecode/census31/census31-fwmt-acceptance-tests/scripts

./start-infra.sh
./prepare-local-artifacts.sh --force   # first time or after seed changes
./start-services.sh --build-missing
./run-acceptance-test.sh CreateTestRunner
# ./run-acceptance-test.sh all
```

Use **`./stop-services.sh`** then **`./drop-infra.sh`** when finished (from `scripts/`; add `--volumes` to reset Postgres/Redis).

## Ports and env

If you run **both** census21 and census31 stacks concurrently, override host ports via env vars to avoid collisions.

### Infrastructure ports (defaults)

| Component | Env override | Default host port |
| --- | --- | --- |
| Postgres | `FWMT_POSTGRES_PORT` | 5432 |
| Redis | `FWMT_REDIS_PORT` | 6379 |
| RM RabbitMQ AMQP | `FWMT_RM_RABBIT_PORT` | 5674 |
| RM RabbitMQ management | `FWMT_RM_RABBIT_MANAGEMENT_PORT` | 15674 |
| GW RabbitMQ AMQP | `FWMT_GW_RABBIT_PORT` | 5673 |
| GW RabbitMQ management | `FWMT_GW_RABBIT_MANAGEMENT_PORT` | 15673 |
| Pub/Sub emulator | `FWMT_PUBSUB_EMULATOR_PORT` | 8085 |

Services started by `start-services.sh` receive `APP_RABBITMQ_RM_PORT` / `APP_RABBITMQ_GW_PORT` from these values. Acceptance tests use `-Dservice.rabbit.port` / `-Dspring.rabbitmq.port` aligned with `FWMT_RM_RABBIT_PORT`.

### Messaging and Pub/Sub

| Variable | Default | Purpose |
| --- | --- | --- |
| `FWMT_MESSAGING` | `rabbit` | `rabbit` \| `pubsub` \| `both` — see `setup-messaging.sh` |
| `FWMT_PUBSUB_EMULATOR_PORT` | `8085` | Host port for the emulator container |
| `FWMT_PUBSUB_PROJECT` | `fwmt-local` | Project id inside the emulator |
| `PUBSUB_EMULATOR_HOST` | *(set by you)* | For JVM clients on the host: `localhost:8085` |

Harness flags: `--messaging MODE`, `--no-setup-rabbitmq`, `--no-setup-pubsub`, `--no-setup-messaging` on `start-services.sh`, `run-acceptance-test.sh`, and `run-all.sh`.

**Pub/Sub-only bootstrap** (services still use Rabbit until migrated):

```bash
./start-infra.sh
FWMT_MESSAGING=pubsub ./setup-messaging.sh
```

**Rabbit + Pub/Sub** (bridging / migration prep):

```bash
./run-all.sh --messaging both --no-tests
```

Cucumber and gateway services remain **Rabbit-backed** unless you change application code. Do not expect acceptance tests to pass with `FWMT_MESSAGING=pubsub` alone until Stage 1+ of the migration.

Details, verification commands, and the full migration guide:

- [pubsub-emulator-and-migration.md](pubsub-emulator-and-migration.md)

## Optional overrides

```bash
export CENSUS31_FWMT_ROOT=/path/to/census31
export CENSUS31_INTEGRATION_COMMON_ROOT=/path/to/census31
export CENSUS31_INT_COMMON_BACKEND=/path/to/census31-int-common-backend
export FWMT_MESSAGING=rabbit
export FWMT_PUBSUB_EMULATOR_PORT=8085
export FWMT_PUBSUB_PROJECT=fwmt-local
```
