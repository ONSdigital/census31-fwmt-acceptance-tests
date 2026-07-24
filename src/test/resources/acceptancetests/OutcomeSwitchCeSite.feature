@Census @Acceptance @Outcome @SPG @CE
Feature: Outcome Tests

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
    And the caseId of the "<InitialOperation>" message will be a new caseId
    And every other message will use the new caseId as its caseId
    And each message has the correct values
    And it will create the following messages "<JsMessages>" to JobService

   Examples:
   | SurveyType | BusinessFunction         | Primary Outcome | Secondary Outcome | Outcome Code | HasLinkedQID | HasFulfilmentRequest | Operation List                                                                                 | RmMessages                                                       | InitialOperation      |  JsMessages     |
   | CE         | Switch Feedback Site      | Irrelevant     | Irrelevant        | 23-20-01     | F            | F                    | SWITCH_FEEDBACK_CE_SITE,UPDATE_RESIDENT_COUNT_0,NEW_UNIT_ADDRESS,FULFILMENT_REQUESTED,LINKED_QID | NEW_ADDRESS_REPORTED,FIELD_CASE_UPDATED                                               | NEW_ADDRESS_REPORTED |SWITCH_CE_TYPE |
   | CE         | Switch Feedback Site      | Irrelevant     | Irrelevant        | 23-20-01     | T            | F                    | SWITCH_FEEDBACK_CE_SITE,UPDATE_RESIDENT_COUNT_0,NEW_UNIT_ADDRESS,FULFILMENT_REQUESTED,LINKED_QID | NEW_ADDRESS_REPORTED,QUESTIONNAIRE_LINKED,FIELD_CASE_UPDATED                          | NEW_ADDRESS_REPORTED | SWITCH_CE_TYPE |
   | CE         | Switch Feedback Site      | Irrelevant     | Irrelevant        | 23-20-01     | F            | T                    | SWITCH_FEEDBACK_CE_SITE,UPDATE_RESIDENT_COUNT_0,NEW_UNIT_ADDRESS,FULFILMENT_REQUESTED,LINKED_QID | NEW_ADDRESS_REPORTED,FULFILMENT_REQUESTED,FIELD_CASE_UPDATED                          | NEW_ADDRESS_REPORTED | SWITCH_CE_TYPE |
   | CE         | Switch Feedback Site      | Irrelevant     | Irrelevant        | 23-20-01     | T            | T                    | SWITCH_FEEDBACK_CE_SITE,UPDATE_RESIDENT_COUNT_0,NEW_UNIT_ADDRESS,FULFILMENT_REQUESTED,LINKED_QID | NEW_ADDRESS_REPORTED,FULFILMENT_REQUESTED,QUESTIONNAIRE_LINKED,FIELD_CASE_UPDATED     | NEW_ADDRESS_REPORTED | SWITCH_CE_TYPE |
