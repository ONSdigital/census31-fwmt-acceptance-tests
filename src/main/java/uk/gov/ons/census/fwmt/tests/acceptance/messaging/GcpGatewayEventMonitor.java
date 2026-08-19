package uk.gov.ons.census.fwmt.tests.acceptance.messaging;

import com.google.cloud.pubsub.v1.stub.GrpcSubscriberStub;
import com.google.cloud.pubsub.v1.stub.SubscriberStubSettings;
import com.google.pubsub.v1.AcknowledgeRequest;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PullRequest;
import com.google.pubsub.v1.PullResponse;
import com.google.pubsub.v1.ReceivedMessage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import uk.gov.ons.census.fwmt.common.events.data.GatewayErrorEventDTO;
import uk.gov.ons.census.fwmt.common.events.data.GatewayEventDTO;

/**
 * GCP Pub/Sub-backed gateway event monitor (real GCP subscription, no emulator).
 */
@Slf4j
public class GcpGatewayEventMonitor {

  static final String TEST_SUBSCRIPTION = "acceptance-tests-Gateway-Events";

  private Map<String, List<GatewayEventDTO>> gatewayEventMap;
  private Map<String, List<GatewayErrorEventDTO>> gatewayErrorEventMap;
  private List<String> eventToWatch = new ArrayList<>();

  private final String projectId;
  private ExecutorService poller;
  private final AtomicBoolean running = new AtomicBoolean(false);

  public GcpGatewayEventMonitor(String projectId) {
    this.projectId = projectId;
  }

  public void tearDownGatewayEventMonitor() {
    running.set(false);
    if (poller != null) {
      poller.shutdownNow();
      try {
        poller.awaitTermination(5, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      poller = null;
    }
    gatewayEventMap = null;
    gatewayErrorEventMap = null;
  }

  public void enableEventMonitor() throws IOException, TimeoutException {
    enableEventMonitor(Collections.emptyList());
  }

  public void enableEventMonitor(List<String> eventsToListen) throws IOException, TimeoutException {
    gatewayEventMap = new ConcurrentHashMap<>();
    gatewayErrorEventMap = new ConcurrentHashMap<>();
    eventToWatch.clear();
    eventToWatch.addAll(eventsToListen);
    log.info("Enabling GCP gateway event monitor subscription={} watchList={}", TEST_SUBSCRIPTION,
        eventToWatch.isEmpty() ? "ALL" : eventToWatch);
    drainSubscription();
    log.info("Drained GCP gateway event monitor backlog for subscription={}", TEST_SUBSCRIPTION);
    running.set(true);
    poller = Executors.newSingleThreadExecutor(r -> {
      Thread thread = new Thread(r, "gcp-gateway-event-monitor");
      thread.setDaemon(true);
      return thread;
    });
    poller.submit(this::pollLoop);
  }

  private void drainSubscription() {
    while (true) {
      List<GcpMessage> batch = pullMessages(100);
      if (batch.isEmpty()) {
        return;
      }
      acknowledge(batch.stream().map(GcpMessage::ackId).toList());
    }
  }

  private void pollLoop() {
    while (running.get()) {
      try {
        List<GcpMessage> batch = pullMessages(10);
        if (batch.isEmpty()) {
          Thread.sleep(100);
          continue;
        }
        for (GcpMessage msg : batch) {
          handleMessage(msg.data());
        }
        acknowledge(batch.stream().map(GcpMessage::ackId).toList());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      } catch (Exception e) {
        log.debug("GCP gateway event poll failed: {}", e.getMessage());
        sleepBriefly();
      }
    }
  }

  private List<GcpMessage> pullMessages(int maxMessages) {
    PullRequest request = PullRequest.newBuilder()
        .setSubscription(ProjectSubscriptionName.of(projectId, TEST_SUBSCRIPTION).toString())
        .setMaxMessages(maxMessages)
        .setReturnImmediately(true)
        .build();
    try (GrpcSubscriberStub subscriber = GrpcSubscriberStub.create(SubscriberStubSettings.newBuilder().build())) {
      PullResponse response = subscriber.pullCallable().call(request);
      return response.getReceivedMessagesList().stream()
          .map(m -> new GcpMessage(m.getAckId(), m.getMessage().getData().toStringUtf8()))
          .toList();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to pull from GCP subscription " + TEST_SUBSCRIPTION, e);
    }
  }

  private void acknowledge(List<String> ackIds) {
    if (ackIds.isEmpty()) {
      return;
    }
    AcknowledgeRequest request = AcknowledgeRequest.newBuilder()
        .setSubscription(ProjectSubscriptionName.of(projectId, TEST_SUBSCRIPTION).toString())
        .addAllAckIds(ackIds)
        .build();
    try (GrpcSubscriberStub subscriber = GrpcSubscriberStub.create(SubscriberStubSettings.newBuilder().build())) {
      subscriber.acknowledgeCallable().call(request);
    } catch (IOException e) {
      log.warn("Failed to acknowledge GCP messages: {}", e.getMessage());
    }
  }

  private void handleMessage(String body) {
    try {
      log.debug(body);
      if (body.contains("exceptionName")) {
        GatewayErrorEventDTO dto = new GatewayErrorEventDTO();
        dto.setCaseId(jsonField(body, "caseId"));
        dto.setErrorEventType(jsonField(body, "errorEventType"));
        String key = createKey(dto.getCaseId(), dto.getErrorEventType());
        List<GatewayErrorEventDTO> dtoList = gatewayErrorEventMap.containsKey(key)
            ? gatewayErrorEventMap.get(key) : new ArrayList<>();
        dtoList.add(dto);
        gatewayErrorEventMap.put(key, dtoList);
      } else {
        GatewayEventDTO dto = new GatewayEventDTO();
        dto.setCaseId(jsonField(body, "caseId"));
        dto.setEventType(jsonField(body, "eventType"));
        dto.setMetadata(parseMetadata(body));
        log.info("GCP gateway event monitor pulled eventType={} caseId={}", dto.getEventType(), dto.getCaseId());
        if (eventToWatch.isEmpty() || eventToWatch.contains(dto.getEventType())) {
          String key = createKey(dto.getCaseId(), dto.getEventType());
          List<GatewayEventDTO> dtoList = gatewayEventMap.containsKey(key)
              ? gatewayEventMap.get(key) : new ArrayList<>();
          dtoList.add(dto);
          gatewayEventMap.put(key, dtoList);
        }
      }
    } catch (Exception e) {
      log.error("Failed to process GCP gateway event", e);
    }
  }

  private static String jsonField(String json, String field) {
    Pattern pattern = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"([^\"]*)\"");
    Matcher matcher = pattern.matcher(json);
    return matcher.find() ? matcher.group(1) : null;
  }

  private static Map<String, String> parseMetadata(String json) {
    Map<String, String> metadata = new HashMap<>();
    int metadataIndex = json.indexOf("\"metadata\"");
    if (metadataIndex < 0) {
      return metadata;
    }
    Pattern pattern = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"");
    Matcher matcher = pattern.matcher(json.substring(metadataIndex));
    while (matcher.find()) {
      if (!"metadata".equals(matcher.group(1))) {
        metadata.put(matcher.group(1), matcher.group(2));
      }
    }
    return metadata;
  }

