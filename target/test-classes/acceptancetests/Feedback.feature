@Census @Acceptance @SPG @Feedback
Feature: SPG Outcome Tests

  Scenario Outline: As a Gateway I can receive an SPG outcome which will provide feedback to tm
    Given a job has been created in TM with case id "bd6345af-d706-43d3-a13b-8c549e081a76"
    And tm sends a "<Type>" outcome
    Then a "<input>" feedback message is sent to tm
    And "<output>" is acknowledged by tm

    Examples:
      | Type               | input                    | output           |
      | CANCEL_FEEDBACK    | COMET_CANCEL_PRE_SENDING | COMET_CANCEL_ACK |
      | DELIVERED_FEEDBACK | COMET_UPDATE_PRE_SENDING | COMET_UPDATE_ACK |

