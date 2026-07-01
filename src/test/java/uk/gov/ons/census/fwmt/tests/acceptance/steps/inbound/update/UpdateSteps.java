package uk.gov.ons.census.fwmt.tests.acceptance.steps.inbound.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static uk.gov.ons.census.fwmt.tests.acceptance.steps.inbound.common.CommonUtils.testBucket;

import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import uk.gov.ons.census.fwmt.events.data.GatewayEventDTO;
import uk.gov.ons.census.fwmt.tests.acceptance.messaging.AcceptanceGatewayEventMonitor;
import uk.gov.ons.census.fwmt.tests.acceptance.steps.inbound.common.CommonUtils;
import uk.gov.ons.census.fwmt.tests.acceptance.utils.QueueClient;

import java.time.OffsetDateTime;


@Slf4j
public class UpdateSteps {

  private CommonUtils commonUtils;

  @Autowired
  private QueueClient queueClient;

  @Autowired
  private AcceptanceGatewayEventMonitor gatewayEventMonitor;

  private static final String RM_UPDATE_REQUEST_RECEIVED = "RM_UPDATE_REQUEST_RECEIVED";

  private static final String COMET_UPDATE_PRE_SENDING = "COMET_UPDATE_PRE_SENDING";

  private static final String COMET_UPDATE_ACK = "COMET_UPDATE_ACK";

  private static final String COMET_CREATE_ACK = "COMET_CREATE_ACK";

  private static final String ROUTING_FAILED = "ROUTING_FAILED";

  private static final String CONVERT_SPG_UNIT_UPDATE_TO_CREATE = "CONVERT_SPG_UNIT_UPDATE_TO_CREATE";

  private static final String COMET_CREATE_PRE_SENDING = "COMET_CREATE_PRE_SENDING";

  private static final String PROCESSING = "PROCESSING";

  private static final String COMET_DELETE_ACK = "COMET_DELETE_ACK";

  private static final String RM_PAUSE_REQUEST_RECEIVED = "RM_PAUSE_REQUEST_RECEIVED";

  private static final String COMET_PAUSE_PRE_SENDING = "COMET_PAUSE_PRE_SENDING";

  private static final String COMET_PAUSE_ACK = "COMET_PAUSE_ACK";

  private static final String MESSAGE_HELD = "MESSAGE_HELD";
  
  private static String hhUpdateJson = null;
  
  private static String hhPauseCaseJson = null;

  private String ceSpgEstabUpdateJson = null;

  private String ceSpgUnitUpdateJson = null;

  private String ceEstabUpdateJson = null;

  private String ceUnitUpdateJson = null;

  private String ceSpgEstabCreateJson = null;

  private String ceSpgUnitCreateJson = null;

  private GatewayEventDTO event_COMET_UPDATE_ACK;

  @Before
  public void setup() throws Exception {
    ceSpgEstabCreateJson = Resources.toString(Resources.getResource("files/input/spg/spgEstabCreate.json"), Charsets.UTF_8);
    ceSpgUnitCreateJson = Resources.toString(Resources.getResource("files/input/spg/spgUnitCreate.json"), Charsets.UTF_8);

    ceSpgEstabUpdateJson = Resources.toString(Resources.getResource("files/input/spg/spgEstabUpdate.json"), Charsets.UTF_8);
    ceSpgUnitUpdateJson = Resources.toString(Resources.getResource("files/input/spg/spgUnitUpdate.json"), Charsets.UTF_8);
    ceEstabUpdateJson = Resources.toString(Resources.getResource("files/input/ce/ceEstabUpdate.json"), Charsets.UTF_8);
    ceUnitUpdateJson = Resources.toString(Resources.getResource("files/input/ce/ceUnitUpdate.json"), Charsets.UTF_8);
    
    hhUpdateJson = Resources.toString(Resources.getResource("files/input/hh/hhUpdate.json"), Charsets.UTF_8);
    hhPauseCaseJson = Resources.toString(Resources.getResource("files/input/hh/hhPauseCase.json"), Charsets.UTF_8);
    //    commonUtils.setup();
 }

  @After
  public void clearDown() throws Exception {
//    commonUtils.clearDown();
  }

  @And("RM sends an update case request for the case")
  public void rmSendsUpdate() throws URISyntaxException {
    String caseId = testBucket.get("caseId");
    String type = testBucket.get("type");

    JSONObject json = new JSONObject(getUpdateRMJson());
    json.remove("caseId");
    json.put("caseId", caseId);

    json.remove("addressLevel");
    if ("Estab".equals(type) || "CE Est".equals(type)) {
      json.put("addressLevel", "E");
    }
    if ("Unit".equals(type) || "CE Unit".equals(type)) {
      json.put("addressLevel", "U");
    }
    
    if ("CE Site".equals(type)){
      json.remove("uprn");
      json.put("uprn", json.get("estabUprn"));
      json.remove("caseId");
      json.put("caseId", "f78607a6-bab4-11ea-b3de-0242ac130004");
      caseId = "f78607a6-bab4-11ea-b3de-0242ac130004";
      testBucket.put("caseId", caseId);
      json.put("addressLevel", "E");
    }
    String request = json.toString(4);
    log.info("Request = " + request);
    queueClient.sendToRMFieldQueue(request, "update");
  }

