package uk.gov.ons.census.fwmt.tests.acceptance.utils;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

@Slf4j
@Component
public class JobServiceRefreshUtils {

  private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(15);
  private static final Duration HEALTH_POLL_INTERVAL = Duration.ofMillis(250);
  private static final String FEATURE_FLAG_RESET_PATH = "/test-support/feature-flags/reset";
  private static final String FEATURE_FLAG_JOB_PATH = "/test-support/feature-flags/job";
  private static final List<String> ENV_ENDPOINT_PATHS = Arrays.asList("/actuator/env", "/env");
  private static final List<String> REFRESH_ENDPOINT_PATHS = Arrays.asList("/actuator/refresh", "/refresh");
  private static final List<String> HEALTH_ENDPOINT_PATHS = Arrays.asList("/health/readiness", "/health");

  @Value("${service.jobservice.url}")
  private String jobServiceUrl;

  @Value("${service.jobservice.username}")
  private String jobServiceUsername;

  @Value("${service.jobservice.password}")
  private String jobServicePassword;

  private final RestTemplate restTemplate = new RestTemplate();

  /**
   * Resets all job-service feature flags to {@code true} and triggers a refresh.
   * Call before each inbound scenario to prevent state leakage from prior runners.
   */
  public void enableDefaultFeatureFlags() {
    if (postFeatureFlagRequest(FEATURE_FLAG_RESET_PATH, Map.of("enabled", true),
        "Reset all job-service feature flags to enabled defaults via custom endpoint")) {
      return;
    }

    Map<String, Boolean> defaults = new LinkedHashMap<>();
    for (String survey : Arrays.asList("hh", "ce", "spg", "ccs", "nc")) {
      defaults.put("feature-flags." + survey + ".create", true);
      defaults.put("feature-flags." + survey + ".update", true);
      defaults.put("feature-flags." + survey + ".cancel", true);
      defaults.put("feature-flags." + survey + ".pause", true);
      defaults.put("feature-flags." + survey + ".reactivate", true);
    }
    defaults.put("feature-flags.ce.switch_ce_type", true);
    defaults.put("feature-flags.feedback.cancel", true);

    for (Map.Entry<String, Boolean> entry : defaults.entrySet()) {
      setPropertyForRefresh(entry.getKey(), String.valueOf(entry.getValue()));
    }
    triggerRefresh();
    waitForServiceHealth();
    log.info("Reset all job-service feature flags to enabled defaults");
  }

  public void setCreateFeatureFlagAndRefresh(String survey, String action, boolean enabled) {
    String normalizedSurvey = survey == null ? "" : survey.trim().toLowerCase();
    String normalizedAction = action == null ? "" : action.trim().toLowerCase();
    if (normalizedSurvey.isEmpty() || normalizedAction.isEmpty()) {
      throw new IllegalArgumentException("Survey and action must not be blank");
    }

    if (postFeatureFlagRequest(FEATURE_FLAG_JOB_PATH,
        Map.of("survey", normalizedSurvey, "action", normalizedAction, "enabled", enabled),
        String.format("Updated job-service feature flag %s/%s to %s via custom endpoint",
            normalizedSurvey, normalizedAction, enabled))) {
      return;
    }

    String propertyName = "feature-flags." + normalizedSurvey + "." + normalizedAction;
    setPropertyForRefresh(propertyName, String.valueOf(enabled));
    triggerRefresh();
    waitForServiceHealth();
  }

  private boolean postFeatureFlagRequest(String path, Map<String, Object> body, String successMessage) {
    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, buildJsonHeaders());
    try {
      ResponseEntity<String> response = restTemplate.exchange(jobServiceUrl + path, HttpMethod.POST, entity, String.class);
      if (response.getStatusCode().is2xxSuccessful()) {
        log.info(successMessage);
        return true;
      }
    } catch (HttpClientErrorException e) {
      if (e.getStatusCode().value() == 404) {
        return false;
      }
      throw new IllegalStateException(
          "Unable to update job-service feature flags via " + path + ", status=" + e.getStatusCode().value(), e);
    }
    throw new IllegalStateException("Unexpected response while updating job-service feature flags via " + path);
  }

  private void setPropertyForRefresh(String propertyName, String propertyValue) {
    Map<String, Object> body = new HashMap<>();
    body.put("name", propertyName);
    body.put("value", propertyValue);

    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, buildJsonHeaders());

    for (String path : ENV_ENDPOINT_PATHS) {
      String url = jobServiceUrl + path;
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
            "Unable to update job-service flag property via " + path + ", status=" + e.getStatusCode().value(),
            e);
      }
    }

    throw new IllegalStateException(
        "Unable to update job-service flag property. Env endpoint not available at /env or /actuator/env. "
            + "Expose management env endpoint in local test profile to support runtime toggle.");
  }

  private void triggerRefresh() {
    HttpEntity<String> entity = new HttpEntity<>("{}", buildJsonHeaders());

    for (String path : REFRESH_ENDPOINT_PATHS) {
      String url = jobServiceUrl + path;
      try {
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
        if (response.getStatusCode().is2xxSuccessful()) {
          log.info("Triggered job-service refresh via {}", path);
          return;
        }
      } catch (HttpClientErrorException e) {
        if (e.getStatusCode().value() == 404) {
          continue;
        }
        throw new IllegalStateException(
            "Failed to trigger job-service refresh via " + path + ", status=" + e.getStatusCode().value(), e);
      }
    }

    throw new IllegalStateException(
        "Refresh endpoint not available at /refresh or /actuator/refresh. "
            + "Ensure APP_TESTING=true and refresh endpoint exposure are enabled for local tests.");
  }

  private void waitForServiceHealth() {
    Instant deadline = Instant.now().plus(HEALTH_TIMEOUT);

    while (Instant.now().isBefore(deadline)) {
      for (String path : HEALTH_ENDPOINT_PATHS) {
        try {
          HttpEntity<String> entity = new HttpEntity<>(buildJsonHeaders());
          ResponseEntity<Map> response = restTemplate.exchange(jobServiceUrl + path, HttpMethod.GET, entity, Map.class);
          if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            Object status = response.getBody().get("status");
            if ("UP".equals(status)) {
              return;
            }
          }
        } catch (HttpClientErrorException e) {
          if (e.getStatusCode().value() == 404) {
            continue;
          }
        } catch (Exception e) {
          log.debug("Health check attempt failed while waiting for job-service after refresh", e);
        }
      }

      try {
        Thread.sleep(HEALTH_POLL_INTERVAL.toMillis());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while waiting for job-service health", e);
      }
    }

    throw new IllegalStateException("Timed out waiting for job-service health endpoint to report UP");
  }

  private HttpHeaders buildJsonHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBasicAuth(jobServiceUsername, jobServicePassword);
    return headers;
  }
}