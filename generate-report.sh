#!/bin/bash

# Generate merged Cucumber HTML report from all test runs
# Usage: ./generate-report.sh [--open]

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

echo "Generating test report from Cucumber JSON files..."
node report-generator.js

# Optionally open the report in the default browser
if [[ "$1" == "--open" || "$1" == "-o" ]]; then
  echo "Opening report..."
  open build/cucumber-report.html
fi

echo "✓ Done! Report available at: build/cucumber-report.html"
