package uk.gov.ons.census.fwmt.tests.acceptance.messaging;

public record ExternalActionInstructionMetadataOverride(
    AttributeOverride eventId,
    AttributeOverride correlationId,
    AttributeOverride caseId,
    AttributeOverride eventType,
    AttributeOverride schemaVersion,
    AttributeOverride occurredAt) {

  public ExternalActionInstructionMetadataOverride {
    eventId = defaultOverride(eventId);
    correlationId = defaultOverride(correlationId);
    caseId = defaultOverride(caseId);
    eventType = defaultOverride(eventType);
    schemaVersion = defaultOverride(schemaVersion);
    occurredAt = defaultOverride(occurredAt);
  }

  public static ExternalActionInstructionMetadataOverride none() {
    return new ExternalActionInstructionMetadataOverride(null, null, null, null, null, null);
  }

  public ExternalActionInstructionMetadataOverride withEventId(String value) {
    return new ExternalActionInstructionMetadataOverride(
        AttributeOverride.withValue(value),
        correlationId,
        caseId,
        eventType,
        schemaVersion,
        occurredAt);
  }

  public ExternalActionInstructionMetadataOverride omitEventId() {
    return new ExternalActionInstructionMetadataOverride(
        AttributeOverride.omit(), correlationId, caseId, eventType, schemaVersion, occurredAt);
  }

  public ExternalActionInstructionMetadataOverride withCorrelationId(String value) {
    return new ExternalActionInstructionMetadataOverride(
        eventId,
        AttributeOverride.withValue(value),
        caseId,
        eventType,
        schemaVersion,
        occurredAt);
  }

  public ExternalActionInstructionMetadataOverride omitCorrelationId() {
    return new ExternalActionInstructionMetadataOverride(
        eventId, AttributeOverride.omit(), caseId, eventType, schemaVersion, occurredAt);
  }

  public ExternalActionInstructionMetadataOverride withCaseId(String value) {
    return new ExternalActionInstructionMetadataOverride(
        eventId,
        correlationId,
        AttributeOverride.withValue(value),
        eventType,
        schemaVersion,
        occurredAt);
  }

  public ExternalActionInstructionMetadataOverride omitCaseId() {
    return new ExternalActionInstructionMetadataOverride(
        eventId, correlationId, AttributeOverride.omit(), eventType, schemaVersion, occurredAt);
  }

  public ExternalActionInstructionMetadataOverride withEventType(String value) {
    return new ExternalActionInstructionMetadataOverride(
        eventId,
        correlationId,
        caseId,
        AttributeOverride.withValue(value),
        schemaVersion,
        occurredAt);
  }

  public ExternalActionInstructionMetadataOverride omitEventType() {
    return new ExternalActionInstructionMetadataOverride(
        eventId, correlationId, caseId, AttributeOverride.omit(), schemaVersion, occurredAt);
  }

  public ExternalActionInstructionMetadataOverride withSchemaVersion(String value) {
    return new ExternalActionInstructionMetadataOverride(
        eventId,
        correlationId,
        caseId,
        eventType,
        AttributeOverride.withValue(value),
        occurredAt);
  }

  public ExternalActionInstructionMetadataOverride omitSchemaVersion() {
    return new ExternalActionInstructionMetadataOverride(
        eventId, correlationId, caseId, eventType, AttributeOverride.omit(), occurredAt);
  }

  public ExternalActionInstructionMetadataOverride withOccurredAt(String value) {
    return new ExternalActionInstructionMetadataOverride(
        eventId,
        correlationId,
        caseId,
        eventType,
        schemaVersion,
        AttributeOverride.withValue(value));
  }

  public ExternalActionInstructionMetadataOverride omitOccurredAt() {
    return new ExternalActionInstructionMetadataOverride(
        eventId, correlationId, caseId, eventType, schemaVersion, AttributeOverride.omit());
  }

  private static AttributeOverride defaultOverride(AttributeOverride override) {
    return override == null ? AttributeOverride.useDefault() : override;
  }

  public record AttributeOverride(boolean overridesDefault, boolean include, String value) {

    public AttributeOverride {
      if (!include && value != null) {
        throw new IllegalArgumentException("Omitted attribute overrides cannot provide a value");
      }
    }

    public static AttributeOverride useDefault() {
      return new AttributeOverride(false, true, null);
    }

    public static AttributeOverride withValue(String value) {
      return new AttributeOverride(true, true, value);
    }

    public static AttributeOverride omit() {
      return new AttributeOverride(true, false, null);
    }
  }
}