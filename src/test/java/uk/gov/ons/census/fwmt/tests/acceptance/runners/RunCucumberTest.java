package uk.gov.ons.census.fwmt.tests.acceptance.runners;

import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("acceptancetests")
public class RunCucumberTest {
}
