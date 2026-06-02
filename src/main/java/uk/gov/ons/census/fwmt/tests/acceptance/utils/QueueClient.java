package uk.gov.ons.census.fwmt.tests.acceptance.utils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.common.base.Strings;

import lombok.extern.slf4j.Slf4j;
import uk.gov.ons.census.fwmt.tests.acceptance.messaging.MessagingTestClient;

@Slf4j
@Component
public final class QueueClient {

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

  @Autowired
  private MessagingTestClient messagingTestClient;

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
    if (messagingTestClient.usesRabbitListenerControl()) {
      disableListeners();
    }
    clearQueues(FIELD_REFUSALS_QUEUE, TEMP_FIELD_OTHERS_QUEUE, RM_FIELD_QUEUE, RM_FIELD_QUEUE_DLQ, OUTCOME_PRE_PROCESSING,
        OUTCOME_PRE_PROCESSING_DLQ);
    if (messagingTestClient.usesRabbitListenerControl()) {
      enableListenenrs();
    }
  }

  private void disableListeners() {
    try {
      resetListeners(jobserviceServiceUrl + "/RM/stopListener", jobServiceUsername, jobServicePassword);
      resetListeners(outcomeServiceUrl + "/StopPreprocessorListener", outcomeServiceUsername, outcomeServicePassword);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void enableListenenrs() {
    try {
      resetListeners(jobserviceServiceUrl + "/RM/startListener", jobServiceUsername, jobServicePassword);
      resetListeners(outcomeServiceUrl + "/StartPreprocessorListener", outcomeServiceUsername, outcomeServicePassword);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public void resetListeners(String listenerUrl, String user, String password) throws Exception {

    URL url = new URL(listenerUrl);
    HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();

    if (!Strings.isNullOrEmpty(user)) {
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

}
