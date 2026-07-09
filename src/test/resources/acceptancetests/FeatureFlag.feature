@Census @Acceptance @FeatureFlag @Inbound
Feature: Inbound feature flags

  Scenario: HH create is ignored when feature flags are disabled
    Given a TM doesnt have a job with case ID "bd6345af-d706-43d3-a13b-8c549e081a76" in TM
    And the "HH" "CREATE" feature flag is disabled
    And RM sends a HH create job request
    Then the request with case ID "bd6345af-d706-43d3-a13b-8c549e081a76" is ignored because the survey or action feature flag is disabled
    And no TM case is created for case ID "bd6345af-d706-43d3-a13b-8c549e081a76"

  Scenario: CE create is ignored when feature flags are disabled
    Given a TM doesnt have a job with case ID "bd6345af-d706-43d3-a13b-8c549e081a76" in TM
    And the "CE" "CREATE" feature flag is disabled
    And RM sends a create job request with "12345678" "CE" "CE Unit" "F" "F"
    Then the request with case ID "bd6345af-d706-43d3-a13b-8c549e081a76" is ignored because the survey or action feature flag is disabled
    And no TM case is created for case ID "bd6345af-d706-43d3-a13b-8c549e081a76"