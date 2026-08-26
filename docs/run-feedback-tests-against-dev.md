# Run Feedback Acceptance Tests Against Dev

This guide runs the `@Feedback` acceptance scenarios locally while targeting the dev environment as closely as possible.

It is intended to make the local Maven `TEST` output resemble the dev Kubernetes job output more closely by using:

- real dev service endpoints via `kubectl port-forward`
- real GCP Pub/Sub instead of the local emulator
- the dev database via a Cloud SQL proxy sidecar port-forward
- the `gcp` Spring profile instead of the default local profile

## Prerequisites

- `kubectl` access to cluster `c31-fwmtg-dev`
- `gcloud` authenticated for project `c31-fwmtg-dev`
- Application Default Credentials available for Pub/Sub access
- a pod in namespace `fwmt` with the Cloud SQL proxy sidecar running

## Start the required port-forwards

Run each of these in a separate terminal:

```bash
kubectl -n fwmt port-forward svc/job-service 18025:80
kubectl -n fwmt port-forward svc/outcome-service 18030:80
kubectl -n fwmt port-forward svc/csv-service 18060:80
kubectl -n fwmt port-forward svc/fwmtgatewaytmmock 18000:80
kubectl -n fwmt port-forward pod/<pod-with-cloud-sql-proxy> 15432:5432
```

## Export dev database credentials

From the acceptance test repository root:

```bash
cd /Users/Simon.Diaz/dev/sourcecode/census31/census31-fwmt-acceptance-tests

export FWMT_GCP_DB_USER="$(kubectl get secret db-credentials -n fwmt -o jsonpath='{.data.username}' | base64 --decode)"
export FWMT_GCP_DB_PASSWORD="$(kubectl get secret db-credentials -n fwmt -o jsonpath='{.data.password}' | base64 --decode)"
export FWMT_GCP_DB_NAME="$(kubectl get configmap db-config -n fwmt -o jsonpath='{.data.db-name}')"
```

## Run the Feedback scenarios with Maven

```bash
mvn --batch-mode verify \
  -Dcucumber.filter.tags='@Feedback' \
  -Dspring.profiles.active=gcp \
  -Dspring.datasource.url="jdbc:postgresql://localhost:15432/${FWMT_GCP_DB_NAME}?currentSchema=fwmtg" \
  -Dspring.datasource.username="${FWMT_GCP_DB_USER}" \
  -Dspring.datasource.password="${FWMT_GCP_DB_PASSWORD}" \
  -Dfwmt.pubsub.mode=gcp \
  -Dfwmt.pubsub.project=c31-fwmtg-dev \
  | cat
```

## Expected differences from the default local run

If the command is wired correctly, the Maven `TEST` output should look closer to dev and include signals such as:

- `Google Pub/Sub` instead of `Pub/Sub emulator`
- `GcpGatewayEventMonitor` instead of `PubSubGatewayEventMonitor`
- `http://localhost:18000` or other forwarded ports instead of default local ports like `http://localhost:8000`

The default local command below does not target dev-equivalent dependencies and will produce different logs:

```bash
mvn --batch-mode --offline verify -Dcucumber.filter.tags='@Feedback'
```

## Optional: use the container wrapper locally

If you want the wrapper-style summary lines that the dev job prints around Maven execution, you can run:

```bash
export SPRING_PROFILES_ACTIVE=gcp
export FWMT_PUBSUB_MODE=gcp
export FWMT_PUBSUB_PROJECT=c31-fwmtg-dev
export REPORTS_BUCKET=
export CUCUMBER_TAGS='@Feedback'

./scripts/docker-run.sh
```

This still relies on the same port-forwards and database credentials described above.