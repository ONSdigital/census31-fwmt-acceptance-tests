package uk.gov.ons.census.fwmt.tests.acceptance.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import uk.gov.ons.census.fwmt.common.data.tm.Case;
import uk.gov.ons.census.fwmt.common.data.tm.CasePause;
import uk.gov.ons.census.fwmt.data.dto.MockMessage;
import uk.gov.ons.census.fwmt.tests.acceptance.exceptions.MockInaccessibleException;
import uk.gov.ons.census.fwmt.tests.acceptance.utils.NodeCheck.NodeCheckBuilder;

import jakarta.xml.bind.JAXBContext;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Component
public final class TMMockUtils {

  @Value("${service.outcome.url}")
  private String outcomeServiceUrl;

  @Value("${service.outcome.CCSPL.endpoint}")
  private String ccsPLOutcomeEnpoint;

  @Value("${service.outcome.CCSInt.endpoint}")
  private String ccsIntOutcomeEnpoint;

  @Value("${service.outcome.SPG.endpoint}")
  private String spgOutcomeEndpoint;

  @Value("${service.outcome.SPGNewUnit.endpoint}")
  private String spgNewUnitOutcomeEndpoint;

  @Value("${service.outcome.SPGStandalone.endpoint}")
  private String spgStandaloneOutcomeEndpoint;

  @Value("${service.outcome.HH.endpoint}")
  private String hhOutcomeEndpoint;

  @Value("${service.outcome.HHNewUnit.endpoint}")
  private String hhNewUnitOutcomeEndpoint;

  @Value("${service.outcome.HHStandalone.endpoint}")
  private String hhStandaloneOutcomeEndpoint;

  @Value("${service.outcome.CE.endpoint}")
  private String ceOutcomeEndpoint;

  @Value("${service.outcome.CENewUnit.endpoint}")
  private String ceNewUnitOutcomeEndpoint;

  @Value("${service.outcome.CEStandalone.endpoint}")
  private String ceStandaloneOutcomeEndpoint;

  @Value("${service.outcome.NC.endpoint}")
  private String ncOutcomeEndpoint;

  @Value("${service.outcome.username}")
  private String outcomeServiceUsername;

  @Value("${service.outcome.password}")
  private String outcomeServicePassword;

  @Value("${service.mocktm.url}")
  private String mockTmUrl;

  @Value("${spring.datasource.url}")
  private String url;

  @Value("${spring.datasource.username}")
  private String username;

  @Value("${spring.datasource.password}")
  private String password;

  private RestTemplate restTemplate = new RestTemplate();

  private JAXBContext jaxbContext;

  public void resetMock() throws IOException {
    URL url = new URL(mockTmUrl + "/logger/reset");
    log.info("reset-mock_url:" + url.toString());
    HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
    httpURLConnection.setRequestMethod("GET");
    if (httpURLConnection.getResponseCode() != 200) {
      throw new MockInaccessibleException("Failed : HTTP error code : " + httpURLConnection.getResponseCode());
    }
  }

  public MockMessage[] getMessages() {
    String url = mockTmUrl + "/logger/allMessages";
    log.info("allMessages-mock_url:" + url);
    return restTemplate.getForObject(url, MockMessage[].class);
  }

  public Case getCaseById(String id) {
    String url = mockTmUrl + "/cases/" + id;
    log.info("getCaseById-mock_url:" + url);
    ResponseEntity<Case> responseEntity;
    responseEntity = restTemplate.getForEntity(url, Case.class);
    return responseEntity.getBody();
  }

  public CasePause getPauseCase(String id) {
    String url = mockTmUrl + "/cases/" + id + "/pause";
    log.info("getCancelCaseById-mock.url:" + url);
    ResponseEntity<CasePause> responseEntity;
    responseEntity = restTemplate.getForEntity(url, CasePause.class);
    return responseEntity.getBody();
  }

  public int sendTMResponseMessage(String data, String caseId) {
    HttpHeaders headers = createBasicAuthHeaders(outcomeServiceUsername, outcomeServicePassword);

    headers.setContentType(MediaType.APPLICATION_JSON);

    RestTemplate restTemplate = new RestTemplate();
    String postUrl = outcomeServiceUrl + hhOutcomeEndpoint + caseId;

    HttpEntity<String> post = new HttpEntity<>(data, headers);
    ResponseEntity<Void> response = restTemplate.exchange(postUrl, HttpMethod.POST, post, Void.class);

    return response.getStatusCode().value();
  }

