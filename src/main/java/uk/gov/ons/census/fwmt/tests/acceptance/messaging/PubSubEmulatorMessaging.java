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
  private final Map<PubSubTestLane, Deque<String>> bufferedMessages =
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
    PubSubTestLane lane = PubSubTestLane.forLogicalQueue(logicalQueue)
        .orElseThrow(() -> new IllegalArgumentException("No Pub/Sub test lane for queue: " + logicalQueue));
    String message = removeFirstBuffered(lane);
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
    http().publish(PubSubTestLane.RM_FIELD.topic(), messageJson, attributes);
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

  String pullOneMessage(PubSubTestLane lane, boolean ack) {
    List<PubSubEmulatorHttp.ReceivedPubSubMessage> batch = http().pull(lane.testSubscription(), 1, true);
    if (batch.isEmpty()) {
      return null;
    }
    PubSubEmulatorHttp.ReceivedPubSubMessage received = batch.get(0);
    if (ack) {
      http().acknowledge(lane.testSubscription(), List.of(received.ackId()));
    }
    return received.data();
  }

  String pullMessageWithEventType(PubSubTestLane lane, String expectedEventType, int msTimeout, int msInterval) {
    String bufferedMatch = removeBuffered(lane, expectedEventType);
    if (bufferedMatch != null) {
      return bufferedMatch;
    }

    int iterations = Math.max(1, (msTimeout + msInterval - 1) / msInterval);
    for (int i = 0; i < iterations; i++) {
      List<PubSubEmulatorHttp.ReceivedPubSubMessage> batch = http().pull(lane.testSubscription(), 10, true);
      if (!batch.isEmpty()) {
        String matchedMessage = null;
        List<String> ackIds = new java.util.ArrayList<>();
        for (PubSubEmulatorHttp.ReceivedPubSubMessage received : batch) {
          ackIds.add(received.ackId());
          String eventType = parseEventType(received.data());
          if (matchedMessage == null && expectedEventType.equals(eventType)) {
            matchedMessage = received.data();
          } else {
            buffer(lane, received.data());
          }
        }
        http().acknowledge(lane.testSubscription(), ackIds);
        performanceTimingRecorder.recordRmMessagePull(batch.size(), 0, matchedMessage != null);
        if (matchedMessage != null) {
          return matchedMessage;
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

  private synchronized void buffer(PubSubTestLane lane, String message) {
    bufferedMessages.computeIfAbsent(lane, ignored -> new ArrayDeque<>()).addLast(message);
  }

  private synchronized String removeFirstBuffered(PubSubTestLane lane) {
    Deque<String> messages = bufferedMessages.get(lane);
    return messages == null ? null : messages.pollFirst();
  }

  private synchronized String removeBuffered(PubSubTestLane lane, String expectedEventType) {
    Deque<String> messages = bufferedMessages.get(lane);
    if (messages == null) {
      return null;
    }
    Iterator<String> iterator = messages.iterator();
    while (iterator.hasNext()) {
      String message = iterator.next();
      if (expectedEventType.equals(parseEventType(message))) {
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
      List<String> ackIds = batch.stream().map(PubSubEmulatorHttp.ReceivedPubSubMessage::ackId).toList();
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
