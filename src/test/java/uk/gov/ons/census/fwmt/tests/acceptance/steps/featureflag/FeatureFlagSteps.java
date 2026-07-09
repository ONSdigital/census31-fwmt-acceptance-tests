package uk.gov.ons.census.fwmt.tests.acceptance.steps.featureflag;

import io.cucumber.java.en.Then;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import uk.gov.ons.census.fwmt.tests.acceptance.messaging.AcceptanceGatewayEventMonitor;
import uk.gov.ons.census.fwmt.tests.acceptance.steps.inbound.common.CommonUtils;
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
}