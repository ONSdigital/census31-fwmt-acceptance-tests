package uk.gov.ons.census.fwmt.tests.acceptance.messaging;

import com.google.api.core.ApiFuture;
import com.google.api.gax.rpc.BidiStreamingCallable;
import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
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
import com.google.pubsub.v1.StreamingPullRequest;
import com.google.pubsub.v1.StreamingPullResponse;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
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

  @Value("${fwmt.pubsub.streaming-pull.enabled:false}")
  private boolean streamingPullEnabled;

  private PubSubOperations operations;

  public GcpPubSubMessaging() {}

  GcpPubSubMessaging(PubSubOperations operations, boolean allowServiceSubscriptionDrain) {
    this.operations = operations;
    this.allowServiceSubscriptionDrain = allowServiceSubscriptionDrain;
  }

  GcpPubSubMessaging(PubSubOperations operations, boolean allowServiceSubscriptionDrain, boolean streamingPullEnabled) {
    this.operations = operations;
    this.allowServiceSubscriptionDrain = allowServiceSubscriptionDrain;
    this.streamingPullEnabled = streamingPullEnabled;
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
            lane.testSubscription(), batch.stream().map(message -> message.ackId()).toList());
    }
  }

  private PubSubOperations operations() {
    if (operations == null) {
      operations = new GooglePubSubOperations(pubsubProject, streamingPullEnabled);
    }
    return operations;
  }

  @PreDestroy
  void closeOperations() {
    if (operations != null) {
      operations.close();
    }
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

    default int pullerParallelismFor(String subscriptionId) {
      return 1;
    }

    default void close() {}
  }

  record TestMessage(String ackId, String data, Map<String, String> attributes) {}

  @FunctionalInterface
  interface SubscriberStubFactory {
    SubscriberStub create() throws IOException;
  }

  static final class GooglePubSubOperations implements PubSubOperations {
    private static final int DRAIN_PULL_BATCH_SIZE = 1000;
    private static final int DEFAULT_PULLER_PARALLELISM = 1;
    private static final int RM_FIELD_PULLER_PARALLELISM = 3;
    private static final Map<String, Integer> PULLER_PARALLELISM_BY_SUB_PREFIX =
        Map.of("acceptance-tests-RM-Field", RM_FIELD_PULLER_PARALLELISM);

    private final String projectId;
    private final boolean streamingPullEnabled;
    private final SubscriberStubFactory subscriberStubFactory;
    private SubscriberStub subscriberStub;

    private GooglePubSubOperations(String projectId) {
      this(projectId, false, () -> GrpcSubscriberStub.create(SubscriberStubSettings.newBuilder().build()));
    }

    GooglePubSubOperations(String projectId, boolean streamingPullEnabled) {
      this(projectId, streamingPullEnabled, () -> GrpcSubscriberStub.create(SubscriberStubSettings.newBuilder().build()));
    }

    GooglePubSubOperations(String projectId, SubscriberStubFactory subscriberStubFactory) {
      this(projectId, false, subscriberStubFactory);
    }

    GooglePubSubOperations(
        String projectId, boolean streamingPullEnabled, SubscriberStubFactory subscriberStubFactory) {
      this.projectId = projectId;
      this.streamingPullEnabled = streamingPullEnabled;
      this.subscriberStubFactory = subscriberStubFactory;
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
      return pullWithStub(subscriber(), subscriptionId, maxMessages);
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
      acknowledgeWithStub(subscriber(), subscriptionId, ackIds);
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
      subscriber().modifyAckDeadlineCallable().call(request);
    }

    @Override
    public void drainSubscription(String subscriptionId) {
      if (streamingPullEnabled) {
        try {
          drainByStreamingPull(subscriber(), subscriptionId);
          return;
        } catch (Exception e) {
          log.warn(
              "Streaming pull drain failed for subscription {}, falling back to pipelined pull drain: {}",
              subscriptionId,
              e.getMessage());
        }
      }
      drainByPipelinedPull(subscriber(), subscriptionId, pullerParallelismFor(subscriptionId));
    }

    @Override
    public int pullerParallelismFor(String subscriptionId) {
      return PULLER_PARALLELISM_BY_SUB_PREFIX.entrySet().stream()
          .filter(entry -> subscriptionId.startsWith(entry.getKey()))
          .map(Map.Entry::getValue)
          .findFirst()
          .orElse(DEFAULT_PULLER_PARALLELISM);
    }

    /**
     * Drains a subscription using multiple concurrent pullers, each pipelining its pulls with the
     * acknowledgements of its previous batch on shared background ack threads. Multiple pullers on
     * one subscription are safe (Pub/Sub distributes pulls; the shared gRPC stub is thread-safe)
     * and are used to raise throughput on the hot RM.Field lane, which dominates the queue-reset
     * critical path. Pipelining overlaps batch k+1's pull with batch k's ack, ~1 RTT per batch.
     */
    private void drainByPipelinedPull(
        SubscriberStub subscriber, String subscriptionId, int pullerParallelism) {
      ExecutorService pullerExecutor =
          Executors.newFixedThreadPool(
              pullerParallelism,
              r -> {
                Thread thread = new Thread(r, "drain-pull-" + subscriptionId);
                thread.setDaemon(true);
                return thread;
              });
      ExecutorService ackExecutor =
          Executors.newFixedThreadPool(
              pullerParallelism,
              r -> {
                Thread thread = new Thread(r, "drain-ack-" + subscriptionId);
                thread.setDaemon(true);
                return thread;
              });
      try {
        List<Future<?>> pullerFutures = new ArrayList<>();
        for (int i = 0; i < pullerParallelism; i++) {
          pullerFutures.add(pullerExecutor.submit(() -> pipelinedPullLoop(subscriber, subscriptionId, ackExecutor)));
        }
        for (Future<?> future : pullerFutures) {
          awaitPuller(future, subscriptionId);
        }
      } finally {
        ackExecutor.shutdownNow();
        pullerExecutor.shutdownNow();
      }
    }

    private void pipelinedPullLoop(
        SubscriberStub subscriber, String subscriptionId, ExecutorService ackExecutor) {
      Future<?> ackFuture = null;
      while (true) {
        List<TestMessage> batch = pullWithStub(subscriber, subscriptionId, DRAIN_PULL_BATCH_SIZE);
        if (ackFuture != null) {
          awaitAck(ackFuture, subscriptionId);
        }
        if (batch.isEmpty()) {
          return;
        }
        List<TestMessage> batchToAck = batch;
        ackFuture =
            ackExecutor.submit(
                () ->
                    acknowledgeWithStub(
                        subscriber,
                        subscriptionId,
                        batchToAck.stream().map(message -> message.ackId()).toList()));
      }
    }

    private static void awaitPuller(Future<?> pullerFuture, String subscriptionId) {
      try {
        pullerFuture.get();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(
            "Interrupted while draining subscription " + subscriptionId, e);
      } catch (ExecutionException e) {
        throw new IllegalStateException(
            "Failed to drain subscription " + subscriptionId, e.getCause());
      }
    }

    private static void awaitAck(Future<?> ackFuture, String subscriptionId) {
      try {
        ackFuture.get();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(
            "Interrupted while awaiting acknowledgement for subscription " + subscriptionId, e);
      } catch (ExecutionException e) {
        throw new IllegalStateException(
            "Failed to acknowledge messages on subscription " + subscriptionId, e.getCause());
      }
    }

    /**
     * Prototype drain using the Pub/Sub StreamingPull bidirectional stream instead of repeated
     * pull-RPCs. A persistent stream receives messages with zero per-batch RPC RTT and acks are
     * sent back on the same stream. Enabled behind the property
     * {@code fwmt.pubsub.streaming-pull.enabled} (default false); any failure falls back to the
     * multi-puller pipelined drain in {@link #drainByPipelinedPull}.
     */
    private void drainByStreamingPull(SubscriberStub subscriber, String subscriptionId) {
      String subscriptionPath = ProjectSubscriptionName.of(projectId, subscriptionId).toString();
      AtomicBoolean streamClosed = new AtomicBoolean(false);
      LinkedBlockingQueue<StreamingPullResponse> responses = new LinkedBlockingQueue<>();

      ResponseObserver<StreamingPullResponse> responseObserver =
          new ResponseObserver<>() {
            @Override
            public void onStart(StreamController controller) {}

            @Override
            public void onResponse(StreamingPullResponse response) {
              try {
                responses.put(response);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            }

            @Override
            public void onError(Throwable t) {
              streamClosed.set(true);
              responses.add(StreamingPullResponse.getDefaultInstance());
            }

            @Override
            public void onComplete() {
              streamClosed.set(true);
              responses.add(StreamingPullResponse.getDefaultInstance());
            }
          };

      BidiStreamingCallable<StreamingPullRequest, StreamingPullResponse> callable =
          subscriber.streamingPullCallable();
      ClientStream<StreamingPullRequest> requestStream = callable.splitCall(responseObserver);

      // Initial request: subscribe to the stream. Subsequent requests carry acks.
      requestStream.send(
          StreamingPullRequest.newBuilder()
              .setSubscription(subscriptionPath)
              .setStreamAckDeadlineSeconds(60)
              .build());

      ExecutorService drainExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "drain-stream-" + subscriptionId);
        thread.setDaemon(true);
        return thread;
      });
      try {
        Future<?> drainResult =
            drainExecutor.submit(
                () -> {
                  int consecutiveEmpty = 0;
                  while (true) {
                    StreamingPullResponse response;
                    try {
                      response = responses.take();
                    } catch (InterruptedException e) {
                      Thread.currentThread().interrupt();
                      return;
                    }
                    List<ReceivedMessage> messages = response.getReceivedMessagesList();
                    for (ReceivedMessage received : messages) {
                      requestStream.send(
                          StreamingPullRequest.newBuilder()
                              .setSubscription(subscriptionPath)
                              .addAllAckIds(List.of(received.getAckId()))
                              .build());
                    }
                    if (messages.isEmpty()) {
                      if (streamClosed.get()) {
                        return;
                      }
                      consecutiveEmpty++;
                      if (consecutiveEmpty >= 2) {
                        return;
                      }
                    } else {
                      consecutiveEmpty = 0;
                    }
                  }
                });
        drainResult.get();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(
            "Interrupted while streaming-draining subscription " + subscriptionId, e);
      } catch (ExecutionException e) {
        throw new IllegalStateException(
            "Failed to streaming-drain subscription " + subscriptionId, e.getCause());
      } finally {
        requestStream.closeSend();
        drainExecutor.shutdownNow();
      }
    }

    @Override
    public synchronized void close() {
      if (subscriberStub == null) {
        return;
      }
      subscriberStub.close();
      subscriberStub = null;
    }

    private synchronized SubscriberStub subscriber() {
      if (subscriberStub != null) {
        return subscriberStub;
      }
      try {
        subscriberStub = subscriberStubFactory.create();
        return subscriberStub;
      } catch (IOException e) {
        throw new IllegalStateException("Failed to create shared subscriber stub for project " + projectId, e);
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


