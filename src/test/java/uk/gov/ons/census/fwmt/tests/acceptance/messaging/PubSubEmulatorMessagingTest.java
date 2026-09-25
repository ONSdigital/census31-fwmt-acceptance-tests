package uk.gov.ons.census.fwmt.tests.acceptance.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uk.gov.ons.census.fwmt.tests.acceptance.timing.PerformanceTimingRecorder;

class PubSubEmulatorMessagingTest {

  @Test
  void shouldReturnObservedMessageWithAttributes() throws InterruptedException {
    RecordingPubSubEmulatorHttp http = new RecordingPubSubEmulatorHttp();
    http.enqueuePull(
        List.of(
            new PubSubEmulatorHttp.ReceivedPubSubMessage(
                "ack-1",
                "{\"actionInstruction\":\"PAUSE\"}",
                Map.of("eventType", "FIELDWORK_ACTION_INSTRUCTION", "caseId", "case-123"))));
    PubSubEmulatorMessaging client =
        new PubSubEmulatorMessaging(http, new PerformanceTimingRecorder());

    MessagingTestClient.ObservedMessage observedMessage =
        client.getObservedMessage("event_fieldwork_action-instruction_internal", 100, 10);

    assertThat(observedMessage.body()).contains("PAUSE");
    assertThat(observedMessage.attributes())
        .containsEntry("eventType", "FIELDWORK_ACTION_INSTRUCTION")
        .containsEntry("caseId", "case-123");
  }

  @Test
  void shouldSettleEntireBatchAndBufferMessagesForLaterExpectedTypes() throws InterruptedException {
    RecordingPubSubEmulatorHttp http = new RecordingPubSubEmulatorHttp();
    http.enqueuePull(
        List.of(
            new PubSubEmulatorHttp.ReceivedPubSubMessage(
                "ack-address", "{\"header\":{\"messageType\":\"FIELD_CASE_UPDATED\"}}", Map.of()),
            new PubSubEmulatorHttp.ReceivedPubSubMessage(
                "ack-fulfilment", "{\"header\":{\"messageType\":\"FULFILMENT_REQUEST\"}}", Map.of())));
    PubSubEmulatorMessaging client =
      new PubSubEmulatorMessaging(http, new PerformanceTimingRecorder());

    String addressMessage =
        client.getMessageWithEventType("event_field-case-updated", "FIELD_CASE_UPDATED", 100, 10);
    String fulfilmentMessage =
        client.getMessageWithEventType("event_field-case-updated", "FULFILMENT_REQUESTED", 100, 10);

    assertThat(addressMessage).contains("FIELD_CASE_UPDATED");
    assertThat(fulfilmentMessage).contains("FULFILMENT_REQUEST");
    assertThat(http.acknowledgedIds).containsExactly("ack-address", "ack-fulfilment");
    assertThat(http.pullCount).isOne();
    assertThat(http.publishedMessages).isEmpty();
  }

  @Test
  void shouldFilterAddressNotValidMessagesOnDictionaryLane() throws InterruptedException {
    RecordingPubSubEmulatorHttp http = new RecordingPubSubEmulatorHttp();
    http.enqueuePull(
        List.of(
            new PubSubEmulatorHttp.ReceivedPubSubMessage(
                "ack-address-not-valid",
                "{\"header\":{\"messageType\":\"ADDRESS_NOT_VALID\"}}",
                Map.of())));
    PubSubEmulatorMessaging client =
        new PubSubEmulatorMessaging(http, new PerformanceTimingRecorder());

    String message =
        client.getMessageWithEventType("event_address-not-valid", "ADDRESS_NOT_VALID", 100, 10);

    assertThat(message).contains("ADDRESS_NOT_VALID");
    assertThat(http.acknowledgedIds).containsExactly("ack-address-not-valid");
  }

