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

## GCP target manual workflow (interim path to Cloud Build)

Use this when you want local test execution against deployed FWMT services and GCP infrastructure. This does **not** replace the default local harness flow.

### Confirmed operating choices

- End goal: run acceptance tests in Cloud Build against GCP infrastructure.
- Interim local mode: use `kubectl port-forward` for service connectivity.
- TM mock: use remote `fwmtgatewaytmmock` from GKE.
- Database isolation: use a dedicated acceptance DB in the same Cloud SQL instance.

### 1) Authenticate and select context

```bash
gcloud config set project c31-fwmtg-dev
gcloud container clusters get-credentials c31-fwmtg-dev --region=europe-west2 --project=c31-fwmtg-dev
kubectl config current-context
```

### 2) (One-time) create dedicated acceptance DB and SQL user

Choose names and manage secrets with your normal team process. Example names are shown below.

```bash
export FWMT_ACCEPTANCE_DB_NAME=fwmtgateway_acceptance
export FWMT_ACCEPTANCE_DB_USER=fwmtg_acceptance

gcloud sql databases create "$FWMT_ACCEPTANCE_DB_NAME" --instance=c31-fwmtg-dev-postgres
gcloud sql users create "$FWMT_ACCEPTANCE_DB_USER" --instance=c31-fwmtg-dev-postgres --password='<set-secure-password>'
```

### 3) Start local connectivity to GKE services

Open separate terminals (or use your preferred process manager):

```bash
kubectl -n fwmt port-forward svc/job-service 18025:80
kubectl -n fwmt port-forward svc/outcome-service 18030:80
kubectl -n fwmt port-forward svc/csv-service 18060:80
kubectl -n fwmt port-forward svc/fwmtgatewaytmmock 18000:80
```

### 4) Start Cloud SQL Proxy locally

```bash
cloud-sql-proxy --private-ip --port 15432 c31-fwmtg-dev:europe-west2:c31-fwmtg-dev-postgres
```

### 5) Run a local acceptance runner with explicit GCP-target overrides

Run from `census31-fwmt-acceptance-tests` root:

```bash
export FWMT_ACCEPTANCE_DB_NAME=fwmtgateway_acceptance
export FWMT_ACCEPTANCE_DB_USER=fwmtg_acceptance
export FWMT_ACCEPTANCE_DB_PASSWORD='<set-secure-password>'

mvn -B test -Dtest=CreateTestRunner \
  -Dservice.jobservice.url=http://localhost:18025 \
  -Dservice.outcome.url=http://localhost:18030 \
  -Dservice.tm.url=http://localhost:18000 \
  -Dservice.mocktm.url=http://localhost:18000 \
  -Dservice.ccscsv.url=http://localhost:18060/ingestCCSCsvFile \
  -Dservice.cecsv.url=http://localhost:18060/ingestCeCsvFile \
  -Dservice.addresscheckcsv.url=http://localhost:18060/ingestAddressCheckCsvFile \
  -Dservice.addressfileload.url=http://localhost:18060/ingestAddressLookupCsvFile \
  -Dspring.datasource.url="jdbc:postgresql://localhost:15432/${FWMT_ACCEPTANCE_DB_NAME}?currentSchema=fwmtg" \
  -Dspring.datasource.username="${FWMT_ACCEPTANCE_DB_USER}" \
  -Dspring.datasource.password="${FWMT_ACCEPTANCE_DB_PASSWORD}" \
  -Dfwmt.pubsub.project=c31-fwmtg-dev
```

### 6) Verify environment state before/after run

```bash
gcloud pubsub topics list --format='value(name)'
gcloud pubsub subscriptions list --filter='name:acceptance-tests-' --format='value(name)'
kubectl -n fwmt get svc
```

### 7) Clean up

Stop the `kubectl port-forward` and `cloud-sql-proxy` processes in their terminals.

### Cloud Build mapping

This manual workflow is the baseline for Cloud Build automation:

- local process start (`kubectl port-forward`, proxy) -> in-cluster network access in Cloud Build execution environment
- local Maven `-D...` overrides -> pipeline-provided environment variables/args
- dedicated acceptance DB/user and acceptance Pub/Sub subscriptions -> reusable shared test assets for build jobs

## Ports and env

If you run **both** census21 and census31 stacks concurrently, override host ports via env vars to avoid collisions.

### Infrastructure ports (defaults)

| Component | Env override | Default host port |
| --- | --- | --- |
| Postgres | `FWMT_POSTGRES_PORT` | 5432 |
| Redis | `FWMT_REDIS_PORT` | 6379 |
| Pub/Sub emulator | `FWMT_PUBSUB_EMULATOR_PORT` | 8085 |
| tm-mock | `FWMT_TM_MOCK_PORT` | 8000 |

### Pub/Sub (only messaging path)

| Variable | Default | Purpose |
| --- | --- | --- |
| `FWMT_PUBSUB_EMULATOR_PORT` | `8085` | Host port for the emulator container |
| `FWMT_PUBSUB_PROJECT` | `fwmt-local` | Project id inside the emulator |
| `PUBSUB_EMULATOR_HOST` | *(set by harness)* | For JVM clients on the host: `localhost:8085` |

Harness flags: `--no-setup-pubsub`, `--no-setup-messaging` on `start-services.sh`, `run-acceptance-test.sh`, and `run-all.sh`.

Details, verification commands, and the migration guide:

- [pubsub-emulator-and-migration.md](pubsub-emulator-and-migration.md)

## Optional overrides

```bash
export CENSUS31_FWMT_ROOT=/path/to/census31
export CENSUS31_INTEGRATION_COMMON_ROOT=/path/to/census31
export CENSUS31_INT_COMMON_BACKEND=/path/to/census31-int-common-backend
export FWMT_PUBSUB_EMULATOR_PORT=8085
export FWMT_PUBSUB_PROJECT=fwmt-local
export FWMT_TM_MOCK_PORT=18000
```
