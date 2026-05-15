package uk.gov.ons.census.fwmt.tests.acceptance.utils;

import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CSVSerivceUtils {

  @Value("${service.ccscsv.url}")
  private String ccsCsvService;

  @Value("${service.cecsv.url}")
  private String ceCsvService;

  @Value("${service.addresscheckcsv.url}")
  private String addressCheckCsvService;

  @Value("${service.addressfileload.url}")
  private String addressFileLoadService;

  @Value("${service.csvservice.username}")
  private String csvServiceUsername;

  @Value("${service.csvservice.password}")
  private String csvServicePassword;

  public CSVSerivceUtils() {
  }

  public int enableCCSCsvService() {
    return sendRequest(ccsCsvService);
  }

  public int enableCECsvService() {
    return sendRequest(ceCsvService);
  }

  public int enableAddressCheckCsvService() {
    return sendRequest(addressCheckCsvService);
  }

  public int ingestAddressCheckFile() {
    return sendRequest(addressFileLoadService);
  }

  private int sendRequest(String url) {
    HttpHeaders headers = createBasicAuthHeaders(csvServiceUsername, csvServicePassword);
    headers.setContentType(MediaType.APPLICATION_JSON);
    RestTemplate restTemplate = new RestTemplate();
    HttpEntity<String> get = new HttpEntity<>(null, headers);
    ResponseEntity<Void> response = restTemplate.exchange(url, HttpMethod.GET, get, Void.class);
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
}
