# syntax=docker/dockerfile:1
# Legacy CI image — prefer building a test jar locally and running via census31-fwmt-docs acceptance harness.
FROM maven:3.9-eclipse-temurin-25

RUN mkdir -p /root/.m2 /opt/census-fsdr-acceptance-tests

# settings.xml configures Artifact Registry repos; copied here for the dependency-cache step below.
COPY settings.xml /root/.m2/settings.xml
COPY pom.xml /opt/census-fsdr-acceptance-tests/

WORKDIR /opt/census-fsdr-acceptance-tests

# Pre-download all Maven dependencies at image-build time so the container runs fully offline
# inside GKE (where there is no outbound internet access).
# ARTIFACT_REGISTRY_TOKEN is a BuildKit secret — it is never stored in the final image.
RUN --mount=type=secret,id=ar_token,env=ARTIFACT_REGISTRY_TOKEN \
    mvn --batch-mode dependency:go-offline dependency:resolve-plugins && \
    rm /root/.m2/settings.xml

COPY . /opt/census-fsdr-acceptance-tests

ENTRYPOINT [ "mvn", "--batch-mode", "--offline", "clean", "test" ]
