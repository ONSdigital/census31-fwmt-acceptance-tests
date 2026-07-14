@Census @Acceptance @FeatureFlag @Outcome
Feature: Outcome feature flags

  Scenario: CE outcome is ignored when outcome feature flags are disabled
    Given the "CE" outcome feature flag is disabled
    Given an "CE" "No Action" outcome message
    And its Primary Outcome is "Irrelavant"
    And its secondary Outcome "Irrelavant"
    And its Outcome code is "25-30-02"
    And the message includes a Linked QID "F"
    And the message includes a Fulfillment Request "F"
    When Gateway receives the outcome
    Then the outcome is ignored due to feature flag for case ID "bd6345af-d706-43d3-a13b-8c549e081a76"