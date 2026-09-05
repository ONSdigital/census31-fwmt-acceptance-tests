package uk.gov.ons.census.fwmt.tests.acceptance.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.api.gax.rpc.UnaryCallable;
import com.google.cloud.pubsub.v1.stub.SubscriberStub;
import com.google.pubsub.v1.AcknowledgeRequest;
import com.google.pubsub.v1.ModifyAckDeadlineRequest;
import com.google.pubsub.v1.PullResponse;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import uk.gov.ons.census.fwmt.common.messaging.FieldWorkerInstructionJsonCodec;
import uk.gov.ons.census.fwmt.tests.acceptance.utils.NodeCheck;

class GcpPubSubMessagingTest {

  @Test
  void shouldDrainOnlyAcceptanceSubscriptionsWhenServiceDrainDisabled() {
    RecordingPubSubOperations operations = new RecordingPubSubOperations();
    GcpPubSubMessaging client = new GcpPubSubMessaging(operations, false);

    client.purge("RM.Field", "Outcome.Preprocessing", "Field.refusals");

    assertThat(operations.drainedSubscriptions)
        .containsExactly(
            "acceptance-tests-RM-Field",
            "acceptance-tests-Outcome-Preprocessing",
            "acceptance-tests-Field-refusals");
  }

  @Test
  void shouldDrainServiceSubscriptionsOnlyWhenExplicitlyAllowed() {
    RecordingPubSubOperations operations = new RecordingPubSubOperations();
    GcpPubSubMessaging client = new GcpPubSubMessaging(operations, true);

    client.purge("RM.Field", "Outcome.PreprocessingDLQ");

    assertThat(operations.drainedSubscriptions)
        .containsExactly(
            "acceptance-tests-RM-Field",
            "job-service-RM-Field",
            "acceptance-tests-Outcome-PreprocessingDLQ",
            "outcome-service-Outcome-PreprocessingDLQ");
  }

  @Test
  void shouldPublishFieldWorkerInstructionWithExpectedAttributes() {
    RecordingPubSubOperations operations = new RecordingPubSubOperations();
    GcpPubSubMessaging client = new GcpPubSubMessaging(operations, false);

    client.publishFieldWorkerInstruction("{\"caseId\":\"123\"}", "cancel");

    assertThat(operations.publishedTopic).isEqualTo("RM.Field");
    assertThat(operations.publishedBody).isEqualTo("{\"caseId\":\"123\"}");
    assertThat(operations.publishedAttributes)
        .containsEntry(
            FieldWorkerInstructionJsonCodec.TYPE_ID_HEADER,
            "uk.gov.ons.census.fwmt.common.rm.dto.FwmtCancelActionInstruction")
        .containsKey(FieldWorkerInstructionJsonCodec.TIMESTAMP_HEADER);
    assertThat(operations.publishedAttributes.get(FieldWorkerInstructionJsonCodec.TIMESTAMP_HEADER))
        .matches("\\d+");
  }

  @Test
  void shouldAcknowledgeMatchingMessageAndReleaseNonMatchingMessagesWhenFilteringByEventType()
      throws InterruptedException {
    RecordingPubSubOperations operations = new RecordingPubSubOperations();
    operations.enqueuePull(
        "acceptance-tests-Field-other",
        List.of(
            new GcpPubSubMessaging.TestMessage("ack-1", "{\"type\":\"event.other\"}", Map.of()),
            new GcpPubSubMessaging.TestMessage(
                "ack-2", "{\"type\":\"event.respondent.refusal\"}", Map.of())));
    GcpPubSubMessaging client = new GcpPubSubMessaging(operations, false);

    String message =
        client.getMessageWithEventType("Field.other", "event.respondent.refusal", 100, 10);

    assertThat(message).isEqualTo("{\"type\":\"event.respondent.refusal\"}");
    assertThat(operations.releasedAckIdsBySubscription)
        .containsEntry("acceptance-tests-Field-other", List.of("ack-1"));
    assertThat(operations.acknowledgedAckIdsBySubscription)
        .containsEntry("acceptance-tests-Field-other", List.of("ack-2"));
  }

  @Test
  void shouldPreflightAgainstGcpAndAvoidServiceSubscriptionDrainByDefault() {
    RecordingPubSubOperations operations = new RecordingPubSubOperations();
    operations.reachable = true;
    GcpPubSubMessaging client = new GcpPubSubMessaging(operations, false);

    NodeCheck nodeCheck = client.doMessagingPreFlightCheck();

    assertThat(nodeCheck.isSuccesful()).isTrue();
    assertThat(nodeCheck.getName()).isEqualTo("Google Pub/Sub");
    assertThat(operations.drainedSubscriptions).containsExactly("acceptance-tests-Field-refusals");
  }

