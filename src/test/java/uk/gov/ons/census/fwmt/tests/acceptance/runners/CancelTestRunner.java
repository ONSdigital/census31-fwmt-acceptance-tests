package uk.gov.ons.census.fwmt.tests.acceptance.runners;

import org.junit.runner.RunWith;

import io.cucumber.junit.CucumberOptions;
import io.cucumber.junit.Cucumber;

@RunWith(Cucumber.class)
@CucumberOptions(plugin = {"pretty", "json:build/cucumber-report.json"},
    features = {"src/test/resources/acceptancetests/Cancel.feature"},
    glue = {"uk.gov.ons.census.fwmt.tests.acceptance.config", "uk.gov.ons.census.fwmt.tests.acceptance.steps.inbound.cancel", "uk.gov.ons.census.fwmt.tests.acceptance.steps.inbound.create", })

public class CancelTestRunner {
}
