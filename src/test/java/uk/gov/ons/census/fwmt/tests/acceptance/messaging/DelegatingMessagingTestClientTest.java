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

    when(emulatorClient.getMessageCount("event_fieldwork_action-instruction")).thenReturn(7L);
    when(emulatorClient.getMessage("event_fieldwork_action-instruction", 5000, 250)).thenReturn("message-body");
    when(emulatorClient.getObservedMessage("event_fieldwork_action-instruction_internal", 5000, 250))
        .thenReturn(new MessagingTestClient.ObservedMessage("typed-body", java.util.Map.of("caseId", "123")));
    when(emulatorClient.getMessageWithEventType("Field.other", "FIELDWORKER_UPDATE", 4000, 200))
        .thenReturn("typed-message");
    when(emulatorClient.doMessagingPreFlightCheck()).thenReturn(preFlight);

    DelegatingMessagingTestClient client =
        new DelegatingMessagingTestClient(emulatorClient, gcpClient, "emulator");

    assertThat(client.getMessageCount("event_fieldwork_action-instruction")).isEqualTo(7L);
    assertThat(client.getMessage("event_fieldwork_action-instruction", 5000, 250)).isEqualTo("message-body");
    assertThat(client.getObservedMessage("event_fieldwork_action-instruction_internal", 5000, 250).attributes())
        .containsEntry("caseId", "123");
    assertThat(client.getMessageWithEventType("Field.other", "FIELDWORKER_UPDATE", 4000, 200))
        .isEqualTo("typed-message");

    ExternalActionInstructionMetadataOverride metadataOverride =
        ExternalActionInstructionMetadataOverride.none().withCorrelationId("corr-1");
    client.publishExternalActionInstruction("{\"actionInstruction\":\"CREATE\",\"caseId\":\"123\",\"surveyName\":\"CENSUS\"}", metadataOverride);
    client.purge("event_fieldwork_action-instruction", "Outcome.Preprocessing");
    client.ensureOutcomeBindings();
    assertThat(client.doMessagingPreFlightCheck()).isSameAs(preFlight);

    verify(emulatorClient).getMessageCount("event_fieldwork_action-instruction");
    verify(emulatorClient).getMessage("event_fieldwork_action-instruction", 5000, 250);
    verify(emulatorClient).getObservedMessage("event_fieldwork_action-instruction_internal", 5000, 250);
    verify(emulatorClient)
        .getMessageWithEventType("Field.other", "FIELDWORKER_UPDATE", 4000, 200);
    verify(emulatorClient)
        .publishExternalActionInstruction(
            "{\"actionInstruction\":\"CREATE\",\"caseId\":\"123\",\"surveyName\":\"CENSUS\"}",
            metadataOverride);
    verify(emulatorClient).purge("event_fieldwork_action-instruction", "Outcome.Preprocessing");
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

    when(gcpClient.getMessageCount("event_fieldwork_action-instruction")).thenReturn(3L);
    when(gcpClient.getMessage("event_fieldwork_action-instruction", 2000, 100)).thenReturn("gcp-message");
    when(gcpClient.getObservedMessage("event_fieldwork_action-instruction_internal", 2000, 100))
        .thenReturn(new MessagingTestClient.ObservedMessage("gcp-observed", java.util.Map.of("eventType", "FIELDWORK_ACTION_INSTRUCTION")));
    when(gcpClient.getMessageWithEventType("Field.refusals", "event.respondent.refusal", 3000, 150))
        .thenReturn("refusal-message");
    when(gcpClient.doMessagingPreFlightCheck()).thenReturn(preFlight);

    DelegatingMessagingTestClient client =
        new DelegatingMessagingTestClient(emulatorClient, gcpClient, "gcp");

    assertThat(client.getMessageCount("event_fieldwork_action-instruction")).isEqualTo(3L);
    assertThat(client.getMessage("event_fieldwork_action-instruction", 2000, 100)).isEqualTo("gcp-message");
    assertThat(client.getObservedMessage("event_fieldwork_action-instruction_internal", 2000, 100).body())
        .isEqualTo("gcp-observed");
    assertThat(
            client.getMessageWithEventType(
                "Field.refusals", "event.respondent.refusal", 3000, 150))
        .isEqualTo("refusal-message");

    ExternalActionInstructionMetadataOverride metadataOverride =
        ExternalActionInstructionMetadataOverride.none().omitOccurredAt();
    client.publishExternalActionInstruction(
        "{\"actionInstruction\":\"CANCEL\",\"caseId\":\"321\",\"surveyName\":\"CENSUS\"}",
        metadataOverride);
    client.purge("Field.refusals");
    client.ensureOutcomeBindings();
    assertThat(client.doMessagingPreFlightCheck()).isSameAs(preFlight);

    verify(gcpClient).getMessageCount("event_fieldwork_action-instruction");
    verify(gcpClient).getMessage("event_fieldwork_action-instruction", 2000, 100);
    verify(gcpClient).getObservedMessage("event_fieldwork_action-instruction_internal", 2000, 100);
    verify(gcpClient)
        .getMessageWithEventType("Field.refusals", "event.respondent.refusal", 3000, 150);
    verify(gcpClient)
        .publishExternalActionInstruction(
            "{\"actionInstruction\":\"CANCEL\",\"caseId\":\"321\",\"surveyName\":\"CENSUS\"}",
            metadataOverride);
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

    assertThatThrownBy(() -> client.getMessageCount("event_fieldwork_action-instruction"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unsupported")
        .hasMessageContaining("fwmt.pubsub.mode");

    verify(emulatorClient, never()).getMessageCount("event_fieldwork_action-instruction");
    verify(gcpClient, never()).getMessageCount("event_fieldwork_action-instruction");
  }
}

