package uk.gov.ons.census.fwmt.tests.acceptance.messaging;

import java.util.Map;
import java.util.Optional;

/**
 * Maps Rabbit queue names used in acceptance tests to Pub/Sub topic + acceptance-only subscription.
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

  private final String rabbitQueueName;
  private final String topic;
  private final String testSubscription;
  private final String serviceSubscription;

  PubSubTestLane(String rabbitQueueName, String topic, String testSubscription, String serviceSubscription) {
    this.rabbitQueueName = rabbitQueueName;
    this.topic = topic;
    this.testSubscription = testSubscription;
    this.serviceSubscription = serviceSubscription;
  }

  public String rabbitQueueName() {
    return rabbitQueueName;
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

  public static Optional<PubSubTestLane> forRabbitQueue(String queueName) {
    for (PubSubTestLane lane : values()) {
      if (lane.rabbitQueueName.equals(queueName)) {
        return Optional.of(lane);
      }
    }
    return Optional.empty();
  }

  public static Map<String, PubSubTestLane> byRabbitQueueName() {
    return Map.of(
        RM_FIELD.rabbitQueueName, RM_FIELD,
        RM_FIELD_DLQ.rabbitQueueName, RM_FIELD_DLQ,
        OUTCOME_PREPROCESSING.rabbitQueueName, OUTCOME_PREPROCESSING,
        OUTCOME_PREPROCESSING_DLQ.rabbitQueueName, OUTCOME_PREPROCESSING_DLQ,
        FIELD_REFUSALS.rabbitQueueName, FIELD_REFUSALS,
        FIELD_OTHER.rabbitQueueName, FIELD_OTHER);
  }
}
