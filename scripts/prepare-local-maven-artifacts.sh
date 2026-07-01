#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CENSUS31_FWMT_ROOT="${CENSUS31_FWMT_ROOT:-/home/simon/dev/sourcecode/census31}"
CENSUS31_INT_COMMON_BACKEND="${CENSUS31_INT_COMMON_BACKEND:-$CENSUS31_FWMT_ROOT/census31-int-common-backend}"
BUILD_DIR="$SCRIPT_DIR/.local-maven-artifacts"

# Built from census31-int-common-backend (Java 25, ons.census.int.common) and installed
# under legacy uk.gov.ons.ctp.integration.common coordinates for FWMT Maven builds.
BACKEND_VERSION="${FWMT_INT_COMMON_BACKEND_VERSION:-1.0.0-SNAPSHOT}"
FRAMEWORK_FWMT_VERSION="${FWMT_CTP_FRAMEWORK_VERSION:-0.0.79}"
TEST_FRAMEWORK_FWMT_VERSION="${FWMT_CTP_TEST_FRAMEWORK_VERSION:-0.0.20}"
PRODUCT_REFERENCE_FWMT_VERSIONS=(${FWMT_PRODUCT_REFERENCE_FWMT_VERSIONS:-1.0.2 1.0.14})

resolve_java_home() {
  local candidates=()

  if [[ -n "${FWMT_JAVA_HOME:-}" ]]; then
    candidates+=("$FWMT_JAVA_HOME")
  fi

  candidates+=(
    "$HOME/.sdkman/candidates/java/25.0.2-open"
    "/usr/lib/jvm/java-25-openjdk"
    "$HOME/.sdkman/candidates/java/21.0.2-open"
  )

  local candidate
  for candidate in "${candidates[@]}"; do
    if [[ -x "$candidate/bin/java" ]]; then
      echo "$candidate"
      return
    fi
  done

  for candidate in /usr/lib/jvm/java-25-openjdk-* /usr/lib/jvm/java-21-openjdk-*; do
    if [[ -x "$candidate/bin/java" ]]; then
      echo "$candidate"
      return
    fi
  done

  echo "Unable to find Java 25 (or 21 fallback). Set FWMT_JAVA_HOME." >&2
  exit 1
}

JAVA_HOME_TO_USE="$(resolve_java_home)"
MAVEN_BIN="${FWMT_MAVEN_BIN:-mvn}"
MAVEN_USER_HOME="${FWMT_MAVEN_USER_HOME:-$HOME}"

if ! command -v "$MAVEN_BIN" >/dev/null 2>&1; then
  echo "Unable to find Maven. Set FWMT_MAVEN_BIN to a Maven executable." >&2
  exit 1
fi

if [[ ! -f "$CENSUS31_INT_COMMON_BACKEND/pom.xml" ]]; then
  echo "Missing census31-int-common-backend at $CENSUS31_INT_COMMON_BACKEND" >&2
  exit 1
fi

run_maven() {
  JAVA_HOME="$JAVA_HOME_TO_USE" \
  PATH="$JAVA_HOME_TO_USE/bin:$PATH" \
  "$MAVEN_BIN" -Duser.home="$MAVEN_USER_HOME" "$@"
}

write_framework_pom() {
  local target="$1"
  local version="$2"
  cat >"$target" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>uk.gov.ons.ctp.integration.common</groupId>
  <artifactId>framework</artifactId>
  <version>$version</version>
  <packaging>jar</packaging>
</project>
EOF
}

write_test_framework_pom() {
  local target="$1"
  local version="$2"
  cat >"$target" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>uk.gov.ons.ctp.integration.common</groupId>
  <artifactId>test-framework</artifactId>
  <version>$version</version>
  <packaging>jar</packaging>
</project>
EOF
}

write_product_reference_pom() {
  local target="$1"
  local version="$2"
  local framework_version="$3"
  cat >"$target" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>uk.gov.ons.ctp.integration.common</groupId>
  <artifactId>product-reference</artifactId>
  <version>$version</version>
  <packaging>jar</packaging>
  <dependencies>
    <dependency>
      <groupId>uk.gov.ons.ctp.integration.common</groupId>
      <artifactId>framework</artifactId>
      <version>$framework_version</version>
    </dependency>
  </dependencies>
</project>
EOF
}

mkdir -p "$BUILD_DIR"

echo "Building census31-int-common-backend (framework, test-framework, product-reference) with Java 25"
run_maven -f "$CENSUS31_INT_COMMON_BACKEND/pom.xml" \
  -pl framework,test-framework,product-reference \
  -am \
  install \
  -Dmaven.test.skip=true \
  -Dcheckstyle.skip=true \
  -Dfmt.skip=true

FRAMEWORK_JAR="$CENSUS31_INT_COMMON_BACKEND/framework/target/framework-$BACKEND_VERSION.jar"
TEST_FRAMEWORK_JAR="$CENSUS31_INT_COMMON_BACKEND/test-framework/target/test-framework-$BACKEND_VERSION.jar"
PRODUCT_REFERENCE_JAR="$CENSUS31_INT_COMMON_BACKEND/product-reference/target/product-reference-$BACKEND_VERSION.jar"

for jar in "$FRAMEWORK_JAR" "$TEST_FRAMEWORK_JAR" "$PRODUCT_REFERENCE_JAR"; do
  if [[ ! -f "$jar" ]]; then
    echo "Expected jar not found: $jar" >&2
    exit 1
  fi
done

FRAMEWORK_POM="$BUILD_DIR/framework-$FRAMEWORK_FWMT_VERSION.pom"
write_framework_pom "$FRAMEWORK_POM" "$FRAMEWORK_FWMT_VERSION"
echo "Installing framework as uk.gov.ons.ctp.integration.common:framework:$FRAMEWORK_FWMT_VERSION"
run_maven -q install:install-file \
  -Dfile="$FRAMEWORK_JAR" \
  -DpomFile="$FRAMEWORK_POM"

TEST_FRAMEWORK_POM="$BUILD_DIR/test-framework-$TEST_FRAMEWORK_FWMT_VERSION.pom"
write_test_framework_pom "$TEST_FRAMEWORK_POM" "$TEST_FRAMEWORK_FWMT_VERSION"
echo "Installing test-framework as uk.gov.ons.ctp.integration.common:test-framework:$TEST_FRAMEWORK_FWMT_VERSION"
run_maven -q install:install-file \
  -Dfile="$TEST_FRAMEWORK_JAR" \
  -DpomFile="$TEST_FRAMEWORK_POM"

for product_reference_fwmt_version in "${PRODUCT_REFERENCE_FWMT_VERSIONS[@]}"; do
  PRODUCT_REFERENCE_POM="$BUILD_DIR/product-reference-$product_reference_fwmt_version.pom"
  write_product_reference_pom "$PRODUCT_REFERENCE_POM" "$product_reference_fwmt_version" "$FRAMEWORK_FWMT_VERSION"
  echo "Installing product-reference as uk.gov.ons.ctp.integration.common:product-reference:$product_reference_fwmt_version"
  run_maven -q install:install-file \
    -Dfile="$PRODUCT_REFERENCE_JAR" \
    -DpomFile="$PRODUCT_REFERENCE_POM"
done

echo "census31-int-common-backend artifacts installed to Maven local (~/.m2)."
