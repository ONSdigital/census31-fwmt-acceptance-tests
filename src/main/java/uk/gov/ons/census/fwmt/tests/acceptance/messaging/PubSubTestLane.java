package uk.gov.ons.census.fwmt.tests.acceptance.messaging;

import java.util.Map;
import java.util.Optional;

/**
 * Maps legacy logical queue names used in acceptance tests to Pub/Sub topic + test subscription.
 */
public enum PubSubTestLane {

  RM_FIELD("RM.Field", "RM.Field", "acceptance-tests-RM-Field", "job-service-RM-Field"),
  RM_FIELD_DLQ("RM.FieldDLQ", "RM.FieldDLQ", "acceptance-tests-RM-FieldDLQ", null),
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
        RM_FIELD.logicalQueueName, RM_FIELD,
        RM_FIELD_DLQ.logicalQueueName, RM_FIELD_DLQ,
        OUTCOME_PREPROCESSING.logicalQueueName, OUTCOME_PREPROCESSING,
        OUTCOME_PREPROCESSING_DLQ.logicalQueueName, OUTCOME_PREPROCESSING_DLQ,
        FIELD_REFUSALS.logicalQueueName, FIELD_REFUSALS,
        FIELD_OTHER.logicalQueueName, FIELD_OTHER);
  }
}
