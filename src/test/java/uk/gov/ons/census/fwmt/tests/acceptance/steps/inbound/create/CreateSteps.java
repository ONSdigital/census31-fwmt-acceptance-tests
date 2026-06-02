package uk.gov.ons.census.fwmt.tests.acceptance.steps.inbound.create;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static uk.gov.ons.census.fwmt.tests.acceptance.steps.inbound.common.CommonUtils.testBucket;

import java.net.URISyntaxException;
import java.util.List;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;
import uk.gov.ons.census.fwmt.common.data.tm.Case;
import uk.gov.ons.census.fwmt.events.data.GatewayEventDTO;
import uk.gov.ons.census.fwmt.tests.acceptance.messaging.AcceptanceGatewayEventMonitor;
import uk.gov.ons.census.fwmt.tests.acceptance.steps.inbound.common.CommonUtils;
import uk.gov.ons.census.fwmt.tests.acceptance.utils.QueueClient;
import uk.gov.ons.census.fwmt.tests.acceptance.utils.TMMockUtils;

@Slf4j
public class CreateSteps {

  @Autowired
  private TMMockUtils tmMockUtils;

  @Autowired
  private AcceptanceGatewayEventMonitor gatewayEventMonitor;

  private static final String RM_CREATE_REQUEST_RECEIVED = "RM_CREATE_REQUEST_RECEIVED";

  private static final String COMET_CREATE_PRE_SENDING = "COMET_CREATE_PRE_SENDING";

  private static final String COMET_CREATE_ACK = "COMET_CREATE_ACK";

  private static final String RM_CREATE_SWITCH_REQUEST_RECEIVED = "RM_CREATE_SWITCH_REQUEST_RECEIVED";

  public static final String COMET_CLOSE_ACK = "COMET_CLOSE_ACK";

  public static final String COMET_REOPEN_ACK = "COMET_REOPEN_ACK";

  private String ceSpgEstabCreateJson = null;

  private String ceSpgUnitCreateJson = null;

  private String ceEstabCreateJson = null;

  private String ceUnitCreateJson = null;
  
  private String hhCreateJson = null;

  private GatewayEventDTO event_COMET_CREATE_PRE_SENDING;

  @Autowired
  private QueueClient queueClient;

  @Autowired
  private CommonUtils commonUtils;

  @Before
  public void setup() throws Exception {
    ceSpgEstabCreateJson = Resources.toString(Resources.getResource("files/input/spg/spgEstabCreate.json"), Charsets.UTF_8);
    ceSpgUnitCreateJson = Resources.toString(Resources.getResource("files/input/spg/spgUnitCreate.json"), Charsets.UTF_8);
    ceEstabCreateJson = Resources.toString(Resources.getResource("files/input/ce/ceEstabCreate.json"), Charsets.UTF_8);
    ceUnitCreateJson = Resources.toString(Resources.getResource("files/input/ce/ceUnitCreate.json"), Charsets.UTF_8);
    hhCreateJson = Resources.toString(Resources.getResource("files/input/hh/hhCreate.json"), Charsets.UTF_8);
    commonUtils.setup();
  }

  @After
  public void clearDown() throws Exception {
    commonUtils.clearDown();
  }

  @Given("a TM doesnt have a job with case ID {string} in TM")
  public void aTMDoesntHaveAJobWithCaseIDInTM(String caseId) {
    try {
      testBucket.put("caseId", caseId);
      tmMockUtils.getCaseById(caseId);
      fail("Case should not exist");
    } catch (HttpClientErrorException e) {
      assertEquals(HttpStatus.NOT_FOUND, e.getStatusCode());
    }
  }

