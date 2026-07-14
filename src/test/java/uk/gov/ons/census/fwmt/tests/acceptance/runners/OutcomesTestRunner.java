package uk.gov.ons.census.fwmt.tests.acceptance.runners;

import io.cucumber.junit.CucumberOptions;
import io.cucumber.junit.Cucumber;
import org.junit.runner.RunWith;

@RunWith(Cucumber.class)
@CucumberOptions(plugin = {"pretty", "json:build/cucumber-outcomes.json"},
    features = {"src/test/resources/acceptancetests/Outcome.feature"},
    glue = {"uk.gov.ons.census.fwmt.tests.acceptance.config", "uk.gov.ons.census.fwmt.tests.acceptance.steps.outcomes"})
public class OutcomesTestRunner {

}