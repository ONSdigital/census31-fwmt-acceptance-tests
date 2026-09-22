package uk.gov.ons.census.fwmt.tests.acceptance.messaging;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.fwmt.common.messaging.FieldWorkerInstructionJsonCodec;
import uk.gov.ons.census.fwmt.tests.acceptance.timing.PerformanceTimingRecorder;
import uk.gov.ons.census.fwmt.tests.acceptance.utils.NodeCheck;

@Slf4j
@Component
@ConditionalOnProperty(name = "fwmt.pubsub.mode", havingValue = "emulator", matchIfMissing = true)
public class PubSubEmulatorMessaging implements MessagingTestClient {

  private static final String TYPE_CANCEL = "cancel";
  private static final Pattern EVENT_TYPE_PATTERN =
      Pattern.compile("\"type\"\\s*:\\s*\"([^\"]+)\"");

  @Value("${fwmt.pubsub.project:fwmt-local}")
  private String pubsubProject;

  @Value("${fwmt.pubsub.emulatorHost:localhost:8085}")
  private String pubsubEmulatorHost;

  @Autowired
  private PerformanceTimingRecorder performanceTimingRecorder;

  private PubSubEmulatorHttp http;
  private final Map<PubSubTestLane, Deque<ObservedMessage>> bufferedMessages =
      new EnumMap<>(PubSubTestLane.class);

  PubSubEmulatorMessaging() {
  }

  PubSubEmulatorMessaging(
      PubSubEmulatorHttp http, PerformanceTimingRecorder performanceTimingRecorder) {
    this.http = http;
    this.performanceTimingRecorder = performanceTimingRecorder;
  }

  private PubSubEmulatorHttp http() {
    if (http == null) {
      http = new PubSubEmulatorHttp(pubsubProject, pubsubEmulatorHost);
    }
    return http;
  }

  @Override
  public long getMessageCount(String logicalQueue) {
    return PubSubTestLane.forLogicalQueue(logicalQueue)
        .map(lane -> countAvailableMessages(lane))
        .orElse(0L);
  }

  @Override
  public String getMessage(String logicalQueue, int msTimeout, int msInterval) throws InterruptedException {
    ObservedMessage message = getObservedMessage(logicalQueue, msTimeout, msInterval);
    return message == null ? null : message.body();
  }

  @Override
  public ObservedMessage getObservedMessage(String logicalQueue, int msTimeout, int msInterval)
      throws InterruptedException {
    PubSubTestLane lane = PubSubTestLane.forLogicalQueue(logicalQueue)
        .orElseThrow(() -> new IllegalArgumentException("No Pub/Sub test lane for queue: " + logicalQueue));
    ObservedMessage message = removeFirstBuffered(lane);
    if (message != null) {
      return message;
    }
    int iterations = (msTimeout + msInterval - 1) / msInterval;
    for (int i = 0; i < iterations; i++) {
      message = pullOneMessage(lane, true);
      if (message != null) {
        break;
      }
      Thread.sleep(msInterval);
    }
    return message;
  }

  @Override
  public String getMessageWithEventType(String logicalQueue, String eventType, int msTimeout, int msInterval)
      throws InterruptedException {
    PubSubTestLane lane = PubSubTestLane.forLogicalQueue(logicalQueue)
        .orElseThrow(() -> new IllegalArgumentException("No Pub/Sub test lane for queue: " + logicalQueue));
    return pullMessageWithEventType(lane, eventType, msTimeout, msInterval);
  }

  @Override
  public void publishFieldWorkerInstruction(String messageJson, String instructionType) {
    Map<String, String> attributes = new HashMap<>();
    attributes.put(FieldWorkerInstructionJsonCodec.TYPE_ID_HEADER, typeIdForInstruction(instructionType));
    attributes.put(FieldWorkerInstructionJsonCodec.TIMESTAMP_HEADER, String.valueOf(System.currentTimeMillis()));
    publishToTopic(PubSubTestLane.RM_FIELD.topic(), messageJson, attributes);
  }

  @Override
  public void publishToTopic(String topicId, String messageJson, Map<String, String> attributes) {
    http().publish(topicId, messageJson, attributes);
  }

  @Override
  public void purge(String... logicalQueues) {
    for (String queue : logicalQueues) {
      PubSubTestLane.forLogicalQueue(queue).ifPresent(lane -> {
        clearBuffered(lane);
        drainSubscription(lane);
      });
    }
  }

  @Override
  public void ensureOutcomeBindings() throws IOException, TimeoutException, InterruptedException {
    // Topics and subscriptions are created by setup-messaging.sh.
  }