  public int sendTMCCSPLResponseMessage(String data, String caseId) {
    HttpHeaders headers = createBasicAuthHeaders(outcomeServiceUsername, outcomeServicePassword);

    headers.setContentType(MediaType.APPLICATION_JSON);

    RestTemplate restTemplate = new RestTemplate();
    String postUrl = outcomeServiceUrl + ccsPLOutcomeEnpoint;

    HttpEntity<String> post = new HttpEntity<>(data, headers);
    ResponseEntity<Void> response = restTemplate.exchange(postUrl, HttpMethod.POST, post, Void.class);

    return response.getStatusCode().value();
  }

  public int sendTMCCSIntResponseMessage(String data, String caseId) {
    HttpHeaders headers = createBasicAuthHeaders(outcomeServiceUsername, outcomeServicePassword);

    headers.setContentType(MediaType.APPLICATION_JSON);

    RestTemplate restTemplate = new RestTemplate();
    String postUrl = outcomeServiceUrl + ccsIntOutcomeEnpoint + caseId;

    HttpEntity<String> post = new HttpEntity<>(data, headers);
    ResponseEntity<Void> response = restTemplate.exchange(postUrl, HttpMethod.POST, post, Void.class);

    return response.getStatusCode().value();
  }

  public int sendTMSPGResponseMessage(String data, String caseId) {
    HttpHeaders headers = createBasicAuthHeaders(outcomeServiceUsername, outcomeServicePassword);

    headers.setContentType(MediaType.APPLICATION_JSON);

    RestTemplate restTemplate = new RestTemplate();
    String postUrl = outcomeServiceUrl + spgOutcomeEndpoint + caseId;

    HttpEntity<String> post = new HttpEntity<>(data, headers);
    ResponseEntity<Void> response = restTemplate.exchange(postUrl, HttpMethod.POST, post, Void.class);

    return response.getStatusCode().value();
  }  

  public int sendTMHHResponseMessage(String data, String caseId) {
    HttpHeaders headers = createBasicAuthHeaders(outcomeServiceUsername, outcomeServicePassword);

    headers.setContentType(MediaType.APPLICATION_JSON);

    RestTemplate restTemplate = new RestTemplate();
    String postUrl = outcomeServiceUrl + hhOutcomeEndpoint + caseId;

    HttpEntity<String> post = new HttpEntity<>(data, headers);
    ResponseEntity<Void> response = restTemplate.exchange(postUrl, HttpMethod.POST, post, Void.class);

    return response.getStatusCode().value();
  }  

  public int sendTMNCResponseMessage(String data, String caseId) {
    HttpHeaders headers = createBasicAuthHeaders(outcomeServiceUsername, outcomeServicePassword);

    headers.setContentType(MediaType.APPLICATION_JSON);

    RestTemplate restTemplate = new RestTemplate();
    String postUrl = outcomeServiceUrl + ncOutcomeEndpoint + caseId;

    HttpEntity<String> post = new HttpEntity<>(data, headers);
    ResponseEntity<Void> response = restTemplate.exchange(postUrl, HttpMethod.POST, post, Void.class);

    return response.getStatusCode().value();
  }

  public int sendTMSPGNewStandaloneAddressResponseMessage(String data) {
    HttpHeaders headers = createBasicAuthHeaders(outcomeServiceUsername, outcomeServicePassword);

    headers.setContentType(MediaType.APPLICATION_JSON);

    RestTemplate restTemplate = new RestTemplate();
    String postUrl = outcomeServiceUrl + spgStandaloneOutcomeEndpoint;

    HttpEntity<String> post = new HttpEntity<>(data, headers);
    ResponseEntity<Void> response = restTemplate.exchange(postUrl, HttpMethod.POST, post, Void.class);

    return response.getStatusCode().value();
  }

  public int sendTMHHNewStandaloneAddressResponseMessage(String data) {
    HttpHeaders headers = createBasicAuthHeaders(outcomeServiceUsername, outcomeServicePassword);

    headers.setContentType(MediaType.APPLICATION_JSON);

    RestTemplate restTemplate = new RestTemplate();
    String postUrl = outcomeServiceUrl + hhStandaloneOutcomeEndpoint;

    HttpEntity<String> post = new HttpEntity<>(data, headers);
    ResponseEntity<Void> response = restTemplate.exchange(postUrl, HttpMethod.POST, post, Void.class);

    return response.getStatusCode().value();
  }

