# Running acceptance tests against existing GCP services (no new DB required)

This guide is the **simplest path** to running acceptance tests locally against the deployed FWMT services in `c31-fwmtg-dev`. It uses the **existing `fwmtgateway` database** that the running services are already connected to — no new database, no new SQL users, no service reconfiguration required.

> **Why this guide exists:** The dedicated-acceptance-DB approach (described in `run-acceptance-tests-locally-census31.md`) requires VPC connectivity from your laptop to the private Cloud SQL IP, which is not available without a VPN or bastion. This guide avoids that problem by tunnelling through a **Cloud SQL proxy sidecar already running inside a GKE pod**.

## How it works

Each FWMT service pod (e.g. `job-service`) runs a Cloud SQL proxy sidecar container on port `5432` **inside the pod**. You can `kubectl port-forward` directly to that pod's sidecar port — no VPN, no local `cloud-sql-proxy` install needed.

```
Your laptop -> kubectl port-forward -> pod:5432 -> Cloud SQL proxy sidecar -> Cloud SQL (private IP)
```

## Prerequisites

- Logged into GCP and GKE context configured:

```bash
gcloud auth login
gcloud auth application-default login
gcloud config set project c31-fwmtg-dev
gcloud container clusters get-credentials c31-fwmtg-dev --region=europe-west2 --project=c31-fwmtg-dev
```

- `kubectl` installed
- `jq` installed (for decoding secrets)
- Maven (`mvn`) installed
- Acceptance test dependencies prepared (`./prepare-local-artifacts.sh` — only needed once or after changes)

## Step 1: Get DB credentials from the K8s secret

```bash
# Decode username and password from the db-credentials secret
kubectl -n fwmt get secret db-credentials -o json \
  | jq -r '.data | to_entries[] | "\(.key)=\(.value|@base64d)"'
```

Export for use in later steps:

```bash
export FWMT_GCP_DB_USER=<username from above>
export FWMT_GCP_DB_PASSWORD=<password from above>
```

Get the DB name from the configmap:

```bash
kubectl -n fwmt get configmap db-config -o jsonpath='{.data.db-name}'
# Expected: fwmtgateway
export FWMT_GCP_DB_NAME=fwmtgateway
```

## Step 2: Port-forward to the Cloud SQL proxy sidecar

Find a running `job-service` pod:

```bash
kubectl -n fwmt get pods -l app=job-service
```

Port-forward to the Cloud SQL proxy sidecar in that pod (port 5432 inside the pod -> local port 15432):

```bash
kubectl -n fwmt port-forward pod/<job-service-pod-name> 15432:5432
```

Keep this terminal open.

Helper: if you want a one-liner that auto-selects the first ready pod:

```bash
kubectl -n fwmt port-forward \
  "$(kubectl -n fwmt get pods -l app=job-service -o jsonpath='{.items[0].metadata.name}')" \
  15432:5432
```

Verify the tunnel is working:

```bash
nc -vz localhost 15432
```

## Step 3: Port-forward to GKE services

In a separate terminal (or use the helper script):

```bash
cd /Users/Simon.Diaz/dev/sourcecode/census31/census31-fwmt-acceptance-tests/scripts
./start-gcp-port-forwards.sh
```

Or manually in separate terminals:

```bash
kubectl -n fwmt port-forward svc/job-service 18025:80
kubectl -n fwmt port-forward svc/outcome-service 18030:80
kubectl -n fwmt port-forward svc/csv-service 18060:80
kubectl -n fwmt port-forward svc/fwmtgatewaytmmock 18000:80
```

## Step 4: Ensure acceptance Pub/Sub subscriptions exist

```bash
cd /Users/Simon.Diaz/dev/sourcecode/census31/census31-fwmt-acceptance-tests/scripts
FWMT_PUBSUB_MODE=gcp ./setup-pubsub.sh
```

Preview without mutation (safe to run first):

```bash
FWMT_PUBSUB_MODE=gcp FWMT_PUBSUB_DRY_RUN=true ./setup-pubsub.sh
```

## Step 5: Run the acceptance tests

From `census31-fwmt-acceptance-tests` root:

```bash
export FWMT_GCP_DB_USER=<username>
export FWMT_GCP_DB_PASSWORD=<password>
export FWMT_GCP_DB_NAME=fwmtgateway

mvn -B test -Dtest=CreateTestRunner \
  -Dspring.profiles.active=gcp \
  -Dspring.datasource.url="jdbc:postgresql://localhost:15432/${FWMT_GCP_DB_NAME}?currentSchema=fwmtg" \
  -Dspring.datasource.username="${FWMT_GCP_DB_USER}" \
  -Dspring.datasource.password="${FWMT_GCP_DB_PASSWORD}" \
  -Dfwmt.pubsub.mode=gcp \
  -Dfwmt.pubsub.project=c31-fwmtg-dev
```

Or using the `run-acceptance-test.sh` script with `--gcp-mode` and explicit overrides:

```bash
cd /Users/Simon.Diaz/dev/sourcecode/census31/census31-fwmt-acceptance-tests/scripts

./run-acceptance-test.sh --gcp-mode CreateTestRunner \
  -- \
  -Dspring.datasource.url="jdbc:postgresql://localhost:15432/${FWMT_GCP_DB_NAME}?currentSchema=fwmtg" \
  -Dspring.datasource.username="${FWMT_GCP_DB_USER}" \
  -Dspring.datasource.password="${FWMT_GCP_DB_PASSWORD}" \
  -Dfwmt.pubsub.mode=gcp \
  -Dfwmt.pubsub.project=c31-fwmtg-dev
```

