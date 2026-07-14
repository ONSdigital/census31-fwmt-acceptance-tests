package uk.gov.ons.census.fwmt.tests.acceptance.runners;

import io.cucumber.junit.CucumberOptions;
import io.cucumber.junit.Cucumber;
import org.junit.runner.RunWith;

@RunWith(Cucumber.class)
@CucumberOptions(plugin = {"pretty", "json:build/cucumber-resilience.json"},
    features = {"src/test/resources/acceptancetests/Resilience.feature"},
    glue = {"uk.gov.ons.census.fwmt.tests.acceptance.config", "uk.gov.ons.census.fwmt.tests.acceptance.steps.inbound.resilience"})
public class ResilienceTestRunner {
}
