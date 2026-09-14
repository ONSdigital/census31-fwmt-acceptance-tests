@Outcome @FeatureFlag @Census27Test
Feature: Outcomes Feature Flag Tests

  Scenario: CE outcome is ignored when outcome feature flags are disabled
    Given the "CE" outcome feature flag is set to "false"
    Given an "CE" "No Action" outcome message
    And its Primary Outcome is "Irrelevant"
    And its secondary Outcome "Irrelevant"
    And its Outcome code is "25-30-02"
    And the message includes a Linked QID "F"
    And the message includes a Fulfillment Request "F"
    When Gateway receives message with No Content Response
    Then the outcome is ignored due to the feature flag