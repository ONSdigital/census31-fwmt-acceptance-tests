package uk.gov.ons.census.fwmt.tests.acceptance.steps.featureflag;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import uk.gov.ons.census.fwmt.tests.acceptance.messaging.AcceptanceGatewayEventMonitor;
import uk.gov.ons.census.fwmt.tests.acceptance.steps.inbound.common.CommonUtils;
import uk.gov.ons.census.fwmt.tests.acceptance.utils.FeatureFlagClient;
import uk.gov.ons.census.fwmt.tests.acceptance.utils.TMMockUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class FeatureFlagSteps {

  private static final String FEATURE_FLAG_IGNORED = "FEATURE_FLAG_IGNORED";
  private static final String COMET_CREATE_PRE_SENDING = "COMET_CREATE_PRE_SENDING";
  private static final String PREPROCESSING_OUTCOME_IGNORED_BY_FEATURE_FLAG = "PREPROCESSING_OUTCOME_IGNORED_BY_FEATURE_FLAG";
  private static final String PROCESSING_OUTCOME = "PROCESSING_OUTCOME";

  @Autowired
  private AcceptanceGatewayEventMonitor gatewayEventMonitor;

  @Autowired
  private TMMockUtils tmMockUtils;

  @Autowired
  private FeatureFlagClient featureFlagClient;

  // -----------------------------------------------------------------------
  // Assertion steps – these read from env vars set at container start-up
  // and assert the initial state before any runtime override is applied.
  // -----------------------------------------------------------------------

  @Given("the {string} {string} feature flag is disabled")
  public void theSurveyActionFeatureFlagIsDisabled(String survey, String action) {
    assertEnvFlagDisabled(jobFlagEnvVar(normalize(survey), normalize(action)));
  }

  @Given("the {string} outcome feature flag is disabled")
  public void theOutcomeFeatureFlagIsDisabled(String survey) {
    assertEnvFlagDisabled(outcomeFlagEnvVar(normalize(survey)));
  }

  // -----------------------------------------------------------------------
  // Runtime-override steps (clean wording — preferred in new scenarios)
  // -----------------------------------------------------------------------

  /**
   * Sets a single outcome-service feature flag at runtime.
   * Example: {@code Given the "CE" outcome feature flag is set to "false"}
   */
  @Given("the {string} outcome feature flag is set to {string}")
  public void setOutcomeFeatureFlag(String survey, String enabled) {
    featureFlagClient.setOutcomeFlag(normalize(survey), Boolean.parseBoolean(enabled));
  }

  /**
   * Sets a single job-service feature flag at runtime.
   * Example: {@code Given the "HH" "CREATE" feature flag is set to "false"}
   */
  @Given("the {string} {string} feature flag is set to {string}")
  public void setJobFeatureFlag(String survey, String action, String enabled) {
    featureFlagClient.setJobFlag(normalize(survey), normalize(action), Boolean.parseBoolean(enabled));
  }

  // -----------------------------------------------------------------------
  // Legacy-wording aliases – kept so existing .feature files continue to work.
  // They delegate to the same FeatureFlagClient methods.
  // -----------------------------------------------------------------------

  /** @deprecated Use {@link #setOutcomeFeatureFlag} — step wording without "refreshed" is preferred. */
  @Given("the {string} outcome feature flag is set to {string} at runtime and outcome service is refreshed")
  public void theOutcomeFeatureFlagIsSetAtRuntimeAndRefreshed(String survey, String enabled) {
    featureFlagClient.setOutcomeFlag(normalize(survey), Boolean.parseBoolean(enabled));
  }

  /** @deprecated Use {@link #setJobFeatureFlag} — step wording without "refreshed" is preferred. */
  @Given("the {string} {string} feature flag is set to {string} at runtime and job service is refreshed")
  public void theSurveyActionFeatureFlagIsSetAtRuntimeAndRefreshed(String survey, String action, String enabled) {
    featureFlagClient.setJobFlag(normalize(survey), normalize(action), Boolean.parseBoolean(enabled));
  }

  // -----------------------------------------------------------------------
  // Then steps
  // -----------------------------------------------------------------------

  @Then("the request with case ID {string} is ignored because the survey or action feature flag is disabled")
  public void theRequestIsIgnoredDueToFeatureFlagForCaseId(String caseId) {
    boolean ignoredEventTriggered = gatewayEventMonitor.hasEventTriggered(caseId, FEATURE_FLAG_IGNORED, CommonUtils.TIMEOUT);
    assertThat(ignoredEventTriggered).isTrue();

    boolean createPreSendTriggered = gatewayEventMonitor.hasEventTriggered(caseId, COMET_CREATE_PRE_SENDING, CommonUtils.TIMEOUT);
    assertThat(createPreSendTriggered).isFalse();
  }

  @Then("no TM case is created for case ID {string}")
  public void noTmCaseIsCreatedForCaseId(String caseId) {
    try {
      tmMockUtils.getCaseById(caseId);
      fail("Case should not exist");
    } catch (HttpClientErrorException e) {
      assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }
  }

  @Then("the outcome is ignored due to feature flag for case ID {string}")
  public void theOutcomeIsIgnoredDueToFeatureFlagForCaseId(String caseId) {
    boolean ignoredEventTriggered = gatewayEventMonitor.hasEventTriggered(caseId,
        PREPROCESSING_OUTCOME_IGNORED_BY_FEATURE_FLAG, CommonUtils.TIMEOUT);
    assertThat(ignoredEventTriggered).isTrue();

    boolean processingEventTriggered = gatewayEventMonitor.hasEventTriggered(caseId, PROCESSING_OUTCOME, CommonUtils.TIMEOUT);
    assertThat(processingEventTriggered).isFalse();
  }

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase();
  }

  private static String jobFlagEnvVar(String survey, String action) {
    return "FEATURE_" + survey + "_" + action;
  }

  private static String outcomeFlagEnvVar(String survey) {
    return "FEATURE_OUTCOME_" + survey;
  }

  private static void assertEnvFlagDisabled(String envVar) {
    String value = System.getenv(envVar);
    boolean enabled = value != null && "true".equalsIgnoreCase(value.trim());
    assertThat(enabled)
        .withFailMessage("Expected env var %s to be disabled (not 'true'), but value was '%s'", envVar, value)
        .isFalse();
  }
}