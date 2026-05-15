package uk.gov.ons.census.fwmt.tests.acceptance.steps.inbound.resilience;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.census.fwmt.tests.acceptance.steps.inbound.common.CommonUtils.testBucket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import cucumber.api.java.After;
import cucumber.api.java.Before;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.ons.census.fwmt.common.error.GatewayException;
import uk.gov.ons.census.fwmt.events.utils.GatewayEventMonitor;
import uk.gov.ons.census.fwmt.tests.acceptance.steps.inbound.common.CommonUtils;
import uk.gov.ons.census.fwmt.tests.acceptance.utils.QueueClient;
import uk.gov.ons.census.fwmt.tests.acceptance.utils.TMMockUtils;

@Slf4j
public class ResilienceSteps {

  @Autowired
  private TMMockUtils tmMockUtils;

  @Autowired
  private GatewayEventMonitor gatewayEventMonitor;

  @Autowired
  private QueueClient queueClient;

  @Autowired
  private CommonUtils commonUtils;

  ObjectMapper mapper = new ObjectMapper();

  private static final String RM_CREATE_REQUEST_RECEIVED = "RM_CREATE_REQUEST_RECEIVED";
  private static final String RM_UPDATE_REQUEST_RECEIVED = "RM_UPDATE_REQUEST_RECEIVED";
  private static final String RM_CANCEL_REQUEST_RECEIVED = "RM_CANCEL_REQUEST_RECEIVED";
  private static final String RM_CREATE_SWITCH_REQUEST_RECEIVED = "RM_CREATE_SWITCH_REQUEST_RECEIVED";
  private static final String COMET_UPDATE_ACK = "COMET_UPDATE_ACK";
  private static final String COMET_CANCEL_ACK = "COMET_CANCEL_ACK";
  private static final String REJECTED_RM_REQUEST = "REJECTED_RM_REQUEST";
  private static final String NO_ACTION_REQUIRED = "NO_ACTION_REQUIRED";
  private static final String ROUTING_FAILED = "ROUTING_FAILED";

  private String cancelJson = null;
  private String ceEstabCreateJson = null;
  private String ceSwitch = null;
  private String updateJson = null;

  @Before
  public void setup() throws Exception {
    cancelJson = Resources.toString(Resources.getResource("files/input/ce/ceEstabCancel.json"), Charsets.UTF_8);
    ceEstabCreateJson = Resources.toString(Resources.getResource("files/input/ce/ceEstabCreate.json"), Charsets.UTF_8);
    ceSwitch = Resources.toString(Resources.getResource("files/input/ce/ceSwitch.json"), Charsets.UTF_8);
    updateJson = Resources.toString(Resources.getResource("files/input/ce/ceEstabUpdate.json"), Charsets.UTF_8);
    commonUtils.setup();
  }

  @After
  public void clearDown() throws Exception {
    commonUtils.clearDown();
  }

