package uk.gov.ons.census.fwmt.tests.acceptance.steps.outcomes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.apache.logging.log4j.util.Strings;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import uk.gov.ons.census.fwmt.events.data.GatewayEventDTO;
import uk.gov.ons.census.fwmt.tests.acceptance.messaging.AcceptanceGatewayEventMonitor;
import uk.gov.ons.census.fwmt.tests.acceptance.steps.inbound.common.CommonUtils;
import uk.gov.ons.census.fwmt.tests.acceptance.utils.OutcomeServiceRefreshUtils;
import uk.gov.ons.census.fwmt.tests.acceptance.utils.QueueClient;
import uk.gov.ons.census.fwmt.tests.acceptance.utils.SpgReasonCodeLookup;
import uk.gov.ons.census.fwmt.tests.acceptance.utils.TMMockUtils;

@Slf4j
public class OutcomeSteps {

    private String surveyType;
    private String businessFunction;
    private String primaryOutcome;
    private String secondaryOutcome;
    private String outcomeCode;
    private boolean hasLinkedQid;
    private boolean hasFulfillmentRequest;
    private boolean hasUsualResidentsCount;
    private List<String> expectedProcessors = new ArrayList<>();
    private List<String> expectedRmMessages = new ArrayList<>();
    private List<String> expectedJsMessages = new ArrayList<>();
    private Map<String, String> actualRmMessageMap = new HashMap<>();
    private Map<String, String> expectedRmMessageMap = new HashMap<>();
    private String addressTypeChangeMsg;
    private String newCaseId;


    private Collection<GatewayEventDTO> processingEvents;

    private Collection<GatewayEventDTO> rmOutcomeEvents;

    private Collection<GatewayEventDTO> jsOutcomeEvents;

    private final static String caseId = "bd6345af-d706-43d3-a13b-8c549e081a76";

    // Pack code that outcome-service derives from questionnaireType HUAC1 (the value
    // hard-coded in FULFILMENT_REQUESTED-in.ftl) via its questionnaireTypeLookup.
    private final static String FULFILMENT_REQUESTED_PACK_CODE = "UACHHT1";

    private static final String TEMPLATE_TYPE_METADATA = "Template type";
    private static final String PROCESSOR_METADATA = "Processor";

    private final static String COMET_SPG_OUTCOME_RECEIVED = "COMET_SPG_OUTCOME_RECEIVED";

    private final static String COMET_CE_OUTCOME_RECEIVED = "COMET_CE_OUTCOME_RECEIVED";

    private final static String PROCESSING_OUTCOME = "PROCESSING_OUTCOME";

    private final static String OUTCOME_SENT = "OUTCOME_SENT";

    private final static String RM_FIELD_REPUBLISH = "RM_FIELD_REPUBLISH";

    private static final String FIELD_REFUSALS_QUEUE = "Field.refusals";

    private static final String TEMP_FIELD_OTHERS_QUEUE = "Field.other";

    private static final String COMET_SPG_UNITADDRESS_OUTCOME_RECEIVED = "COMET_SPG_UNITADDRESS_OUTCOME_RECEIVED";

    private static final String COMET_SPG_STANDALONE_OUTCOME_RECEIVED = "COMET_SPG_STANDALONE_OUTCOME_RECEIVED";

    private static final String COMET_CE_UNITADDRESS_OUTCOME_RECEIVED = "COMET_CE_UNITADDRESS_OUTCOME_RECEIVED";

    private static final String COMET_CE_STANDALONE_OUTCOME_RECEIVED = "COMET_CE_STANDALONE_OUTCOME_RECEIVED";

    private static final String RM_CREATE_REQUEST_RECEIVED = "RM_CREATE_REQUEST_RECEIVED";
    private static final String COMET_HH_SPLITADDRESS_RECEIVED = "COMET_HH_SPLITADDRESS_RECEIVED";
    private static final String COMET_HH_STANDALONE_RECEIVED = "COMET_HH_STANDALONE_RECEIVED";
    private static final String COMET_HH_OUTCOME_RECEIVED = "COMET_HH_OUTCOME_RECEIVED";
    private static final String COMET_NC_OUTCOME_RECEIVED = "COMET_NC_OUTCOME_RECEIVED";
    private static final String ncCaseId = "e0b12e26-5a6d-11eb-ae93-0242ac130002";

    @Autowired
    private QueueClient queueClient;

    @Autowired
    private CommonUtils commonUtils;

    @Autowired
    private AcceptanceGatewayEventMonitor gatewayEventMonitor;

    @Autowired
    private TMMockUtils tmMockUtils;

    @Autowired
    private OutcomeServiceRefreshUtils outcomeServiceRefreshUtils;

    private final ObjectMapper jsonObjectMapper = new ObjectMapper();

    @Autowired
    private SpgReasonCodeLookup spgReasonCodeLookup;