    @Test
    void shouldPublishExternalActionInstructionWithCanonicalDefaults() {
    RecordingPubSubEmulatorHttp http = new RecordingPubSubEmulatorHttp();
    PubSubEmulatorMessaging client =
      new PubSubEmulatorMessaging(http, new PerformanceTimingRecorder());

    client.publishExternalActionInstruction(
      "{\"caseId\":\"123\",\"surveyName\":\"CENSUS\",\"actionInstruction\":\"PAUSE\"}");

    assertThat(http.publishedTopic).isEqualTo("event_fieldwork_action-instruction");
    assertThat(http.publishedBody)
      .isEqualTo("{\"caseId\":\"123\",\"surveyName\":\"CENSUS\",\"actionInstruction\":\"PAUSE\"}");
    assertThat(http.publishedAttributes)
      .containsEntry("caseId", "123")
      .containsEntry("eventType", "CASE_UPDATE")
      .containsEntry("schemaVersion", "1.0")
      .containsKey("eventId")
      .containsKey("occurredAt")
      .doesNotContainKeys("__TypeId__", "timestamp");
    }

    @Test
    void shouldApplyExternalActionInstructionOverrideValues() {
    RecordingPubSubEmulatorHttp http = new RecordingPubSubEmulatorHttp();
    PubSubEmulatorMessaging client =
      new PubSubEmulatorMessaging(http, new PerformanceTimingRecorder());

    client.publishExternalActionInstruction(
      "{\"caseId\":\"123\",\"surveyName\":\"CENSUS\",\"actionInstruction\":\"UPDATE\"}",
      ExternalActionInstructionMetadataOverride.none()
        .withCorrelationId("corr-2")
        .omitEventId()
        .withOccurredAt("not-an-instant"));

    assertThat(http.publishedAttributes)
      .containsEntry("correlationId", "corr-2")
      .containsEntry("occurredAt", "not-an-instant")
      .doesNotContainKey("eventId");
    }

    @Test
    void shouldRejectExternalActionInstructionWithWrongSurveyName() {
    RecordingPubSubEmulatorHttp http = new RecordingPubSubEmulatorHttp();
    PubSubEmulatorMessaging client =
      new PubSubEmulatorMessaging(http, new PerformanceTimingRecorder());

    org.assertj.core.api.Assertions.assertThatThrownBy(
        () ->
          client.publishExternalActionInstruction(
            "{\"caseId\":\"123\",\"surveyName\":\"NOT_CENSUS\",\"actionInstruction\":\"UPDATE\"}"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("surveyName");
  }

  @Test
  void shouldPreflightAgainstCanonicalExternalLane() {
    RecordingPubSubEmulatorHttp http = new RecordingPubSubEmulatorHttp();
    http.reachable = true;
    PubSubEmulatorMessaging client =
        new PubSubEmulatorMessaging(http, new PerformanceTimingRecorder());

    uk.gov.ons.census.fwmt.tests.acceptance.utils.NodeCheck nodeCheck =
        client.doMessagingPreFlightCheck();

    assertThat(nodeCheck.isSuccesful()).isTrue();
    assertThat(http.drainedSubscriptions)
        .containsExactly(
            "acceptance-tests-fieldwork-action-instruction",
            "job-service-fieldwork-action-instruction");
  }

  private static final class RecordingPubSubEmulatorHttp extends PubSubEmulatorHttp {
    private final Deque<List<ReceivedPubSubMessage>> pullBatches = new ArrayDeque<>();
    private final List<String> acknowledgedIds = new ArrayList<>();
    private final List<String> drainedSubscriptions = new ArrayList<>();
    private final List<String> publishedMessages = new ArrayList<>();
    private String publishedTopic;
    private String publishedBody;
    private Map<String, String> publishedAttributes;
    private int pullCount;
    private boolean reachable;

    private RecordingPubSubEmulatorHttp() {
      super("test-project", "localhost:1");
    }

    @Override
    boolean isReachable() {
      return reachable;
    }

    @Override
    List<ReceivedPubSubMessage> pull(
        String subscriptionId, int maxMessages, boolean returnImmediately) {
      pullCount += 1;
      return pullBatches.isEmpty() ? List.of() : pullBatches.removeFirst();
    }

    @Override
    void acknowledge(String subscriptionId, List<String> ackIds) {
      acknowledgedIds.addAll(ackIds);
    }

    @Override
    void drainSubscription(String subscriptionId) {
      drainedSubscriptions.add(subscriptionId);
    }

    @Override
    void publish(String topicId, String jsonBody, Map<String, String> attributes) {
      publishedTopic = topicId;
      publishedBody = jsonBody;
      publishedAttributes = Map.copyOf(attributes);
      publishedMessages.add(jsonBody);
    }

    private void enqueuePull(List<ReceivedPubSubMessage> batch) {
      pullBatches.addLast(batch);
    }
  }
}