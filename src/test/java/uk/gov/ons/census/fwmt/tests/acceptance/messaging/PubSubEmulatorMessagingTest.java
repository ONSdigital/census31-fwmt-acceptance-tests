package uk.gov.ons.census.fwmt.tests.acceptance.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import uk.gov.ons.census.fwmt.tests.acceptance.timing.PerformanceTimingRecorder;

class PubSubEmulatorMessagingTest {

  @Test
  void shouldSettleEntireBatchAndBufferMessagesForLaterExpectedTypes() throws InterruptedException {
    RecordingPubSubEmulatorHttp http = new RecordingPubSubEmulatorHttp();
    http.enqueuePull(
        List.of(
            new PubSubEmulatorHttp.ReceivedPubSubMessage(
                "ack-address", "{\"event\":{\"type\":\"ADDRESS_TYPE_CHANGED\"}}", Map.of()),
            new PubSubEmulatorHttp.ReceivedPubSubMessage(
                "ack-fulfilment", "{\"event\":{\"type\":\"FULFILMENT_REQUESTED\"}}", Map.of())));
    PubSubEmulatorMessaging client =
      new PubSubEmulatorMessaging(http, new PerformanceTimingRecorder());

    String addressMessage =
        client.getMessageWithEventType("RM.Field", "ADDRESS_TYPE_CHANGED", 100, 10);
    String fulfilmentMessage =
        client.getMessageWithEventType("RM.Field", "FULFILMENT_REQUESTED", 100, 10);

    assertThat(addressMessage).contains("ADDRESS_TYPE_CHANGED");
    assertThat(fulfilmentMessage).contains("FULFILMENT_REQUESTED");
    assertThat(http.acknowledgedIds).containsExactly("ack-address", "ack-fulfilment");
    assertThat(http.pullCount).isOne();
    assertThat(http.publishedMessages).isEmpty();
  }

  private static final class RecordingPubSubEmulatorHttp extends PubSubEmulatorHttp {
    private final Deque<List<ReceivedPubSubMessage>> pullBatches = new ArrayDeque<>();
    private final List<String> acknowledgedIds = new ArrayList<>();
    private final List<String> publishedMessages = new ArrayList<>();
    private int pullCount;

    private RecordingPubSubEmulatorHttp() {
      super("test-project", "localhost:1");
    }

    @Override
    List<ReceivedPubSubMessage> pull(
        String subscriptionId, int maxMessages, boolean returnImmediately) {
      pullCount += 1;
      return pullBatches.isEmpty() ? List.of() : pullBatches.removeFirst();
    }

    @Override
    void acknowledge(String subscriptionId, List<String> ackIds) {
      acknowledgedIds.addAll(ackIds);
    }

    @Override
    void publish(String topicId, String jsonBody, Map<String, String> attributes) {
      publishedMessages.add(jsonBody);
    }

    private void enqueuePull(List<ReceivedPubSubMessage> batch) {
      pullBatches.addLast(batch);
    }
  }
}