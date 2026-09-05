package uk.gov.ons.census.fwmt.tests.acceptance.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.ons.census.fwmt.tests.acceptance.messaging.MessagingTestClient;
import uk.gov.ons.census.fwmt.tests.acceptance.timing.PerformanceTimingRecorder;

class QueueClientTest {

  @Test
  void resetShouldRecordPerQueueResetOperations() throws Exception {
    MessagingTestClient messagingTestClient = mock(MessagingTestClient.class);
    PerformanceTimingRecorder recorder = new PerformanceTimingRecorder();
    Path timingsFile = Files.createTempFile("performance-timings", ".ndjson");
    ReflectionTestUtils.setField(recorder, "runId", "test-run");
    ReflectionTestUtils.setField(recorder, "timingsFile", timingsFile.toString());
    recorder.scenarioStarted("scenario-1", "Scenario name", "feature", 42);

    List<String> listenerUrls = new ArrayList<>();
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", exchange -> {
      listenerUrls.add(exchange.getRequestURI().getPath());
      exchange.sendResponseHeaders(200, -1);
      exchange.close();
    });
    server.start();

    QueueClient queueClient = new QueueClient();
    ReflectionTestUtils.setField(queueClient, "messagingTestClient", messagingTestClient);
    ReflectionTestUtils.setField(queueClient, "performanceTimingRecorder", recorder);
    String baseUrl = "http://localhost:" + server.getAddress().getPort();
    ReflectionTestUtils.setField(queueClient, "jobserviceServiceUrl", baseUrl);
    ReflectionTestUtils.setField(queueClient, "outcomeServiceUrl", baseUrl);

    try {
      queueClient.reset();
    } finally {
      server.stop(0);
    }

    assertThat(listenerUrls)
        .containsExactlyInAnyOrder(
            "/RM/stopListener",
            "/StopPreprocessorListener",
            "/RM/startListener",
            "/StartPreprocessorListener");

    String output = Files.readString(timingsFile);
    assertThat(output)
        .contains("\"operationName\":\"queue-reset-pause-inbound-adapters\"")
        .contains("\"operationName\":\"queue-reset-drain-Field.refusals\"")
        .contains("\"operationName\":\"queue-reset-drain-Field.other\"")
        .contains("\"operationName\":\"queue-reset-drain-RM.Field\"")
        .contains("\"operationName\":\"queue-reset-drain-RM.FieldDLQ\"")
        .contains("\"operationName\":\"queue-reset-drain-Outcome.Preprocessing\"")
        .contains("\"operationName\":\"queue-reset-drain-Outcome.PreprocessingDLQ\"")
        .contains("\"operationName\":\"queue-reset-resume-inbound-adapters\"");
  }
}