  @Given("a job with case ID {string}, exists in FWMT {string}, estabUprn {string} with type of address {string} exists in cache")
  public void cacheHasCaseIdandEstabUprn(String caseId, String exisitsInFwmt, String estabUprn, String type) throws Exception {
    boolean exists = Boolean.parseBoolean(exisitsInFwmt);
    boolean ifExists;
    int establishmentUprn = Integer.parseInt(estabUprn);
    int typeOfAddress = Integer.parseInt(type);

    testBucket.put("caseId", caseId);
    testBucket.put("estabUprn", estabUprn);

    tmMockUtils.addToDatabase(caseId, exists, establishmentUprn, typeOfAddress);

    ifExists = tmMockUtils.checkExists();

    if (!ifExists) {
      fail("Case does not exist");
    }
  }

  @Given("a CE Unit with estabUprn {string} exists in cache")
  public void aCEUnitWithEstabUprnExistsInCache(String estabUprn) throws Exception {
    tmMockUtils.addToDatabase("cached-ce-unit-case", true, Integer.parseInt(estabUprn), 3);
  }

  @And("RM sends a create job request with {string} {string} {string} {string} {string}")
  public void rmSendsACECreateJobRequest(String caseRef, String survey, String type, String isSecure, String isHandDeliver) throws Exception {
    testBucket.put("survey", survey);
    testBucket.put("type", type);

    String caseId = testBucket.get("caseId");

    JSONObject json = new JSONObject(getCreateRMJson());

    commonRMMessageObjects(json, caseId, caseRef, isSecure, isHandDeliver, false);

    String request = json.toString(4);
    log.info("Request = " + request);
    queueClient.sendToRMFieldQueue(request, "create");
    boolean hasBeenTriggered = gatewayEventMonitor.hasEventTriggered(caseId, RM_CREATE_REQUEST_RECEIVED, CommonUtils.TIMEOUT);
    assertThat(hasBeenTriggered).isTrue();
  }

  @Given("RM sends a HH create job request")
  public void rm_sends_a_HH_create_job_request() throws URISyntaxException {
    String caseId = testBucket.get("caseId");
    testBucket.put("survey", "HH");

    JSONObject json = new JSONObject(getCreateRMJson());

    commonRMMessageObjects(json, caseId, "12345", "F", "F", true);
    
    String request = json.toString(4);
    log.info("Request = " + request);
    queueClient.sendToRMFieldQueue(request, "create");
    boolean hasBeenTriggered = gatewayEventMonitor.hasEventTriggered(caseId, RM_CREATE_REQUEST_RECEIVED, CommonUtils.TIMEOUT);
    assertThat(hasBeenTriggered).isTrue();
  }

  
  @Given("RM sends a HH create job request with {string} {string} {string}")
  public void rm_sends_a_HH_create_job_request_with(String caseRef, String survey, String oa) throws URISyntaxException {
    testBucket.put("survey", survey);
    testBucket.put("type", null);
    testBucket.put("oa", oa);
   
  
    String caseId = testBucket.get("caseId");

    JSONObject json = new JSONObject(getCreateRMJson());

    commonRMMessageObjects(json, caseId, caseRef, "F", "F", true);
    json.remove("oa");
    json.put("oa", oa);
    
    String request = json.toString(4);
    log.info("Request = " + request);
    queueClient.sendToRMFieldQueue(request, "create");
    boolean hasBeenTriggered = gatewayEventMonitor.hasEventTriggered(caseId, RM_CREATE_REQUEST_RECEIVED, CommonUtils.TIMEOUT);
    assertThat(hasBeenTriggered).isTrue();
  }
  
  @And("RM sends a create CE Site job request with {string} {string} {string} {string} {string}")
  public void rmSendsACECreateSiteJobRequest(String caseRef, String survey, String type, String isSecure, String isHandDeliver) throws Exception {
    String caseId = "f78607a6-bab4-11ea-b3de-0242ac130004";

    testBucket.put("survey", survey);
    testBucket.put("type", type);
    testBucket.put("caseId", caseId);

    JSONObject json = new JSONObject(getCreateRMJson());

    commonRMMessageObjects(json, caseId, caseRef, isSecure, isHandDeliver, true);

    String request = json.toString(4);
    log.info("Request = " + request);
    queueClient.sendToRMFieldQueue(request, "create");
    boolean hasBeenTriggered = gatewayEventMonitor.hasEventTriggered(caseId, RM_CREATE_REQUEST_RECEIVED, CommonUtils.TIMEOUT);
    assertThat(hasBeenTriggered).isTrue();
  }