    @Before
    public void setup() throws Exception {

        commonUtils.setup();
      outcomeServiceRefreshUtils.enableDefaultOutcomeFeatureFlags();

        surveyType = null;
        businessFunction = null;
        primaryOutcome = null;
        secondaryOutcome = null;
        outcomeCode = null;
        hasLinkedQid = false;
        hasFulfillmentRequest = false;
        hasUsualResidentsCount = false;
        expectedProcessors.clear();
        expectedRmMessages.clear();
        expectedJsMessages.clear();
        actualRmMessageMap.clear();
        expectedRmMessageMap.clear();
        addressTypeChangeMsg = null;
        newCaseId = null;
    }

    @After
    public void tearDownGatewayEventMonitor() throws Exception {
      commonUtils.clearDown();
    }

    @Given("an {string} {string} outcome message")
    public void outcomeservice_receives_a_outcome_message(String surveyType, String businessFunction) {
        this.surveyType = surveyType;
        this.businessFunction = businessFunction;
    }

    @Given("its Primary Outcome is {string}")
    public void its_Primary_Outcome_is(String primaryOutcome) {
        this.primaryOutcome = primaryOutcome;
    }

    @Given("its secondary Outcome {string}")
    public void its_secondary_Outcome(String secondaryOutcome) {
        this.secondaryOutcome = secondaryOutcome;
    }

    @Given("its Outcome code is {string}")
    public void its_Outcome_code_is(String outcomeCode) {
        this.outcomeCode = outcomeCode;
    }

    @Given("the message includes a Linked QID {string}")
    public void the_message_includes_a_Linked_QID(String hasLinkedQid) {
        this.hasLinkedQid = "T".equals(hasLinkedQid);
    }

    @Given("the message includes a Fulfillment Request {string}")
    public void the_message_includes_a_Fulfillment_Request(String hasFulfillmentRequest) {
        this.hasFulfillmentRequest = "T".equals(hasFulfillmentRequest);
    }

    @When("Gateway receives the outcome")
    public void gateway_processes_the_outcome() throws Exception {
        sendTMOutcomeMessage();
        confirmOutcomeServiceReceivesMessage();
        if (isGeneratedCaseIdFlow()) {
            resolveGeneratedCaseIdFromPreprocessingEvent();
        }
    }

    private void collectProcessingEvents() {
        long deadline = System.currentTimeMillis() + CommonUtils.TIMEOUT;
        processingEvents = new ArrayList<>();
        while (System.currentTimeMillis() < deadline) {
            processingEvents = gatewayEventMonitor.grabEventsTriggered(PROCESSING_OUTCOME, 50, 500L).stream()
                    .filter(e -> matchesProcessingEventCaseId(e.getCaseId()))
                    .filter(e -> surveyType.equals(e.getMetadata().get("survey type")))
                    .collect(Collectors.toList());
            if (processingEvents.size() >= expectedProcessors.size()) {
                return;
            }
        }
    }

    private void collectRmOutcomeEvents() {
        long deadline = System.currentTimeMillis() + CommonUtils.TIMEOUT;
        rmOutcomeEvents = new ArrayList<>();
        while (System.currentTimeMillis() < deadline) {
            rmOutcomeEvents = gatewayEventMonitor.grabEventsTriggered(OUTCOME_SENT, 50, 500L).stream()
                    .filter(e -> matchesRmOutcomeEventCaseId(e.getCaseId()))
                    .filter(e -> surveyType.equals(e.getMetadata().get("survey type")))
                    .collect(Collectors.toList());
            if (rmOutcomeEvents.size() >= expectedRmMessages.size()) {
                return;
            }
        }
    }

    private void collectJsOutcomeEvents() {
        long deadline = System.currentTimeMillis() + CommonUtils.TIMEOUT;
        jsOutcomeEvents = new ArrayList<>();
        while (System.currentTimeMillis() < deadline) {
            jsOutcomeEvents = gatewayEventMonitor.grabEventsTriggered(RM_FIELD_REPUBLISH, 50, 500L).stream()
                    .filter(e -> matchesJsOutcomeEventCaseId(e.getCaseId()))
                    .filter(e -> surveyType.equals(e.getMetadata().get("Address Type")))
                    .collect(Collectors.toList());
            if (jsOutcomeEvents.size() >= expectedJsMessages.size()) {
                return;
            }
        }
    }


    @Then("It will run the following processors {string}")
    public void it_will_run_the_following_processors(String processors) {
        String[] processorsArray = (!Strings.isBlank(processors)) ? processors.split(",") : new String[0];
        expectedProcessors = Arrays.asList(processorsArray);
        if (isGeneratedCaseIdFlow() && newCaseId == null) {
            resolveGeneratedCaseIdFromPreprocessingEvent();
        }
        collectProcessingEvents();
        confirmProcessorsAreExcecuted();
    }

