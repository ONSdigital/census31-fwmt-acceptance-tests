package uk.gov.ons.census.fwmt.tests.acceptance.steps;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import uk.gov.ons.census.fwmt.tests.acceptance.steps.inbound.common.CommonUtils;

class PreFlightHooksTest {

  @AfterEach
  void resetPreFlightState() {
    CommonUtils.resetPreFlightStateForTests();
  }

  @Test
  void shouldRunSuitePreFlightFromSpringContext() {
    CommonUtils commonUtils = mock(CommonUtils.class);

    PreFlightHooks.runSuitePreFlight(commonUtils);

    verify(commonUtils).runPreFlightOnce();
  }

  @Test
  void shouldSurfacePreFlightFailureBeforeScenariosRun() {
    CommonUtils commonUtils = mock(CommonUtils.class);
    IllegalStateException failure = new IllegalStateException("preflight failed");
    doThrow(failure).when(commonUtils).runPreFlightOnce();

    assertThatThrownBy(() -> PreFlightHooks.runSuitePreFlight(commonUtils))
        .isSameAs(failure)
        .hasMessageContaining("preflight failed");
  }
}