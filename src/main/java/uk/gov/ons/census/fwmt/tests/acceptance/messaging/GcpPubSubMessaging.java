package uk.gov.ons.census.fwmt.tests.acceptance.messaging;

import com.google.api.core.ApiFuture;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.stub.GrpcSubscriberStub;
import com.google.cloud.pubsub.v1.stub.SubscriberStub;
import com.google.cloud.pubsub.v1.stub.SubscriberStubSettings;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.AcknowledgeRequest;
import com.google.pubsub.v1.ModifyAckDeadlineRequest;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.PullRequest;
import com.google.pubsub.v1.PullResponse;
import com.google.pubsub.v1.ReceivedMessage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.fwmt.common.messaging.FieldWorkerInstructionJsonCodec;
import uk.gov.ons.census.fwmt.tests.acceptance.utils.NodeCheck;

/** Real Google Pub/Sub implementation for the acceptance test messaging client. */
@Slf4j
@Component
@ConditionalOnProperty(name = "fwmt.pubsub.mode", havingValue = "gcp")
public class GcpPubSubMessaging implements MessagingTestClient {

  private static final String TYPE_CANCEL = "cancel";
  private static final Pattern EVENT_TYPE_PATTERN =
      Pattern.compile("\"type\"\\s*:\\s*\"([^\"]+)\"");

  @Value("${fwmt.pubsub.project:c31-fwmtg-dev}")
  private String pubsubProject;

  @Value("${fwmt.pubsub.allowServiceSubscriptionDrain:false}")
  private boolean allowServiceSubscriptionDrain;

  private PubSubOperations operations;

  public GcpPubSubMessaging() {}

  GcpPubSubMessaging(PubSubOperations operations, boolean allowServiceSubscriptionDrain) {
    this.operations = operations;
    this.allowServiceSubscriptionDrain = allowServiceSubscriptionDrain;
  }

  @Override
  public long getMessageCount(String logicalQueue) {
    return PubSubTestLane.forLogicalQueue(logicalQueue).map(this::countAvailableMessages).orElse(0L);
  }

  @Override
  public String getMessage(String logicalQueue, int msTimeout, int msInterval) throws InterruptedException {
    PubSubTestLane lane =
        PubSubTestLane.forLogicalQueue(logicalQueue)
            .orElseThrow(
                () -> new IllegalArgumentException("No Pub/Sub test lane for queue: " + logicalQueue));
    int iterations = Math.max(1, (msTimeout + msInterval - 1) / msInterval);
    for (int i = 0; i < iterations; i++) {
      List<TestMessage> batch = operations().pull(lane.testSubscription(), 1);
      if (!batch.isEmpty()) {
        TestMessage received = batch.getFirst();
        operations().acknowledge(lane.testSubscription(), List.of(received.ackId()));
        return received.data();
      }
      Thread.sleep(msInterval);
    }
    return null;
  }

  @Override
  public String getMessageWithEventType(String logicalQueue, String eventType, int msTimeout, int msInterval)
      throws InterruptedException {
    PubSubTestLane lane =
        PubSubTestLane.forLogicalQueue(logicalQueue)
            .orElseThrow(
                () -> new IllegalArgumentException("No Pub/Sub test lane for queue: " + logicalQueue));
    int iterations = Math.max(1, (msTimeout + msInterval - 1) / msInterval);
    for (int i = 0; i < iterations; i++) {
      List<TestMessage> batch = operations().pull(lane.testSubscription(), 10);
      if (!batch.isEmpty()) {
        List<String> ackIdsToRelease = new ArrayList<>();
        for (TestMessage received : batch) {
          String actualEventType = parseEventType(received.data());
          if (eventType.equals(actualEventType)) {
            operations().acknowledge(lane.testSubscription(), List.of(received.ackId()));
            if (!ackIdsToRelease.isEmpty()) {
              operations().release(lane.testSubscription(), ackIdsToRelease);
            }
            return received.data();
          }
          ackIdsToRelease.add(received.ackId());
        }
        if (!ackIdsToRelease.isEmpty()) {
          operations().release(lane.testSubscription(), ackIdsToRelease);
        }
      }
      Thread.sleep(msInterval);
    }
    return null;
  }

