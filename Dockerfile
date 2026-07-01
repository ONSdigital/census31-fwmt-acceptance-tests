# Legacy CI image — prefer building a test jar locally and running via census31-fwmt-docs acceptance harness.
FROM maven:3.9-eclipse-temurin-25

RUN mkdir /opt/census-fsdr-acceptance-tests
COPY . /opt/census-fsdr-acceptance-tests

WORKDIR /opt/census-fsdr-acceptance-tests
ENTRYPOINT [ "mvn", "--batch-mode", "clean", "test" ]
