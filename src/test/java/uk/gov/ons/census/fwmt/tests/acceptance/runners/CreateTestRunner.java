package uk.gov.ons.census.fwmt.tests.acceptance.runners;

import io.cucumber.junit.CucumberOptions;
import io.cucumber.junit.Cucumber;
import org.junit.runner.RunWith;

@RunWith(Cucumber.class)
@CucumberOptions(plugin = {"pretty", "json:build/cucumber-create.json"},
    features = {"src/test/resources/acceptancetests/Create.feature"},
    glue = {"uk.gov.ons.census.fwmt.tests.acceptance.config", "uk.gov.ons.census.fwmt.tests.acceptance.steps.inbound.create"})

public class CreateTestRunner {
}