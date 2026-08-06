package uk.gov.ons.census.fwmt.tests.acceptance.runners;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("acceptancetests/Outcomes.feature")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "uk.gov.ons.census.fwmt.tests.acceptance.config,uk.gov.ons.census.fwmt.tests.acceptance.steps.outcomes")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty,json:target/jsonReports/cucumber-outcomes.json")
public class OutcomesTestRunner {

}