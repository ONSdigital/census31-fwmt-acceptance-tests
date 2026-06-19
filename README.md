> **THIS REPO IS SEEDED FROM 2021 CODE AND AS SUCH CURRENTLY NEEDS MODERNISATION!** (see also [SEEDING.md](SEEDING.md).)

# census31-fwmt-acceptance-tests

Cucumber acceptance tests for the FWMT gateway. Local setup and service orchestration live under **`scripts/`** (moved from `census31-fwmt-docs/acceptance-tests`).

## One-command quick start

From the **`scripts/`** directory (first time or after dependency changes, add `--force-prepare`):

```bash
cd census31-fwmt-acceptance-tests/scripts
FWMT_TM_MOCK_PORT=18000 ./run-all.sh all
```

That runs, in order:

1. `start-infra.sh` — Docker: Postgres, Redis, Pub/Sub emulator (8085)
2. `prepare-local-artifacts.sh` — local Maven FWMT libs
3. `start-services.sh --build-missing` — tm-mock, job-service, outcome-service (Pub/Sub)
4. `run-acceptance-test.sh` — Cucumber via Maven

Start stack only (no Cucumber):

```bash
./run-all.sh --no-tests
```

## Typical flow (step by step)

| Step | Command | Purpose |
|------|---------|---------|
| 1 | `./start-infra.sh` | Postgres + Redis + Pub/Sub emulator (8085) |
| 2 | `./prepare-local-artifacts.sh` | Build/install integration + FWMT libs (`--force` to rebuild) |
| 3 | `./build-services.sh` | Optional: build boot jars before start |
| 4 | `./start-services.sh --build-missing` | Bootstrap Pub/Sub + start apps (logs in `scripts/logs/`) |
| 5 | `./run-acceptance-test.sh CreateTestRunner` | Run one Cucumber runner (or `all`) |
| 6 | `./stop-services.sh` | Stop Spring Boot processes |
| 7 | `./drop-infra.sh` | Tear down Docker infra (`--volumes` to wipe Postgres/Redis data) |

`setup-messaging.sh` runs automatically from `start-services.sh` and `run-acceptance-test.sh` (Pub/Sub bootstrap only).

### Prerequisites

- **Docker** or **Podman** with compose support for infra
- On **macOS with Podman only**:
  1. `podman machine init && podman machine start` (once)
  2. `brew install podman-compose` — Podman's built-in `podman compose` needs this (or `docker-compose`) as a provider
- Java **25** for services and Maven builds (see `local-test-env.sh`)
- Bash **3.2+** (macOS system bash is fine; scripts avoid Bash 4-only features)
- Maven (`mvn`) for acceptance tests and several services
- job-service PGP test key: run `./install-local-decryption-key.sh` once (or let `start-services.sh` install from git commit `e695484`); see `census31-fwmt-job-service/docs/gitguardian-pgp-private-key.md`

### Environment overrides

```bash
export CENSUS31_FWMT_ROOT=/path/to/census31
export FWMT_RUNTIME=podman          # or docker; auto-detects a working runtime by default
export FWMT_TM_MOCK_PORT=18000      # when host port 8000 is in use
export FWMT_PUBSUB_EMULATOR_PORT=8085
export FWMT_PUBSUB_PROJECT=fwmt-local
export FWMT_LOG_DIR=/path/to/logs
```

Details: [docs/run-acceptance-tests-locally-census31.md](docs/run-acceptance-tests-locally-census31.md).

## Pub/Sub emulator (local)

| Item | Default |
| --- | --- |
| Host port | `8085` (`FWMT_PUBSUB_EMULATOR_PORT`) |
| Emulator project id | `fwmt-local` (`FWMT_PUBSUB_PROJECT`) |
| Client env (host JVM) | `PUBSUB_EMULATOR_HOST=localhost:8085` |

Migration history and topology reference:

- [docs/pubsub-emulator-and-migration.md](docs/pubsub-emulator-and-migration.md)

## Scripts reference

| Script | Role |
|--------|------|
| `run-all.sh` | Full flow (infra → prepare → services → tests) |
| `local-test-env.sh` | Shared paths, Java, ports (sourced by others) |
| `start-infra.sh` | Postgres + Redis + Pub/Sub emulator via compose (Docker or Podman) |
| `drop-infra.sh` | Tear down compose infra |
| `apply-podman-runtime-support.sh` | One-shot migration for older checkouts (usually not needed) |
| `prepare-local-artifacts.sh` | Cached wrapper for Maven local installs |
| `prepare-local-maven-artifacts.sh` | `census31-int-*` integration JARs |
| `prepare-local-fwmt-libs.sh` | parent BOM + common, events, canonical, storage-utils → `~/.m2` |
| `build-service.sh` / `build-services.sh` | Build service boot jars |
| `start-services.sh` | Start tm-mock, job-service, outcome-service |
| `stop-services.sh` / `restart-service.sh` | Stop or restart services |
| `setup-messaging.sh` | Bootstrap Pub/Sub topics/subscriptions |
| `setup-pubsub.sh` | Create topics/subscriptions in Pub/Sub emulator |
| `run-acceptance-test.sh` | `mvn test` in this repo |
| `install-local-decryption-key.sh` | Restore test PGP key to `~/.fwmt/keys/` from job-service git history |
| `prepare-job-service-db.sh` | Liquibase migrate `fwmtg` tables in local Postgres (auto-run before job-service) |

Runtime artefacts (gitignored): `scripts/logs/`, `scripts/.pids/`, `scripts/.local-artifacts/`.

## Related

- Performance tests: `census31-fwmt-performance-tests` — `./run-jobservice-perf.sh --local` after `./start-services.sh job-service tm-mock`
- Harness formerly in `census31-fwmt-docs/acceptance-tests/` — thin wrappers remain there pointing at `scripts/`