  @And("RM sends a create CE Est job request with uprn matching estabUPRN with {string} {string} {string} {string} {string}")
  public void rmSendsACEEstabWithMatchingUPRNJobRequest(String caseRef, String survey, String type, String isSecure, String isHandDeliver) throws Exception {
    testBucket.put("survey", survey);
    testBucket.put("type", type);

    String caseId = testBucket.get("caseId");

    JSONObject json = new JSONObject(getCreateRMJson());

    commonRMMessageObjects(json, caseId, caseRef, isSecure, isHandDeliver, false);
    json.remove("uprn");
    json.put("uprn", json.get("estabUprn"));

    String request = json.toString(4);
    log.info("Request = " + request);
    queueClient.sendToRMFieldQueue(request, "create");
    boolean hasBeenTriggered = gatewayEventMonitor.hasEventTriggered(caseId, RM_CREATE_REQUEST_RECEIVED, CommonUtils.TIMEOUT);
    assertThat(hasBeenTriggered).isTrue();
  }

  @And("RM sends a create CE Unit with the same estabUPRN as the above CE Est request with {string} {string} {string} {string} {string}")
  public void rmSendsACEUnitWithMatchingEstabUPRNJobRequest(String caseRef, String survey, String type, String isSecure, String isHandDeliver) throws Exception {
    String caseId = "f78607a6-bab4-11ea-b3de-0242ac130004";

    testBucket.put("survey", survey);
    testBucket.put("type", type);
    testBucket.put("caseId", caseId);


    JSONObject json = new JSONObject(getCreateRMJson());

    commonRMMessageObjects(json, caseId, caseRef, isSecure, isHandDeliver, true);

    String request = json.toString(4);
    log.info("Request = " + request);
    queueClient.sendToRMFieldQueue(request, "create");
    boolean hasBeenTriggered = gatewayEventMonitor.hasEventTriggered(caseId, RM_CREATE_REQUEST_RECEIVED, CommonUtils.TIMEOUT);
    assertThat(hasBeenTriggered).isTrue();
  }

  @When("the Gateway sends a Create Job message to TM")
  public void theGatewaySendsACreateJobMessageToTM() {
    String caseId = testBucket.get("caseId");
    boolean hasBeenTriggered = gatewayEventMonitor.hasEventTriggered(caseId, COMET_CREATE_PRE_SENDING, CommonUtils.TIMEOUT);
    assertThat(hasBeenTriggered).isTrue();
    List<GatewayEventDTO> events = gatewayEventMonitor.getEventsForEventType(COMET_CREATE_PRE_SENDING, 1);
    event_COMET_CREATE_PRE_SENDING = null;
    for (GatewayEventDTO event : events) {
      if (caseId.equals(event.getCaseId())) {
        event_COMET_CREATE_PRE_SENDING = event;
        break;
      }
    }
    assertThat(event_COMET_CREATE_PRE_SENDING).isNotNull();
  }

  @Then("a new case is created of the right {string}")
  public void a_new_case_is_created_of_the_right_type(String expectedSurveyType) {
    String actualSurveyType = event_COMET_CREATE_PRE_SENDING.getMetadata().get("Survey Type");
    assertEquals("Survey Types created for TM", expectedSurveyType, actualSurveyType);
  }

  @And("the right caseRef {string}")
  public void and_the_right_caseref(String expectedCaseRef) {
    String actualCaseRef = event_COMET_CREATE_PRE_SENDING.getMetadata().get("Case Ref");
    assertEquals("Case Ref created for TM", expectedCaseRef, actualCaseRef);
  }

