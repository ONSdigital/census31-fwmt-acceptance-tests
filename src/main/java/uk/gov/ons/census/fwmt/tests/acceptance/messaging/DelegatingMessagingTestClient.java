package uk.gov.ons.census.fwmt.tests.acceptance.messaging;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.fwmt.tests.acceptance.utils.NodeCheck;
import uk.gov.ons.census.fwmt.tests.acceptance.utils.QueueUtils;

@Component
public class DelegatingMessagingTestClient implements MessagingTestClient {

  @Value("${fwmt.messaging.provider:rabbit}")
  private String messagingProvider;

  @Value("${fwmt.pubsub.project:fwmt-local}")
  private String pubsubProject;

  @Value("${fwmt.pubsub.emulatorHost:localhost:8085}")
  private String pubsubEmulatorHost;

  private final QueueUtils queueUtils;
  private PubSubEmulatorMessaging pubSub;

  public DelegatingMessagingTestClient(QueueUtils queueUtils) {
    this.queueUtils = queueUtils;
  }

  private PubSubEmulatorMessaging pubSub() {
    if (pubSub == null) {
      pubSub = new PubSubEmulatorMessaging(pubsubProject, pubsubEmulatorHost);
    }
    return pubSub;
  }

  private boolean isPubSub() {
    return "pubsub".equalsIgnoreCase(messagingProvider);
  }

  @Override
  public long getMessageCount(String logicalQueue) {
    if (isPubSub()) {
      return PubSubTestLane.forRabbitQueue(logicalQueue)
          .map(lane -> pubSub().countAvailableMessages(lane))
          .orElse(0L);
    }
    return queueUtils.getMessageCount(logicalQueue);
  }

  @Override
  public String getMessage(String logicalQueue, int msTimeout, int msInterval) throws InterruptedException {
    if (isPubSub()) {
      PubSubTestLane lane = PubSubTestLane.forRabbitQueue(logicalQueue)
          .orElseThrow(() -> new IllegalArgumentException("No Pub/Sub test lane for queue: " + logicalQueue));
      String message = null;
      int iterations = (msTimeout + msInterval - 1) / msInterval;
      for (int i = 0; i < iterations; i++) {
        message = pubSub().pullOneMessage(lane, true);
        if (message != null) {
          break;
        }
        Thread.sleep(msInterval);
      }
      return message;
    }
    String message = null;
    int iterations = (msTimeout + msInterval - 1) / msInterval;
    for (int i = 0; i < iterations; i++) {
      message = queueUtils.getMessageOffQueue(logicalQueue);
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
    if (isPubSub()) {
      PubSubTestLane lane = PubSubTestLane.forRabbitQueue(logicalQueue)
          .orElseThrow(() -> new IllegalArgumentException("No Pub/Sub test lane for queue: " + logicalQueue));
      return pubSub().pullMessageWithEventType(lane, eventType, msTimeout, msInterval);
    }
    int iterations = (msTimeout + msInterval - 1) / msInterval;
    for (int i = 0; i < iterations; i++) {
      String message = queueUtils.getMessageOffQueue(logicalQueue);
      if (message != null && message.contains("\"type\":\"" + eventType + "\"")) {
        return message;
      }
      Thread.sleep(msInterval);
    }
    return null;
  }

  @Override
  public void publishFieldWorkerInstruction(String messageJson, String instructionType) {
    if (isPubSub()) {
      pubSub().publishFieldWorkerInstruction(messageJson, instructionType);
    } else {
      queueUtils.addMessage("", "RM.Field", messageJson, instructionType);
    }
  }

  @Override
  public void purge(String... logicalQueues) {
    if (isPubSub()) {
      for (String queue : logicalQueues) {
        PubSubTestLane.forRabbitQueue(queue).ifPresent(lane -> pubSub().drainSubscription(lane));
      }
    } else {
      for (String queue : logicalQueues) {
        queueUtils.deleteMessage(queue);
      }
    }
  }

  @Override
  public void ensureOutcomeBindings() throws IOException, TimeoutException, InterruptedException {
    if (!isPubSub()) {
      queueUtils.createOutcomeQueues();
    }
  }

  @Override
  public NodeCheck doMessagingPreFlightCheck() {
    if (isPubSub()) {
      NodeCheck.NodeCheckBuilder builder = NodeCheck.builder().name("Pub/Sub emulator").url(pubsubEmulatorHost);
      if (pubSub().isReachable()) {
        pubSub().drainSubscription(PubSubTestLane.FIELD_REFUSALS);
        builder.isSuccesful(true);
      } else {
        builder.isSuccesful(false).failureMsg("Pub/Sub emulator is not reachable");
      }
      return builder.build();
    }
    return queueUtils.doPreFlightCheck();
  }

  @Override
  public boolean usesRabbitListenerControl() {
    // Pause job-service / outcome-service consumers during queue reset (Rabbit and Pub/Sub).
    return true;
  }
}
