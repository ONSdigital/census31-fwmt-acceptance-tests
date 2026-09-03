package uk.gov.ons.census.fwmt.tests.acceptance.steps;

import io.cucumber.java.BeforeAll;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import uk.gov.ons.census.fwmt.tests.acceptance.steps.inbound.common.CommonUtils;

@Slf4j
public final class PreFlightHooks {

  private PreFlightHooks() {
  }

  @BeforeAll(order = 0)
  public static void runSuitePreFlight() {
    try (ClassPathXmlApplicationContext applicationContext =
        new ClassPathXmlApplicationContext("appcontext.xml")) {
      runSuitePreFlight(applicationContext.getBean(CommonUtils.class));
    }
  }

  static void runSuitePreFlight(CommonUtils commonUtils) {
    log.info("Running acceptance-suite pre-flight checks");
    commonUtils.runPreFlightOnce();
    log.info("Acceptance-suite pre-flight checks completed");
  }
}