  public int sendTMSPGNewUnitAddressResponseMessage(String data) {
    HttpHeaders headers = createBasicAuthHeaders(outcomeServiceUsername, outcomeServicePassword);

    headers.setContentType(MediaType.APPLICATION_JSON);

    RestTemplate restTemplate = new RestTemplate();
    String postUrl = outcomeServiceUrl + spgNewUnitOutcomeEndpoint;

    HttpEntity<String> post = new HttpEntity<>(data, headers);
    ResponseEntity<Void> response = restTemplate.exchange(postUrl, HttpMethod.POST, post, Void.class);

    return response.getStatusCode().value();
  }

  public int sendTMHHNewUnitAddressResponseMessage(String data) {
    HttpHeaders headers = createBasicAuthHeaders(outcomeServiceUsername, outcomeServicePassword);

    headers.setContentType(MediaType.APPLICATION_JSON);

    RestTemplate restTemplate = new RestTemplate();
    String postUrl = outcomeServiceUrl + hhNewUnitOutcomeEndpoint;

    HttpEntity<String> post = new HttpEntity<>(data, headers);
    ResponseEntity<Void> response = restTemplate.exchange(postUrl, HttpMethod.POST, post, Void.class);

    return response.getStatusCode().value();
  }

  public int sendTMCEResponseMessage(String data, String caseId) {
    HttpHeaders headers = createBasicAuthHeaders(outcomeServiceUsername, outcomeServicePassword);

    headers.setContentType(MediaType.APPLICATION_JSON);

    RestTemplate restTemplate = new RestTemplate();
    String postUrl = outcomeServiceUrl + ceOutcomeEndpoint + caseId;

    HttpEntity<String> post = new HttpEntity<>(data, headers);
    ResponseEntity<Void> response = restTemplate.exchange(postUrl, HttpMethod.POST, post, Void.class);

    return response.getStatusCode().value();
  }

  public int sendTMCENewStandaloneAddressResponseMessage(String data) {
    HttpHeaders headers = createBasicAuthHeaders(outcomeServiceUsername, outcomeServicePassword);

    headers.setContentType(MediaType.APPLICATION_JSON);

    RestTemplate restTemplate = new RestTemplate();
    String postUrl = outcomeServiceUrl + ceStandaloneOutcomeEndpoint;

    HttpEntity<String> post = new HttpEntity<>(data, headers);
    ResponseEntity<Void> response = restTemplate.exchange(postUrl, HttpMethod.POST, post, Void.class);

    return response.getStatusCode().value();
  }

  public int sendTMCENewUnitAddressResponseMessage(String data) {
    HttpHeaders headers = createBasicAuthHeaders(outcomeServiceUsername, outcomeServicePassword);

    headers.setContentType(MediaType.APPLICATION_JSON);

    RestTemplate restTemplate = new RestTemplate();
    String postUrl = outcomeServiceUrl + ceNewUnitOutcomeEndpoint;

    HttpEntity<String> post = new HttpEntity<>(data, headers);
    ResponseEntity<Void> response = restTemplate.exchange(postUrl, HttpMethod.POST, post, Void.class);

    return response.getStatusCode().value();
  }


  private HttpHeaders createBasicAuthHeaders(String username, String password) {
    HttpHeaders headers = new HttpHeaders();
    final String plainCreds = username + ":" + password;
    byte[] plainCredsBytes = plainCreds.getBytes();
    byte[] base64CredsBytes = Base64.encodeBase64(plainCredsBytes);
    String base64Creds = new String(base64CredsBytes);
    headers.add("Authorization", "Basic " + base64Creds);
    return headers;
  }

  public void enableRequestRecorder() throws IOException {
    URL url = new URL(mockTmUrl + "/logger/enableRequestRecorder");
    log.info("enableRequestRecorder-mock_url:" + url.toString());
    HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
    httpURLConnection.setRequestMethod("GET");
    if (httpURLConnection.getResponseCode() != 200) {
      throw new MockInaccessibleException("Failed : HTTP error code : " + httpURLConnection.getResponseCode());
    }
  }

