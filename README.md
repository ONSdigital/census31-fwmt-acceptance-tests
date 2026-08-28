> **THIS REPO IS SEEDED FROM 2021 CODE AND AS SUCH CURRENTLY NEEDS MODERNISATION!** (see also [SEEDING.md](SEEDING.md).)
trigger no change
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

## Manual local-against-GCP workflow (phase 1)

This repo still defaults to local infra (`run-all.sh`). As a stepping stone to Cloud Build execution against GCP, use a manual runbook that keeps test execution local but points at deployed GKE services and Cloud SQL.

- Connectivity: `kubectl port-forward` to `job-service`, `outcome-service`, `csv-service`, and remote `fwmtgatewaytmmock`
- DB: dedicated acceptance database in the same Cloud SQL instance (`c31-fwmtg-dev-postgres`) via local Cloud SQL Proxy
- Messaging: target real Pub/Sub project (`c31-fwmtg-dev`) with dedicated acceptance subscriptions

Before running in this mode, ensure:

- `gcloud auth login` and `gcloud auth application-default login` are complete (ADC required by Java GCP clients)
- GKE context is set to cluster `c31-fwmtg-dev` in region `europe-west2`
- Namespace assumption is `fwmt`

Shared-environment guardrails:

- Keep `fwmt.pubsub.allowServiceSubscriptionDrain=false` (default) so tests do not drain service subscriptions
- Use `acceptance-tests-*` subscriptions for assertions and queue draining
- Use a dedicated acceptance DB/user, not shared `fwmtgateway`

Two GCP-target runbooks are available:

| Guide | DB strategy | Network requirement |
|-------|-------------|---------------------|
| [run-acceptance-tests-against-existing-gcp.md](docs/run-acceptance-tests-against-existing-gcp.md) | Existing `fwmtgateway` DB via pod sidecar port-forward | No VPN needed — tunnels through existing GKE pod |
| [run-acceptance-tests-locally-census31.md](docs/run-acceptance-tests-locally-census31.md#gcp-target-manual-workflow-interim-path-to-cloud-build) | Dedicated `fwmtgateway_acceptance` DB | Requires VPC route or bastion to Cloud SQL private IP |

Cloud Build remains the target state; this manual flow is the validation baseline before full CI wiring. The mapping of local commands to Cloud Build execution is documented in [docs/run-acceptance-tests-locally-census31.md](docs/run-acceptance-tests-locally-census31.md#cloud-build-target-mapping-step-6-wrap-up).

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
| `prepare-local-fwmt-libs.sh` | parent BOM + common, events, canonical, storage-utils -> `$HOME/.m2` |
| `build-service.sh` / `build-services.sh` | Build service boot jars |
| `start-services.sh` | Start tm-mock, job-service, outcome-service |
| `stop-services.sh` / `restart-service.sh` | Stop or restart services |
| `setup-messaging.sh` | Bootstrap Pub/Sub topics/subscriptions |
| `setup-pubsub.sh` | Create topics/subscriptions in Pub/Sub emulator |
| `run-acceptance-test.sh` | `mvn test` in this repo |
| `install-local-decryption-key.sh` | Restore test PGP key to `$HOME/.fwmt/keys/` from job-service git history |
| `prepare-job-service-db.sh` | Liquibase migrate `fwmtg` tables in local Postgres (auto-run before job-service) |

Runtime artefacts (gitignored): `scripts/logs/`, `scripts/.pids/`, `scripts/.local-artifacts/`.

## Related

- Performance tests: `census31-fwmt-performance-tests` — `./run-jobservice-perf.sh --local` after `./start-services.sh job-service tm-mock`
- Harness formerly in `census31-fwmt-docs/acceptance-tests/` — thin wrappers remain there pointing at `scripts/`


## Docker-compose

The `docker-compose.yml` file in this repo will spin up all 4 FWMT services as well as fully set up postgres DB and pubsub emulator. This is useful for local development and testing.

There are a few prerequisite steps before the `docker-compose` can be run. These are:

1. Make sure you're authenticated with gcloud and have the correct project set:
   ```
    gcloud auth login
    gcloud auth configure-docker europe-west2-docker.pkg.dev
   ```
2. Setup GPG key
   - Generate a dummy pgp private key, you can use all the default values, but give it a recognisable name and email address:
     ```
     gpg --full-generate-key
     ```
   - Find your key id:
     ```
     gpg --list-secret-keys --keyid-format LONG
     ```
   - Look for the key with the matching name and email address and then look for the line that looks like `rsa4096/<YOUR_KEY_ID> 2026-08-11 [SC]`, copy `<YOUR_KEY_ID>` to the clipboard.
   
   - Make a directory in your home directory called `.fwmt` with a subdirectory called `keys`:
     ```
     mkdir -p ~/.fwmt/keys
     ```
   - Export your private key to a file in the keys directory:
     ```
     gpg --export-secret-keys <YOUR_KEY_ID> > ~/.fwmt/keys/decryption.private
     ```
3. Give your Podman Machine more memory and CPUs, the default is 2GB and 2 CPUs which is not enough to run all the services. You can do this by running:
   ```
   podman machine stop
   podman machine set --cpus 4 --memory 8192
   podman machine start
   ```
   
There is a script in the `scripts` directory called `publish-test-message.sh` which will allow you to publish messages to the various queues.
Messages should be unencoded JSON as found in the default acceptance test fixtures, the pubsub message shell and attributes will be added by the script. See examples below.
It will autogenerate a caseID if none is provided.

Currently, it is designed to work with the HH_CREATE and CE_CREATE messages, but other message types may work if supplied as raw JSON.

Here are some examples:

### Clear database (this can be used in conjunction with other commands to reset the database before publishing messages)
```
./publish-test-message.sh -c
```
### Use default HH or CE Create fixtures found in the acceptance tests resources directory (with a randomly generated caseId)
```
./publish-test-message.sh -t HH_CREATE
./publish-test-message.sh -t CE_CREATE
```
### Use alternate fixture file
```
./publish-test-message.sh -t HH_CREATE -f hhCreate.json
./publish-test-message.sh -t HH_CREATE -f ./fixtures/my-hh.json

./publish-test-message.sh -t CE_CREATE -f ceEstabCreate.json
./publish-test-message.sh -t CE_CREATE -f /tmp/custom-message.json
```
### Raw JSON (script auto-adds attributes)
```
./publish-test-message.sh -m '{
        "actionInstruction": "CREATE",
        "surveyName": "CENSUS",
        "addressType": "HH",
        "caseRef": "12345678",
        "fieldOfficerId": "SH-TWH1-ZJ-05",
        "fieldCoordinatorId": "SH-TWH1-ZJ",
        "organisationName": "",
        "uprn": "6031151",
        "estabUprn": "6123456",
        "addressLine1": "123 Main Street",
        "addressLine2": "Apartment 4",
        "addressLine3": "District",
        "townName": "London",
        "postcode": "E14 9LB",
        "oa": "E00167164",
        "latitude": 51.497421,
        "longitude": -0.0222139,
        "ce1Complete": false,
        "handDeliver": false,
        "ceExpectedCapacity": null,
        "ceActualResponses": 0,
        "undeliveredAsAddress": false,
        "blankFormReturned": false,
        "secureEstablishment": false,
        "addressLevel": "U"
    }'
```
### Show usage
```
./publish-test-message.sh -h
```

### Other topics
There is a `-T` flag to specify a different pubsub topic (rather than the default `RM.Field`) however this is untested.
   
