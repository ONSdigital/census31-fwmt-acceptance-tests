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

RUN mkdir -p /root/.m2 /opt/census-fsdr-acceptance-tests

COPY settings.xml /root/.m2/settings.xml
COPY . /opt/census-fsdr-acceptance-tests

WORKDIR /opt/census-fsdr-acceptance-tests

# Run verify (not just test) so verify-phase plugins like maven-cucumber-reporting are cached.
RUN --mount=type=secret,id=ar_token \
    ARTIFACT_REGISTRY_TOKEN=$(cat /run/secrets/ar_token) \
    mvn --batch-mode clean verify || true && \
    find /root/.m2 -name "_remote.repositories" -delete && \
    rm /root/.m2/settings.xml

# Bucket for uploading Cucumber reports after the run (injected by the Kubernetes Job).
ENV REPORTS_BUCKET=""

ENTRYPOINT ["./scripts/docker-run.sh"]

