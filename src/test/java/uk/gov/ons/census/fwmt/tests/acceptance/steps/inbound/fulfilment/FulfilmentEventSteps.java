package uk.gov.ons.census.fwmt.tests.acceptance.steps.inbound.fulfilment;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.census.fwmt.tests.acceptance.steps.inbound.common.CommonUtils.testBucket;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.ons.census.fwmt.tests.acceptance.messaging.AcceptanceGatewayEventMonitor;
import uk.gov.ons.census.fwmt.tests.acceptance.messaging.MessagingTestClient;
import uk.gov.ons.census.fwmt.tests.acceptance.steps.inbound.common.CommonUtils;
import uk.gov.ons.census.fwmt.tests.acceptance.utils.QueueClient;
import uk.gov.ons.census.fwmt.tests.acceptance.utils.TMMockUtils;

public class FulfilmentEventSteps {

  private static final String FULFILMENT_REQUEST_TOPIC = "event_fulfilment-request";
  private static final String INTERNAL_ACTION_INSTRUCTION_LANE =
      "event_fieldwork_action-instruction_internal";
  private static final String DEFAULT_CHANNEL = "CC";

  private static final String PAUSE_PROCESSED_AND_SENT = "PAUSE_PROCESSED_AND_SENT";
  private static final String NO_RECORD_FOUND = "NO_RECORD_FOUND";
  private static final String CASE_ALREADY_CANCELLED = "CASE_ALREADY_CANCELLED";
  private static final String UNRECOGNISED_FULFILLMENT_CODE = "UNRECOGNISED_FULFILLMENT_CODE";
  private static final String COMET_PAUSE_PRE_SENDING = "COMET_PAUSE_PRE_SENDING";
  private static final String COMET_PAUSE_ACK = "COMET_PAUSE_ACK";

  @Autowired
  private QueueClient queueClient;

  @Autowired
  private TMMockUtils tmMockUtils;

  @Autowired
  private AcceptanceGatewayEventMonitor gatewayEventMonitor;

  private MessagingTestClient.ObservedMessage internalActionInstruction;
  private String correlationId;

  @Given("an in-field household case exists in the Gateway cache with case ID {string}")
  public void anInFieldHouseholdCaseExistsInTheGatewayCacheWithCaseId(String caseId) throws Exception {
    testBucket.put("caseId", caseId);
    tmMockUtils.addHouseholdCaseToDatabase(caseId);
    assertThat(tmMockUtils.checkCaseIdExists(caseId)).isEqualTo(1);
  }

  @Given("no in-field household case exists in the Gateway cache with case ID {string}")
  public void noInFieldHouseholdCaseExistsInTheGatewayCacheWithCaseId(String caseId) throws Exception {
    testBucket.put("caseId", caseId);
    assertThat(tmMockUtils.checkCaseIdExists(caseId)).isZero();
  }

  @And("the household case has last action instruction {string}")
  public void theHouseholdCaseHasLastActionInstruction(String lastActionInstruction) throws Exception {
    String caseId = testBucket.get("caseId");
    tmMockUtils.updateGatewayCaseRecordLastActionInstruction(caseId, lastActionInstruction);
    assertThat(tmMockUtils.checkActionExistsInGatewayCaseRecord(lastActionInstruction, caseId)).isEqualTo(1);
  }

  @And("the Gateway receives a FulfilmentRequest with fulfilment code {string}, case ID {string}, and channel {string}")
  public void theGatewayReceivesAFulfilmentRequestWithChannel(String fulfilmentCode, String caseId, String channel) {
    publishFulfilmentRequest(fulfilmentCode, caseId, channel);
  }

  @And("the Gateway receives a FulfilmentRequest with fulfilment code {string} and case ID {string}")
  public void theGatewayReceivesAFulfilmentRequest(String fulfilmentCode, String caseId) {
    publishFulfilmentRequest(fulfilmentCode, caseId, DEFAULT_CHANNEL);
  }

  @When("the fulfilment request is processed")
  public void theFulfilmentRequestIsProcessed() {
    // Processing is asserted by the outcome-specific Then steps.
  }

  @Then("the Gateway publishes an internal fieldwork action instruction for case ID {string}")
  public void theGatewayPublishesAnInternalFieldworkActionInstructionForCaseId(String caseId)
      throws InterruptedException {
    internalActionInstruction = queueClient.getObservedMessage(
        INTERNAL_ACTION_INSTRUCTION_LANE,
        (int) CommonUtils.TIMEOUT,
        50);

    assertThat(internalActionInstruction).isNotNull();
    JSONObject payload = internalActionInstructionPayload();
    assertThat(payload.getString("caseId")).isEqualTo(caseId);
    assertThat(internalActionInstruction.attributes())
        .containsEntry("caseId", caseId)
        .containsEntry("correlationId", correlationId)
        .containsEntry("eventType", "FIELDWORK_ACTION_INSTRUCTION")
        .containsEntry("schemaVersion", "1.0");
    assertThat(internalActionInstruction.attributes().get("eventId")).isNotBlank();
    assertThat(internalActionInstruction.attributes().get("occurredAt")).isNotBlank();
    assertThat(Instant.parse(internalActionInstruction.attributes().get("occurredAt"))).isNotNull();
    assertThat(gatewayEventMonitor.hasEventTriggered(caseId, PAUSE_PROCESSED_AND_SENT, CommonUtils.TIMEOUT)).isTrue();
  }

