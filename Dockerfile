# syntax=docker/dockerfile:1
# Legacy CI image — prefer building a test jar locally and running via census31-fwmt-docs acceptance harness.
FROM maven:3.9-eclipse-temurin-25

RUN mkdir -p /root/.m2 /opt/census-fsdr-acceptance-tests

COPY settings.xml /root/.m2/settings.xml
COPY . /opt/census-fsdr-acceptance-tests

WORKDIR /opt/census-fsdr-acceptance-tests

# Compile test code at image-build time to fully warm the Maven cache (resolves version ranges,
# transitive POMs, and plugin deps that dependency:go-offline misses).
# ARTIFACT_REGISTRY_TOKEN is a BuildKit secret — never stored in the final image.
RUN --mount=type=secret,id=ar_token \
    ARTIFACT_REGISTRY_TOKEN=$(cat /run/secrets/ar_token) \
    mvn --batch-mode clean test -DskipTests && \
    find /root/.m2 -name "_remote.repositories" -delete && \
    rm /root/.m2/settings.xml

ENTRYPOINT [ "mvn", "--batch-mode", "--offline", "clean", "test" ]
