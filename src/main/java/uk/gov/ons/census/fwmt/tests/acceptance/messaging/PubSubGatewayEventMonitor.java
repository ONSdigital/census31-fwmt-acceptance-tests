package uk.gov.ons.census.fwmt.tests.acceptance.messaging;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;
import uk.gov.ons.census.fwmt.events.data.GatewayErrorEventDTO;
import uk.gov.ons.census.fwmt.events.data.GatewayEventDTO;

/**
 * Pub/Sub-backed gateway event monitor for acceptance tests (topic {@code Gateway.Events.Exchange}).
 */
@Slf4j
public class PubSubGatewayEventMonitor {

  public static final String TEST_SUBSCRIPTION = "acceptance-tests-Gateway-Events";

  private Map<String, List<GatewayEventDTO>> gatewayEventMap;
  private Map<String, List<GatewayErrorEventDTO>> gatewayErrorEventMap;
  private List<String> eventToWatch = new ArrayList<>();

  private final PubSubEmulatorHttp http;
  private ExecutorService poller;
  private final AtomicBoolean running = new AtomicBoolean(false);

  public PubSubGatewayEventMonitor(String projectId, String emulatorHost) {
    this.http = new PubSubEmulatorHttp(projectId, emulatorHost);
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

  public void enableEventMonitor(String ignoredHost, String ignoredUser, String ignoredPassword, Integer ignoredPort)
      throws IOException, TimeoutException {
    enableEventMonitor(ignoredHost, ignoredUser, ignoredPassword, ignoredPort, Collections.emptyList());
  }

  public void enableEventMonitor(
      String ignoredHost,
      String ignoredUser,
      String ignoredPassword,
      Integer ignoredPort,
      List<String> eventsToListen) throws IOException, TimeoutException {
    gatewayEventMap = new ConcurrentHashMap<>();
    gatewayErrorEventMap = new ConcurrentHashMap<>();
    eventToWatch.clear();
    eventToWatch.addAll(eventsToListen);
    // Drop any backlog (e.g. events emitted during Maven startup or a prior scenario) before
    // starting the poller, otherwise stale events for the reused caseId pollute the map.
    http.drainSubscription(TEST_SUBSCRIPTION);
    running.set(true);
    poller = Executors.newSingleThreadExecutor(r -> {
      Thread thread = new Thread(r, "pubsub-gateway-event-monitor");
      thread.setDaemon(true);
      return thread;
    });
    poller.submit(this::pollLoop);
  }

  private void pollLoop() {
    while (running.get()) {
      try {
        List<PubSubEmulatorHttp.ReceivedPubSubMessage> batch = http.pull(TEST_SUBSCRIPTION, 10, true);
        if (batch.isEmpty()) {
          Thread.sleep(100);
          continue;
        }
        List<String> ackIds = new ArrayList<>();
        for (PubSubEmulatorHttp.ReceivedPubSubMessage received : batch) {
          handleMessage(received.data());
          ackIds.add(received.ackId());
        }
        http.acknowledge(TEST_SUBSCRIPTION, ackIds);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      } catch (Exception e) {
        log.debug("Gateway event poll failed: {}", e.getMessage());
        sleepBriefly();
      }
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
        if (eventToWatch.isEmpty() || eventToWatch.contains(dto.getEventType())) {
          String key = createKey(dto.getCaseId(), dto.getEventType());
          List<GatewayEventDTO> dtoList = gatewayEventMap.containsKey(key)
              ? gatewayEventMap.get(key) : new ArrayList<>();
          dtoList.add(dto);
          gatewayEventMap.put(key, dtoList);
        }
      }
    } catch (Exception e) {
      log.error("Failed to process gateway event from Pub/Sub", e);
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
    return checkForEvent(caseId, eventType);
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
    gatewayEventMap.clear();
    gatewayErrorEventMap.clear();
  }

  private static void sleepBriefly() {
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static String createKey(String caseId, String eventType) {
    return caseId + " " + eventType;
  }
}