  private static String createKey(String caseId, String eventType) {
    return caseId + "_" + eventType;
  }

  public Boolean checkForEvent(String caseId, String eventType) {
    return gatewayEventMap.containsKey(createKey(caseId, eventType));
  }

  public Boolean checkForErrorEvent(String caseId, String eventType) {
    return gatewayErrorEventMap.containsKey(createKey(caseId, eventType));
  }

  public List<GatewayEventDTO> getEventsForEventType(String eventType, int qty) {
    List<GatewayEventDTO> eventsFound = new ArrayList<>();
    for (String key : gatewayEventMap.keySet()) {
      if (key.endsWith(eventType)) {
        eventsFound.addAll(gatewayEventMap.get(key));
      }
    }
    return eventsFound;
  }

  public Collection<GatewayEventDTO> grabEventsTriggered(String eventType, int qty, Long timeOut) {
    long startTime = System.currentTimeMillis();
    List<GatewayEventDTO> eventsFound;
    while (true) {
      eventsFound = getEventsForEventType(eventType, qty);
      if (eventsFound.size() >= qty || System.currentTimeMillis() - startTime > timeOut) {
        break;
      }
      sleepBriefly();
    }
    return eventsFound;
  }

  public boolean hasEventTriggered(String caseId, String eventType) {
    return hasEventTriggered(caseId, eventType, 2000L);
  }

  public boolean hasEventTriggered(String caseId, String eventType, Long timeOut) {
    long startTime = System.currentTimeMillis();
    while (true) {
      if (checkForEvent(caseId, eventType) || System.currentTimeMillis() - startTime > timeOut) {
        break;
      }
      sleepBriefly();
    }
    boolean found = checkForEvent(caseId, eventType);
    if (!found) {
      log.warn("GCP gateway event monitor timed out waiting for caseId={} eventType={} after {}ms",
          caseId, eventType, timeOut);
    }
    return found;
  }

  public boolean hasErrorEventTriggered(String caseId, String eventType) {
    return hasErrorEventTriggered(caseId, eventType, 2000L);
  }

  public boolean hasErrorEventTriggered(String caseId, String eventType, Long timeOut) {
    long startTime = System.currentTimeMillis();
    while (true) {
      if (checkForErrorEvent(caseId, eventType) || System.currentTimeMillis() - startTime > timeOut) {
        break;
      }
      sleepBriefly();
    }
    return checkForErrorEvent(caseId, eventType);
  }

  public void reset() {
    if (gatewayEventMap != null) {
      gatewayEventMap.clear();
    }
    if (gatewayErrorEventMap != null) {
      gatewayErrorEventMap.clear();
    }
  }

  private static void sleepBriefly() {
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private record GcpMessage(String ackId, String data) {}
}
