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
    And each message has the correct values
    And it will create the following messages "<JsMessages>" to JobService

   Examples:
   | SurveyType | BusinessFunction | Primary Outcome | Secondary Outcome | Outcome Code | HasLinkedQID | HasFulfilmentRequest | Operation List | RmMessages | JsMessages |

   | CE | Switch Feedback Estab | Irrelavant | Irrelavant | 20-20-08 | F | F | SWITCH_FEEDBACK_CE_EST_F,UPDATE_RESIDENT_COUNT,FULFILMENT_REQUESTED,LINKED_QID | FIELD_CASE_UPDATED | SWITCH_CE_TYPE |
   | CE | Switch Feedback Estab | Irrelavant | Irrelavant | 20-20-08 | T | F | SWITCH_FEEDBACK_CE_EST_F,UPDATE_RESIDENT_COUNT,FULFILMENT_REQUESTED,LINKED_QID | FIELD_CASE_UPDATED,QUESTIONNAIRE_LINKED | SWITCH_CE_TYPE |
   | CE | Switch Feedback Estab | Irrelavant | Irrelavant | 20-20-08 | F | T | SWITCH_FEEDBACK_CE_EST_F,UPDATE_RESIDENT_COUNT,FULFILMENT_REQUESTED,LINKED_QID | FIELD_CASE_UPDATED,FULFILMENT_REQUESTED | SWITCH_CE_TYPE |
   | CE | Switch Feedback Estab | Irrelavant | Irrelavant | 20-20-08 | T | T | SWITCH_FEEDBACK_CE_EST_F,UPDATE_RESIDENT_COUNT,FULFILMENT_REQUESTED,LINKED_QID | FIELD_CASE_UPDATED,FULFILMENT_REQUESTED,QUESTIONNAIRE_LINKED | SWITCH_CE_TYPE |

   | CE | Switch Feedback Unit | Irrelavant | Irrelavant | 24-30-04 | F | F | SWITCH_FEEDBACK_CE_UNIT_F,FULFILMENT_REQUESTED,LINKED_QID |  | SWITCH_CE_TYPE |
   | CE | Switch Feedback Unit | Irrelavant | Irrelavant | 24-30-04 | T | F | SWITCH_FEEDBACK_CE_UNIT_F,FULFILMENT_REQUESTED,LINKED_QID | QUESTIONNAIRE_LINKED | SWITCH_CE_TYPE |
   | CE | Switch Feedback Unit | Irrelavant | Irrelavant | 24-30-04 | F | T | SWITCH_FEEDBACK_CE_UNIT_F,FULFILMENT_REQUESTED,LINKED_QID | FULFILMENT_REQUESTED | SWITCH_CE_TYPE |
   | CE | Switch Feedback Unit | Irrelavant | Irrelavant | 24-30-04 | T | T | SWITCH_FEEDBACK_CE_UNIT_F,FULFILMENT_REQUESTED,LINKED_QID | FULFILMENT_REQUESTED,QUESTIONNAIRE_LINKED | SWITCH_CE_TYPE |
