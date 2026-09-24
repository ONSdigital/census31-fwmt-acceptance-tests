package uk.gov.ons.census.fwmt.tests.acceptance.messaging;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import uk.gov.ons.census.fwmt.tests.acceptance.utils.NodeCheck;

/**
 * Test harness port for inject/purge/poll messaging lanes via the Pub/Sub emulator.
 */
public interface MessagingTestClient {

  record ObservedMessage(String body, Map<String, String> attributes) {}

  long getMessageCount(String logicalQueue);

  default String getMessage(String logicalQueue, int msTimeout, int msInterval) throws InterruptedException {
    ObservedMessage message = getObservedMessage(logicalQueue, msTimeout, msInterval);
    return message == null ? null : message.body();
  }

  ObservedMessage getObservedMessage(String logicalQueue, int msTimeout, int msInterval)
      throws InterruptedException;

  /**
   * Pull a census RM outcome event from the logical queue whose {@code event.type} matches.
   */
  String getMessageWithEventType(String logicalQueue, String eventType, int msTimeout, int msInterval)
      throws InterruptedException;

  default void publishExternalActionInstruction(String messageJson) {
    publishExternalActionInstruction(messageJson, ExternalActionInstructionMetadataOverride.none());
  }

  void publishExternalActionInstruction(
      String messageJson, ExternalActionInstructionMetadataOverride metadataOverride);

  void publishToTopic(String topicId, String messageJson, Map<String, String> attributes);

  void purge(String... logicalQueues);

  void ensureOutcomeBindings() throws IOException, TimeoutException, InterruptedException;

  NodeCheck doMessagingPreFlightCheck();
}
