> **THIS REPO IS SEEDED FROM 2021 CODE AND AS SUCH CURRENTLY NEEDS MODERNISATION!** (see also [SEEDING.md](SEEDING.md).)

# census31-fwmt-acceptance-tests

Cucumber acceptance tests for the FWMT gateway. Local setup and service orchestration live under **`scripts/`** (moved from `census31-fwmt-docs/acceptance-tests`).

## One-command quick start

From the **`scripts/`** directory (first time or after dependency changes, add `--force-prepare`):

```bash
cd census31-fwmt-acceptance-tests/scripts
./run-all.sh
```

That runs, in order:

1. `start-infra.sh` — Docker: Postgres, RM/GW Rabbit, Redis, Pub/Sub emulator (8085)  
2. `prepare-local-artifacts.sh` — local Maven FWMT libs  
3. `start-services.sh --build-missing` — tm-mock, job-service, outcome-service  
4. `run-acceptance-test.sh CreateTestRunner` — Cucumber via Maven  

Run the full suite:

```bash
./run-all.sh all
```

Start stack only (no Cucumber):

```bash
./run-all.sh --no-tests
```

## Typical flow (step by step)

| Step | Command | Purpose |
|------|---------|---------|
| 1 | `./start-infra.sh` | Postgres + Rabbit (5674/5673) + Redis + Pub/Sub emulator (8085) |
| 2 | `./prepare-local-artifacts.sh` | Build/install integration + FWMT libs (`--force` to rebuild) |
| 3 | `./build-services.sh` | Optional: build boot jars before start |
| 4 | `./start-services.sh --build-missing` | Bootstrap queues + start apps (logs in `scripts/logs/`) |
| 5 | `./run-acceptance-test.sh CreateTestRunner` | Run one Cucumber runner (or `all`) |
| 6 | `./stop-services.sh` | Stop Spring Boot processes |
| 7 | `./drop-infra.sh` | Tear down Docker infra (`--volumes` to wipe Postgres/Redis data) |

`setup-messaging.sh` runs automatically from `start-services.sh` and `run-acceptance-test.sh` (default: RabbitMQ only; see `FWMT_MESSAGING` below).

### Prerequisites

- Docker (for infra)
- Java **25** for services and Maven builds (see `local-test-env.sh`)
- Bash **3.2+** (macOS system bash is fine; scripts avoid Bash 4-only features)
- Maven (`mvn`) for acceptance tests and several services
- job-service PGP test key: run `./install-local-decryption-key.sh` once (or let `start-services.sh` install from git commit `e695484`); see `census31-fwmt-job-service/docs/gitguardian-pgp-private-key.md`

### Environment overrides

```bash
export CENSUS31_FWMT_ROOT=/path/to/census31
export FWMT_RM_RABBIT_PORT=5674
export FWMT_LOG_DIR=/path/to/logs
export FWMT_MESSAGING=rabbit          # rabbit | pubsub | both
export FWMT_PUBSUB_EMULATOR_PORT=8085
```

Details: [docs/run-acceptance-tests-locally-census31.md](docs/run-acceptance-tests-locally-census31.md).

## Pub/Sub emulator (local landing zone)

A **Google Pub/Sub emulator** runs alongside RabbitMQ in `scripts/docker-compose-infra.yml`. FWMT services still default to RabbitMQ; the emulator is for bootstrap and future migration.

| Item | Default |
| --- | --- |
| Host port | `8085` (`FWMT_PUBSUB_EMULATOR_PORT`) |
| Emulator project id | `fwmt-local` (`FWMT_PUBSUB_PROJECT`) |
| Client env (host JVM) | `PUBSUB_EMULATOR_HOST=localhost:8085` |

**Bootstrap Pub/Sub only** (after `./start-infra.sh`):

```bash
cd scripts
FWMT_MESSAGING=pubsub ./setup-messaging.sh
# or: ./start-services.sh --messaging pubsub
# or: ./run-all.sh --messaging both --no-tests
```

**Messaging mode** (`FWMT_MESSAGING` or `--messaging`):

| Mode | Bootstrap |
| --- | --- |
| `rabbit` (default) | `setup-rabbitmq.sh` |
| `pubsub` | `setup-pubsub.sh` |
| `both` | Rabbit + Pub/Sub |

Full ports, env vars, troubleshooting, and the **RabbitMQ → Pub/Sub migration plan**:

- [docs/pubsub-emulator-and-migration.md](docs/pubsub-emulator-and-migration.md)

## Scripts reference

| Script | Role |
|--------|------|
| `run-all.sh` | Full flow (infra → prepare → services → tests) |
| `local-test-env.sh` | Shared paths, Java, ports (sourced by others) |
| `start-infra.sh` | `docker compose -f docker-compose-infra.yml up -d` |
| `drop-infra.sh` | `docker compose -f docker-compose-infra.yml down` |
| `prepare-local-artifacts.sh` | Cached wrapper for Maven local installs |
| `prepare-local-maven-artifacts.sh` | `census31-int-*` integration JARs |
| `prepare-local-fwmt-libs.sh` | parent BOM + common, events, canonical, storage-utils → `~/.m2` |
| `build-service.sh` / `build-services.sh` | Build service boot jars |
| `start-services.sh` | Start tm-mock, job-service, outcome-service |
| `stop-services.sh` / `restart-service.sh` | Stop or restart services |
| `setup-messaging.sh` | Bootstrap Rabbit and/or Pub/Sub per `FWMT_MESSAGING` |
| `setup-pubsub.sh` | Create topics/subscriptions in Pub/Sub emulator |
| `run-acceptance-test.sh` | `mvn test` in this repo |
| `install-local-decryption-key.sh` | Restore test PGP key to `~/.fwmt/keys/` from job-service git history |
| `prepare-job-service-db.sh` | Liquibase migrate `fwmtg` tables in local Postgres (auto-run before job-service) |

Runtime artefacts (gitignored): `scripts/logs/`, `scripts/.pids/`, `scripts/.local-artifacts/`.

Local infra uses **`scripts/docker-compose-infra.yml`** (Postgres, Redis, Pub/Sub emulator) plus locally built Census 31 services — see `scripts/run-all.sh`.

## Related

- Performance tests: `census31-fwmt-performance-tests` — use `./run-jobservice-perf.sh --local` after `./start-services.sh job-service tm-mock`
- Harness formerly in `census31-fwmt-docs/acceptance-tests/` — thin wrappers remain there pointing at `scripts/`
