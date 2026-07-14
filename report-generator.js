const reporter = require('cucumber-html-reporter');
const fs = require('fs');
const path = require('path');
const glob = require('glob');

// Find all cucumber JSON report files
const buildDir = 'build';
const jsonFiles = glob
  .sync(path.join(buildDir, 'cucumber-*.json'))
  .filter(file => path.basename(file) !== 'cucumber-merged.json');

if (jsonFiles.length === 0) {
  console.error('✗ No cucumber JSON files found in build/');
  process.exit(1);
}

console.log(`Found ${jsonFiles.length} test report(s): ${jsonFiles.map(f => path.basename(f)).join(', ')}`);

// Merge all JSON reports into a single array
let allFeatures = [];
let totalScenarios = 0;

for (const jsonFile of jsonFiles) {
  try {
    const content = fs.readFileSync(jsonFile, 'utf-8');
    const features = JSON.parse(content);
    
    if (Array.isArray(features)) {
      allFeatures = allFeatures.concat(features);
      const scenarioCount = features.reduce((sum, f) => sum + (f.elements?.length || 0), 0);
      totalScenarios += scenarioCount;
      console.log(`  ✓ ${path.basename(jsonFile)}: ${scenarioCount} scenarios`);
    }
  } catch (error) {
    console.error(`✗ Error reading ${jsonFile}:`, error.message);
  }
}

console.log(`\nTotal: ${totalScenarios} scenarios across ${jsonFiles.length} report(s)`);

// Write merged JSON to temp file
const mergedFile = path.join(buildDir, 'cucumber-merged.json');
fs.writeFileSync(mergedFile, JSON.stringify(allFeatures, null, 2));

const options = {
  theme: 'bootstrap',
  jsonFile: mergedFile,
  output: 'build/cucumber-report.html',
  reportSuiteAsScenarios: true,
  scenarioTimestamp: true,
  launchReport: false,
  metadata: {
    'App Version': '4.0.0-SNAPSHOT',
    'Test Environment': 'Local',
    'Platform': process.platform,
    'Executed': new Date().toISOString(),
    'Reports Merged': `${jsonFiles.length} files`,
    'Total Scenarios': totalScenarios
  }
};

try {
  reporter.generate(options);
  console.log('\n✓ Report generated: ' + options.output);
} catch (error) {
  console.error('\n✗ Error generating report:', error.message);
  process.exit(1);
}