  @Given("that gateway has a message stored of type {string} with case ID {string}")
  public void checkStoredMessageType(String actionStored, String caseId) throws Exception {
    boolean cancelNeeded = false;
    boolean updateNeeded = false;
    JSONObject json = null;
    JSONObject jsonCancel = new JSONObject(cancelJson);
    JSONObject jsonCreate = new JSONObject(ceEstabCreateJson);
    JSONObject jsonUpdate = new JSONObject(updateJson);
    String request;
    String rmAction = null;

    testBucket.put("caseId", caseId);
    testBucket.put("actionStored", actionStored);
    if (actionStored.equals("Empty")) {
      int checkRecords = tmMockUtils.checkCaseIdExists(caseId);
      assertThat(checkRecords).isZero();
    } else {
      switch (actionStored) {
        case "Cancel":
          cancelNeeded = true;
          break;
        case "Cancel(Held)":
          json = jsonCancel;
          rmAction = "cancel";
          break;
        case "Create":
          json = new JSONObject(ceEstabCreateJson);
          json.put("handDeliver", true);
          rmAction = "create";
          break;
        case "Update":
          updateNeeded = true;
          break;
        case "Update(Held)":
          json = jsonUpdate;
          rmAction = "update";
          break;
        default:
          throw new GatewayException(GatewayException.Fault.SYSTEM_ERROR, actionStored, "No such action");
      }
      if (cancelNeeded) {
        request = jsonCreate.toString(4);
        log.info("Request = " + request);
        queueClient.sendToRMFieldQueue(request, "create");
        assertThat(waitForGatewayAction("Create", caseId)).isEqualTo(1);
        request = jsonCancel.toString(4);
        log.info("Request = " + request);
        queueClient.sendToRMFieldQueue(request, "cancel");
      } else if (updateNeeded) {
        request = jsonCreate.toString(4);
        log.info("Request = " + request);
        queueClient.sendToRMFieldQueue(request, "create");
        assertThat(waitForGatewayAction("Create", caseId)).isEqualTo(1);
        request = jsonUpdate.toString(4);
        log.info("Request = " + request);
        queueClient.sendToRMFieldQueue(request, "update");
      } else {
        request = json.toString(4);
        log.info("Request = " + request);
        queueClient.sendToRMFieldQueue(request, rmAction);
      }
      assertThat(waitForGatewayAction(actionStored, caseId)).isEqualTo(1);
    }
  }

  @And("RM sends a {string} with the same case ID that is {string} than whats in cache")
  public void rmSendsNewActionInstruction(String instruction, String messageAge) throws Exception {
    JSONObject json;
    String caseId = testBucket.get("caseId");
    String rmAction;
    String gatewayResponse;
    String storedAction = testBucket.get("actionStored");

    int rowCount = "Empty".equals(storedAction) ? 0 : waitForGatewayAction(storedAction, caseId);

    testBucket.put("instruction", instruction);

    if (messageAge.equals("Older") && rowCount == 1) {
      tmMockUtils.updateStoredMessageTimeStamp(storedAction, caseId);
    }

    switch (instruction) {
      case "Create":
        json = new JSONObject(ceEstabCreateJson);
        json.put("handDeliver", true);
        rmAction = "create";
        gatewayResponse = RM_CREATE_REQUEST_RECEIVED;
        break;
      case "Update":
        json  = new JSONObject(updateJson);
        rmAction = "update";
        gatewayResponse = RM_UPDATE_REQUEST_RECEIVED;
        break;
      case "Cancel":
        json = new JSONObject(cancelJson);
        rmAction = "cancel";
        gatewayResponse = RM_CANCEL_REQUEST_RECEIVED;
        break;
      case "CE Switch":
        json = new JSONObject(ceSwitch);
        rmAction = "create";
        gatewayResponse = RM_CREATE_SWITCH_REQUEST_RECEIVED;
        break;
      default:
        throw new GatewayException(GatewayException.Fault.SYSTEM_ERROR, instruction, "No such action");
    }

    String request = json.toString(4);
    log.info("Request = " + request);
    queueClient.sendToRMFieldQueue(request, rmAction);
    boolean hasBeenTriggered = gatewayEventMonitor.hasEventTriggered(caseId, gatewayResponse, CommonUtils.TIMEOUT);
    assertThat(hasBeenTriggered).isTrue();
  }

