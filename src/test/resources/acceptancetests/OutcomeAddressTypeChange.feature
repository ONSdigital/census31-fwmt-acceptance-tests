@Census @Acceptance @Outcome @SPG @CE
Feature: Outcome Address Type Change Tests

  Scenario Outline: As a Gateway I can receive an outcome from TM and create Census Events
    Given a parent case exists
    And an "<SurveyType>" "<BusinessFunction>" outcome message
    And its Primary Outcome is "<Primary Outcome>"
    And its secondary Outcome "<Secondary Outcome>"
    And its Outcome code is "<Outcome Code>"
    And the message includes a Linked QID "<HasLinkedQID>"
    And the message includes a Fulfillment Request "<HasFulfilmentRequest>"
    When Gateway receives the outcome
    Then It will run the following processors "<Operation List>"
    And create the following messages to RM "<RmMessages>"
    And the caseId of the "<InitialOperation>" message will be the original caseid
    And it will include a new caseId
    And every other message will use the new caseId as its caseId
    And each message has the correct values
    And it will create the following messages "<JsMessages>" to JobService

    Examples:
      | SurveyType | BusinessFunction | Primary Outcome | Secondary Outcome | Outcome Code | HasLinkedQID | HasFulfilmentRequest | Operation List | RmMessages | InitialOperation | JsMessages |
      | SPG | Address Type Changed HH | Not Valid | Phone - Property is a household | 6-10-03 | F | F | ADDRESS_TYPE_CHANGED_HH,FULFILMENT_REQUESTED,LINKED_QID | ADDRESS_TYPE_CHANGED | ADDRESS_TYPE_CHANGED |  |
      | SPG | Address Type Changed HH | Not Valid | Phone - Property is a household | 6-10-03 | T | F | ADDRESS_TYPE_CHANGED_HH,FULFILMENT_REQUESTED,LINKED_QID | ADDRESS_TYPE_CHANGED,QUESTIONNAIRE_LINKED | ADDRESS_TYPE_CHANGED |  |
      | SPG | Address Type Changed HH | Not Valid | Phone - Property is a household | 6-10-03 | F | T | ADDRESS_TYPE_CHANGED_HH,FULFILMENT_REQUESTED,LINKED_QID | ADDRESS_TYPE_CHANGED,FULFILMENT_REQUESTED | ADDRESS_TYPE_CHANGED |  |
      | SPG | Address Type Changed HH | Not Valid | Phone - Property is a household | 6-10-03 | T | T | ADDRESS_TYPE_CHANGED_HH,FULFILMENT_REQUESTED,LINKED_QID | ADDRESS_TYPE_CHANGED,FULFILMENT_REQUESTED,QUESTIONNAIRE_LINKED | ADDRESS_TYPE_CHANGED |  |

      | SPG | Address Type Changed CE | Not Valid | Phone - Property is a CE | 6-10-01 | F | F | ADDRESS_TYPE_CHANGED_CE_EST,FULFILMENT_REQUESTED,LINKED_QID | ADDRESS_TYPE_CHANGED | ADDRESS_TYPE_CHANGED |  |
      | SPG | Address Type Changed CE | Not Valid | Phone - Property is a CE | 6-10-01 | T | F | ADDRESS_TYPE_CHANGED_CE_EST,FULFILMENT_REQUESTED,LINKED_QID | ADDRESS_TYPE_CHANGED,QUESTIONNAIRE_LINKED | ADDRESS_TYPE_CHANGED |  |
      | SPG | Address Type Changed CE | Not Valid | Phone - Property is a CE | 6-10-01 | F | T | ADDRESS_TYPE_CHANGED_CE_EST,FULFILMENT_REQUESTED,LINKED_QID | ADDRESS_TYPE_CHANGED,FULFILMENT_REQUESTED | ADDRESS_TYPE_CHANGED |  |
      | SPG | Address Type Changed CE | Not Valid | Phone - Property is a CE | 6-10-01 | T | T | ADDRESS_TYPE_CHANGED_CE_EST,FULFILMENT_REQUESTED,LINKED_QID | ADDRESS_TYPE_CHANGED,FULFILMENT_REQUESTED,QUESTIONNAIRE_LINKED | ADDRESS_TYPE_CHANGED |  |

      | CE | Address Type Changed HH | Not Valid | Phone - Property is a household | 20-10-01 | F | F | ADDRESS_TYPE_CHANGED_HH,FULFILMENT_REQUESTED,LINKED_QID | ADDRESS_TYPE_CHANGED | ADDRESS_TYPE_CHANGED |  |
      | CE | Address Type Changed HH | Not Valid | Phone - Property is a household | 20-10-01 | T | F | ADDRESS_TYPE_CHANGED_HH,FULFILMENT_REQUESTED,LINKED_QID | ADDRESS_TYPE_CHANGED,QUESTIONNAIRE_LINKED | ADDRESS_TYPE_CHANGED |  |
      | CE | Address Type Changed HH | Not Valid | Phone - Property is a household | 20-10-01 | F | T | ADDRESS_TYPE_CHANGED_HH,FULFILMENT_REQUESTED,LINKED_QID | ADDRESS_TYPE_CHANGED,FULFILMENT_REQUESTED | ADDRESS_TYPE_CHANGED |  |
      | CE | Address Type Changed HH | Not Valid | Phone - Property is a household | 20-10-01 | T | T | ADDRESS_TYPE_CHANGED_HH,FULFILMENT_REQUESTED,LINKED_QID | ADDRESS_TYPE_CHANGED,FULFILMENT_REQUESTED,QUESTIONNAIRE_LINKED | ADDRESS_TYPE_CHANGED |  |

      | CE | Address Type Changed SPG | Not Valid | Phone - Property is a CE | 20-10-03 | F | F | ADDRESS_TYPE_CHANGED_SPG,FULFILMENT_REQUESTED,LINKED_QID | ADDRESS_TYPE_CHANGED | ADDRESS_TYPE_CHANGED |  |
      | CE | Address Type Changed SPG | Not Valid | Phone - Property is a CE | 20-10-03 | T | F | ADDRESS_TYPE_CHANGED_SPG,FULFILMENT_REQUESTED,LINKED_QID | ADDRESS_TYPE_CHANGED,QUESTIONNAIRE_LINKED | ADDRESS_TYPE_CHANGED |  |
      | CE | Address Type Changed SPG | Not Valid | Phone - Property is a CE | 20-10-03 | F | T | ADDRESS_TYPE_CHANGED_SPG,FULFILMENT_REQUESTED,LINKED_QID | ADDRESS_TYPE_CHANGED,FULFILMENT_REQUESTED | ADDRESS_TYPE_CHANGED |  |
      | CE | Address Type Changed SPG | Not Valid | Phone - Property is a CE | 20-10-03 | T | T | ADDRESS_TYPE_CHANGED_SPG,FULFILMENT_REQUESTED,LINKED_QID | ADDRESS_TYPE_CHANGED,FULFILMENT_REQUESTED,QUESTIONNAIRE_LINKED | ADDRESS_TYPE_CHANGED |  |

      | HH | Address Type Changed CE | Not Valid | Phone - Property is a CE | 01-03-06 | F | F | ADDRESS_TYPE_CHANGED_CE_EST | ADDRESS_TYPE_CHANGED | ADDRESS_TYPE_CHANGED |  |