  public void disableRequestRecorder() throws IOException {
    URL url = new URL(mockTmUrl + "/logger/disableRequestRecorder");
    log.info("disableRequestRecorder-mock_url:" + url.toString());
    HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
    httpURLConnection.setRequestMethod("GET");
    if (httpURLConnection.getResponseCode() != 200) {
      throw new MockInaccessibleException("Failed : HTTP error code : " + httpURLConnection.getResponseCode());
    }
  }

  public void clearDownDatabase() throws Exception {
    System.out.println("CLEARDB" + url + username + password);
    Statement stmt = null;
    try (Connection conn = DriverManager.getConnection(url, username, password)) {
      if (conn != null) {
        System.out.println("Connected to the database!");
        stmt = conn.createStatement();
        String sql = "DELETE FROM message_cache";
        stmt.executeUpdate(sql);
        sql = "DELETE FROM gateway_case_record";
        stmt.executeUpdate(sql);
        sql = "DELETE FROM request_log";
        stmt.execute(sql);
        sql = "DELETE FROM quarantined_message";
        stmt.execute(sql);
      } else {
        System.out.println("Failed to make connection!");
      }
    } finally {
      try {
        if (stmt != null)
          stmt.close();
      } catch (SQLException ignored) {
      }
    }
  }

  public void addToDatabase(String caseId, boolean existInFwmt, int estabUprn, int type) throws Exception {
    Statement stmt = null;
    try (Connection conn = DriverManager.getConnection(url, username, password)) {
      if (conn != null) {
        System.out.println("Connected to the database!");
        stmt = conn.createStatement();
        String sql = "INSERT INTO fwmtg.gateway_case_record (case_id, is_delivered, exists_in_fwmt, estab_uprn, type)\n" +
                "VALUES ('" + caseId + "', false, " + existInFwmt +  ", " + estabUprn + ", " + type + ")";
        stmt.executeUpdate(sql);
      } else {
        System.out.println("Failed to make connection!");
      }
    } finally {
      try {
        if (stmt != null)
          stmt.close();
      } catch (SQLException ignored) {
      }
    }
  }

  public void addNcToDatabase(String ncCaseId, String originalCaseId) throws Exception {
    Statement stmt = null;
    try (Connection conn = DriverManager.getConnection(url, username, password)) {
      if (conn != null) {
        System.out.println("Connected to the database!");
        stmt = conn.createStatement();
        String sql = "INSERT INTO fwmtg.gateway_case_record "
            + "(case_id, is_delivered, exists_in_fwmt, type, original_case_id, last_action_instruction) "
            + "VALUES ('" + ncCaseId + "', false, true, 10, '" + originalCaseId + "', 'CREATE')";
        stmt.executeUpdate(sql);
      } else {
        System.out.println("Failed to make connection!");
      }
    } finally {
      try {
        if (stmt != null) {
          stmt.close();
        }
      } catch (SQLException ignored) {
      }
    }
  }

  public int checkCaseIdExists(String caseId) throws Exception {
    Statement stmt = null;
    ResultSet resultSet = null;
    int recordNumbers = 0;
    try (Connection conn = DriverManager.getConnection(url, username, password)) {
      if (conn != null) {
        System.out.println("Checking whether caseID is in the database!");
        stmt = conn.createStatement();
        String checkCaseId = "'" + caseId + "'";
        String sql = "SELECT * FROM fwmtg.gateway_case_record WHERE case_Id = " + checkCaseId;
        resultSet = stmt.executeQuery(sql);
        while (resultSet.next()) {
          recordNumbers = resultSet.getRow();
          break;
        }
      } else {
        System.out.println("Failed to make connection!");
      }
    } finally {
      try {
        if (resultSet != null) {
          resultSet.close();
        }
        if (stmt != null) {
          stmt.close();
        }
      } catch (SQLException ignored) {
      }
    }
    return recordNumbers;
  }