  @Override
  public NodeCheck doMessagingPreFlightCheck() {
    NodeCheck.NodeCheckBuilder builder = NodeCheck.builder().name("Pub/Sub emulator").url(pubsubEmulatorHost);
    if (http().isReachable()) {
      drainSubscription(PubSubTestLane.FIELD_REFUSALS);
      builder.isSuccesful(true);
    } else {
      builder.isSuccesful(false).failureMsg("Pub/Sub emulator is not reachable");
    }
    return builder.build();
  }

  void drainSubscription(PubSubTestLane lane) {
    http().drainSubscription(lane.testSubscription());
    lane.serviceSubscription().ifPresent(http()::drainSubscription);
  }

  ObservedMessage pullOneMessage(PubSubTestLane lane, boolean ack) {
    List<PubSubEmulatorHttp.ReceivedPubSubMessage> batch = http().pull(lane.testSubscription(), 1, true);
    if (batch.isEmpty()) {
      return null;
    }
    PubSubEmulatorHttp.ReceivedPubSubMessage received = batch.get(0);
    if (ack) {
      http().acknowledge(lane.testSubscription(), List.of(received.ackId()));
    }
    return new ObservedMessage(received.data(), received.attributes());
  }

  String pullMessageWithEventType(PubSubTestLane lane, String expectedEventType, int msTimeout, int msInterval) {
    ObservedMessage bufferedMatch = removeBuffered(lane, expectedEventType);
    if (bufferedMatch != null) {
      return bufferedMatch.body();
    }

    int iterations = Math.max(1, (msTimeout + msInterval - 1) / msInterval);
    for (int i = 0; i < iterations; i++) {
      List<PubSubEmulatorHttp.ReceivedPubSubMessage> batch = http().pull(lane.testSubscription(), 10, true);
      if (!batch.isEmpty()) {
        ObservedMessage matchedMessage = null;
        List<String> ackIds = new java.util.ArrayList<>();
        for (PubSubEmulatorHttp.ReceivedPubSubMessage received : batch) {
          ackIds.add(received.ackId());
          String eventType = parseEventType(received.data());
          if (matchedMessage == null && expectedEventType.equals(eventType)) {
            matchedMessage = new ObservedMessage(received.data(), received.attributes());
          } else {
            buffer(lane, new ObservedMessage(received.data(), received.attributes()));
          }
        }
        http().acknowledge(lane.testSubscription(), ackIds);
        performanceTimingRecorder.recordRmMessagePull(batch.size(), 0, matchedMessage != null);
        if (matchedMessage != null) {
          return matchedMessage.body();
        }
      } else {
        performanceTimingRecorder.recordRmMessagePull(0, 0, false);
      }
      try {
        Thread.sleep(msInterval);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return null;
      }
    }
    return null;
  }

  private synchronized void buffer(PubSubTestLane lane, ObservedMessage message) {
    bufferedMessages.computeIfAbsent(lane, ignored -> new ArrayDeque<>()).addLast(message);
  }

  private synchronized ObservedMessage removeFirstBuffered(PubSubTestLane lane) {
    Deque<ObservedMessage> messages = bufferedMessages.get(lane);
    return messages == null ? null : messages.pollFirst();
  }

  private synchronized ObservedMessage removeBuffered(PubSubTestLane lane, String expectedEventType) {
    Deque<ObservedMessage> messages = bufferedMessages.get(lane);
    if (messages == null) {
      return null;
    }
    Iterator<ObservedMessage> iterator = messages.iterator();
    while (iterator.hasNext()) {
      ObservedMessage message = iterator.next();
      if (expectedEventType.equals(parseEventType(message.body()))) {
        iterator.remove();
        return message;
      }
    }
    return null;
  }

  private synchronized void clearBuffered(PubSubTestLane lane) {
    bufferedMessages.remove(lane);
  }

  private long countAvailableMessages(PubSubTestLane lane) {
    long count = 0;
    while (true) {
      List<PubSubEmulatorHttp.ReceivedPubSubMessage> batch = http().pull(lane.testSubscription(), 100, true);
      if (batch.isEmpty()) {
        return count;
      }
      count += batch.size();
      List<String> ackIds = new java.util.ArrayList<>(batch.size());
      for (PubSubEmulatorHttp.ReceivedPubSubMessage receivedMessage : batch) {
        ackIds.add(receivedMessage.ackId());
      }
      http().acknowledge(lane.testSubscription(), ackIds);
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
}
