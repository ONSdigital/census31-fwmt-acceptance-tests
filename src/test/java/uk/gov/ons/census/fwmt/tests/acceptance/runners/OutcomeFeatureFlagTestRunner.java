package uk.gov.ons.census.fwmt.tests.acceptance.runners;

import io.cucumber.junit.Cucumber;
import io.cucumber.junit.CucumberOptions;
import org.junit.runner.RunWith;

@RunWith(Cucumber.class)
@CucumberOptions(
    plugin = {"pretty", "json:build/cucumber-outcome-feature-flag-report.json"},
    features = {"src/test/resources/acceptancetests/OutcomeFeatureFlag.feature"},
    glue = {
        "uk.gov.ons.census.fwmt.tests.acceptance.config",
        "uk.gov.ons.census.fwmt.tests.acceptance.steps.outcomes",
        "uk.gov.ons.census.fwmt.tests.acceptance.steps.featureflag"
    })
public class OutcomeFeatureFlagTestRunner {
}