package uk.gov.ons.census.fwmt.tests.acceptance.messaging;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.fwmt.tests.acceptance.utils.NodeCheck;

@Component
public class DelegatingMessagingTestClient implements MessagingTestClient {

  @Value("${fwmt.pubsub.project:fwmt-local}")
  private String pubsubProject;

  @Value("${fwmt.pubsub.emulatorHost:localhost:8085}")
  private String pubsubEmulatorHost;

  private PubSubEmulatorMessaging pubSub;

  private PubSubEmulatorMessaging pubSub() {
    if (pubSub == null) {
      pubSub = new PubSubEmulatorMessaging(pubsubProject, pubsubEmulatorHost);
    }
    return pubSub;
  }

  @Override
  public long getMessageCount(String logicalQueue) {
    return PubSubTestLane.forRabbitQueue(logicalQueue)
        .map(lane -> pubSub().countAvailableMessages(lane))
        .orElse(0L);
  }

  @Override
  public String getMessage(String logicalQueue, int msTimeout, int msInterval) throws InterruptedException {
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

  @Override
  public String getMessageWithEventType(String logicalQueue, String eventType, int msTimeout, int msInterval)
      throws InterruptedException {
    PubSubTestLane lane = PubSubTestLane.forRabbitQueue(logicalQueue)
        .orElseThrow(() -> new IllegalArgumentException("No Pub/Sub test lane for queue: " + logicalQueue));
    return pubSub().pullMessageWithEventType(lane, eventType, msTimeout, msInterval);
  }

  @Override
  public void publishFieldWorkerInstruction(String messageJson, String instructionType) {
    pubSub().publishFieldWorkerInstruction(messageJson, instructionType);
  }

  @Override
  public void purge(String... logicalQueues) {
    for (String queue : logicalQueues) {
      PubSubTestLane.forRabbitQueue(queue).ifPresent(lane -> pubSub().drainSubscription(lane));
    }
  }

  @Override
  public void ensureOutcomeBindings() throws IOException, TimeoutException, InterruptedException {
    // Pub/Sub topics and subscriptions are created by setup-messaging.sh.
  }

  @Override
  public NodeCheck doMessagingPreFlightCheck() {
    NodeCheck.NodeCheckBuilder builder = NodeCheck.builder().name("Pub/Sub emulator").url(pubsubEmulatorHost);
    if (pubSub().isReachable()) {
      pubSub().drainSubscription(PubSubTestLane.FIELD_REFUSALS);
      builder.isSuccesful(true);
    } else {
      builder.isSuccesful(false).failureMsg("Pub/Sub emulator is not reachable");
    }
    return builder.build();
  }

  @Override
  public boolean usesRabbitListenerControl() {
    // Pause job-service / outcome-service consumers during queue reset.
    return true;
  }
}
