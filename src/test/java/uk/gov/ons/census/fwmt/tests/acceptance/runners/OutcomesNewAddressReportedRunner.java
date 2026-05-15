package uk.gov.ons.census.fwmt.tests.acceptance.runners;

import cucumber.api.CucumberOptions;
import cucumber.api.junit.Cucumber;
import org.junit.runner.RunWith;
import org.springframework.context.annotation.ComponentScan;

@RunWith(Cucumber.class)
@CucumberOptions(plugin = {"pretty", "json:build/cucumber-report.json"},
    features = {"src/test/resources/acceptancetests/OutcomeNewAddressReported.feature"},
    glue = {"uk.gov.ons.census.fwmt.tests.acceptance.steps.outcomes"})
@ComponentScan({"uk.gov.census.ffa.storage.utils","uk.gov.ons.ctp.integration.common.product","uk.gov.ons.census.fwmt.tests.acceptance.steps.outcomes"})
public class OutcomesNewAddressReportedRunner {

}