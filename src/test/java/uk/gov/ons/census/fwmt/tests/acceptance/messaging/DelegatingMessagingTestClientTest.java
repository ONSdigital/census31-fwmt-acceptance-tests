package uk.gov.ons.census.fwmt.tests.acceptance.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import uk.gov.ons.census.fwmt.tests.acceptance.utils.NodeCheck;

class DelegatingMessagingTestClientTest {

  @Test
  void shouldDelegateAllOperationsToEmulatorClientWhenModeIsEmulator()
      throws IOException, TimeoutException, InterruptedException {
    MessagingTestClient emulatorClient = mock(MessagingTestClient.class);
    MessagingTestClient gcpClient = mock(MessagingTestClient.class);
    NodeCheck preFlight = NodeCheck.builder().name("emulator").isSuccesful(true).build();

    when(emulatorClient.getMessageCount("RM.Field")).thenReturn(7L);
    when(emulatorClient.getMessage("RM.Field", 5000, 250)).thenReturn("message-body");
    when(emulatorClient.getMessageWithEventType("Field.other", "FIELDWORKER_UPDATE", 4000, 200))
        .thenReturn("typed-message");
    when(emulatorClient.doMessagingPreFlightCheck()).thenReturn(preFlight);

    DelegatingMessagingTestClient client =
        new DelegatingMessagingTestClient(emulatorClient, gcpClient, "emulator");

    assertThat(client.getMessageCount("RM.Field")).isEqualTo(7L);
    assertThat(client.getMessage("RM.Field", 5000, 250)).isEqualTo("message-body");
    assertThat(client.getMessageWithEventType("Field.other", "FIELDWORKER_UPDATE", 4000, 200))
        .isEqualTo("typed-message");

    client.publishFieldWorkerInstruction("{\"action\":\"create\"}", "create");
    client.purge("RM.Field", "Outcome.Preprocessing");
    client.ensureOutcomeBindings();
    assertThat(client.doMessagingPreFlightCheck()).isSameAs(preFlight);

    verify(emulatorClient).getMessageCount("RM.Field");
    verify(emulatorClient).getMessage("RM.Field", 5000, 250);
    verify(emulatorClient)
        .getMessageWithEventType("Field.other", "FIELDWORKER_UPDATE", 4000, 200);
    verify(emulatorClient).publishFieldWorkerInstruction("{\"action\":\"create\"}", "create");
    verify(emulatorClient).purge("RM.Field", "Outcome.Preprocessing");
    verify(emulatorClient).ensureOutcomeBindings();
    verify(emulatorClient).doMessagingPreFlightCheck();
    verifyNoInteractions(gcpClient);
  }

  @Test
  void shouldDelegateAllOperationsToGcpClientWhenModeIsGcp()
      throws IOException, TimeoutException, InterruptedException {
    MessagingTestClient emulatorClient = mock(MessagingTestClient.class);
    MessagingTestClient gcpClient = mock(MessagingTestClient.class);
    NodeCheck preFlight = NodeCheck.builder().name("gcp").isSuccesful(true).build();

    when(gcpClient.getMessageCount("RM.Field")).thenReturn(3L);
    when(gcpClient.getMessage("RM.Field", 2000, 100)).thenReturn("gcp-message");
    when(gcpClient.getMessageWithEventType("Field.refusals", "event.respondent.refusal", 3000, 150))
        .thenReturn("refusal-message");
    when(gcpClient.doMessagingPreFlightCheck()).thenReturn(preFlight);

    DelegatingMessagingTestClient client =
        new DelegatingMessagingTestClient(emulatorClient, gcpClient, "gcp");

    assertThat(client.getMessageCount("RM.Field")).isEqualTo(3L);
    assertThat(client.getMessage("RM.Field", 2000, 100)).isEqualTo("gcp-message");
    assertThat(
            client.getMessageWithEventType(
                "Field.refusals", "event.respondent.refusal", 3000, 150))
        .isEqualTo("refusal-message");

    client.publishFieldWorkerInstruction("{\"action\":\"cancel\"}", "cancel");
    client.purge("Field.refusals");
    client.ensureOutcomeBindings();
    assertThat(client.doMessagingPreFlightCheck()).isSameAs(preFlight);

    verify(gcpClient).getMessageCount("RM.Field");
    verify(gcpClient).getMessage("RM.Field", 2000, 100);
    verify(gcpClient)
        .getMessageWithEventType("Field.refusals", "event.respondent.refusal", 3000, 150);
    verify(gcpClient).publishFieldWorkerInstruction("{\"action\":\"cancel\"}", "cancel");
    verify(gcpClient).purge("Field.refusals");
    verify(gcpClient).ensureOutcomeBindings();
    verify(gcpClient).doMessagingPreFlightCheck();
    verifyNoInteractions(emulatorClient);
  }

  @Test
  void shouldRejectUnsupportedPubSubMode() {
    MessagingTestClient emulatorClient = mock(MessagingTestClient.class);
    MessagingTestClient gcpClient = mock(MessagingTestClient.class);

    DelegatingMessagingTestClient client =
        new DelegatingMessagingTestClient(emulatorClient, gcpClient, "unsupported");

    assertThatThrownBy(() -> client.getMessageCount("RM.Field"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsupported")
        .hasMessageContaining("fwmt.pubsub.mode");

    verify(emulatorClient, never()).getMessageCount("RM.Field");
    verify(gcpClient, never()).getMessageCount("RM.Field");
  }
}

