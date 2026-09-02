package uk.gov.ons.census.fwmt.tests.acceptance.steps;

import org.springframework.beans.factory.annotation.Autowired;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import uk.gov.ons.census.fwmt.tests.acceptance.steps.inbound.common.CommonUtils;

/**
 * Scenario-scoped setup and teardown for the whole suite. Cucumber applies unconditional hooks to
 * every scenario, so this must live in exactly one glue class: when several step classes each
 * declared it, the full setup ran once per class per scenario.
 */
public class ScenarioHooks {

  @Autowired
  private CommonUtils commonUtils;

  // order 0 runs before the step classes' own @Before hooks; @After hooks run in reverse, so
  // teardown still runs last.
  @Before(order = 0)
  public void setup() throws Exception {
    commonUtils.setup();
  }

  @After(order = 0)
  public void clearDown() throws Exception {
    commonUtils.clearDown();
  }
}