  @Then("the gateway will {string} the message")
  public void gatewayProcessingResponse(String gatewayAction) throws GatewayException {
    String caseId = testBucket.get("caseId");
    String actionStored = testBucket.get("actionStored");
    boolean hasBeenTriggered = false;
    boolean isProcess = false;
    switch (gatewayAction) {
      case "Merge":
        if (actionStored.equals("Cancel(Held)")) {
          hasBeenTriggered = gatewayEventMonitor.hasEventTriggered(caseId, COMET_CANCEL_ACK, CommonUtils.TIMEOUT);
        } else if (actionStored.equals("Update(Held)")) {
          hasBeenTriggered = gatewayEventMonitor.hasEventTriggered(caseId, COMET_UPDATE_ACK, CommonUtils.TIMEOUT);
        }
        break;
      case "No Action":
        hasBeenTriggered = gatewayEventMonitor.hasEventTriggered(caseId, NO_ACTION_REQUIRED, CommonUtils.TIMEOUT);
        break;
      case "Process":
        isProcess = true;
        break;
      case "Reject":
        hasBeenTriggered = gatewayEventMonitor.hasErrorEventTriggered(caseId, REJECTED_RM_REQUEST, CommonUtils.TIMEOUT);
        break;
      default:
        throw new GatewayException(GatewayException.Fault.SYSTEM_ERROR, gatewayAction, "No such process");
    }

    if (isProcess) {
      String instruction = testBucket.get("instruction");
      switch (instruction) {
        case "Cancel":
          hasBeenTriggered = gatewayEventMonitor.hasEventTriggered(caseId, RM_CANCEL_REQUEST_RECEIVED, CommonUtils.TIMEOUT);
          break;
        case "Create":
          hasBeenTriggered = gatewayEventMonitor.hasEventTriggered(caseId, RM_CREATE_REQUEST_RECEIVED, CommonUtils.TIMEOUT);
          break;
        case "Update":
          hasBeenTriggered = gatewayEventMonitor.hasEventTriggered(caseId, RM_UPDATE_REQUEST_RECEIVED, CommonUtils.TIMEOUT);
          break;
        case "CE Switch":
          hasBeenTriggered = gatewayEventMonitor.hasEventTriggered(caseId, RM_CREATE_SWITCH_REQUEST_RECEIVED, CommonUtils.TIMEOUT);
          break;
        default:
          throw new GatewayException(GatewayException.Fault.SYSTEM_ERROR, gatewayAction, "No such instruction");
      }
    }
    assertThat(hasBeenTriggered).isTrue();
  }

  @Then("this message will have the {string} in the message cache and {string} in the gateway cache")
  public void checkActionInMessageCache(String actionGateway, String actionMessage) throws Exception {
    String caseId = testBucket.get("caseId");
    int checkGatewayCache;
    int checkMessageCache = 0;

    switch (actionGateway) {
      case "Create":
      case "Update":
      case "Cancel":
        checkGatewayCache = waitForGatewayAction(actionGateway, caseId);
        break;
      case "Update(held)":
      case "Cancel(held)":
        checkGatewayCache = waitForGatewayAction(actionGateway, caseId);
        checkMessageCache = waitForMessageAction(actionMessage, caseId);
        break;
      default:
        throw new GatewayException(GatewayException.Fault.SYSTEM_ERROR, actionGateway, "No such action");
    }

    if ((actionGateway.equals("Cancel(held)") || actionGateway.equals("Update(held)")) && !actionMessage.isEmpty()) {
      assertThat(checkGatewayCache).isEqualTo(1);
      assertThat(checkMessageCache).isEqualTo(1);
    } else {
      assertThat(checkGatewayCache).isEqualTo(1);
    }
  }

  @Then("the gateway will not process the stored message the message")
  public void gatewayWillNotProcessMessage() {
    String caseId = testBucket.get("caseId");
    boolean hasBeenTriggered = gatewayEventMonitor.hasErrorEventTriggered(caseId, ROUTING_FAILED, CommonUtils.TIMEOUT);
    assertThat(hasBeenTriggered).isTrue();
  }

  private int waitForGatewayAction(String action, String caseId) throws Exception {
    long endTime = System.currentTimeMillis() + CommonUtils.TIMEOUT;
    int count;
    do {
      count = tmMockUtils.checkActionExistsInGatewayCache(action, caseId);
      if (count == 1) {
        return count;
      }
      Thread.sleep(250);
    } while (System.currentTimeMillis() < endTime);
    return count;
  }

  private int waitForMessageAction(String action, String caseId) throws Exception {
    if (action.isEmpty()) {
      return 0;
    }

    long endTime = System.currentTimeMillis() + CommonUtils.TIMEOUT;
    int count;
    do {
      count = tmMockUtils.checkActionExistsInMessageCache(action, caseId);
      if (count == 1) {
        return count;
      }
      Thread.sleep(250);
    } while (System.currentTimeMillis() < endTime);
    return count;
  }
}
