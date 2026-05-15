package uk.gov.ons.census.fwmt.tests.acceptance.utils;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.common.base.Strings;

import lombok.extern.slf4j.Slf4j;

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
  private QueueUtils queueUtils;

  public long getMessageCount(String queueName) {
    Long messageCount = queueUtils.getMessageCount(queueName);
    return messageCount;
  }

  public String getMessage(String queueName) throws InterruptedException {
    return getMessage(queueName, 10000, 10);
  }

  public String getMessage(String queueName, int msTimeout) throws InterruptedException {
    return getMessage(queueName, msTimeout, 10);
  }

  public String getMessage(String queueName, int msTimeout, int msInterval) throws InterruptedException {
    String message = null;
    int iterations = (msTimeout + msInterval - 1) / msInterval; // division
                                                                // rounding up
    for (int i = 0; i < iterations; i++) {
      message = queueUtils.getMessageOffQueue(queueName);
      if (message != null) {
        break;
      }
      Thread.sleep(msInterval);
    }
    return message;
  }

  public void sendToRMFieldQueue(String message, String type) throws URISyntaxException {
    String exchangeName = "";
    String routingKey = "RM.Field";
    queueUtils.addMessage(exchangeName, routingKey, message, type);
  }

  public void clearQueues(String... qnames) throws URISyntaxException {
    for (String q : qnames) {
      clearQueue(q);
    }
  }

  public void createQueue() throws IOException, TimeoutException, InterruptedException {
    queueUtils.createOutcomeQueues();
  }

  private void clearQueue(String queueName) throws URISyntaxException {
    queueUtils.deleteMessage(queueName);
  }

  public void reset() throws Exception {
    disableListeners();
    clearQueues(FIELD_REFUSALS_QUEUE, TEMP_FIELD_OTHERS_QUEUE, RM_FIELD_QUEUE, RM_FIELD_QUEUE_DLQ, OUTCOME_PRE_PROCESSING,
        OUTCOME_PRE_PROCESSING_DLQ);
    enableListenenrs();
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
    return queueUtils.doPreFlightCheck();

  }

}