    @Then("create the following messages to RM {string}")
    public void create_the_following_messages_to_RM(String rmMessages) throws Exception{
        String[] rmMessagesArray = (!Strings.isBlank(rmMessages)) ? rmMessages.split(",") : new String[0];
        expectedRmMessages = Arrays.asList(rmMessagesArray);
        if (isGeneratedCaseIdFlow() && newCaseId == null) {
            resolveGeneratedCaseIdFromPreprocessingEvent();
        }
        if (isAddressTypeChangeFlow()) {
            resolveNewCaseIdFromAddressTypeChangedMessage();
        }
        collectRmOutcomeEvents();
        collectRmMessages();
        confirmRmMessagesAreSent();
        createExpectedRmMessages();
    }

    private void createExpectedRmMessages() throws Exception{
      expectedRmMessageMap.clear();
      for (String rmMessageType : expectedRmMessages) {
        Map<String, Object> root = new HashMap();

        root.clear();
        root.put("reason", spgReasonCodeLookup.getLookup(outcomeCode));
        // FULFILMENT_REQUESTED carries the RM pack code, not the outcome code. The TM
        // fulfilment input (FULFILMENT_REQUESTED-in.ftl) always uses questionnaireType
        // HUAC1, which outcome-service maps to pack code UACHHT1 before sending to RM.
        root.put("fulfilmentCode",
            "FULFILMENT_REQUESTED".equals(rmMessageType) ? FULFILMENT_REQUESTED_PACK_CODE : outcomeCode);
        root.put("newCaseId", newCaseId != null ? newCaseId : UUID.randomUUID().toString());
        root.put("surveyType", surveyType);
        root.put("usualResidents", usesCeSiteResidentCountZero() ? 0 : 5);
        root.put("newAddressUsualResidents", expectedNewAddressUsualResidents());
        String expectedRmMessage = createExpectedRmMessage(rmMessageType, root);
        expectedRmMessageMap.put(rmMessageType, expectedRmMessage);
      }
    }

    @Then("the caseId of the {string} message will be the original caseid")
    public void the_caseId_of_the_message_will_be_the_original_caseid(String messageType) throws Exception{
        addressTypeChangeMsg = actualRmMessageMap.get(messageType);
        System.out.println("Actual:" + addressTypeChangeMsg);
        JsonNode actualJson = jsonObjectMapper.readTree(addressTypeChangeMsg);
        JsonNode caseIdNode = actualJson.findPath("id");
        assertThat(caseIdNode!=null && !caseIdNode.isMissingNode()).isTrue();
        assertThat(caseId.equals(caseIdNode.asText())).isTrue();
    }

    private void collectRmMessages() throws Exception {
      for (String rmMessageType : expectedRmMessages) {
        if (actualRmMessageMap.containsKey(rmMessageType)) {
          continue;
        }
        String queue = operationToQueue(rmMessageType);
        String msg = queueClient.getMessageWithEventType(queue, rmMessageType, (int) CommonUtils.TIMEOUT, 50);
        JsonNode actualMessageRootNode = jsonObjectMapper.readTree(msg);
        JsonNode typeNode = actualMessageRootNode.path("event").path("type");
        actualRmMessageMap.put(typeNode.asText(), msg);
      }
    }

    private boolean isAddressTypeChangeFlow() {
      return businessFunction != null && businessFunction.startsWith("Address Type Changed");
    }

    /**
     * Address-type-change processors return a new caseId; fulfilment and linked-QID RM messages
     * (and their OUTCOME_SENT events) are keyed to that id, not the original parent caseId.
     */
    private void resolveNewCaseIdFromAddressTypeChangedMessage() throws Exception {
      if (!expectedRmMessages.contains("ADDRESS_TYPE_CHANGED") || newCaseId != null) {
        return;
      }
      String queue = operationToQueue("ADDRESS_TYPE_CHANGED");
      String msg = queueClient.getMessageWithEventType(queue, "ADDRESS_TYPE_CHANGED",
          (int) CommonUtils.TIMEOUT, 50);
      assertThat(msg).isNotNull();
      actualRmMessageMap.put("ADDRESS_TYPE_CHANGED", msg);
      addressTypeChangeMsg = msg;
      JsonNode newCaseIdNode = jsonObjectMapper.readTree(msg).findPath("newCaseId");
      assertThat(newCaseIdNode != null && !newCaseIdNode.isMissingNode()).isTrue();
      newCaseId = newCaseIdNode.asText();
    }

    private boolean matchesRmOutcomeEventCaseId(String eventCaseId) {
      if (getMessageCaseId().equals(eventCaseId)) {
        return true;
      }
      if ("NC".equals(surveyType) && caseId.equals(eventCaseId)) {
        return true;
      }
      if (newCaseId != null && newCaseId.equals(eventCaseId)) {
        return true;
      }
      return false;
    }

