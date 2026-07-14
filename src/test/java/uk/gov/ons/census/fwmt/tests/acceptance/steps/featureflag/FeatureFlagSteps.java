package uk.gov.ons.census.fwmt.tests.acceptance.steps.featureflag;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import uk.gov.ons.census.fwmt.tests.acceptance.messaging.AcceptanceGatewayEventMonitor;
import uk.gov.ons.census.fwmt.tests.acceptance.steps.inbound.common.CommonUtils;
import uk.gov.ons.census.fwmt.tests.acceptance.utils.JobServiceRefreshUtils;
import uk.gov.ons.census.fwmt.tests.acceptance.utils.OutcomeServiceRefreshUtils;
import uk.gov.ons.census.fwmt.tests.acceptance.utils.TMMockUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Map;

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
  private OutcomeServiceRefreshUtils outcomeServiceRefreshUtils;

  @Autowired
  private JobServiceRefreshUtils jobServiceRefreshUtils;

  private static final Map<String, String> JOB_FLAG_BY_SURVEY_ACTION = new HashMap<>();
  private static final Map<String, String> OUTCOME_FLAG_BY_SURVEY = new HashMap<>();

  static {
    JOB_FLAG_BY_SURVEY_ACTION.put("HH:CREATE", "FEATURE_HH_CREATE");
    JOB_FLAG_BY_SURVEY_ACTION.put("CE:CREATE", "FEATURE_CE_CREATE");
    JOB_FLAG_BY_SURVEY_ACTION.put("SPG:CREATE", "FEATURE_SPG_CREATE");
    JOB_FLAG_BY_SURVEY_ACTION.put("CCS:CREATE", "FEATURE_CCS_CREATE");
    JOB_FLAG_BY_SURVEY_ACTION.put("NC:CREATE", "FEATURE_NC_CREATE");

    OUTCOME_FLAG_BY_SURVEY.put("HH", "FEATURE_OUTCOME_HH");
    OUTCOME_FLAG_BY_SURVEY.put("CE", "FEATURE_OUTCOME_CE");
    OUTCOME_FLAG_BY_SURVEY.put("SPG", "FEATURE_OUTCOME_SPG");
    OUTCOME_FLAG_BY_SURVEY.put("CCS", "FEATURE_OUTCOME_CCS");
    OUTCOME_FLAG_BY_SURVEY.put("NC", "FEATURE_OUTCOME_NC");
  }

  @Given("the {string} {string} feature flag is disabled")
  public void theSurveyActionFeatureFlagIsDisabled(String survey, String action) {
    String key = normalize(survey) + ":" + normalize(action);
    String envVar = JOB_FLAG_BY_SURVEY_ACTION.get(key);
    assertThat(envVar)
        .withFailMessage("No env var mapping found for survey/action key %s", key)
        .isNotNull();

    assertFlagDisabled(envVar);
  }

  @Given("the {string} outcome feature flag is disabled")
  public void theOutcomeFeatureFlagIsDisabled(String survey) {
    String envVar = OUTCOME_FLAG_BY_SURVEY.get(normalize(survey));
    assertThat(envVar)
        .withFailMessage("No outcome env var mapping found for survey %s", survey)
        .isNotNull();

    assertFlagDisabled(envVar);
  }

  @Given("the {string} outcome feature flag is set to {string} at runtime and outcome service is refreshed")
  public void theOutcomeFeatureFlagIsSetAtRuntimeAndRefreshed(String survey, String enabled) {
    String normalizedSurvey = normalize(survey);
    assertThat(OUTCOME_FLAG_BY_SURVEY.containsKey(normalizedSurvey))
        .withFailMessage("No outcome env var mapping found for survey %s", survey)
        .isTrue();

    boolean enabledValue = Boolean.parseBoolean(enabled);
    outcomeServiceRefreshUtils.setOutcomeFeatureFlagAndRefresh(normalizedSurvey, enabledValue);
  }

  @Given("the {string} {string} feature flag is set to {string} at runtime and job service is refreshed")
  public void theSurveyActionFeatureFlagIsSetAtRuntimeAndRefreshed(String survey, String action,
      String enabled) {
    String key = normalize(survey) + ":" + normalize(action);
    String envVar = JOB_FLAG_BY_SURVEY_ACTION.get(key);
    assertThat(envVar)
        .withFailMessage("No env var mapping found for survey/action key %s", key)
        .isNotNull();

    boolean enabledValue = Boolean.parseBoolean(enabled);
    jobServiceRefreshUtils.setCreateFeatureFlagAndRefresh(normalize(survey), normalize(action), enabledValue);
  }

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

  private static String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase();
  }

  private static void assertFlagDisabled(String envVar) {
    String value = System.getenv(envVar);
    boolean enabled = value != null && "true".equalsIgnoreCase(value.trim());
    assertThat(enabled)
        .withFailMessage("Expected %s to be disabled, but value was '%s'", envVar, value)
        .isFalse();
  }
}