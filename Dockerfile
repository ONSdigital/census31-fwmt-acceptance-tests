# syntax=docker/dockerfile:1
# Legacy CI image — prefer building a test jar locally and running via census31-fwmt-docs acceptance harness.
FROM maven:3.9-eclipse-temurin-25

# Install zsh (required by run-tagged-acceptance.sh shebang) and Google Cloud CLI (for gcloud storage upload).
RUN apt-get update && \
    apt-get install -y --no-install-recommends curl gnupg python3 zsh && \
    echo "deb [signed-by=/usr/share/keyrings/cloud.google.gpg] \
         https://packages.cloud.google.com/apt cloud-sdk main" \
         | tee /etc/apt/sources.list.d/google-cloud-sdk.list && \
    curl https://packages.cloud.google.com/apt/doc/apt-key.gpg \
         | gpg --dearmor -o /usr/share/keyrings/cloud.google.gpg && \
    apt-get update && \
    apt-get install -y --no-install-recommends google-cloud-cli && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /opt/census-fsdr-acceptance-tests

# Copy stable files first (minimizes cache invalidation)
COPY settings.xml .
COPY pom.xml .
COPY .mvn .mvn

# Warm Maven cache during image build — cache mount persists across builds via Artifact Registry
RUN --mount=type=secret,id=ar_token \
    --mount=type=cache,target=/root/.m2,mode=max \
    export ARTIFACT_REGISTRY_TOKEN="$(cat /run/secrets/ar_token)" && \
    mvn --batch-mode -U -DskipTests dependency:go-offline && \
    find /root/.m2 -name "_remote.repositories" -delete

# Copy source code (only after cache is warmed to maximize cache layer reuse)
COPY . .

# Build with cached Maven dependencies
RUN --mount=type=secret,id=ar_token \
    --mount=type=cache,target=/root/.m2,mode=max \
    export ARTIFACT_REGISTRY_TOKEN="$(cat /run/secrets/ar_token)" && \
    mvn --batch-mode -DskipTests clean package && \
    rm settings.xml

# Bucket for uploading Cucumber reports after the run (injected by the Kubernetes Job).
ENV REPORTS_BUCKET=""

ENTRYPOINT ["./scripts/docker-run.sh"]

