package uk.gov.ons.census.fwmt.tests.acceptance.steps.inbound.feedback;


import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;

import org.springframework.beans.factory.annotation.Autowired;

import cucumber.api.java.After;
import cucumber.api.java.Before;
import cucumber.api.java.en.And;
import cucumber.api.java.en.Given;
import cucumber.api.java.en.Then;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateExceptionHandler;
import lombok.extern.slf4j.Slf4j;
import uk.gov.ons.census.fwmt.events.utils.GatewayEventMonitor;
import uk.gov.ons.census.fwmt.tests.acceptance.steps.inbound.common.CommonUtils;
import uk.gov.ons.census.fwmt.tests.acceptance.steps.spg.outcome.SPGOutcomeSteps;
import uk.gov.ons.census.fwmt.tests.acceptance.utils.QueueClient;
import uk.gov.ons.census.fwmt.tests.acceptance.utils.TMMockUtils;

@Slf4j
public class FeedbackSteps {

  @Autowired
  private TMMockUtils tmMockUtils;

  @Autowired
  private QueueClient queueClient;

  @Autowired
  private CommonUtils commonUtils;

  @Autowired
  private GatewayEventMonitor gatewayEventMonitor;

  private String caseId;

  private String eventType;

  private final String surveyType = "spg";

  private static final String COMET_CREATE_ACK = "COMET_CREATE_ACK";

  @Before
  public void setup() throws Exception {
    commonUtils.setup();
  }

  @After
  public void clearDown() throws Exception {
    commonUtils.clearDown();
  }

  @Given("a job has been created in TM with case id {string}")
  public void aJobHasBeenCreatedInTMWithCaseId(String caseId) throws IOException, URISyntaxException {
    this.caseId = caseId;
    String request = Resources.toString(Resources.getResource("files/input/spg/spgUnitCreate.json"), Charsets.UTF_8);
    queueClient.sendToRMFieldQueue(request, "create");
    boolean jobAcknowledged = gatewayEventMonitor.hasEventTriggered(caseId, COMET_CREATE_ACK, CommonUtils.TIMEOUT);
    assertThat(jobAcknowledged).isTrue();
  }

  @And("tm sends a {string} outcome")
  public void tmSendsAOutcome(String type) {
    Map<String, Object> inputRoot = new HashMap<>();
    if (type.equals("CANCEL_FEEDBACK")) {
      inputRoot.put("primaryOutcomeDescription", "Engagement - Contact made");
      inputRoot.put("secondaryOutcomeDescription", "Visit - Hard refusal");
      inputRoot.put("outcomeCode", "6-20-53");
      eventType = "CANCEL_FEEDBACK";
    } else if (type.equals("DELIVERED_FEEDBACK")) {
      inputRoot.put("primaryOutcomeDescription", "Contact made");
      inputRoot.put("secondaryOutcomeDescription", "HUAC required by text ");
      inputRoot.put("outcomeCode", "7-20-04");
//      eventType = "FULFILMENT_REQUESTED";
      eventType = "DELIVERED_FEEDBACK";
    }

    String TMRequest = createOutcomeMessage(eventType + "-in", inputRoot, surveyType);

    int response = tmMockUtils.sendTMSPGResponseMessage(TMRequest, caseId);
    assertEquals(200, response);

  }

  @Then("a {string} feedback message is sent to tm")
  public void aFeedbackMessageIsSentToTm(String message) {
    log.info("Sending job type :" + message);
    boolean jobSent = gatewayEventMonitor.hasEventTriggered(caseId, message, CommonUtils.TIMEOUT);
    assertThat(jobSent).isTrue();
  }

  @And("{string} is acknowledged by tm")
  public void isAcknowledgedByTm(String message) {
    log.info("Confirming tm recieved " + message);
    boolean jobAcknowledged = gatewayEventMonitor.hasEventTriggered(caseId, message, CommonUtils.TIMEOUT);
    assertThat(jobAcknowledged).isTrue();
  }

  public String createOutcomeMessage(String eventType, Map<String, Object> root, String surveyType) {
    String outcomeMessage = "";

    try {
      Configuration configuration = new Configuration(Configuration.VERSION_2_3_28);
      configuration.setClassForTemplateLoading(SPGOutcomeSteps.class, "/files/outcome/");
      configuration.setDefaultEncoding("UTF-8");
      configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
      configuration.setLogTemplateExceptions(false);
      configuration.setWrapUncheckedExceptions(true);

      Template temp = configuration.getTemplate("tm/" + eventType + ".ftl");
      try (StringWriter out = new StringWriter(); StringWriter outcomeEventMessage = new StringWriter()) {

        temp.process(root, out);
        out.flush();

        outcomeEventMessage.flush();
        outcomeMessage = out.toString();

      } catch (TemplateException e) {
        log.error("Error: ", e);
        throw new RuntimeException(e);
      }
    } catch (IOException e) {
      log.error("Error: ", e);
      throw new RuntimeException(e);
    }
    return outcomeMessage;
  }
}