    /**
     * NC outcomes remap to the original HH caseId in the DTO, but {@code CANCEL_FEEDBACK} logs on
     * the NC caseId. Address-type-change and new-address flows use a generated caseId stored in
     * {@link #newCaseId}.
     */
    private boolean matchesProcessingEventCaseId(String eventCaseId) {
      if (caseId.equals(eventCaseId)) {
        return true;
      }
      if ("NC".equals(surveyType) && ncCaseId.equals(eventCaseId)) {
        return true;
      }
      if (newCaseId != null && newCaseId.equals(eventCaseId)) {
        return true;
      }
      return false;
    }

    private boolean matchesJsOutcomeEventCaseId(String eventCaseId) {
      if (getMessageCaseId().equals(eventCaseId)) {
        return true;
      }
      if ("NC".equals(surveyType) && ncCaseId.equals(eventCaseId)) {
        return true;
      }
      if (newCaseId != null && newCaseId.equals(eventCaseId)) {
        return true;
      }
      return false;
    }

    private boolean isGeneratedCaseIdFlow() {
      return "New Unit Reported".equals(businessFunction)
          || "New Standalone Address".equals(businessFunction)
          || "Switch Feedback Site".equals(businessFunction);
    }

    /**
     * New-unit and standalone outcomes receive {@code UUID.randomUUID()} in preprocessing; all
     * subsequent processor and RM events are keyed to that id (not the parent case or {@code N/A}).
     */
    private void resolveGeneratedCaseIdFromPreprocessingEvent() {
      if (!isGeneratedCaseIdFlow() || newCaseId != null || outcomeCode == null) {
        return;
      }
      String preprocessingEvent = getPreprocessingEventName();
      long deadline = System.currentTimeMillis() + CommonUtils.TIMEOUT;
      while (System.currentTimeMillis() < deadline) {
        Collection<GatewayEventDTO> events =
            gatewayEventMonitor.grabEventsTriggered(preprocessingEvent, 50, 500L);
        for (GatewayEventDTO event : events) {
          if (matchesPreprocessingEvent(event)
              && outcomeCode.equals(event.getMetadata().get("Outcome code"))) {
            newCaseId = event.getCaseId();
            return;
          }
        }
      }
    }

    private boolean matchesPreprocessingEvent(GatewayEventDTO event) {
      String eventSurveyType = event.getMetadata().get("Survey type");
      if (eventSurveyType == null) {
        eventSurveyType = event.getMetadata().get("survey type");
      }
      return surveyType.equals(eventSurveyType);
    }

    private String getPreprocessingEventName() {
      switch (surveyType) {
      case "SPG":
        if ("New Unit Reported".equals(businessFunction)) {
          return "PREPROCESSING_SPG_UNITADDRESS_OUTCOME";
        }
        if ("New Standalone Address".equals(businessFunction)) {
          return "PREPROCESSING_SPG_STANDALONE_OUTCOME";
        }
        break;
      case "CE":
        if ("New Unit Reported".equals(businessFunction) || "Switch Feedback Site".equals(businessFunction)) {
          return "PREPROCESSING_CE_UNITADDRESS_OUTCOME";
        }
        if ("New Standalone Address".equals(businessFunction)) {
          return "PREPROCESSING_CE_STANDALONE_OUTCOME";
        }
        break;
      case "HH":
        if ("New Unit Reported".equals(businessFunction) || "Switch Feedback Site".equals(businessFunction)) {
          return "PREPROCESSING_HH_SPLITADDRESS_OUTCOME";
        }
        if ("New Standalone Address".equals(businessFunction)) {
          return "PREPROCESSING_HH_STANDALONE_OUTCOME";
        }
        break;
      default:
        break;
      }
      throw new IllegalStateException(
          "No preprocessing event for surveyType=" + surveyType + " businessFunction=" + businessFunction);
    }

    private String processorFromEvent(GatewayEventDTO event) {
      String processor = event.getMetadata().get(PROCESSOR_METADATA);
      if (processor != null) {
        return processor;
      }
      return event.getMetadata().get("processor");
    }

    private String rmMessageTypeFromEvent(GatewayEventDTO event) {
      String templateType = event.getMetadata().get(TEMPLATE_TYPE_METADATA);
      if (templateType != null) {
        return templateType;
      }
      return event.getMetadata().get("type");
    }

    @Then("it will include a new caseId")
    public void it_will_include_a_new_caseId() throws Exception{
      JsonNode actualJson = jsonObjectMapper.readTree(addressTypeChangeMsg);
      JsonNode newCaseIdNode = actualJson.findPath("newCaseId");
      assertThat(newCaseIdNode!=null && !newCaseIdNode.isMissingNode()).isTrue();
      assertThat(!caseId.equals(newCaseIdNode.asText())).isTrue();
      newCaseId = newCaseIdNode.asText();
    }

