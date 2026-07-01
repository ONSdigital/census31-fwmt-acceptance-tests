package uk.gov.ons.census.fwmt.tests.acceptance.runners;

import io.cucumber.junit.CucumberOptions;
import io.cucumber.junit.Cucumber;
import org.junit.runner.RunWith;

@RunWith(Cucumber.class)
@CucumberOptions(plugin = {"pretty", "json:build/cucumber-report.json"},
    features = {"src/test/resources/acceptancetests/Feedback.feature"},
    glue = {"uk.gov.ons.census.fwmt.tests.acceptance.config", "uk.gov.ons.census.fwmt.tests.acceptance.steps.inbound.feedback"})
public class FeedbackTestRunner {
}
