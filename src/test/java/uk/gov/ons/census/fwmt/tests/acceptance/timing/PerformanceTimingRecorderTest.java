package uk.gov.ons.census.fwmt.tests.acceptance.timing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PerformanceTimingRecorderTest {

  @Test
  void rmWaitRecordsShouldIncludeTransactionId() throws Exception {
    Path timingsFile = Files.createTempFile("performance-timings", ".ndjson");
    PerformanceTimingRecorder recorder = new PerformanceTimingRecorder();
    setField(recorder, "runId", "test-run");
    setField(recorder, "timingsFile", timingsFile.toString());

    recorder.scenarioStarted("scenario-1", "Scenario name", "feature", 42);
    recorder.startRmMessageWait(
        "collectRmMessages",
        "Field.other",
        "QUESTIONNAIRE_LINKED",
        java.util.List.of("QUESTIONNAIRE_LINKED"),
        "transaction-123",
        "case-123",
        "case-123",
        null,
        10_000,
        50);
    recorder.recordRmMessagePull(1, 0, true);
    recorder.finishRmMessageWait("{}", null);

    String output = Files.readString(timingsFile);
    assertThat(output).contains("\"messageTransactionId\":\"transaction-123\"");
  }

  @Test
  void hookOperationRecordsShouldIncludeOperationAndScenarioDetails() throws Exception {
    Path timingsFile = Files.createTempFile("performance-timings", ".ndjson");
    PerformanceTimingRecorder recorder = new PerformanceTimingRecorder();
    setField(recorder, "runId", "test-run");
    setField(recorder, "timingsFile", timingsFile.toString());

    recorder.scenarioStarted("scenario-1", "Scenario name", "feature", 42);
    recorder.recordHookOperation("ScenarioHooks.setup", "queue-reset", () -> {});

    String output = Files.readString(timingsFile);
    assertThat(output)
        .contains("\"type\":\"hook-operation\"")
        .contains("\"hookName\":\"ScenarioHooks.setup\"")
        .contains("\"operationName\":\"queue-reset\"")
        .contains("\"scenarioId\":\"scenario-1\"")
        .contains("\"outcome\":\"success\"");
  }

  private void setField(Object target, String fieldName, String value) throws Exception {
    java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }
}