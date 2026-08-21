#!/usr/bin/env bash
set -euo pipefail

echo "Running acceptance tests with @Feedback tag"
mvn --batch-mode --offline verify -Dcucumber.filter.tags="@Feedback"
echo "Finished running acceptance tests with @Feedback tag"
ls -l target/cucumber-reports 2>/dev/null || echo "Warning: cucumber-reports not found"