  @Override
  public void publishFieldWorkerInstruction(String messageJson, String instructionType) {
    Map<String, String> attributes = new HashMap<>();
    attributes.put(FieldWorkerInstructionJsonCodec.TYPE_ID_HEADER, typeIdForInstruction(instructionType));
    attributes.put(FieldWorkerInstructionJsonCodec.TIMESTAMP_HEADER, String.valueOf(System.currentTimeMillis()));
    operations().publish(PubSubTestLane.RM_FIELD.topic(), messageJson, attributes);
  }

  @Override
  public void purge(String... logicalQueues) {
    for (String queue : logicalQueues) {
      PubSubTestLane.forLogicalQueue(queue).ifPresent(this::drainForLane);
    }
  }

  @Override
  public void ensureOutcomeBindings() throws IOException, TimeoutException, InterruptedException {
    // Acceptance subscriptions are provisioned separately by the GCP bootstrap step.
  }

  @Override
  public NodeCheck doMessagingPreFlightCheck() {
    NodeCheck.NodeCheckBuilder builder =
        NodeCheck.builder().name("Google Pub/Sub").url("projects/" + pubsubProject);
    if (operations().isReachable()) {
      drainTestSubscription(PubSubTestLane.FIELD_REFUSALS);
      builder.isSuccesful(true);
    } else {
      builder.isSuccesful(false).failureMsg("Google Pub/Sub is not reachable");
    }
    return builder.build();
  }

  private void drainForLane(PubSubTestLane lane) {
    drainTestSubscription(lane);
    if (allowServiceSubscriptionDrain) {
      lane.serviceSubscription().ifPresent(operations()::drainSubscription);
    }
  }

  private void drainTestSubscription(PubSubTestLane lane) {
    operations().drainSubscription(lane.testSubscription());
  }

  private long countAvailableMessages(PubSubTestLane lane) {
    long count = 0;
    while (true) {
      List<TestMessage> batch = operations().pull(lane.testSubscription(), 100);
      if (batch == null || batch.isEmpty()) {
        return count;
      }
      count += batch.size();
      operations()
          .acknowledge(
              lane.testSubscription(), batch.stream().map(TestMessage::ackId).toList());
    }
  }

  private PubSubOperations operations() {
    if (operations == null) {
      operations = new GooglePubSubOperations(pubsubProject);
    }
    return operations;
  }

  private static String parseEventType(String json) {
    Matcher matcher = EVENT_TYPE_PATTERN.matcher(json);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return "";
  }

  private static String typeIdForInstruction(String instructionType) {
    if (TYPE_CANCEL.equals(instructionType)) {
      return "uk.gov.ons.census.fwmt.common.rm.dto.FwmtCancelActionInstruction";
    }
    return "uk.gov.ons.census.fwmt.common.rm.dto.FwmtActionInstruction";
  }

  interface PubSubOperations {
    boolean isReachable();

    void publish(String topicId, String jsonBody, Map<String, String> attributes);

    List<TestMessage> pull(String subscriptionId, int maxMessages);

    void acknowledge(String subscriptionId, List<String> ackIds);

    void release(String subscriptionId, List<String> ackIds);

    void drainSubscription(String subscriptionId);
  }

  record TestMessage(String ackId, String data, Map<String, String> attributes) {}

  private static final class GooglePubSubOperations implements PubSubOperations {
    private final String projectId;

    private GooglePubSubOperations(String projectId) {
      this.projectId = projectId;
    }

    @Override
    public boolean isReachable() {
      // Use a lightweight pull against known acceptance subscriptions so preflight
      // does not require broad topic-list permissions.
      for (PubSubTestLane lane : List.of(PubSubTestLane.RM_FIELD, PubSubTestLane.OUTCOME_PREPROCESSING)) {
        try {
          pull(lane.testSubscription(), 1);
          return true;
        } catch (Exception e) {
          log.debug(
              "Pub/Sub pull probe failed for project {} subscription {}: {}",
              projectId,
              lane.testSubscription(),
              e.getMessage());
        }
      }

      return false;
    }

