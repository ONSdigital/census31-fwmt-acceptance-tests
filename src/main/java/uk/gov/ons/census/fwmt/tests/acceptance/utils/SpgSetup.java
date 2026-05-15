package uk.gov.ons.census.fwmt.tests.acceptance.utils;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import uk.gov.ons.census.fwmt.common.error.GatewayException;
import uk.gov.ons.census.fwmt.events.utils.GatewayEventMonitor;

@Configuration
public class SpgSetup {
  @Autowired
  private ResourceLoader resourceLoader;

  @Value(value = "${outcomeservice.reasonCodeLookup.path}")
  private String reasonCodeLookupPath;

  @Bean
  public SpgReasonCodeLookup buildSPGReasonCodeLookup() throws GatewayException {
    String line;
    Resource resource = resourceLoader.getResource(reasonCodeLookupPath);
    SpgReasonCodeLookup spgReasonCodeLookup = new SpgReasonCodeLookup();
    try (BufferedReader in = new BufferedReader(new InputStreamReader(resource.getInputStream(), UTF_8))) {
      while ((line = in.readLine()) != null) {
        String[] lookup = line.split(",");
        spgReasonCodeLookup.add(lookup[0], lookup[1]);
      }
    } catch (IOException e) {
      throw new GatewayException(GatewayException.Fault.SYSTEM_ERROR, e, "Cannot process reason code lookup");
    }
    return spgReasonCodeLookup;
  }


  @Bean
  public GatewayEventMonitor createGatewayEventMonitor(){
    return new GatewayEventMonitor();
  }
}
