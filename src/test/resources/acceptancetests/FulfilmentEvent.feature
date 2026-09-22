@FulfilmentEvent @Regression
Feature: Fulfilment event pause handling

  The Gateway pauses an in-field household case when a fulfilment request is
  received from another channel.

  Scenario Outline: Pause an in-field household case for a fulfilment request
    Given an in-field household case exists in the Gateway cache with case ID "bd6345af-d706-43d3-a13b-8c549e081a76"
    And the Gateway receives a FulfilmentRequest with fulfilment code "P_OR_H1", case ID "bd6345af-d706-43d3-a13b-8c549e081a76", and channel "<channel>"
    When the fulfilment request is processed
    Then the Gateway publishes an internal fieldwork action instruction for case ID "bd6345af-d706-43d3-a13b-8c549e081a76"
    And the internal action instruction has action instruction "PAUSE", survey name "CENSUS", address type "HH", and address level "U"
    And the pause uses the configured pause code "L"
    And Job Service sends the PAUSE event to TM for case ID "bd6345af-d706-43d3-a13b-8c549e081a76"
    And TM acknowledges the PAUSE for case ID "bd6345af-d706-43d3-a13b-8c549e081a76"

    Examples:
      | channel |
      | CC      |
      | RH      |
      | FIELD   |
      | RO      |

  Scenario: Ignore a fulfilment request when the household case is not in the field
    Given no in-field household case exists in the Gateway cache with case ID "bd6345af-d706-43d3-a13b-8c549e081a76"
    And the Gateway receives a FulfilmentRequest with fulfilment code "P_OR_H1" and case ID "bd6345af-d706-43d3-a13b-8c549e081a76"
    When the fulfilment request is processed
    Then the Gateway does not publish an internal fieldwork action instruction
    And the fulfilment request is acknowledged as ignored

  Scenario: Ignore a fulfilment request for an already cancelled household case
    Given an in-field household case exists in the Gateway cache with case ID "bd6345af-d706-43d3-a13b-8c549e081a76"
    And the household case has last action instruction "CANCEL"
    And the Gateway receives a FulfilmentRequest with fulfilment code "P_OR_H1" and case ID "bd6345af-d706-43d3-a13b-8c549e081a76"
    When the fulfilment request is processed
    Then the Gateway does not publish an internal fieldwork action instruction
    And the fulfilment request is acknowledged as already cancelled

  Scenario: Ignore a fulfilment request with an unrecognised product code
    Given an in-field household case exists in the Gateway cache with case ID "bd6345af-d706-43d3-a13b-8c549e081a76"
    And the Gateway receives a FulfilmentRequest with fulfilment code "UNKNOWN_PRODUCT" and case ID "bd6345af-d706-43d3-a13b-8c549e081a76"
    When the fulfilment request is processed
    Then the Gateway does not publish an internal fieldwork action instruction
    And the fulfilment request is recorded as having an unrecognised fulfilment code
