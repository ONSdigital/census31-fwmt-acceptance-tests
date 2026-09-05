package uk.gov.ons.census.fwmt.tests.acceptance.timing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PerformanceTimingRecorder {

  private final ThreadLocal<ScenarioContext> currentScenario = new ThreadLocal<>();
  private final ThreadLocal<RmWaitContext> currentRmWait = new ThreadLocal<>();

  @Value("${fwmt.performance.run-id:local-run}")
  private String runId;

  @Value("${fwmt.performance.timings.file:target/performance-investigation/timings.ndjson}")
  private String timingsFile;

  public void scenarioStarted(String scenarioId, String scenarioName, String feature, int line) {
    ScenarioContext context = new ScenarioContext(scenarioId, scenarioName, feature, line);
    currentScenario.set(context);

    append(baseRecord("scenario-start")
        + ",\"scenarioId\":" + quote(context.id)
        + ",\"scenarioName\":" + quote(context.name)
        + ",\"feature\":" + quote(context.feature)
        + ",\"line\":" + context.line
        + "}");
  }

  public void scenarioFinished(String status, boolean failed) {
    ScenarioContext context = currentScenario.get();
    StringBuilder record = new StringBuilder(baseRecord("scenario-finish"));
    if (context != null) {
      record.append(",\"scenarioId\":").append(quote(context.id))
          .append(",\"scenarioName\":").append(quote(context.name))
          .append(",\"feature\":").append(quote(context.feature))
          .append(",\"line\":").append(context.line);
    }
    record.append(",\"status\":").append(quote(status))
        .append(",\"failed\":").append(failed)
        .append('}');
    append(record.toString());

    currentRmWait.remove();
    currentScenario.remove();
  }

  public void recordHookOperation(
      String hookName, String operationName, HookOperation operation) throws Exception {
    long startedAtMs = System.currentTimeMillis();
    Exception failure = null;
    try {
      operation.run();
    } catch (Exception e) {
      failure = e;
      throw e;
    } finally {
      ScenarioContext scenario = currentScenario.get();
      StringBuilder record = new StringBuilder(baseRecord("hook-operation"));
      if (scenario != null) {
        record.append(",\"scenarioId\":").append(quote(scenario.id))
            .append(",\"scenarioName\":").append(quote(scenario.name))
            .append(",\"feature\":").append(quote(scenario.feature))
            .append(",\"line\":").append(scenario.line);
      }
      record.append(",\"hookName\":").append(quote(hookName))
          .append(",\"operationName\":").append(quote(operationName))
          .append(",\"durationMs\":").append(System.currentTimeMillis() - startedAtMs)
          .append(",\"outcome\":").append(quote(failure == null ? "success" : "error"));
      if (failure != null) {
        record.append(",\"errorType\":").append(quote(failure.getClass().getName()))
            .append(",\"errorMessage\":").append(quote(failure.getMessage()));
      }
      record.append('}');
      append(record.toString());
    }
  }

  @FunctionalInterface
  public interface HookOperation {
    void run() throws Exception;
  }

  public void startRmMessageWait(
      String phase,
      String logicalQueue,
      String awaitedEventType,
      List<String> expectedRmMessages,
      String messageTransactionId,
      String messageCaseId,
      String parentCaseId,
      String generatedCaseId,
      int timeoutMs,
      int pollIntervalMs) {
    currentRmWait.set(
        new RmWaitContext(
            phase,
            logicalQueue,
            awaitedEventType,
            List.copyOf(expectedRmMessages),
            messageTransactionId,
            messageCaseId,
            parentCaseId,
            generatedCaseId,
            timeoutMs,
            pollIntervalMs,
            System.currentTimeMillis(),
            Instant.now().toString()));
  }

  public void recordRmMessagePull(int batchSize, int republishedCount, boolean matchedInThisPull) {
    RmWaitContext wait = currentRmWait.get();
    if (wait == null) {
      return;
    }

    wait.iterations += 1;
    wait.messagesPulled += batchSize;
    wait.messagesRepublished += republishedCount;
    if (wait.iterations == 1) {
      wait.firstPullMatched = matchedInThisPull;
    }
  }

  public void finishRmMessageWait(String messageJson, Exception error) {
    RmWaitContext wait = currentRmWait.get();
    currentRmWait.remove();
    if (wait == null) {
      return;
    }

    long finishedAtMs = System.currentTimeMillis();
    ScenarioContext scenario = currentScenario.get();

    StringBuilder record = new StringBuilder(baseRecord("rm-message-wait"));
    if (scenario != null) {
      record.append(",\"scenarioId\":").append(quote(scenario.id))
          .append(",\"scenarioName\":").append(quote(scenario.name))
          .append(",\"feature\":").append(quote(scenario.feature))
          .append(",\"line\":").append(scenario.line);
    }
    record.append(",\"phase\":").append(quote(wait.phase))
        .append(",\"logicalQueue\":").append(quote(wait.logicalQueue))
        .append(",\"awaitedEventType\":").append(quote(wait.awaitedEventType))
        .append(",\"expectedRmMessages\":").append(quoteArray(wait.expectedRmMessages))
      .append(",\"messageTransactionId\":").append(quote(wait.messageTransactionId))
        .append(",\"messageCaseId\":").append(quote(wait.messageCaseId))
        .append(",\"parentCaseId\":").append(quote(wait.parentCaseId))
        .append(",\"generatedCaseId\":").append(quote(wait.generatedCaseId))
        .append(",\"waitStartedAtUtc\":").append(quote(wait.startedAtUtc))
        .append(",\"waitFinishedAtUtc\":").append(quote(Instant.now().toString()))
        .append(",\"durationMs\":").append(finishedAtMs - wait.startedAtMs)
        .append(",\"timeoutMs\":").append(wait.timeoutMs)
        .append(",\"pollIntervalMs\":").append(wait.pollIntervalMs)
        .append(",\"iterations\":").append(wait.iterations)
        .append(",\"messagesPulled\":").append(wait.messagesPulled)
        .append(",\"messagesRepublished\":").append(wait.messagesRepublished)
        .append(",\"firstPullMatched\":").append(wait.firstPullMatched)
        .append(",\"outcome\":")
        .append(quote(error != null ? "error" : (messageJson != null ? "found" : "not_found")));
    if (error != null) {
      record.append(",\"errorType\":").append(quote(error.getClass().getName()))
          .append(",\"errorMessage\":").append(quote(error.getMessage()));
    }
    record.append('}');
    append(record.toString());
  }

  private String baseRecord(String type) {
    return "{\"type\":" + quote(type)
        + ",\"timestampUtc\":" + quote(Instant.now().toString())
        + ",\"runId\":" + quote(runId);
  }

  private String quoteArray(List<String> values) {
    StringBuilder quoted = new StringBuilder("[");
    for (int index = 0; index < values.size(); index++) {
      if (index > 0) {
        quoted.append(',');
      }
      quoted.append(quote(values.get(index)));
    }
    quoted.append(']');
    return quoted.toString();
  }

  private String quote(String value) {
    if (value == null) {
      return "null";
    }
    String escaped = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
    return '"' + escaped + '"';
  }

  private synchronized void append(String record) {
    try {
      Path path = Path.of(timingsFile);
      Path parent = path.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      String line = record + System.lineSeparator();
      Files.writeString(
          path,
          line,
          StandardCharsets.UTF_8,
          java.nio.file.StandardOpenOption.CREATE,
          java.nio.file.StandardOpenOption.APPEND);
    } catch (IOException e) {
      throw new IllegalStateException("Unable to append performance timings to " + timingsFile, e);
    }
  }

  private static final class ScenarioContext {
    private final String id;
    private final String name;
    private final String feature;
    private final int line;

    private ScenarioContext(String id, String name, String feature, int line) {
      this.id = id;
      this.name = name;
      this.feature = feature;
      this.line = line;
    }
  }

  private static final class RmWaitContext {
    private final String phase;
    private final String logicalQueue;
    private final String awaitedEventType;
    private final List<String> expectedRmMessages;
    private final String messageTransactionId;
    private final String messageCaseId;
    private final String parentCaseId;
    private final String generatedCaseId;
    private final int timeoutMs;
    private final int pollIntervalMs;
    private final long startedAtMs;
    private final String startedAtUtc;
    private int iterations;
    private int messagesPulled;
    private int messagesRepublished;
    private boolean firstPullMatched;

    private RmWaitContext(
        String phase,
        String logicalQueue,
        String awaitedEventType,
        List<String> expectedRmMessages,
        String messageTransactionId,
        String messageCaseId,
        String parentCaseId,
        String generatedCaseId,
        int timeoutMs,
        int pollIntervalMs,
        long startedAtMs,
        String startedAtUtc) {
      this.phase = phase;
      this.logicalQueue = logicalQueue;
      this.awaitedEventType = awaitedEventType;
      this.expectedRmMessages = expectedRmMessages;
      this.messageTransactionId = messageTransactionId;
      this.messageCaseId = messageCaseId;
      this.parentCaseId = parentCaseId;
      this.generatedCaseId = generatedCaseId;
      this.timeoutMs = timeoutMs;
      this.pollIntervalMs = pollIntervalMs;
      this.startedAtMs = startedAtMs;
      this.startedAtUtc = startedAtUtc;
    }
  }
}
