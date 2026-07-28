@Census @Acceptance @Outcome @NC
Feature: Outcome Hard Refusal Tests

  Scenario Outline: As a Gateway I can receive a hard refusal outcome from TM and create Census Events
    Given a parent case exists for "<SurveyType>"
    And an "<SurveyType>" "<BusinessFunction>" outcome message
    And its Primary Outcome is "<Primary Outcome>"
    And its secondary Outcome "<Secondary Outcome>"
    And its Outcome code is "<Outcome Code>"
    And the message includes a Linked QID "<HasLinkedQID>"
    And the message includes a Fulfillment Request "<HasFulfilmentRequest>"
    When Gateway receives the outcome
    Then It will run the following processors "<Operation List>"
    And create the following messages to RM "<RmMessages>"
    And it will create the following messages "<JsMessages>" to JobService

    Examples:
      | SurveyType | BusinessFunction | Primary Outcome | Secondary Outcome | Outcome Code | HasLinkedQID | HasFulfilmentRequest | Operation List | RmMessages | JsMessages |
      | HH | Hard Refusal | Contact Made | Visit - Hard refusal | 21-20-14 | T | F | HARD_REFUSAL_RECEIVED,CANCEL_FEEDBACK,FULFILMENT_REQUESTED,LINKED_QID | REFUSAL_RECEIVED,QUESTIONNAIRE_LINKED | CANCEL |
      | HH | Hard Refusal | Contact Made | Phone - Hard Refusal | 01-02-07 | F | F | HARD_REFUSAL_RECEIVED,FEEDBACK_LONG_PAUSE | REFUSAL_RECEIVED | CANCEL |

      | CE | Hard Refusal | Contact Made | Visit - Hard refusal | 01-02-07 | F | F | HARD_REFUSAL_RECEIVED,FEEDBACK_LONG_PAUSE | REFUSAL_RECEIVED |  |
      | CE | Hard Refusal | Contact Made | Phone - Hard Refusal | 20-20-04 | F | F | HARD_REFUSAL_RECEIVED,CANCEL_FEEDBACK,FULFILMENT_REQUESTED,LINKED_QID | REFUSAL_RECEIVED | CANCEL |
      | CE | Hard Refusal | Contact Made | Phone - Hard Refusal | 20-20-04 | T | F | HARD_REFUSAL_RECEIVED,CANCEL_FEEDBACK,FULFILMENT_REQUESTED,LINKED_QID | REFUSAL_RECEIVED,QUESTIONNAIRE_LINKED | CANCEL |
      | CE | Hard Refusal | Contact Made | Phone - Hard Refusal | 20-20-04 | F | T | HARD_REFUSAL_RECEIVED,CANCEL_FEEDBACK,FULFILMENT_REQUESTED,LINKED_QID | REFUSAL_RECEIVED,FULFILMENT_REQUESTED | CANCEL |
      | CE | Hard Refusal | Contact Made | Phone - Hard Refusal | 20-20-04 | T | T | HARD_REFUSAL_RECEIVED,CANCEL_FEEDBACK,FULFILMENT_REQUESTED,LINKED_QID | REFUSAL_RECEIVED,FULFILMENT_REQUESTED,QUESTIONNAIRE_LINKED | CANCEL |

      | SPG | Hard Refusal | Contact Made | Phone - Hard Refusal | 6-20-04 | F | F | HARD_REFUSAL_RECEIVED,CANCEL_FEEDBACK,FULFILMENT_REQUESTED,LINKED_QID | REFUSAL_RECEIVED | CANCEL |
      | SPG | Hard Refusal | Contact Made | Phone - Hard Refusal | 6-20-04 | T | F | HARD_REFUSAL_RECEIVED,CANCEL_FEEDBACK,FULFILMENT_REQUESTED,LINKED_QID | REFUSAL_RECEIVED,QUESTIONNAIRE_LINKED | CANCEL |
      | SPG | Hard Refusal | Contact Made | Phone - Hard Refusal | 6-20-04 | F | T | HARD_REFUSAL_RECEIVED,CANCEL_FEEDBACK,FULFILMENT_REQUESTED,LINKED_QID | REFUSAL_RECEIVED,FULFILMENT_REQUESTED | CANCEL |
      | SPG | Hard Refusal | Contact Made | Phone - Hard Refusal | 6-20-04 | T | T | HARD_REFUSAL_RECEIVED,CANCEL_FEEDBACK,FULFILMENT_REQUESTED,LINKED_QID | REFUSAL_RECEIVED,FULFILMENT_REQUESTED,QUESTIONNAIRE_LINKED | CANCEL |

      | CE | Address Type Changed CE | Not Valid | Phone - Property is a CE | 01-03-07 | F | F | ADDRESS_NOT_VALID,HARD_REFUSAL_RECEIVED | ADDRESS_NOT_VALID,REFUSAL_RECEIVED |  |

  Scenario Outline: As a Gateway I can receive a hard refusal outcome from TM and create Census Events
    Given a parent case exists for "HH"
    And an NC create case already exists
    And an "<SurveyType>" "<BusinessFunction>" outcome message
    And its Primary Outcome is "<Primary Outcome>"
    And its secondary Outcome "<Secondary Outcome>"
    And its Outcome code is "<Outcome Code>"
    And the message includes a Linked QID "<HasLinkedQID>"
    And the message includes a Fulfillment Request "<HasFulfilmentRequest>"
    When Gateway receives the outcome
    Then It will run the following processors "<Operation List>"
    And create the following messages to RM "<RmMessages>"
    And it will create the following messages "<JsMessages>" to JobService

    Examples:
      | SurveyType | BusinessFunction | Primary Outcome | Secondary Outcome | Outcome Code | HasLinkedQID | HasFulfilmentRequest | Operation List | RmMessages | JsMessages |
      | NC | No Action | Irrelevant | Irrelevant | 04-01-01 | F | F | NO_ACTION |  |  |
      | NC | No Action | Irrelevant | Irrelevant | 04-01-02 | F | F | NO_ACTION |  |  |
      | NC | Cancel Feedback | Contact Made | Cancel Feedback | 04-01-03 | F | F | CANCEL_FEEDBACK |  | CANCEL |
      | NC | Cancel Feedback | Contact Made | Cancel Feedback | 04-01-04 | F | F | CANCEL_FEEDBACK |  | CANCEL |
      | NC | Cancel Feedback | Contact Made | Linked Qid | 04-02-01 | T | F | CANCEL_FEEDBACK,LINKED_QID | QUESTIONNAIRE_LINKED | CANCEL |
      | NC | Cancel Feedback | Contact Made | Linked Qid | 04-02-02 | T | F | CANCEL_FEEDBACK,LINKED_QID | QUESTIONNAIRE_LINKED | CANCEL |
      | NC | No Action | Irrelevant | Irrelevant | 04-02-03 | F | F | NO_ACTION |  |  |
      | NC | No Action | Irrelevant | Irrelevant | 04-02-06 | F | F | NO_ACTION |  |  |
      | NC | No Action | Irrelevant | Irrelevant | 04-02-07 | F | F | NO_ACTION |  |  |
      | NC | Cancel Feedback | Contact Made | Cancel Feedback | 04-02-08 | F | F | CANCEL_FEEDBACK |  | CANCEL |
      | NC | Cancel Feedback | Contact Made | Cancel Feedback | 04-02-10 | F | F | CANCEL_FEEDBACK |  | CANCEL |
      | NC | No Action | Irrelevant | Irrelevant | 04-03-01 | F | F | NO_ACTION |  |  |
