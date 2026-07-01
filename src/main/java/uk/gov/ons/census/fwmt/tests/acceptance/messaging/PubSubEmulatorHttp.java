package uk.gov.ons.census.fwmt.tests.acceptance.messaging;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

/**
 * Minimal Pub/Sub emulator client over the HTTP API (same surface as {@code setup-pubsub.sh}).
 */
@Slf4j
class PubSubEmulatorHttp {

  private static final Pattern ACK_ID_PATTERN = Pattern.compile("\"ackId\"\\s*:\\s*\"([^\"]+)\"");
  private static final Pattern DATA_PATTERN = Pattern.compile("\"data\"\\s*:\\s*\"([^\"]+)\"");

  private final String apiBase;

  PubSubEmulatorHttp(String projectId, String emulatorHost) {
    this.apiBase = "http://" + emulatorHost + "/v1/projects/" + projectId;
  }

  boolean isReachable() {
    try {
      httpGet(apiBase + "/topics");
      return true;
    } catch (Exception e) {
      log.debug("Pub/Sub emulator unreachable at {}: {}", apiBase, e.getMessage());
      return false;
    }
  }

  void publish(String topicId, String jsonBody, Map<String, String> attributes) {
    StringBuilder attrs = new StringBuilder();
    attributes.forEach((key, value) -> {
      if (attrs.length() > 0) {
        attrs.append(',');
      }
      attrs.append('"').append(escapeJson(key)).append("\":\"")
          .append(escapeJson(value)).append('"');
    });
    String body = "{\"messages\":[{\"data\":\""
        + Base64.getEncoder().encodeToString(jsonBody.getBytes(StandardCharsets.UTF_8))
        + "\",\"attributes\":{" + attrs + "}}]}";
    try {
      httpPost(apiBase + "/topics/" + encode(topicId) + ":publish", body);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to publish to topic " + topicId, e);
    }
  }

  List<ReceivedPubSubMessage> pull(String subscriptionId, int maxMessages, boolean returnImmediately) {
    String body = "{\"maxMessages\":" + maxMessages + ",\"returnImmediately\":" + returnImmediately + "}";
    try {
      String response = httpPost(apiBase + "/subscriptions/" + encode(subscriptionId) + ":pull", body);
      return parsePullResponse(response);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to pull from subscription " + subscriptionId, e);
    }
  }

  void acknowledge(String subscriptionId, List<String> ackIds) {
    if (ackIds.isEmpty()) {
      return;
    }
    StringBuilder ids = new StringBuilder();
    for (String ackId : ackIds) {
      if (ids.length() > 0) {
        ids.append(',');
      }
      ids.append('"').append(escapeJson(ackId)).append('"');
    }
    String body = "{\"ackIds\":[" + ids + "]}";
    try {
      httpPost(apiBase + "/subscriptions/" + encode(subscriptionId) + ":acknowledge", body);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to ack subscription " + subscriptionId, e);
    }
  }

  void drainSubscription(String subscriptionId) {
    while (true) {
      List<ReceivedPubSubMessage> batch = pull(subscriptionId, 500, true);
      if (batch.isEmpty()) {
        return;
      }
      List<String> ackIds = new ArrayList<>();
      for (ReceivedPubSubMessage message : batch) {
        ackIds.add(message.ackId());
      }
      acknowledge(subscriptionId, ackIds);
    }
  }

  private List<ReceivedPubSubMessage> parsePullResponse(String response) {
    List<ReceivedPubSubMessage> received = new ArrayList<>();
    if (response == null || !response.contains("receivedMessages")) {
      return received;
    }
    int index = 0;
    while (true) {
      int ackStart = response.indexOf("\"ackId\"", index);
      if (ackStart < 0) {
        break;
      }
      Matcher ackMatcher = ACK_ID_PATTERN.matcher(response.substring(ackStart));
      if (!ackMatcher.find()) {
        break;
      }
      String ackId = ackMatcher.group(1);
      int dataStart = response.indexOf("\"data\"", ackStart);
      if (dataStart < 0) {
        break;
      }
      Matcher dataMatcher = DATA_PATTERN.matcher(response.substring(dataStart));
      if (!dataMatcher.find()) {
        break;
      }
      String data = new String(Base64.getDecoder().decode(dataMatcher.group(1)), StandardCharsets.UTF_8);
      received.add(new ReceivedPubSubMessage(ackId, data, Map.of()));
      index = dataStart + 1;
    }
    return received;
  }

  private String httpPost(String url, String body) throws IOException {
    java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
    java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Content-Type", "application/json")
        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
        .build();
    try {
      java.net.http.HttpResponse<String> response =
          client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IOException("HTTP " + response.statusCode() + " from " + url + ": " + response.body());
      }
      return response.body() == null ? "" : response.body();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted calling " + url, e);
    }
  }

  private void httpGet(String url) throws IOException {
    java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
    java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
        .uri(URI.create(url))
        .GET()
        .build();
    try {
      java.net.http.HttpResponse<String> response =
          client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IOException("HTTP " + response.statusCode() + " from " + url);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted calling " + url, e);
    }
  }

  private static String encode(String segment) {
    return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
  }

  private static String escapeJson(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  record ReceivedPubSubMessage(String ackId, String data, Map<String, String> attributes) {
  }
}