  @Test
  void shouldReuseSingleSubscriberStubAcrossOperationsUntilClosed() throws Exception {
    AtomicInteger stubCreations = new AtomicInteger();
    SubscriberStub subscriberStub = mock(SubscriberStub.class);
    @SuppressWarnings("unchecked")
    UnaryCallable<com.google.pubsub.v1.PullRequest, PullResponse> pullCallable = mock(UnaryCallable.class);
    @SuppressWarnings("unchecked")
    UnaryCallable<AcknowledgeRequest, com.google.protobuf.Empty> acknowledgeCallable = mock(UnaryCallable.class);
    @SuppressWarnings("unchecked")
    UnaryCallable<ModifyAckDeadlineRequest, com.google.protobuf.Empty> modifyAckCallable = mock(UnaryCallable.class);

    when(subscriberStub.pullCallable()).thenReturn(pullCallable);
    when(subscriberStub.acknowledgeCallable()).thenReturn(acknowledgeCallable);
    when(subscriberStub.modifyAckDeadlineCallable()).thenReturn(modifyAckCallable);
    when(pullCallable.call(org.mockito.ArgumentMatchers.any()))
        .thenReturn(PullResponse.getDefaultInstance())
        .thenReturn(PullResponse.getDefaultInstance());

    GcpPubSubMessaging.GooglePubSubOperations operations =
        new GcpPubSubMessaging.GooglePubSubOperations(
            "project-id",
            () -> {
              stubCreations.incrementAndGet();
              return subscriberStub;
            });

    operations.pull("acceptance-tests-RM-Field", 1);
    operations.acknowledge("acceptance-tests-RM-Field", List.of("ack-1"));
    operations.release("acceptance-tests-RM-Field", List.of("ack-2"));
    operations.drainSubscription("acceptance-tests-RM-Field");

    assertThat(stubCreations).hasValue(1);

    operations.close();

    verify(subscriberStub, times(1)).close();
  }

  @Test
  void shouldUseMultiplePullersForRmFieldSubscription() throws Exception {
    AtomicInteger stubCreations = new AtomicInteger();
    SubscriberStub subscriberStub = mock(SubscriberStub.class);
    @SuppressWarnings("unchecked")
    UnaryCallable<com.google.pubsub.v1.PullRequest, PullResponse> pullCallable = mock(UnaryCallable.class);
    @SuppressWarnings("unchecked")
    UnaryCallable<AcknowledgeRequest, com.google.protobuf.Empty> acknowledgeCallable = mock(UnaryCallable.class);

    PullResponse empty = PullResponse.getDefaultInstance();
    when(subscriberStub.pullCallable()).thenReturn(pullCallable);
    when(subscriberStub.acknowledgeCallable()).thenReturn(acknowledgeCallable);
    when(pullCallable.call(org.mockito.ArgumentMatchers.any())).thenReturn(empty);

    GcpPubSubMessaging.GooglePubSubOperations operations =
        new GcpPubSubMessaging.GooglePubSubOperations(
            "project-id",
            () -> {
              stubCreations.incrementAndGet();
              return subscriberStub;
            });

    assertThat(operations.pullerParallelismFor("acceptance-tests-RM-Field")).isEqualTo(3);
    assertThat(operations.pullerParallelismFor("acceptance-tests-Field-other")).isEqualTo(1);

    operations.drainSubscription("acceptance-tests-RM-Field");

    assertThat(stubCreations).hasValue(1);
    operations.close();
    verify(subscriberStub, times(1)).close();
  }

  private static class RecordingPubSubOperations implements GcpPubSubMessaging.PubSubOperations {
    private final List<String> drainedSubscriptions = new ArrayList<>();
    private final Map<String, Deque<List<GcpPubSubMessaging.TestMessage>>> pullBatches =
        new HashMap<>();
    private final Map<String, List<String>> acknowledgedAckIdsBySubscription = new HashMap<>();
    private final Map<String, List<String>> releasedAckIdsBySubscription = new HashMap<>();

    private boolean reachable;
    private String publishedTopic;
    private String publishedBody;
    private Map<String, String> publishedAttributes;

    @Override
    public boolean isReachable() {
      return reachable;
    }

    @Override
    public void publish(String topicId, String jsonBody, Map<String, String> attributes) {
      this.publishedTopic = topicId;
      this.publishedBody = jsonBody;
      this.publishedAttributes = Map.copyOf(attributes);
    }

    @Override
    public List<GcpPubSubMessaging.TestMessage> pull(String subscriptionId, int maxMessages) {
      return pullBatches.getOrDefault(subscriptionId, new ArrayDeque<>()).pollFirst();
    }

    @Override
    public void acknowledge(String subscriptionId, List<String> ackIds) {
      acknowledgedAckIdsBySubscription.put(subscriptionId, List.copyOf(ackIds));
    }

    @Override
    public void release(String subscriptionId, List<String> ackIds) {
      releasedAckIdsBySubscription.put(subscriptionId, List.copyOf(ackIds));
    }

    @Override
    public void drainSubscription(String subscriptionId) {
      drainedSubscriptions.add(subscriptionId);
    }

    private void enqueuePull(String subscriptionId, List<GcpPubSubMessaging.TestMessage> batch) {
      pullBatches.computeIfAbsent(subscriptionId, ignored -> new ArrayDeque<>()).addLast(batch);
      pullBatches.get(subscriptionId).addLast(List.of());
    }
  }
}


