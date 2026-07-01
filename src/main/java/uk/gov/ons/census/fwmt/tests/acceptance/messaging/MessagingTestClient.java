package uk.gov.ons.census.fwmt.tests.acceptance.messaging;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import uk.gov.ons.census.fwmt.tests.acceptance.utils.NodeCheck;

/**
 * Test harness port for inject/purge/poll messaging lanes (Rabbit queues or Pub/Sub topics).
 */
public interface MessagingTestClient {

  long getMessageCount(String logicalQueue);

  String getMessage(String logicalQueue, int msTimeout, int msInterval) throws InterruptedException;

  /**
   * Pull a census RM outcome event from the logical queue whose {@code event.type} matches.
   */
  String getMessageWithEventType(String logicalQueue, String eventType, int msTimeout, int msInterval)
      throws InterruptedException;

  void publishFieldWorkerInstruction(String messageJson, String instructionType);

  void purge(String... logicalQueues);

  void ensureOutcomeBindings() throws IOException, TimeoutException, InterruptedException;

  NodeCheck doMessagingPreFlightCheck();

  boolean usesRabbitListenerControl();

}
