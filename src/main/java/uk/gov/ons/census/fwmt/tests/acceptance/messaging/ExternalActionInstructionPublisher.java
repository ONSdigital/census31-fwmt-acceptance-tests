package uk.gov.ons.census.fwmt.tests.acceptance.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class ExternalActionInstructionPublisher {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final String EXPECTED_SURVEY_NAME = "CENSUS";
  static final String EXTERNAL_EVENT_TYPE = "CASE_UPDATE";
  static final String INTERNAL_EVENT_TYPE = "FIELDWORK_ACTION_INSTRUCTION";
  static final String SCHEMA_VERSION = "1.0";

  private ExternalActionInstructionPublisher() {}

  static Map<String, String> buildExternalAttributes(
      String messageJson, ExternalActionInstructionMetadataOverride metadataOverride) {
    ActionInstructionPayload payload = validatePayload(messageJson);
    ExternalActionInstructionMetadataOverride overrides =
        metadataOverride == null ? ExternalActionInstructionMetadataOverride.none() : metadataOverride;

    Map<String, String> attributes = new LinkedHashMap<>();
    attributes.put("eventId", UUID.randomUUID().toString());
    attributes.put("correlationId", "");
    attributes.put("caseId", payload.caseId());
    attributes.put("eventType", EXTERNAL_EVENT_TYPE);
    attributes.put("schemaVersion", SCHEMA_VERSION);
    attributes.put("occurredAt", Instant.now().toString());

    applyOverride(attributes, "eventId", overrides.eventId());
    applyOverride(attributes, "correlationId", overrides.correlationId());
    applyOverride(attributes, "caseId", overrides.caseId());
    applyOverride(attributes, "eventType", overrides.eventType());
    applyOverride(attributes, "schemaVersion", overrides.schemaVersion());
    applyOverride(attributes, "occurredAt", overrides.occurredAt());
    return Map.copyOf(attributes);
  }

  static ActionInstructionPayload validatePayload(String messageJson) {
    JsonNode root = parseJson(messageJson);
    String caseId = requireText(root, "caseId");
    String surveyName = requireText(root, "surveyName");
    String actionInstruction = requireText(root, "actionInstruction");

    if (!EXPECTED_SURVEY_NAME.equals(surveyName)) {
      throw new IllegalArgumentException(
          "External action instruction payload must set surveyName='" + EXPECTED_SURVEY_NAME + "'");
    }

    return new ActionInstructionPayload(caseId, actionInstruction);
  }

  private static JsonNode parseJson(String messageJson) {
    try {
      return OBJECT_MAPPER.readTree(messageJson);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("External action instruction payload must be valid JSON", e);
    }
  }

  private static String requireText(JsonNode root, String fieldName) {
    JsonNode node = root.path(fieldName);
    if (!node.isTextual() || node.asText().trim().isEmpty()) {
      throw new IllegalArgumentException(
          "External action instruction payload must contain a non-empty textual " + fieldName);
    }
    return node.asText();
  }

  private static void applyOverride(
      Map<String, String> attributes,
      String attributeName,
      ExternalActionInstructionMetadataOverride.AttributeOverride override) {
    if (override == null || !override.overridesDefault()) {
      return;
    }
    if (!override.include()) {
      attributes.remove(attributeName);
      return;
    }
    attributes.put(attributeName, override.value());
  }

  record ActionInstructionPayload(String caseId, String actionInstruction) {}
}