package uk.gov.ons.census.fwmt.tests.acceptance.messaging;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.fwmt.events.data.GatewayEventDTO;

/**
 * Gateway event assertions via Pub/Sub emulator ({@code acceptance-tests-Gateway-Events}).
 */
@Component
public class AcceptanceGatewayEventMonitor {

  @Value("${fwmt.pubsub.project:fwmt-local}")
  private String pubsubProject;

  @Value("${fwmt.pubsub.emulatorHost:localhost:8085}")
  private String pubsubEmulatorHost;

  private PubSubGatewayEventMonitor pubSubMonitor;

  private PubSubGatewayEventMonitor pubSubMonitor() {
    if (pubSubMonitor == null) {
      pubSubMonitor = new PubSubGatewayEventMonitor(pubsubProject, pubsubEmulatorHost);
    }
    return pubSubMonitor;
  }

  public void tearDownGatewayEventMonitor() {
    if (pubSubMonitor != null) {
      pubSubMonitor.tearDownGatewayEventMonitor();
    }
  }

  public void enableEventMonitor(String rabbitLocation, String rabbitUsername, String rabbitPassword, Integer port)
      throws IOException, TimeoutException {
    pubSubMonitor().enableEventMonitor(rabbitLocation, rabbitUsername, rabbitPassword, port);
  }

  public Boolean checkForEvent(String caseId, String eventType) {
    return pubSubMonitor().checkForEvent(caseId, eventType);
  }

  public List<GatewayEventDTO> getEventsForEventType(String eventType, int qty) {
    return pubSubMonitor().getEventsForEventType(eventType, qty);
  }

  public Collection<GatewayEventDTO> grabEventsTriggered(String eventType, int qty, Long timeOut) {
    return pubSubMonitor().grabEventsTriggered(eventType, qty, timeOut);
  }

  public boolean hasEventTriggered(String caseId, String eventType) {
    return pubSubMonitor().hasEventTriggered(caseId, eventType);
  }

  public boolean hasEventTriggered(String caseId, String eventType, Long timeOut) {
    return pubSubMonitor().hasEventTriggered(caseId, eventType, timeOut);
  }

  public boolean hasErrorEventTriggered(String caseId, String eventType) {
    return pubSubMonitor().hasErrorEventTriggered(caseId, eventType);
  }

  public boolean hasErrorEventTriggered(String caseId, String eventType, Long timeOut) {
    return pubSubMonitor().hasErrorEventTriggered(caseId, eventType, timeOut);
  }

  public void reset() {
    pubSubMonitor().reset();
  }
}