    @Then("every other message will use the new caseId as its caseId")
    public void every_other_message_will_use_the_new_caseId_as_its_caseId() throws Exception{
      for (String messageType : expectedRmMessageMap.keySet()) {
        String atcMsg = expectedRmMessageMap.get(messageType);

        switch (messageType) {
        case "ADDRESS_TYPE_CHANGED":
          atcMsg = replaceValueInJson(atcMsg, "newCaseId", newCaseId);
          break;
        case "QUESTIONNAIRE_LINKED":
        case "FULFILMENT_REQUESTED":
          atcMsg = replaceValueInJson(atcMsg, "caseId", newCaseId);
          break;
        case "FIELD_CASE_UPDATED":
          if (usesCeSiteResidentCountZero()) {
            JsonNode newAddressMessage = jsonObjectMapper.readTree(actualRmMessageMap.get("NEW_ADDRESS_REPORTED"));
            atcMsg = replaceValueInJson(atcMsg, "id", newAddressMessage.findPath("sourceCaseId").asText());
          } else {
            atcMsg = replaceValueInJson(atcMsg, "id", newCaseId);
          }
          break;
        default:
          atcMsg = replaceValueInJson(atcMsg, "id", newCaseId);
          break;
        }
        expectedRmMessageMap.put(messageType, atcMsg);
      }
    }

    private String replaceValueInJson(String msg, String keyName, String newValue) throws Exception{
      System.out.println(msg);
      JsonNode actualJson = jsonObjectMapper.readTree(msg);
      msg = actualJson.toPrettyString();
      String docturedJson = msg.replaceAll("(?<=\"" + keyName + "\" : \")[^\\\"]+", newValue);
      return docturedJson;
    }

    private boolean usesCeSiteResidentCountZero() {
      return "CE".equals(surveyType)
          && ("Switch Feedback Site".equals(businessFunction) || "New Unit Reported".equals(businessFunction));
    }

    private int expectedNewAddressUsualResidents() {
      if (!"CE".equals(surveyType)) {
        return 0;
      }
      if ("Switch Feedback Site".equals(businessFunction)) {
        return 5;
      }
      return hasUsualResidentsCount ? 5 : ("New Unit Reported".equals(businessFunction) ? 1 : 0);
    }

    @Then("each message has the correct values")
    public void each_message_has_the_correct_values() throws Exception {
        confirmMessagesAreValid();
    }

    @Then("it will create the following messages {string} to JobService")
    public void it_will_create_the_following_messages_to_JobService(String jsMessages) {
        String[] jsMessagesArray = (!Strings.isBlank(jsMessages)) ? jsMessages.split(",") : new String[0];
        expectedJsMessages = Arrays.asList(jsMessagesArray);
        collectJsOutcomeEvents();
        confirmJsMessagesAreSent();
    }

    private void confirmProcessorsAreExcecuted() {
        List<String> actualProcessors = processingEvents.stream()
                .map(this::processorFromEvent)
                .collect(Collectors.toList());
        assertEquals(expectedProcessors.size(), actualProcessors.size());
        assertThat(expectedProcessors.containsAll(actualProcessors));
    }

    private void confirmRmMessagesAreSent() {
        List<String> actualMessages = rmOutcomeEvents.stream()
                .filter(e -> matchesRmOutcomeEventCaseId(e.getCaseId()))
                .filter(e -> surveyType.equals(e.getMetadata().get("survey type")))
                .map(this::rmMessageTypeFromEvent)
                .collect(Collectors.toList());
                assertEquals(expectedRmMessages.size(), actualRmMessageMap.size());
                assertEquals(expectedRmMessages.size(), actualMessages.size());
                assertThat(expectedRmMessages.containsAll(actualMessages));
    }

    private void confirmJsMessagesAreSent() {
        List<String> actualMessages = jsOutcomeEvents.stream()
                .filter(e -> matchesJsOutcomeEventCaseId(e.getCaseId()))
                .filter(e -> surveyType.equals(e.getMetadata().get("Address Type")))
                .map(e -> e.getMetadata().get("Action Instructions"))
                .collect(Collectors.toList());
        assertEquals(expectedJsMessages.size(), actualMessages.size());
        assertThat(expectedJsMessages.containsAll(actualMessages));
    }

    private void sendTMOutcomeMessage() throws Exception {
        int response = -1;
        String request = getTmOutcomeRequest();
        switch (surveyType) {
        case "SPG":
          response = sendTmSpg(request);
            break;
        case "CE":
          response = sendCe(request);
            break;
        case "HH":
          response = sendHH(request);
            break;
        case "NC":
          response = tmMockUtils.sendTMNCResponseMessage(request, ncCaseId);
            break;
        default:
            break;
        }
        assertEquals(200, response);
    }

