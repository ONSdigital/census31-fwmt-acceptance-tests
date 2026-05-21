package uk.gov.ons.census.fwmt.tests.acceptance.config;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.test.context.ContextConfiguration;

@CucumberContextConfiguration
@ContextConfiguration(locations = "classpath:appcontext.xml")
public class CucumberSpringConfiguration {
}
