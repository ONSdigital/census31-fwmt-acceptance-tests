package uk.gov.ons.census.fwmt.tests.acceptance.messaging;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.fwmt.common.events.data.GatewayEventDTO;

/**
 * Gateway event assertions via Pub/Sub ({@code acceptance-tests-Gateway-Events}).
 * Routes to the emulator or real GCP based on {@code fwmt.pubsub.mode}.
 */
@Component
public class AcceptanceGatewayEventMonitor {

  @Value("${fwmt.pubsub.project:fwmt-local}")
  private String pubsubProject;

  @Value("${fwmt.pubsub.emulatorHost:localhost:8085}")
  private String pubsubEmulatorHost;

  // "gcp" uses real GCP Pub/Sub; anything else (default "emulator") uses the HTTP emulator.
  @Value("${fwmt.pubsub.mode:emulator}")
  private String pubsubMode;

  private PubSubGatewayEventMonitor emulatorMonitor;
  private GcpGatewayEventMonitor gcpMonitor;

  private boolean isGcp() {
    return "gcp".equals(pubsubMode);
  }

  private PubSubGatewayEventMonitor emulatorMonitor() {
    if (emulatorMonitor == null) {
      emulatorMonitor = new PubSubGatewayEventMonitor(pubsubProject, pubsubEmulatorHost);
    }
    return emulatorMonitor;
  }

  private GcpGatewayEventMonitor gcpMonitor() {
    if (gcpMonitor == null) {
      gcpMonitor = new GcpGatewayEventMonitor(pubsubProject);
    }
    return gcpMonitor;
  }

  public void tearDownGatewayEventMonitor() {
    if (isGcp()) {
      if (gcpMonitor != null) gcpMonitor.tearDownGatewayEventMonitor();
    } else {
      if (emulatorMonitor != null) emulatorMonitor.tearDownGatewayEventMonitor();
    }
  }

  public void enableEventMonitor() throws IOException, TimeoutException {
    if (isGcp()) {
      gcpMonitor().enableEventMonitor();
    } else {
      emulatorMonitor().enableEventMonitor();
    }
  }

  public Boolean checkForEvent(String caseId, String eventType) {
    return isGcp() ? gcpMonitor().checkForEvent(caseId, eventType)
        : emulatorMonitor().checkForEvent(caseId, eventType);
  }

  public List<GatewayEventDTO> getEventsForEventType(String eventType, int qty) {
    return isGcp() ? gcpMonitor().getEventsForEventType(eventType, qty)
        : emulatorMonitor().getEventsForEventType(eventType, qty);
  }

  public Collection<GatewayEventDTO> grabEventsTriggered(String eventType, int qty, Long timeOut) {
    return isGcp() ? gcpMonitor().grabEventsTriggered(eventType, qty, timeOut)
        : emulatorMonitor().grabEventsTriggered(eventType, qty, timeOut);
  }

  public boolean hasEventTriggered(String caseId, String eventType) {
    return isGcp() ? gcpMonitor().hasEventTriggered(caseId, eventType)
        : emulatorMonitor().hasEventTriggered(caseId, eventType);
  }

  public boolean hasEventTriggered(String caseId, String eventType, Long timeOut) {
    return isGcp() ? gcpMonitor().hasEventTriggered(caseId, eventType, timeOut)
        : emulatorMonitor().hasEventTriggered(caseId, eventType, timeOut);
  }

  public boolean hasErrorEventTriggered(String caseId, String eventType) {
    return isGcp() ? gcpMonitor().hasErrorEventTriggered(caseId, eventType)
        : emulatorMonitor().hasErrorEventTriggered(caseId, eventType);
  }

  public boolean hasErrorEventTriggered(String caseId, String eventType, Long timeOut) {
    return isGcp() ? gcpMonitor().hasErrorEventTriggered(caseId, eventType, timeOut)
        : emulatorMonitor().hasErrorEventTriggered(caseId, eventType, timeOut);
  }

  public void reset() {
    if (isGcp()) {
      gcpMonitor().reset();
    } else {
      emulatorMonitor().reset();
    }
  }
}