    private int sendCe(String request) {
      int response;
      switch (businessFunction) {
      case "New Unit Reported":
      case "Switch Feedback Site":
        response = tmMockUtils.sendTMCENewUnitAddressResponseMessage(request);
        break;
      case "New Standalone Address":
        response = tmMockUtils.sendTMCENewStandaloneAddressResponseMessage(request);
        break;
      default:
        response = tmMockUtils.sendTMCEResponseMessage(request, caseId);
      }
      return response;
    }

    private int sendTmSpg(String request) {
      int response;
      switch (businessFunction) {
      case "New Unit Reported":
        response = tmMockUtils.sendTMSPGNewUnitAddressResponseMessage(request);
        break;
      case "New Standalone Address":
        response = tmMockUtils.sendTMSPGNewStandaloneAddressResponseMessage(request);
        break;
      default:
        response = tmMockUtils.sendTMSPGResponseMessage(request, caseId);
      }
      return response;
    }

    private int sendHH(String request) {
      int response;
      switch (businessFunction) {
      case "New Unit Reported":
        response = tmMockUtils.sendTMHHNewUnitAddressResponseMessage(request);
        break;
      case "New Standalone Address":
        response = tmMockUtils.sendTMHHNewStandaloneAddressResponseMessage(request);
        break;
      default:
        response = tmMockUtils.sendTMHHResponseMessage(request, caseId);
      }
      return response;
    }

   private String getTmOutcomeRequest() throws Exception {
        Map<String, Object> root = new HashMap();

        String linkedQid = (hasLinkedQid) ? createOutcomeMessage("LINKED_QID", root) : null;
        String fulfilmentRequested = (hasFulfillmentRequest) ? createOutcomeMessage("FULFILMENT_REQUESTED", root) : null;
        String usualResidents = (hasUsualResidentsCount) ? createOutcomeMessage("USUAL_RESIDENTS", root) : null;

        root.put("caseId", caseId);
        root.put("primaryOutcomeDescription", primaryOutcome);
        root.put("secondaryOutcomeDescription", secondaryOutcome);
        root.put("outcomeCode", outcomeCode);
        root.put("linkedQid", linkedQid);
        root.put("fulfilmentRequested", fulfilmentRequested);
        root.put("usualResidents", usualResidents);
        root.put("surveyType", surveyType);

        try {
            String request = null;
            switch (businessFunction) {
            case "Not Valid Address":
                request = createOutcomeMessage("ADDRESS_NOT_VALID", root);
                break;
            case "Hard Refusal":
            case "Extraordinary Refusal":
                request = createOutcomeMessage("REFUSAL_RECEIVED", root);
                break;
            case "Address Type Changed HH":
                request = createOutcomeMessage("ADDRESS_TYPE_CHANGED_HH", root);
                break;
            case "Address Type Changed CE":
                request = createOutcomeMessage("ADDRESS_TYPE_CHANGED_CE", root);
                break;
            case "Address Type Changed SPG":
              request = createOutcomeMessage("ADDRESS_TYPE_CHANGED_SPG", root);
              break;
            case "Cancel Feedback":
              request = createOutcomeMessage("CANCEL_FEEDBACK", root);
              break;
            case "Delivered Feedback":
              request = createOutcomeMessage("DELIVERED_FEEDBACK", root);
              break;
            case "Update Resident Count":
              request = createOutcomeMessage("UPDATE_RESIDENT_COUNT", root);
              break;
            case "New Unit Reported":
              request = createOutcomeMessage("NEW_UNIT_ADDRESS", root);
              break;
            case "New Standalone Address":
              request = createOutcomeMessage("NEW_STANDALONE_ADDRESS", root);
              break;
            case "No Action":
              request = createOutcomeMessage("NO_ACTION", root);
              break;
            case "Switch Feedback Estab":
              request = createOutcomeMessage("SWITCH_FEEDBACK_CE_EST_F", root);
              break;
            case "Switch Feedback Unit":
              request = createOutcomeMessage("SWITCH_FEEDBACK_CE_UNIT_F", root);
              break;
            case "Switch Feedback Site":
              request = createOutcomeMessage("SWITCH_FEEDBACK_CE_SITE", root);
              break;
            default:
                break;
            }
            System.out.print("Survey Type: " + surveyType + "\nREQUEST:  " + request);
            return request;
        } catch (Exception e) {
            throw new RuntimeException("Problem with setting up", e);
        }
    }

