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

1. `start-infra.sh` — Docker: Postgres, RM/GW Rabbit, Redis  
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
| 1 | `./start-infra.sh` | Postgres + Rabbit (5674/5673) + Redis |
| 2 | `./prepare-local-artifacts.sh` | Build/install integration + FWMT libs (`--force` to rebuild) |
| 3 | `./build-services.sh` | Optional: build boot jars before start |
| 4 | `./start-services.sh --build-missing` | Bootstrap queues + start apps (logs in `scripts/logs/`) |
| 5 | `./run-acceptance-test.sh CreateTestRunner` | Run one Cucumber runner (or `all`) |
| 6 | `./stop-services.sh` | Stop Spring Boot processes |
| 7 | `./drop-infra.sh` | Tear down Docker infra (`--volumes` to wipe Postgres/Redis data) |

`setup-rabbitmq.sh` runs automatically from `start-services.sh` and `run-acceptance-test.sh`.

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
```

Details: [docs/run-acceptance-tests-locally-census31.md](docs/run-acceptance-tests-locally-census31.md).

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
| `setup-rabbitmq.sh` | Declare queues on RM/GW Rabbit management API |
| `run-acceptance-test.sh` | `mvn test` in this repo |
| `install-local-decryption-key.sh` | Restore test PGP key to `~/.fwmt/keys/` from job-service git history |

Runtime artefacts (gitignored): `scripts/logs/`, `scripts/.pids/`, `scripts/.local-artifacts/`.

## Legacy Docker compose (2021 GCR images)

The repo root `docker-compose.yml` is the **old** all-in-one stack (pre-built GCR images). The harness above uses **`scripts/docker-compose-infra.yml`** plus locally built Census 31 services.

```bash
# Legacy path only — not used by scripts/run-all.sh
docker compose up -d
```

For RM end-to-end integration: `docker-compose-rm-integration.yml` (see original census21 docs).

## Related

- Performance tests: `census31-fwmt-performance-tests` — use `./run-jobservice-perf.sh --local` after `./start-services.sh job-service tm-mock`
- Harness formerly in `census31-fwmt-docs/acceptance-tests/` — thin wrappers remain there pointing at `scripts/`
