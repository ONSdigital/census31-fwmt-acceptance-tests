package uk.gov.ons.census.fwmt.tests.acceptance.utils;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.common.base.Strings;

import lombok.extern.slf4j.Slf4j;
import uk.gov.ons.census.fwmt.tests.acceptance.utils.NodeCheck.NodeCheckBuilder;

@Component
@Slf4j
public class PreFlightCheck {

  @Autowired
  private QueueUtils queueUtils;
  
  @Autowired
  private TMMockUtils tmMockUtils;
  
  @Value("${service.outcome.url}")
  private String outcomeServiceUrl;

  @Value("${service.jobservice.url}")
  private String jobserviceServiceUrl;

  @Value("${service.outcome.username}")
  private String outcomeServiceUsername;

  @Value("${service.outcome.password}")
  private String outcomeServicePassword;

  @Value("${service.jobservice.username}")
  private String jobServiceUsername;

  @Value("${service.jobservice.password}")
  private String jobServicePassword;
  
  @Value("${service.tm.url}")
  private String tmServiceUrl;

  @Value("${service.tm.username}")
  private String tmServiceUsername;

  @Value("${service.tm.password}")
  private String tmServicePassword;
  
  @Value("${spring.datasource.url}")
  private String postgresUrl;

  public void doCheck() {
    List<NodeCheck> checks = new ArrayList<>();
    checks.add(queueUtils.doPreFlightCheck());
    checks.add(checkService("outcome-service", outcomeServiceUrl+"/swagger-ui.html", outcomeServiceUsername, outcomeServicePassword));
    checks.add(checkService("job-service", jobserviceServiceUrl+"/swagger-ui.html", jobServiceUsername, jobServicePassword));
    checks.add(checkService("tm-service", tmServiceUrl+"/swagger-ui.html", tmServiceUsername, tmServicePassword));
    checks.add(tmMockUtils.checkDbUp());
    checks.stream().forEach(n -> System.out.println(n.toString()));
  }

  public NodeCheck checkService(String name, String address, String user, String password){
    NodeCheckBuilder builder = NodeCheck.builder().name(name).url(address);
    
    try {
      URL url = new URL(address);
      HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();

      if (!Strings.isNullOrEmpty(user)) {
        String auth = user + ":" + password;
        byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
        String authHeaderValue = "Basic " + new String(encodedAuth);
        httpURLConnection.setRequestProperty("Authorization", authHeaderValue);
      }

      httpURLConnection.setRequestMethod("GET");
      if (httpURLConnection.getResponseCode() == 200) {
        builder.isSuccesful(true);
      }else {
        builder.isSuccesful(false).failureMsg(httpURLConnection.getResponseMessage());
      }
    } catch (Exception e) {
      builder.isSuccesful(false).failureMsg(e.getMessage());
    }
    return builder.build();
  }

  
  
}