  public int checkActionExistsInMessageCache(String storedAction, String caseId) throws Exception {
    Statement stmt = null;
    ResultSet resultSet = null;
    int recordNumbers = 0;
    try (Connection conn = DriverManager.getConnection(url, username, password)) {
      if (conn != null) {
        System.out.println("Checking whether caseID is in the database!");
        stmt = conn.createStatement();
        String memoryQuery = "'" + caseId + "' AND message_type = '" + normaliseStoredAction(storedAction) + "'";
        String sql = "SELECT * FROM fwmtg.message_cache WHERE case_Id = " + memoryQuery;
        resultSet = stmt.executeQuery(sql);
        while (resultSet.next()) {
          recordNumbers = resultSet.getRow();
          break;
        }
      } else {
        System.out.println("Failed to make connection!");
      }
    } finally {
      try {
        if (resultSet != null) {
          resultSet.close();
        }
        if (stmt != null) {
          stmt.close();
        }
      } catch (SQLException ignored) {
      }
    }
    return recordNumbers;
  }

  public int checkActionExistsInGatewayCaseRecord(String storedAction, String caseId) throws Exception {
    Statement stmt = null;
    ResultSet resultSet = null;
    int recordNumbers = 0;
    try (Connection conn = DriverManager.getConnection(url, username, password)) {
      if (conn != null) {
        System.out.println("Checking whether caseID is in the database!");
        stmt = conn.createStatement();
        String memoryQuery = "'" + caseId + "' AND last_action_instruction = '" + normaliseStoredAction(storedAction) + "'";
        String sql = "SELECT * FROM fwmtg.gateway_case_record WHERE case_Id = " + memoryQuery;
        resultSet = stmt.executeQuery(sql);
        while (resultSet.next()) {
          recordNumbers = resultSet.getRow();
          break;
        }
      } else {
        System.out.println("Failed to make connection!");
      }
    } finally {
      try {
        if (resultSet != null) {
          resultSet.close();
        }
        if (stmt != null) {
          stmt.close();
        }
      } catch (SQLException ignored) {
      }
    }
    return recordNumbers;
  }

  public void updateStoredMessageTimeStamp(String storedAction, String caseId) throws Exception {
    Statement stmt = null;
    Instant actionTime;
    try (Connection conn = DriverManager.getConnection(url, username, password)) {
      if (conn != null) {
        conn.setAutoCommit(false);
        System.out.println("Connected to the database!");
        stmt = conn.createStatement();
        actionTime = Instant.now().plus(2, ChronoUnit.HOURS);
        String checkCaseQuery = "'" + actionTime + "' where case_Id = '" + caseId
            + "' AND last_action_instruction = '" + normaliseStoredAction(storedAction) + "'";

        String sql = "UPDATE fwmtg.gateway_case_record SET last_action_time = " + checkCaseQuery;
        stmt.executeUpdate(sql);
        conn.commit();
      } else {
        System.out.println("Failed to make connection!");
      }
    } finally {
      try {
        if (stmt != null) {
          stmt.close();
        }
      } catch (SQLException ignored) {
      }
    }
  }

  private String normaliseStoredAction(String storedAction) {
    return storedAction.toUpperCase();
  }

  public boolean checkExists() throws Exception {
    boolean exists = false;
    Statement stmt = null;
    try (Connection conn = DriverManager.getConnection(url, username, password)) {
      if (conn != null) {
        System.out.println("Connected to the database!");
        stmt = conn.createStatement();
        String sql = "SELECT * FROM fwmtg.gateway_case_record WHERE case_Id = 'bd6345af-d706-43d3-a13b-8c549e081a76'\n " +
                "AND estab_uprn = '6123456'";
        exists = stmt.execute(sql);

      } else {
        System.out.println("Failed to make connection!");
      }
    } finally {
      try {
        if (stmt != null)
          stmt.close();
      } catch (SQLException ignored) {
      }
    }
    return exists;
  }
  public NodeCheck checkDbUp(){
    NodeCheckBuilder builder = NodeCheck.builder().name("Postgres").url(url);

    Statement stmt = null;
    try (Connection conn = DriverManager.getConnection(url, username, password)) {
      if (conn != null) {
        System.out.println("Connected to the database!");
        stmt = conn.createStatement();
        String sql = "SELECT * FROM fwmtg.gateway_case_record";
        stmt.execute(sql);
        return builder.isSuccesful(true).build(); 
      } else {
        return builder.isSuccesful(false).failureMsg("Failed to make connection!").build(); 
      }
    } catch (SQLException e) {
      return builder.isSuccesful(false).failureMsg(e.getMessage()).build(); 
    } finally {
      try {
        if (stmt != null)
          stmt.close();
      } catch (SQLException e) {
        return builder.isSuccesful(false).failureMsg(e.getMessage()).build(); 
      }
    }
  }
  
  
}
