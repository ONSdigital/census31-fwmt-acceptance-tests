package uk.gov.ons.census.fwmt.tests.acceptance.messaging;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import uk.gov.ons.census.fwmt.tests.acceptance.utils.NodeCheck;

/**
 * Delegates messaging test operations to the active Pub/Sub client implementation based on
 * {@code fwmt.pubsub.mode}.
 */
public class DelegatingMessagingTestClient implements MessagingTestClient {

  static final String MODE_EMULATOR = "emulator";
  static final String MODE_GCP = "gcp";

  private final MessagingTestClient emulatorClient;
  private final MessagingTestClient gcpClient;
  private final String pubSubMode;

  public DelegatingMessagingTestClient(
      MessagingTestClient emulatorClient, MessagingTestClient gcpClient, String pubSubMode) {
    this.emulatorClient = emulatorClient;
    this.gcpClient = gcpClient;
    this.pubSubMode = pubSubMode;
  }

  @Override
  public long getMessageCount(String logicalQueue) {
    return activeClient().getMessageCount(logicalQueue);
  }

  @Override
  public String getMessage(String logicalQueue, int msTimeout, int msInterval)
      throws InterruptedException {
    return activeClient().getMessage(logicalQueue, msTimeout, msInterval);
  }

  @Override
  public String getMessageWithEventType(
      String logicalQueue, String eventType, int msTimeout, int msInterval)
      throws InterruptedException {
    return activeClient().getMessageWithEventType(logicalQueue, eventType, msTimeout, msInterval);
  }

  @Override
  public void publishFieldWorkerInstruction(String messageJson, String instructionType) {
    activeClient().publishFieldWorkerInstruction(messageJson, instructionType);
  }

  @Override
  public void purge(String... logicalQueues) {
    activeClient().purge(logicalQueues);
  }

  @Override
  public void ensureOutcomeBindings() throws IOException, TimeoutException, InterruptedException {
    activeClient().ensureOutcomeBindings();
  }

  @Override
  public NodeCheck doMessagingPreFlightCheck() {
    return activeClient().doMessagingPreFlightCheck();
  }

  private MessagingTestClient activeClient() {
    String normalizedMode = normalizeMode(pubSubMode);
    return switch (normalizedMode) {
      case MODE_EMULATOR -> requireClient(emulatorClient, MODE_EMULATOR);
      case MODE_GCP -> requireClient(gcpClient, MODE_GCP);
      default -> throw unsupportedMode(normalizedMode);
    };
  }

  private static String normalizeMode(String mode) {
    return Objects.requireNonNullElse(mode, "").trim().toLowerCase(Locale.ROOT);
  }

  private static MessagingTestClient requireClient(MessagingTestClient client, String mode) {
    if (client == null) {
      throw new IllegalStateException(
          "No MessagingTestClient configured for fwmt.pubsub.mode='" + mode + "'");
    }
    return client;
  }

  private IllegalArgumentException unsupportedMode(String normalizedMode) {
    return new IllegalArgumentException(
        "Unsupported fwmt.pubsub.mode='"
            + normalizedMode
            + "'. Expected one of: '"
            + MODE_EMULATOR
            + "', '"
            + MODE_GCP
            + "'");
  }
}

