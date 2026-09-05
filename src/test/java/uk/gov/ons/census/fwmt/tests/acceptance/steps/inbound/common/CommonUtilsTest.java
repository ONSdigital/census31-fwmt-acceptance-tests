package uk.gov.ons.census.fwmt.tests.acceptance.steps.inbound.common;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.ons.census.fwmt.tests.acceptance.utils.PreFlightCheck;

class CommonUtilsTest {

  @AfterEach
  void resetPreFlightState() {
    CommonUtils.resetPreFlightStateForTests();
  }

  @Test
  void shouldRunPreFlightOnlyOnce() {
    CommonUtils commonUtils = new CommonUtils();
    PreFlightCheck preFlightCheck = mock(PreFlightCheck.class);
    ReflectionTestUtils.setField(commonUtils, "preFlightCheck", preFlightCheck);

    commonUtils.runPreFlightOnce();
    commonUtils.runPreFlightOnce();

    verify(preFlightCheck, times(1)).doCheck();
  }

  @Test
  void shouldCachePreFlightFailureAndFailAgainWithoutRetrying() {
    CommonUtils commonUtils = new CommonUtils();
    PreFlightCheck preFlightCheck = mock(PreFlightCheck.class);
    IllegalStateException failure = new IllegalStateException("dependency down");
    ReflectionTestUtils.setField(commonUtils, "preFlightCheck", preFlightCheck);
    doThrow(failure).when(preFlightCheck).doCheck();

    assertThatThrownBy(commonUtils::runPreFlightOnce)
        .isSameAs(failure)
        .hasMessageContaining("dependency down");
    assertThatThrownBy(commonUtils::runPreFlightOnce)
        .isSameAs(failure)
        .hasMessageContaining("dependency down");

    verify(preFlightCheck, times(1)).doCheck();
  }
}