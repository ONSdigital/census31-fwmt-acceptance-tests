package uk.gov.ons.census.fwmt.tests.acceptance.utils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.common.base.Strings;

import lombok.extern.slf4j.Slf4j;
import uk.gov.ons.census.fwmt.tests.acceptance.messaging.MessagingTestClient;
import uk.gov.ons.census.fwmt.tests.acceptance.timing.PerformanceTimingRecorder;

@Slf4j
@Component
public final class QueueClient {

  private static final String RESET_HOOK_NAME = "ScenarioHooks.setup";

  @Value("${service.outcome.url}")
  private String outcomeServiceUrl;

  @Value("${service.jobservice.url}")
  private String jobserviceServiceUrl;

  @Value("${service.outcome.username}")
  private String outcomeServiceUsername;

  @Value("${service.outcome.password}")
  private String outcomeServicePassword;

  @Value("${service.jobservice.username}")
  private String jobServiceUsername;

  @Value("${service.jobservice.password}")
  private String jobServicePassword;

  private static final String RM_FIELD_QUEUE = "RM.Field";

  private static final String RM_FIELD_QUEUE_DLQ = "RM.FieldDLQ";

  private static final String OUTCOME_PRE_PROCESSING = "Outcome.Preprocessing";

  private static final String OUTCOME_PRE_PROCESSING_DLQ = "Outcome.PreprocessingDLQ";

  private static final String FIELD_REFUSALS_QUEUE = "Field.refusals";

  private static final String TEMP_FIELD_OTHERS_QUEUE = "Field.other";

  private static final String[] RESET_QUEUES = {
      FIELD_REFUSALS_QUEUE,
      TEMP_FIELD_OTHERS_QUEUE,
      RM_FIELD_QUEUE,
      RM_FIELD_QUEUE_DLQ,
      OUTCOME_PRE_PROCESSING,
      OUTCOME_PRE_PROCESSING_DLQ
  };

  @Autowired
  private MessagingTestClient messagingTestClient;

  @Autowired
  private PerformanceTimingRecorder performanceTimingRecorder;

  public long getMessageCount(String queueName) {
    return messagingTestClient.getMessageCount(queueName);
  }

  public String getMessage(String queueName) throws InterruptedException {
    return getMessage(queueName, 10000, 10);
  }

  public String getMessage(String queueName, int msTimeout) throws InterruptedException {
    return getMessage(queueName, msTimeout, 10);
  }

  public String getMessage(String queueName, int msTimeout, int msInterval) throws InterruptedException {
    return messagingTestClient.getMessage(queueName, msTimeout, msInterval);
  }

  public String getMessageWithEventType(String queueName, String eventType, int msTimeout, int msInterval)
      throws InterruptedException {
    return messagingTestClient.getMessageWithEventType(queueName, eventType, msTimeout, msInterval);
  }

  public void sendToRMFieldQueue(String message, String type) {
    messagingTestClient.publishFieldWorkerInstruction(message, type);
  }

  public void clearQueues(String... qnames) {
    messagingTestClient.purge(qnames);
  }

  public void createQueue() throws IOException, TimeoutException, InterruptedException {
    messagingTestClient.ensureOutcomeBindings();
  }

  private void clearQueue(String queueName) {
    messagingTestClient.purge(queueName);
  }

  public void reset() throws Exception {
    recordResetOperation("queue-reset-pause-inbound-adapters", this::pauseInboundAdapters);
    drainQueuesInParallel();
    recordResetOperation("queue-reset-resume-inbound-adapters", this::resumeInboundAdapters);
  }

  private void drainQueuesInParallel() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(RESET_QUEUES.length);
    try {
      List<Future<?>> futures = new ArrayList<>();
      for (String queueName : RESET_QUEUES) {
        Future<?> future = executor.submit(() -> {
          try {
            recordResetOperation("queue-reset-drain-" + queueName, () -> clearQueue(queueName));
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
        futures.add(future);
      }
      // Wait for all drain operations to complete
      for (Future<?> future : futures) {
        future.get();
      }
    } finally {
      executor.shutdown();
    }
  }

  private void pauseInboundAdapters() {
    resetListenersInParallel(
        new ListenerCall("job-service", jobserviceServiceUrl + "/RM/stopListener", jobServiceUsername, jobServicePassword),
        new ListenerCall("outcome-service", outcomeServiceUrl + "/StopPreprocessorListener", outcomeServiceUsername, outcomeServicePassword));
  }

  private void resumeInboundAdapters() {
    resetListenersInParallel(
        new ListenerCall("job-service", jobserviceServiceUrl + "/RM/startListener", jobServiceUsername, jobServicePassword),
        new ListenerCall("outcome-service", outcomeServiceUrl + "/StartPreprocessorListener", outcomeServiceUsername, outcomeServicePassword));
  }

  private void resetListenersInParallel(ListenerCall first, ListenerCall second) {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      List<Future<?>> futures = new ArrayList<>();
      futures.add(executor.submit(() -> callListener(first)));
      futures.add(executor.submit(() -> callListener(second)));
      for (Future<?> future : futures) {
        future.get();
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to reset inbound adapters in parallel", e);
    } finally {
      executor.shutdown();
    }
  }

  private void callListener(ListenerCall listenerCall) {
    try {
      resetListeners(listenerCall.url(), listenerCall.user(), listenerCall.password());
    } catch (Exception e) {
      throw new RuntimeException("Failed to call listener: " + listenerCall.name(), e);
    }
  }

  private record ListenerCall(String name, String url, String user, String password) {}

  public void resetListeners(String listenerUrl, String user, String password) throws Exception {

    URL url = URI.create(listenerUrl).toURL();
    HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();

    if (user != null && !Strings.isNullOrEmpty(user)) {
      String auth = user + ":" + password;
      byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
      String authHeaderValue = "Basic " + new String(encodedAuth);
      httpURLConnection.setRequestProperty("Authorization", authHeaderValue);
    }

    httpURLConnection.setRequestMethod("GET");
    if (httpURLConnection.getResponseCode() != 200) {
      throw new RuntimeException("Failed : HTTP error code : " + httpURLConnection.getResponseCode());
    }
  }

  public NodeCheck doPreFlightCheck() {
    return messagingTestClient.doMessagingPreFlightCheck();
  }

  private void recordResetOperation(String operationName, PerformanceTimingRecorder.HookOperation operation)
      throws Exception {
    if (performanceTimingRecorder == null) {
      operation.run();
      return;
    }
    performanceTimingRecorder.recordHookOperation(RESET_HOOK_NAME, operationName, operation);
  }

}