  @Then("a new case with id of {string} is created in TM")
  public void aNewCaseIsCreatedInTm(String caseId) throws InterruptedException {
    boolean hasBeenTriggered = gatewayEventMonitor.hasEventTriggered(caseId, COMET_CREATE_ACK, CommonUtils.TIMEOUT);
    assertThat(hasBeenTriggered).isTrue();

    Case modelCase = tmMockUtils.getCaseById(caseId);
    assertEquals(caseId, modelCase.getId().toString());
  }

  @And("the existing case is updated to a switch and put back on the queue with caseId {string}")
  public void sendBackToQueue(String caseId){
    boolean hasBeenTriggered = gatewayEventMonitor.hasEventTriggered(caseId, RM_CREATE_SWITCH_REQUEST_RECEIVED, CommonUtils.TIMEOUT);
    assertThat(hasBeenTriggered).isTrue();
  }

  @Then("the related case will be closed with case ID {string}")
  public void sendCloseToQueue(String caseId) {
    boolean hasBeenTriggered = gatewayEventMonitor.hasEventTriggered(caseId, COMET_CLOSE_ACK, CommonUtils.TIMEOUT);
    assertThat(hasBeenTriggered).isTrue();
  }

  @And("then reopened with the new SurveyType {string} and case ID {string}")
  public void sendReopenToQueue(String surveyType, String caseId) {
    boolean hasBeenTriggered = gatewayEventMonitor.hasEventTriggered(caseId, COMET_REOPEN_ACK, CommonUtils.TIMEOUT);
    assertThat(hasBeenTriggered).isTrue();
  }

  private String getCreateRMJson() {
    String type = testBucket.get("type");
    String survey = testBucket.get("survey");

    if (survey.equals("HH"))
      return hhCreateJson;
    
    switch (type) {
      case "Estab" :
        return ceSpgEstabCreateJson;
      case "Unit" :
        return ceSpgUnitCreateJson;
      case "CE Est" :
      case "CE Site":
        return ceEstabCreateJson;
      case "CE Unit" :
          return ceUnitCreateJson;
      default:
        throw new RuntimeException("Incorrect survey " + survey + " and type " + type);
    }
  }

  private JSONObject updateSecure(JSONObject json, String isSecure) {
    if ("T".equals(isSecure)) {
      json.remove("secureEstablishment");
      json.put("secureEstablishment", true);
    }
    return json;
  }

  private JSONObject updateHandDeliver(JSONObject json, String isHandDeliver) {
    if ("T".equals(isHandDeliver)){
      json.remove("handDeliver");
      json.put("handDeliver", true);
    }
    return json;
  }

  private JSONObject commonRMMessageObjects(JSONObject json, String caseId, String caseRef, String isSecure, String isHandDeliver, boolean extraObjects){
    if ("T".equals(isSecure)) {
      json.remove("secureEstablishment");
      json.put("secureEstablishment", true);
    }else {
      json.remove("secureEstablishment");
      json.put("secureEstablishment", false);
    }


    if ("T".equals(isHandDeliver)){
      json.remove("handDeliver");
      json.put("handDeliver", true);
    }else {
      json.remove("handDeliver");
      json.put("handDeliver", false);
    }

    json.remove("caseRef");
    json.put("caseRef", caseRef);

    if ("HH".equals(testBucket.get("survey"))) {
      json.remove("addressLevel");
      json.put("addressLevel", "U");
      json.remove("oa");
      json.put("oa", "NISRA".equals(testBucket.get("type")) ? "N00000001" : "E00167164");
    }

    if (extraObjects == true) {
      json.remove("caseRef");
      json.put("caseRef", caseRef);

      json.remove("uprn");
      json.put("uprn", json.get("estabUprn"));

      json.remove("caseId");
      json.put("caseId", caseId);
    }

    return json;
  }

  
}
