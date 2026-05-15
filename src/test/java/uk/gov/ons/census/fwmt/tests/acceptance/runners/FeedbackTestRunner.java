package uk.gov.ons.census.fwmt.tests.acceptance.runners;

import cucumber.api.CucumberOptions;
import cucumber.api.junit.Cucumber;
import org.junit.runner.RunWith;
import org.springframework.context.annotation.ComponentScan;

@RunWith(Cucumber.class)
@CucumberOptions(plugin = {"pretty", "json:build/cucumber-report.json"},
    features = {"src/test/resources/acceptancetests/Feedback.feature"},
    glue = {"uk.gov.ons.census.fwmt.tests.acceptance.steps.inbound.feedback"})
@ComponentScan({"uk.gov.census.ffa.storage.utils"})
public class FeedbackTestRunner {
}