  @Then("the Gateway does not publish an internal fieldwork action instruction")
  public void theGatewayDoesNotPublishAnInternalFieldworkActionInstruction() throws InterruptedException {
    internalActionInstruction = queueClient.getObservedMessage(INTERNAL_ACTION_INSTRUCTION_LANE, 1000, 50);
    assertThat(internalActionInstruction).isNull();
  }

  @And("the internal action instruction has action instruction {string}, survey name {string}, address type {string}, and address level {string}")
  public void theInternalActionInstructionHasExpectedPayload(
      String actionInstruction, String surveyName, String addressType, String addressLevel) {
    JSONObject payload = internalActionInstructionPayload();
    assertThat(payload.getString("actionInstruction")).isEqualTo(actionInstruction);
    assertThat(payload.getString("surveyName")).isEqualTo(surveyName);
    assertThat(payload.getString("addressType")).isEqualTo(addressType);
    assertThat(payload.getString("addressLevel")).isEqualTo(addressLevel);
    assertThat(payload.has("header")).isFalse();
    assertThat(payload.has("payload")).isFalse();
  }

  @And("the pause has the configured pause code {string}")
  public void thePauseHasTheConfiguredPauseCode(String pauseCode) {
    assertThat(internalActionInstructionPayload().getString("pauseCode")).isEqualTo(pauseCode);
  }

  @And("Job Service sends the PAUSE event to TM for case ID {string}")
  public void jobServiceSendsThePauseEventToTmForCaseId(String caseId) {
    assertThat(gatewayEventMonitor.hasEventTriggered(caseId, COMET_PAUSE_PRE_SENDING, CommonUtils.TIMEOUT))
        .isTrue();
  }

  @And("TM acknowledges the PAUSE for case ID {string}")
  public void tmAcknowledgesThePauseForCaseId(String caseId) {
    assertThat(gatewayEventMonitor.hasEventTriggered(caseId, COMET_PAUSE_ACK, CommonUtils.TIMEOUT))
        .isTrue();
  }

  @And("the fulfilment request is acknowledged as ignored")
  public void theFulfilmentRequestIsAcknowledgedAsIgnored() {
    String caseId = testBucket.get("caseId");
    assertThat(gatewayEventMonitor.hasEventTriggered(caseId, NO_RECORD_FOUND, CommonUtils.TIMEOUT)).isTrue();
  }

  @And("the fulfilment request is acknowledged as already cancelled")
  public void theFulfilmentRequestIsAcknowledgedAsAlreadyCancelled() {
    String caseId = testBucket.get("caseId");
    assertThat(gatewayEventMonitor.hasEventTriggered(caseId, CASE_ALREADY_CANCELLED, CommonUtils.TIMEOUT))
        .isTrue();
  }

  @And("the fulfilment request is recorded as having an unrecognised fulfilment code")
  public void theFulfilmentRequestIsRecordedAsHavingAnUnrecognisedFulfilmentCode() {
    String caseId = testBucket.get("caseId");
    assertThat(gatewayEventMonitor.hasEventTriggered(caseId, UNRECOGNISED_FULFILLMENT_CODE, CommonUtils.TIMEOUT))
        .isTrue();
  }

  private void publishFulfilmentRequest(String fulfilmentCode, String caseId, String channel) {
    correlationId = UUID.randomUUID().toString();
    testBucket.put("caseId", caseId);

    JSONObject header = new JSONObject()
        .put("topic", FULFILMENT_REQUEST_TOPIC)
        .put("messageType", "FULFILMENT_REQUEST")
        .put("source", "CONTACT_CENTRE_API")
        .put("channel", channel)
        .put("version", "0.5.0")
        .put("dateTime", Instant.now().toString())
        .put("messageId", UUID.randomUUID().toString())
        .put("correlationId", correlationId);
    JSONObject fulfilmentRequest = new JSONObject()
        .put("fulfilmentCode", fulfilmentCode)
        .put("caseId", caseId);
    JSONObject payload = new JSONObject().put("fulfilmentRequest", fulfilmentRequest);
    JSONObject event = new JSONObject().put("header", header).put("payload", payload);

    queueClient.publishToTopic(FULFILMENT_REQUEST_TOPIC, event.toString(), Map.of());
  }

  private JSONObject internalActionInstructionPayload() {
    assertThat(internalActionInstruction).isNotNull();
    return new JSONObject(internalActionInstruction.body());
  }
}