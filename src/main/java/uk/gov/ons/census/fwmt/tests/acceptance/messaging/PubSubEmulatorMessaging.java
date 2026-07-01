package uk.gov.ons.census.fwmt.tests.acceptance.messaging;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import uk.gov.ons.census.fwmt.common.messaging.FieldWorkerInstructionJsonCodec;

@Slf4j
class PubSubEmulatorMessaging {

  private static final String TYPE_CANCEL = "cancel";
  private static final Pattern EVENT_TYPE_PATTERN =
      Pattern.compile("\"type\"\\s*:\\s*\"([^\"]+)\"");

  private final PubSubEmulatorHttp http;

  PubSubEmulatorMessaging(String projectId, String emulatorHost) {
    this.http = new PubSubEmulatorHttp(projectId, emulatorHost);
  }

  void publishFieldWorkerInstruction(String messageJson, String instructionType) {
    Map<String, String> attributes = new HashMap<>();
    attributes.put(FieldWorkerInstructionJsonCodec.TYPE_ID_HEADER, typeIdForInstruction(instructionType));
    attributes.put(FieldWorkerInstructionJsonCodec.TIMESTAMP_HEADER, String.valueOf(System.currentTimeMillis()));
    http.publish(PubSubTestLane.RM_FIELD.topic(), messageJson, attributes);
  }

  void drainSubscription(PubSubTestLane lane) {
    http.drainSubscription(lane.testSubscription());
    lane.serviceSubscription().ifPresent(http::drainSubscription);
  }

  String pullOneMessage(PubSubTestLane lane, boolean ack) {
    List<PubSubEmulatorHttp.ReceivedPubSubMessage> batch = http.pull(lane.testSubscription(), 1, true);
    if (batch.isEmpty()) {
      return null;
    }
    PubSubEmulatorHttp.ReceivedPubSubMessage received = batch.get(0);
    if (ack) {
      http.acknowledge(lane.testSubscription(), List.of(received.ackId()));
    }
    return received.data();
  }

  String pullMessageWithEventType(PubSubTestLane lane, String expectedEventType, int msTimeout, int msInterval) {
    int iterations = Math.max(1, (msTimeout + msInterval - 1) / msInterval);
    for (int i = 0; i < iterations; i++) {
      List<PubSubEmulatorHttp.ReceivedPubSubMessage> batch = http.pull(lane.testSubscription(), 10, true);
      if (!batch.isEmpty()) {
        List<String> ackIds = new java.util.ArrayList<>();
        for (PubSubEmulatorHttp.ReceivedPubSubMessage received : batch) {
          String eventType = parseEventType(received.data());
          if (expectedEventType.equals(eventType)) {
            http.acknowledge(lane.testSubscription(), List.of(received.ackId()));
            return received.data();
          }
          // Non-matching messages are republished so later assertions can still consume them.
          http.publish(lane.topic(), received.data(), Map.of("contentType", "application/json"));
          ackIds.add(received.ackId());
        }
        http.acknowledge(lane.testSubscription(), ackIds);
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

  private static String parseEventType(String json) {
    Matcher matcher = EVENT_TYPE_PATTERN.matcher(json);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return "";
  }

  long countAvailableMessages(PubSubTestLane lane) {
    long count = 0;
    while (true) {
      List<PubSubEmulatorHttp.ReceivedPubSubMessage> batch = http.pull(lane.testSubscription(), 100, true);
      if (batch.isEmpty()) {
        return count;
      }
      count += batch.size();
      List<String> ackIds = batch.stream().map(PubSubEmulatorHttp.ReceivedPubSubMessage::ackId).toList();
      http.acknowledge(lane.testSubscription(), ackIds);
    }
  }

  boolean isReachable() {
    return http.isReachable();
  }

  PubSubEmulatorHttp http() {
    return http;
  }

  private static String typeIdForInstruction(String instructionType) {
    if (TYPE_CANCEL.equals(instructionType)) {
      return "uk.gov.ons.census.fwmt.common.rm.dto.FwmtCancelActionInstruction";
    }
    return "uk.gov.ons.census.fwmt.common.rm.dto.FwmtActionInstruction";
  }
}
