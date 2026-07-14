package uk.gov.ons.census.fwmt.tests.acceptance.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class OutcomeServiceRefreshUtils {

  private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(15);
  private static final Duration HEALTH_POLL_INTERVAL = Duration.ofMillis(250);

  @Value("${service.outcome.url}")
  private String outcomeServiceUrl;

  @Value("${service.outcome.username}")
  private String outcomeServiceUsername;

  @Value("${service.outcome.password}")
  private String outcomeServicePassword;

  private final RestTemplate restTemplate = new RestTemplate();

  private static final List<String> ENV_ENDPOINT_PATHS = Arrays.asList("/actuator/env", "/env");
  private static final List<String> REFRESH_ENDPOINT_PATHS = Arrays.asList("/actuator/refresh", "/refresh");

  public void setOutcomeFeatureFlagAndRefresh(String survey, boolean enabled) {
    String normalizedSurvey = survey == null ? "" : survey.trim().toUpperCase();
    if (normalizedSurvey.isEmpty()) {
      throw new IllegalArgumentException("Survey must not be blank");
    }

    String propertyName = "feature-flags.outcome.surveys." + normalizedSurvey;
    setPropertyForRefresh(propertyName, String.valueOf(enabled));
    triggerRefresh();
    waitForServiceHealth();
  }

  private void setPropertyForRefresh(String propertyName, String propertyValue) {
    Map<String, Object> body = new HashMap<>();
    body.put("name", propertyName);
    body.put("value", propertyValue);

    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, buildJsonHeaders());

    for (String path : ENV_ENDPOINT_PATHS) {
      String url = outcomeServiceUrl + path;
      try {
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        if (response.getStatusCode().is2xxSuccessful()) {
          log.info("Updated property {} to {} via {}", propertyName, propertyValue, path);
          return;
        }
      } catch (HttpClientErrorException e) {
        if (e.getStatusCode().value() == 404) {
          continue;
        }
        throw new IllegalStateException(
            "Unable to update outcome flag property via " + path + ", status=" + e.getStatusCode().value(), e);
      }
    }

    throw new IllegalStateException(
        "Unable to update outcome flag property. Env endpoint not available at /env or /actuator/env. "
            + "Expose management env endpoint in local test profile to support runtime toggle.");
  }

  private void triggerRefresh() {
    HttpEntity<String> entity = new HttpEntity<>("{}", buildJsonHeaders());

    for (String path : REFRESH_ENDPOINT_PATHS) {
      String url = outcomeServiceUrl + path;
      try {
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        if (response.getStatusCode().is2xxSuccessful()) {
          log.info("Triggered outcome-service refresh via {}", path);
          return;
        }
      } catch (HttpClientErrorException e) {
        if (e.getStatusCode().value() == 404) {
          continue;
        }
        throw new IllegalStateException(
            "Failed to trigger outcome-service refresh via " + path + ", status=" + e.getStatusCode().value(), e);
      }
    }

    throw new IllegalStateException(
        "Refresh endpoint not available at /refresh or /actuator/refresh. "
            + "Ensure APP_TESTING=true and refresh endpoint exposure are enabled for local tests.");
  }

  private void waitForServiceHealth() {
    String url = outcomeServiceUrl + "/health";
    Instant deadline = Instant.now().plus(HEALTH_TIMEOUT);

    while (Instant.now().isBefore(deadline)) {
      try {
        HttpEntity<String> entity = new HttpEntity<>(buildJsonHeaders());
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
          Object status = response.getBody().get("status");
          if ("UP".equals(status)) {
            return;
          }
        }
      } catch (Exception e) {
        log.debug("Health check attempt failed while waiting for outcome-service after refresh", e);
      }

      try {
        Thread.sleep(HEALTH_POLL_INTERVAL.toMillis());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while waiting for outcome-service health", e);
      }
    }

    throw new IllegalStateException("Timed out waiting for outcome-service health endpoint to report UP");
  }

  private HttpHeaders buildJsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBasicAuth(outcomeServiceUsername, outcomeServicePassword);
    return headers;
  }
}