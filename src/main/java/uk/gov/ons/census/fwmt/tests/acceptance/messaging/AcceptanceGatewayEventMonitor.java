package uk.gov.ons.census.fwmt.tests.acceptance.messaging;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.fwmt.events.data.GatewayEventDTO;
import uk.gov.ons.census.fwmt.events.utils.GatewayEventMonitor;

/**
 * Delegates gateway event assertions to Rabbit or Pub/Sub monitor based on {@code fwmt.messaging.provider}.
 */
@Component
public class AcceptanceGatewayEventMonitor {

  private final GatewayEventMonitor rabbitMonitor = new GatewayEventMonitor();

  @Value("${fwmt.messaging.provider:rabbit}")
  private String messagingProvider;

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
    if (isPubSub() && pubSubMonitor != null) {
      pubSubMonitor.tearDownGatewayEventMonitor();
    } else {
      rabbitMonitor.tearDownGatewayEventMonitor();
    }
  }

  public void enableEventMonitor(String rabbitLocation, String rabbitUsername, String rabbitPassword, Integer port)
      throws IOException, TimeoutException {
    if (isPubSub()) {
      pubSubMonitor().enableEventMonitor(rabbitLocation, rabbitUsername, rabbitPassword, port);
    } else {
      rabbitMonitor.enableEventMonitor(rabbitLocation, rabbitUsername, rabbitPassword, port);
    }
  }

  public Boolean checkForEvent(String caseId, String eventType) {
    return isPubSub() ? pubSubMonitor().checkForEvent(caseId, eventType) : rabbitMonitor.checkForEvent(caseId, eventType);
  }

  public List<GatewayEventDTO> getEventsForEventType(String eventType, int qty) {
    return isPubSub()
        ? pubSubMonitor().getEventsForEventType(eventType, qty)
        : rabbitMonitor.getEventsForEventType(eventType, qty);
  }

  public Collection<GatewayEventDTO> grabEventsTriggered(String eventType, int qty, Long timeOut) {
    return isPubSub()
        ? pubSubMonitor().grabEventsTriggered(eventType, qty, timeOut)
        : rabbitMonitor.grabEventsTriggered(eventType, qty, timeOut);
  }

  public boolean hasEventTriggered(String caseId, String eventType) {
    return isPubSub()
        ? pubSubMonitor().hasEventTriggered(caseId, eventType)
        : rabbitMonitor.hasEventTriggered(caseId, eventType);
  }

  public boolean hasEventTriggered(String caseId, String eventType, Long timeOut) {
    return isPubSub()
        ? pubSubMonitor().hasEventTriggered(caseId, eventType, timeOut)
        : rabbitMonitor.hasEventTriggered(caseId, eventType, timeOut);
  }

  public boolean hasErrorEventTriggered(String caseId, String eventType) {
    return isPubSub()
        ? pubSubMonitor().hasErrorEventTriggered(caseId, eventType)
        : rabbitMonitor.hasErrorEventTriggered(caseId, eventType);
  }

  public boolean hasErrorEventTriggered(String caseId, String eventType, Long timeOut) {
    return isPubSub()
        ? pubSubMonitor().hasErrorEventTriggered(caseId, eventType, timeOut)
        : rabbitMonitor.hasErrorEventTriggered(caseId, eventType, timeOut);
  }

  public void reset() {
    if (isPubSub()) {
      pubSubMonitor().reset();
    } else {
      rabbitMonitor.reset();
    }
  }

  private boolean isPubSub() {
    return "pubsub".equalsIgnoreCase(messagingProvider);
  }
}
