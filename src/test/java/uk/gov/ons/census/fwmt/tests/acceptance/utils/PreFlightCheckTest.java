package uk.gov.ons.census.fwmt.tests.acceptance.utils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import uk.gov.ons.census.fwmt.tests.acceptance.messaging.MessagingTestClient;

class PreFlightCheckTest {

  @Test
  void shouldThrowWhenAnyPreFlightDependencyFails() {
    MessagingTestClient messagingTestClient = mock(MessagingTestClient.class);
    TMMockUtils tmMockUtils = mock(TMMockUtils.class);
    when(messagingTestClient.doMessagingPreFlightCheck())
        .thenReturn(NodeCheck.builder().name("Pub/Sub emulator").url("emulator").isSuccesful(true).build());
    when(tmMockUtils.checkDbUp())
        .thenReturn(
            NodeCheck.builder()
                .name("Postgres")
                .url("jdbc:postgresql://db")
                .isSuccesful(false)
                .failureMsg("connection refused")
                .build());

    TestPreFlightCheck preFlightCheck = new TestPreFlightCheck();
    ReflectionTestUtils.setField(preFlightCheck, "messagingTestClient", messagingTestClient);
    ReflectionTestUtils.setField(preFlightCheck, "tmMockUtils", tmMockUtils);
    preFlightCheck.serviceChecks.put(
        "outcome-service",
        NodeCheck.builder().name("outcome-service").url("http://outcome/swagger-ui.html").isSuccesful(true).build());
    preFlightCheck.serviceChecks.put(
        "job-service",
        NodeCheck.builder().name("job-service").url("http://job/swagger-ui.html").isSuccesful(true).build());
    preFlightCheck.serviceChecks.put(
        "tm-service",
        NodeCheck.builder().name("tm-service").url("http://tm/swagger-ui.html").isSuccesful(true).build());
    ReflectionTestUtils.setField(preFlightCheck, "outcomeServiceUrl", "http://outcome");
    ReflectionTestUtils.setField(preFlightCheck, "jobserviceServiceUrl", "http://job");
    ReflectionTestUtils.setField(preFlightCheck, "tmServiceUrl", "http://tm/");

    assertThatThrownBy(preFlightCheck::doCheck)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Pre-flight checks failed")
        .hasMessageContaining("Postgres")
        .hasMessageContaining("connection refused");
  }

  private static class TestPreFlightCheck extends PreFlightCheck {
    private final Map<String, NodeCheck> serviceChecks = new HashMap<>();

    @Override
    public NodeCheck checkService(String name, String address, String user, String password) {
      return serviceChecks.get(name);
    }
  }
}