   private void confirmOutcomeServiceReceivesMessage() {
     String event = null;
     switch (surveyType) {
     case "SPG":
       event = getSpgRequestReceivedEventName();
         break;
     case "CE":
       event = getCeRequestReceivedEventName();
         break;
     case "HH":
       event = getHhRequestReceivedEventName();
         break;
     case "NC":
       event = COMET_NC_OUTCOME_RECEIVED;
         break;
     default:
         break;
     }

     String messageCaseId = getMessageCaseId();
     boolean isMsgRecieved = gatewayEventMonitor.hasEventTriggered(messageCaseId, event, CommonUtils.TIMEOUT);
     assertThat(isMsgRecieved).isTrue();
 }

    private String getMessageCaseId() {
      String messageCaseId;
      switch (businessFunction) {
      case "New Standalone Address":
      case "New Unit Reported":
      case "Switch Feedback Site":
        messageCaseId = "N/A";
        break;
      case "No Action":
      case "Cancel Feedback":
        messageCaseId = "NC".equals(surveyType) ? ncCaseId : caseId;
        break;
      default:
        messageCaseId = caseId;
      }
      return messageCaseId;
    }

    private String getSpgRequestReceivedEventName() {
      String event;
      switch (businessFunction) {
      case "New Unit Reported":
        event = COMET_SPG_UNITADDRESS_OUTCOME_RECEIVED;
        break;
      case "New Standalone Address":
        event = COMET_SPG_STANDALONE_OUTCOME_RECEIVED;
        break;
      default:
        event = COMET_SPG_OUTCOME_RECEIVED;
      }
      return event;
    }

    private String getCeRequestReceivedEventName() {
      String event;
      switch (businessFunction) {
      case "New Unit Reported":
      case "Switch Feedback Site":
        event = COMET_CE_UNITADDRESS_OUTCOME_RECEIVED;
        break;
      case "New Standalone Address":
        event = COMET_CE_STANDALONE_OUTCOME_RECEIVED;
        break;
      default:
        event = COMET_CE_OUTCOME_RECEIVED;
      }
      return event;
    }

    private String getHhRequestReceivedEventName() {
      String event;
      switch (businessFunction) {
      case "New Unit Reported":
      case "Switch Feedback Site":
        event = COMET_HH_SPLITADDRESS_RECEIVED;
        break;
      case "New Standalone Address":
        event = COMET_HH_STANDALONE_RECEIVED;
        break;
      default:
        event = COMET_HH_OUTCOME_RECEIVED;
      }
      return event;
    }

    private String createOutcomeMessage(String eventType, Map<String, Object> root)
            throws Exception {
        String outcomeMessage = "";

        Configuration configuration = new Configuration(Configuration.VERSION_2_3_28);
        configuration.setClassForTemplateLoading(OutcomeSteps.class, "/files/outcome/tm/");
        configuration.setDefaultEncoding("UTF-8");
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false);
        configuration.setWrapUncheckedExceptions(true);