    @Override
    public void publish(String topicId, String jsonBody, Map<String, String> attributes) {
      ProjectTopicName topicName = ProjectTopicName.of(projectId, topicId);
      Publisher publisher = null;
      try {
        publisher = Publisher.newBuilder(topicName).build();
        PubsubMessage message =
            PubsubMessage.newBuilder()
                .setData(ByteString.copyFromUtf8(jsonBody))
                .putAllAttributes(attributes)
                .build();
        ApiFuture<String> future = publisher.publish(message);
        future.get();
      } catch (IOException | InterruptedException | ExecutionException e) {
        if (e instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        throw new IllegalStateException("Failed to publish to topic " + topicId, e);
      } finally {
        shutdownPublisher(publisher);
      }
    }

    @Override
    public List<TestMessage> pull(String subscriptionId, int maxMessages) {
      try (SubscriberStub subscriber = GrpcSubscriberStub.create(SubscriberStubSettings.newBuilder().build())) {
        return pullWithStub(subscriber, subscriptionId, maxMessages);
      } catch (IOException e) {
        throw new IllegalStateException("Failed to create subscriber stub for subscription " + subscriptionId, e);
      }
    }

    private List<TestMessage> pullWithStub(SubscriberStub subscriber, String subscriptionId, int maxMessages) {
      PullRequest request =
          PullRequest.newBuilder()
              .setSubscription(ProjectSubscriptionName.of(projectId, subscriptionId).toString())
              .setMaxMessages(maxMessages)
              .setReturnImmediately(true)
              .build();
      PullResponse response = subscriber.pullCallable().call(request);
      return response.getReceivedMessagesList().stream().map(this::toEnvelope).toList();
    }

    @Override
    public void acknowledge(String subscriptionId, List<String> ackIds) {
      if (ackIds.isEmpty()) {
        return;
      }
      try (SubscriberStub subscriber = GrpcSubscriberStub.create(SubscriberStubSettings.newBuilder().build())) {
        acknowledgeWithStub(subscriber, subscriptionId, ackIds);
      } catch (IOException e) {
        throw new IllegalStateException("Failed to create subscriber stub for subscription " + subscriptionId, e);
      }
    }

    private void acknowledgeWithStub(SubscriberStub subscriber, String subscriptionId, List<String> ackIds) {
      AcknowledgeRequest request =
          AcknowledgeRequest.newBuilder()
              .setSubscription(ProjectSubscriptionName.of(projectId, subscriptionId).toString())
              .addAllAckIds(ackIds)
              .build();
      subscriber.acknowledgeCallable().call(request);
    }

    @Override
    public void release(String subscriptionId, List<String> ackIds) {
      if (ackIds.isEmpty()) {
        return;
      }
      ModifyAckDeadlineRequest request =
          ModifyAckDeadlineRequest.newBuilder()
              .setSubscription(ProjectSubscriptionName.of(projectId, subscriptionId).toString())
              .addAllAckIds(ackIds)
              .setAckDeadlineSeconds(0)
              .build();
      try (SubscriberStub subscriber = GrpcSubscriberStub.create(SubscriberStubSettings.newBuilder().build())) {
        subscriber.modifyAckDeadlineCallable().call(request);
      } catch (IOException e) {
        throw new IllegalStateException(
            "Failed to release messages for subscription " + subscriptionId, e);
      }
    }

    @Override
    public void drainSubscription(String subscriptionId) {
      try (SubscriberStub subscriber = GrpcSubscriberStub.create(SubscriberStubSettings.newBuilder().build())) {
        while (true) {
          List<TestMessage> batch = pullWithStub(subscriber, subscriptionId, 500);
          if (batch.isEmpty()) {
            return;
          }
          acknowledgeWithStub(subscriber, subscriptionId, batch.stream().map(TestMessage::ackId).toList());
        }
      } catch (IOException e) {
        throw new IllegalStateException("Failed to drain subscription " + subscriptionId, e);
      }
    }

    private TestMessage toEnvelope(ReceivedMessage receivedMessage) {
      PubsubMessage message = receivedMessage.getMessage();
      return new TestMessage(
          receivedMessage.getAckId(), message.getData().toStringUtf8(), message.getAttributesMap());
    }

    private void shutdownPublisher(Publisher publisher) {
      if (publisher == null) {
        return;
      }
      publisher.shutdown();
      try {
        publisher.awaitTermination(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }
}