  @Given("RM sends a HH Pause Case request for the case")
  public void rm_sends_a_HH_Pause_Case_request_for_the_case() throws URISyntaxException {
    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");
    
    String caseId = testBucket.get("caseId");

    JSONObject json = new JSONObject(hhPauseCaseJson);
    json.remove("caseId");
    json.put("caseId", caseId);
    json.remove("addressLevel");
    json.put("addressLevel", "U");
  
    Date now = new Date();
    String pauseFrom = dateFormat.format(now);
    
    json.remove("pauseFrom");
    json.put("pauseFrom", pauseFrom);
    
    String request = json.toString(4);
    log.info("Request = " + request);
    queueClient.sendToRMFieldQueue(request, "update");  
  }

  
  @Given("RM sends a HH update case request for the case {string} {string}")
  public void rm_sends_a_HH_update_case_request_for_the_case(String isBlankFormReturned, String isUndeliveredAsAddress) throws URISyntaxException {
    String caseId = testBucket.get("caseId");
    String type = testBucket.get("type");
    String survey = testBucket.get("survey");
    String oa = testBucket.get("oa");

    JSONObject json = new JSONObject(getUpdateRMJson());
    json.remove("caseId");
    json.put("caseId", caseId);
    json.remove("oa");
    json.put("oa", oa);
    json.remove("addressLevel");
    json.put("addressLevel", "U");

    if ("T".equals(isBlankFormReturned)){
      json.remove("blankFormReturned");
      json.put("blankFormReturned", true);
    }else {
      json.remove("blankFormReturned");
      json.put("blankFormReturned", false);
    }
    
    if ("T".equals(isUndeliveredAsAddress)){
      json.remove("undeliveredAsAddress");
      json.put("undeliveredAsAddress", true);
    }else {
      json.remove("undeliveredAsAddress");
      json.put("undeliveredAsAddress", false);
    }
  
    String request = json.toString(4);
    log.info("Request = " + request);
    queueClient.sendToRMFieldQueue(request, "update");
  }
  
  @When("Gateway receives an update message for the case")
  public void gatewayReceivesTheMessage() {
    String caseId = testBucket.get("caseId");
    boolean hasBeenTriggered = gatewayEventMonitor.hasEventTriggered(caseId, RM_UPDATE_REQUEST_RECEIVED, CommonUtils.TIMEOUT);
    assertThat(hasBeenTriggered).isTrue();
  }
  
  @When("Gateway receives an HH Pause Case message for the case")
  public void gateway_receives_an_HH_Pause_Case_message_for_the_case() {
    String caseId = testBucket.get("caseId");
    boolean hasBeenTriggered = gatewayEventMonitor.hasEventTriggered(caseId, RM_PAUSE_REQUEST_RECEIVED, CommonUtils.TIMEOUT);
    assertThat(hasBeenTriggered).isTrue();
  }  
  
  @When("is Processed as {string}")
  public void is_Processed_as(String processedAs) {
    String caseId = testBucket.get("caseId");
    boolean hasBeenTriggered = gatewayEventMonitor.hasEventTriggered(caseId, PROCESSING, CommonUtils.TIMEOUT);
    assertThat(hasBeenTriggered).isTrue();

    List<GatewayEventDTO> processed = gatewayEventMonitor.getEventsForEventType(PROCESSING, 1);
    GatewayEventDTO event = null;
    for (GatewayEventDTO candidate : processed) {
      if (caseId.equals(candidate.getCaseId())) {
        event = candidate;
        break;
      }
    }
    assertThat(event).isNotNull();
        
    String actualType = event.getMetadata().get("type");
    assertEquals(processedAs, actualType);

  }
  
  @Then("an associated a Pause is deleted {string}")
  public void an_associated_a_Pause_is_deleted(String isDeleted) {
    boolean expectedIsDeleted = "T".equals(isDeleted);
    String caseId = testBucket.get("caseId");
    boolean hasBeenTriggered = gatewayEventMonitor.hasEventTriggered(caseId, COMET_DELETE_ACK, CommonUtils.TIMEOUT);
    assertEquals(expectedIsDeleted, hasBeenTriggered);
  }
  