        Template temp = configuration.getTemplate(eventType + "-in.ftl");
        try (StringWriter out = new StringWriter(); StringWriter outcomeEventMessage = new StringWriter()) {

            temp.process(root, out);
            out.flush();

            outcomeEventMessage.flush();
            outcomeMessage = out.toString();

        } finally {
        }
        return outcomeMessage;
    }

    private String operationToQueue(String operation) {
        switch (operation) {
        case "REFUSAL_RECEIVED":
        case "HARD_REFUSAL_RECEIVED":
            return FIELD_REFUSALS_QUEUE;
        case "ADDRESS_NOT_VALID":
        case "ADDRESS_TYPE_CHANGED":
        case "FULFILMENT_REQUESTED":
        case "QUESTIONNAIRE_LINKED":
        case "NEW_ADDRESS_REPORTED":
        case "FIELD_CASE_UPDATED":
        case "UPDATE_RESIDENT_COUNT_1":
        case "UPDATE_RESIDENT_COUNT":
        case "UPDATE_RESIDENT_COUNT_0":
            return TEMP_FIELD_OTHERS_QUEUE;
        default:
            throw new RuntimeException("Problem matching operation");
        }
    }

    private String createExpectedRmMessage(String rmMessageType, Map<String, Object> root) throws Exception {
        String inputMessage = "";
        if ("ADDRESS_TYPE_CHANGED".equals(rmMessageType)) {
          switch (businessFunction) {
          case "Address Type Changed HH":
              rmMessageType = rmMessageType + "_HH";
              break;
          case "Address Type Changed CE":
              rmMessageType = rmMessageType + "_CE";
              break;
          case "Address Type Changed SPG":
              rmMessageType = rmMessageType + "_SPG";
              break;
          default:
              break;
          }
      }
        if ("NEW_ADDRESS_REPORTED".equals(rmMessageType)) {
          switch (businessFunction) {
          case "New Unit Reported":
          case "Switch Feedback Site":
              rmMessageType = rmMessageType + "_UNIT";
              break;
          case "New Standalone Address":
              rmMessageType = rmMessageType + "_STANDALONE";
              break;

          default:
              break;
          }
      }

        Configuration configuration = new Configuration(Configuration.VERSION_2_3_28);
        configuration.setClassForTemplateLoading(OutcomeSteps.class, "/files/outcome/rm/");
        configuration.setDefaultEncoding("UTF-8");
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setLogTemplateExceptions(false);
        configuration.setWrapUncheckedExceptions(true);

        Template temp = configuration.getTemplate(rmMessageType + "-out.ftl");
        try (
                StringWriter out = new StringWriter();
                StringWriter inputEventMessage = new StringWriter()) {

            temp.process(root, out);
            out.flush();

            inputEventMessage.flush();
            inputMessage = out.toString();

        } finally {
        }
        return inputMessage;
    }

    private void confirmMessagesAreValid() throws Exception {
        assertEquals(expectedRmMessages.size(), actualRmMessageMap.size());
        assertThat(expectedRmMessages.containsAll(actualRmMessageMap.keySet()));

        Map<String, Object> root = new HashMap();
        for (String rmMessageType : expectedRmMessages) {
            String expectedRmMessage = expectedRmMessageMap.get(rmMessageType);
            JsonNode expectedJson = jsonObjectMapper.readTree(expectedRmMessage);

            String actualRmMessage = actualRmMessageMap.get(rmMessageType);
            JsonNode actualJson = jsonObjectMapper.readTree(actualRmMessage);

            boolean isEqual = expectedJson.equals(actualJson);
            if (!isEqual) {
                log.info("expected and actual caseEvents are not the same: \n expected:\n {} \n\n actual: \n {}",
                        expectedJson.toPrettyString(), actualJson.toPrettyString());
            }
            assertThat(isEqual).isTrue();
        }
    }

    @Then("the caseId of the {string} message will be a new caseId")
    public void the_caseId_of_the_message_will_be_a_new_caseId(String messageType) throws Exception {
      addressTypeChangeMsg = actualRmMessageMap.get(messageType);
      System.out.println("Actual:" + addressTypeChangeMsg);
      JsonNode actualJson = jsonObjectMapper.readTree(addressTypeChangeMsg);
      JsonNode caseIdNode = actualJson.findPath("id");
      assertThat(caseIdNode!=null && !caseIdNode.isMissingNode()).isTrue();
      assertThat(caseId.equals(caseIdNode.asText())).isFalse();
      newCaseId = caseIdNode.asText();
    }

    @Given("the message includes Usual Residents Count {string}")
    public void the_message_includes_Usual_Residents_Count(String hasUsualResidentsCount) {
      this.hasUsualResidentsCount = "T".equals(hasUsualResidentsCount);    }

    @Given("a parent case exists")
    public void a_parent_case_exists() throws Exception {
      gatewayEventMonitor.reset();
      String ceSpgEstabCreateJson = Resources.toString(Resources.getResource("files/input/spg/spgEstabCreate.json"), Charsets.UTF_8);
      JSONObject json = new JSONObject(ceSpgEstabCreateJson);

      commonRMMessageObjects(json, caseId, "1234", "F", "F", false);

      String request = json.toString(4);
      log.info("Request = " + request);
      queueClient.sendToRMFieldQueue(request, "create");
      boolean hasBeenTriggered = gatewayEventMonitor.hasEventTriggered(caseId, RM_CREATE_REQUEST_RECEIVED, CommonUtils.TIMEOUT);
      assertThat(hasBeenTriggered).isTrue();


    }

    @Given("a parent case exists for {string}")
    public void a_parent_case_exists(String surveyType) throws Exception {
      gatewayEventMonitor.reset();
      String parentCreate;
      if (surveyType.equals("HH")) {
        parentCreate = Resources.toString(Resources.getResource("files/input/hh/hhCreate.json"), Charsets.UTF_8);
      } else {
        parentCreate = Resources.toString(Resources.getResource("files/input/ce/ceEstabCreate.json"), Charsets.UTF_8);
      }

      JSONObject json = new JSONObject(parentCreate);

      commonRMMessageObjects(json, caseId, "1234", "F", "F", false);

      json.put("oldCaseId", "bd6345af-d706-43d3-a13b-8c549e081a76");

      String request = json.toString(4);
      log.info("Request = " + request);
      queueClient.sendToRMFieldQueue(request, "create");
      boolean hasBeenTriggered = gatewayEventMonitor.hasEventTriggered(caseId, RM_CREATE_REQUEST_RECEIVED, CommonUtils.TIMEOUT);
      assertThat(hasBeenTriggered).isTrue();
    }

    @And("an NC create case already exists")
    public void an_nc_create_case_exists() throws Exception {
      tmMockUtils.addNcToDatabase(ncCaseId, caseId);
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

      if ("HH".equals(json.optString("addressType"))) {
        json.put("addressLevel", "U");
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