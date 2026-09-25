package uk.gov.ons.census.fwmt.tests.acceptance.utils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
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

    private static final String FIELDWORK_ACTION_INSTRUCTION =
      "event_fieldwork_action-instruction";

  private static final String FIELDWORK_ACTION_INSTRUCTION_INTERNAL =
      "event_fieldwork_action-instruction_internal";

  private static final String OUTCOME_PRE_PROCESSING = "Outcome.Preprocessing";

  private static final String OUTCOME_PRE_PROCESSING_DLQ = "Outcome.PreprocessingDLQ";

  private static final String REFUSAL_RECEIVED_TOPIC = "event_refusal-received";

  private static final String FIELD_CASE_UPDATED_TOPIC = "event_field-case-updated";

  private static final String FULFILMENT_REQUEST_TOPIC = "event_fulfilment-request";

  private static final String ADDRESS_NOT_VALID_TOPIC = "event_address-not-valid";

  private static final String QUESTIONNAIRE_LINKED_TOPIC = "event_questionnaire-linked";

  private static final String FIELD_REFUSALS_QUEUE = "Field.refusals";

  private static final String TEMP_FIELD_OTHERS_QUEUE = "Field.other";

  private static final String[] RESET_QUEUES = {
      REFUSAL_RECEIVED_TOPIC,
      FIELD_CASE_UPDATED_TOPIC,
      FULFILMENT_REQUEST_TOPIC,
      ADDRESS_NOT_VALID_TOPIC,
      QUESTIONNAIRE_LINKED_TOPIC,
      FIELD_REFUSALS_QUEUE,
      TEMP_FIELD_OTHERS_QUEUE,
      FIELDWORK_ACTION_INSTRUCTION,
      FIELDWORK_ACTION_INSTRUCTION_INTERNAL,
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

  public MessagingTestClient.ObservedMessage getObservedMessage(String queueName, int msTimeout, int msInterval)
      throws InterruptedException {
    return messagingTestClient.getObservedMessage(queueName, msTimeout, msInterval);
  }

  public String getMessageWithEventType(String queueName, String eventType, int msTimeout, int msInterval)
      throws InterruptedException {
    return messagingTestClient.getMessageWithEventType(queueName, eventType, msTimeout, msInterval);
  }

  public void publishExternalActionInstruction(String message) {
    messagingTestClient.publishExternalActionInstruction(message);
  }

  public void publishExternalActionInstruction(
      String message, uk.gov.ons.census.fwmt.tests.acceptance.messaging.ExternalActionInstructionMetadataOverride metadataOverride) {
    messagingTestClient.publishExternalActionInstruction(message, metadataOverride);
  }

  public void publishToTopic(String topicId, String message, Map<String, String> attributes) {
    messagingTestClient.publishToTopic(topicId, message, attributes);
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
    resetListeners(
        new ListenerCall(
            "outcome-service",
            outcomeServiceUrl + "/StopPreprocessorListener",
            outcomeServiceUsername,
            outcomeServicePassword));
  }

  private void resumeInboundAdapters() {
    resetListeners(
        new ListenerCall(
            "outcome-service",
            outcomeServiceUrl + "/StartPreprocessorListener",
            outcomeServiceUsername,
            outcomeServicePassword));
  }

  private void resetListeners(ListenerCall... listenerCalls) {
    ExecutorService executor = Executors.newFixedThreadPool(Math.max(1, listenerCalls.length));
    try {
      List<Future<?>> futures = new ArrayList<>();
      for (ListenerCall listenerCall : listenerCalls) {
        futures.add(executor.submit(() -> callListener(listenerCall)));
      }
      for (Future<?> future : futures) {
        future.get();
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to reset inbound adapters", e);
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