> If you see errors mentioning `PubSubEmulatorHttp` or `localhost:8085`, the test run is still using emulator settings. Ensure `-Dfwmt.pubsub.mode=gcp` is present and that acceptance subscriptions have been created with `FWMT_PUBSUB_MODE=gcp ./setup-pubsub.sh`.

## Step 6: Verify before/after run

```bash
# Check pub/sub subscriptions exist
gcloud pubsub subscriptions list --filter='name:acceptance-tests-' --format='value(name)'

# Check services are reachable
curl -sf http://localhost:18025/info | jq .
curl -sf http://localhost:18030/info | jq .

# Check DB tunnel is alive
nc -vz localhost 15432

# Check ADC is valid
gcloud auth application-default print-access-token >/dev/null && echo 'ADC OK'
```

## Step 7: Clean up

Stop port-forwards:

```bash
cd /Users/Simon.Diaz/dev/sourcecode/census31/census31-fwmt-acceptance-tests/scripts
./stop-gcp-port-forwards.sh
```

Stop the Cloud SQL sidecar port-forward with `Ctrl+C` in its terminal.

---

## Shared-environment safety notes

> ⚠️ You are connecting to the **live `fwmtgateway` database** used by deployed services.

- Keep `fwmt.pubsub.allowServiceSubscriptionDrain=false` (default, enforced in `application-gcp.properties`).
- Only drain `acceptance-tests-*` subscriptions; do not drain `job-service-*` or `outcome-service-*` subscriptions.
- Test data written to `fwmtgateway` by the acceptance tests may be visible to other team members.
- Avoid running destructive test suites against shared dev; coordinate with the team.
- For fully isolated testing, use the dedicated-DB approach in `run-acceptance-tests-locally-census31.md` once VPN/bastion access is available.

---

## Quick reference: all commands

```bash
# 1) Auth
gcloud auth login
gcloud auth application-default login
gcloud config set project c31-fwmtg-dev
gcloud container clusters get-credentials c31-fwmtg-dev --region=europe-west2 --project=c31-fwmtg-dev

# 2) Get credentials
kubectl -n fwmt get secret db-credentials -o json \
  | jq -r '.data | to_entries[] | "\(.key)=\(.value|@base64d)"'

# 3) DB tunnel (keep open)
kubectl -n fwmt port-forward \
  "$(kubectl -n fwmt get pods -l app=job-service -o jsonpath='{.items[0].metadata.name}')" \
  15432:5432

# 4) Service port-forwards (keep open)
cd scripts && ./start-gcp-port-forwards.sh

# 5) Pub/Sub subscriptions
FWMT_PUBSUB_MODE=gcp ./setup-pubsub.sh

# 6) Run tests
mvn -B test -Dtest=CreateTestRunner \
  -Dspring.profiles.active=gcp \
  -Dspring.datasource.url="jdbc:postgresql://localhost:15432/fwmtgateway?currentSchema=fwmtg" \
  -Dspring.datasource.username="<user>" \
  -Dspring.datasource.password="<password>" \
  -Dfwmt.pubsub.mode=gcp \
  -Dfwmt.pubsub.project=c31-fwmtg-dev
```

---

## Troubleshooting

### Port-forward `15432` drops / times out

`kubectl port-forward` can drop after inactivity. Restart with the same command or add `--pod-running-timeout=24h` to extend the keepalive.

### `relation "fwmtg.*" does not exist`

The `fwmtg` schema may not be initialised on the shared DB. Check:

```bash
# psql via the proxy (requires psql installed)
PGPASSWORD=<password> psql -h localhost -p 15432 -U <user> -d fwmtgateway -c '\dn'
```

If missing, schema migration runs automatically on service startup. Restart the `job-service` pod or check Liquibase logs.

### `ADC token invalid` / `invalid_rapt`

Re-authenticate:

```bash
gcloud auth revoke --all
gcloud auth login
gcloud auth application-default login
```

### `Failed to pull from subscription ... localhost:8085 ... Subscription does not exist`

This means the acceptance tests are still using the Pub/Sub emulator client instead of real GCP Pub/Sub.

Use the GCP flags explicitly:

```bash
mvn -B test -Dtest=CreateTestRunner \
  -Dspring.profiles.active=gcp \
  -Dfwmt.pubsub.mode=gcp \
  -Dfwmt.pubsub.project=c31-fwmtg-dev \
  -Dspring.datasource.url="jdbc:postgresql://localhost:15432/fwmtgateway?currentSchema=fwmtg" \
  -Dspring.datasource.username="${FWMT_GCP_DB_USER}" \
  -Dspring.datasource.password="${FWMT_GCP_DB_PASSWORD}"
```

And ensure the acceptance subscriptions exist:

```bash
cd /Users/Simon.Diaz/dev/sourcecode/census31/census31-fwmt-acceptance-tests/scripts
FWMT_PUBSUB_MODE=gcp ./setup-pubsub.sh
gcloud pubsub subscriptions list --filter='name:acceptance-tests-' --format='value(name)'
```

### Port-forward pod is in `Terminating`

Find another running pod:

```bash
kubectl -n fwmt get pods -l app=job-service
```

Use a `Running` pod name in the port-forward command.


