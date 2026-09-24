package uk.gov.ons.census.fwmt.tests.acceptance.messaging;

import java.util.Map;
import java.util.Optional;

/**
 * Maps acceptance-test logical queue names to Pub/Sub topic + test subscription.
 */
public enum PubSubTestLane {

  FIELDWORK_ACTION_INSTRUCTION(
      "event_fieldwork_action-instruction",
      "event_fieldwork_action-instruction",
      "acceptance-tests-fieldwork-action-instruction",
      "job-service-fieldwork-action-instruction"),
  FIELDWORK_ACTION_INSTRUCTION_INTERNAL(
      "event_fieldwork_action-instruction_internal",
      "event_fieldwork_action-instruction_internal",
      "acceptance-tests-fieldwork-action-instruction-internal",
      "job-service-fieldwork-action-instruction-internal"),
  OUTCOME_PREPROCESSING(
      "Outcome.Preprocessing",
      "Outcome.Preprocessing",
      "acceptance-tests-Outcome-Preprocessing",
      "outcome-service-Outcome-Preprocessing"),
  OUTCOME_PREPROCESSING_DLQ(
      "Outcome.PreprocessingDLQ",
      "Outcome.PreprocessingDLQ",
      "acceptance-tests-Outcome-PreprocessingDLQ",
      "outcome-service-Outcome-PreprocessingDLQ"),
    REFUSAL_RECEIVED(
      "event_refusal-received",
      "event_refusal-received",
      "acceptance-tests-refusal-received",
      null),
    FIELD_CASE_UPDATED(
      "event_field-case-updated",
      "event_field-case-updated",
      "acceptance-tests-field-case-updated",
      null),
    FULFILMENT_REQUEST(
      "event_fulfilment-request",
      "event_fulfilment-request",
      "acceptance-tests-fulfilment-request",
      "fulfilment-event-service-fulfilment-request"),
  FIELD_REFUSALS("Field.refusals", "Field.refusals", "acceptance-tests-Field-refusals", null),
  FIELD_OTHER("Field.other", "Field.other", "acceptance-tests-Field-other", null);

  private final String logicalQueueName;
  private final String topic;
  private final String testSubscription;
  private final String serviceSubscription;

  PubSubTestLane(String logicalQueueName, String topic, String testSubscription, String serviceSubscription) {
    this.logicalQueueName = logicalQueueName;
    this.topic = topic;
    this.testSubscription = testSubscription;
    this.serviceSubscription = serviceSubscription;
  }

  public String logicalQueueName() {
    return logicalQueueName;
  }

  public String topic() {
    return topic;
  }

  public String testSubscription() {
    return testSubscription;
  }

  public Optional<String> serviceSubscription() {
    return Optional.ofNullable(serviceSubscription);
  }

  public static Optional<PubSubTestLane> forLogicalQueue(String queueName) {
    for (PubSubTestLane lane : values()) {
      if (lane.logicalQueueName.equals(queueName)) {
        return Optional.of(lane);
      }
    }
    return Optional.empty();
  }

  public static Map<String, PubSubTestLane> byLogicalQueueName() {
    return Map.of(
        FIELDWORK_ACTION_INSTRUCTION.logicalQueueName, FIELDWORK_ACTION_INSTRUCTION,
        FIELDWORK_ACTION_INSTRUCTION_INTERNAL.logicalQueueName, FIELDWORK_ACTION_INSTRUCTION_INTERNAL,
        OUTCOME_PREPROCESSING.logicalQueueName, OUTCOME_PREPROCESSING,
        OUTCOME_PREPROCESSING_DLQ.logicalQueueName, OUTCOME_PREPROCESSING_DLQ,
        REFUSAL_RECEIVED.logicalQueueName, REFUSAL_RECEIVED,
        FIELD_CASE_UPDATED.logicalQueueName, FIELD_CASE_UPDATED,
        FULFILMENT_REQUEST.logicalQueueName, FULFILMENT_REQUEST,
        FIELD_REFUSALS.logicalQueueName, FIELD_REFUSALS,
        FIELD_OTHER.logicalQueueName, FIELD_OTHER);
  }
}
