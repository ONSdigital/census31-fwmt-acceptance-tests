#!/usr/bin/env bash
set -euo pipefail

echo "Running acceptance tests with @Feedback tag"
exec mvn --batch-mode --offline verify -Dcucumber.filter.tags="@Feedback"
echo "Finished running acceptance tests with @Feedback tag"
ls -l target
