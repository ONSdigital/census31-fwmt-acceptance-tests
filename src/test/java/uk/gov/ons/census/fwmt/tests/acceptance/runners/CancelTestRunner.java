package uk.gov.ons.census.fwmt.tests.acceptance.runners;

import org.junit.runner.RunWith;
import org.springframework.context.annotation.ComponentScan;

import cucumber.api.CucumberOptions;
import cucumber.api.junit.Cucumber;

@RunWith(Cucumber.class)
@CucumberOptions(plugin = {"pretty", "json:build/cucumber-report.json"},
    features = {"src/test/resources/acceptancetests/Cancel.feature"},
    glue = {"uk.gov.ons.census.fwmt.tests.acceptance.steps.inbound.cancel", "uk.gov.ons.census.fwmt.tests.acceptance.steps.inbound.create", })
@ComponentScan({"uk.gov.census.ffa.storage.utils"})

public class CancelTestRunner {
}