  @Then("it will update the job in TM")
  public void confirmTmAction() {
    String caseId = testBucket.get("caseId");
    boolean hasBeenTriggered = gatewayEventMonitor.hasEventTriggered(caseId, COMET_UPDATE_PRE_SENDING, CommonUtils.TIMEOUT);
    assertThat(hasBeenTriggered).isTrue();
  }

  @And("the updated job is acknowledged by TM")
  public void the_cancel_job_is_acknowledged_by_tm() {
    String caseId = testBucket.get("caseId");
    boolean hasBeenTriggered = gatewayEventMonitor.hasEventTriggered(caseId, COMET_UPDATE_ACK, CommonUtils.TIMEOUT);
    assertTrue(hasBeenTriggered);
  }

  @Given("RM sends a unit update case request where undeliveredAsAddress is {string}")
  public void rm_sends_a_cancel_case_request(String undeliveredAsAddress) throws URISyntaxException {
    JSONObject json = new JSONObject(ceSpgUnitUpdateJson);
    json.remove("undeliveredAsAddress");
    json.put("undeliveredAsAddress", "true".equals(undeliveredAsAddress));
    testBucket.put("caseId", json.get("caseId").toString());

    String request = json.toString(4);
    log.info("Request = " + request);
    queueClient.sendToRMFieldQueue(request, "update");
  }

  @Then("the update job should fail")
  public void the_update_job_should_fail_by_tm() {
    String caseId = testBucket.get("caseId");
    boolean hasBeenTriggered = gatewayEventMonitor.hasErrorEventTriggered(caseId, ROUTING_FAILED, CommonUtils.TIMEOUT);
    assertThat(hasBeenTriggered).isTrue();
  }

  @Then("the update job should be held")
  public void theUpdateJobShouldBeHeld() {
    String caseId = testBucket.get("caseId");
    boolean hasBeenTriggered = gatewayEventMonitor.hasEventTriggered(caseId, MESSAGE_HELD, CommonUtils.TIMEOUT);
    assertThat(hasBeenTriggered).isTrue();
  }

  @Then("Gateway will reroute it as a create message")
  public void gateway_will_reroute_it_as_a_create_message() {
    String caseId = testBucket.get("caseId");
    boolean hasBeenTriggered = gatewayEventMonitor.hasEventTriggered(caseId, CONVERT_SPG_UNIT_UPDATE_TO_CREATE, CommonUtils.TIMEOUT);
    assertThat(hasBeenTriggered).isTrue();
  }

  @Then("Gateway will send a create job to TM")
  public void gateway_will_send_a_create_job_to_TM() {
    String caseId = testBucket.get("caseId");
    boolean hasBeenTriggered = gatewayEventMonitor.hasEventTriggered(caseId, COMET_CREATE_PRE_SENDING, CommonUtils.TIMEOUT);
    assertThat(hasBeenTriggered).isTrue();
  }

  @Then("it will Pause the job in TM")
  public void it_will_Pause_the_job_in_TM() {
    String caseId = testBucket.get("caseId");
    boolean hasBeenTriggered = gatewayEventMonitor.hasEventTriggered(caseId, COMET_PAUSE_PRE_SENDING, CommonUtils.TIMEOUT);
    assertThat(hasBeenTriggered).isTrue();
  }
  
  @Then("the create job is acknowledged by tm")
  public void the_create_job_is_acknowledged_by_tm() {
    String caseId = testBucket.get("caseId");
    boolean hasBeenTriggered = gatewayEventMonitor.hasEventTriggered(caseId, COMET_CREATE_ACK, CommonUtils.TIMEOUT);
    assertThat(hasBeenTriggered).isTrue();
  }

  @Then("the Paused job is acknowledged by TM")
  public void the_Paused_job_is_acknowledged_by_TM() {
    String caseId = testBucket.get("caseId");
    boolean hasBeenTriggered = gatewayEventMonitor.hasEventTriggered(caseId, COMET_PAUSE_ACK, CommonUtils.TIMEOUT);
    assertThat(hasBeenTriggered).isTrue();
  }
  
  private String getUpdateRMJson() {
    String survey = testBucket.get("survey");
    String type = testBucket.get("type");
    
    if (survey.equals("HH"))
      return hhUpdateJson;
    
    switch (type) {
    case "Estab":
      return ceSpgEstabUpdateJson;
    case "Unit":
      return ceSpgUnitUpdateJson;
    case "CE Est":
    case "CE Site":
      return ceEstabUpdateJson;
    case "CE Unit":
      return ceUnitUpdateJson;
    default:
      throw new RuntimeException("Incorrect survey " + survey + " and type " + type);
    }
  }

  @And("the Gateway sends a update HH job message to TM with {string} {string}")
  public void theGatewaySendsAUpdateHHJobMessageToTMWith(String undeliveredAsAddressed, String blankQuestionnaire) {

  }
}
