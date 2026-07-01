@Census @Acceptance @Outcome @SPG @CE
Feature: Outcome Tests

  Scenario Outline: As a Gateway I can receive an outcome from TM and create Census Events
    Given an "<SurveyType>" "<BusinessFunction>" outcome message
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
      | SPG | Not Valid Address | Not Valid | Visit - Unoccupied Site | 6-30-03 | F | F | ADDRESS_NOT_VALID,CANCEL_FEEDBACK,FULFILMENT_REQUESTED,LINKED_QID | ADDRESS_NOT_VALID | CANCEL |
      | SPG | Not Valid Address | Not Valid | Visit - Unoccupied Site | 6-30-03 | T | F | ADDRESS_NOT_VALID,CANCEL_FEEDBACK,FULFILMENT_REQUESTED,LINKED_QID | ADDRESS_NOT_VALID,QUESTIONNAIRE_LINKED | CANCEL |
      | SPG | Not Valid Address | Not Valid | Visit - Unoccupied Site | 6-30-03 | F | T | ADDRESS_NOT_VALID,CANCEL_FEEDBACK,FULFILMENT_REQUESTED,LINKED_QID | ADDRESS_NOT_VALID,FULFILMENT_REQUESTED | CANCEL |
      | SPG | Not Valid Address | Not Valid | Visit - Unoccupied Site | 6-30-03 | T | T | ADDRESS_NOT_VALID,CANCEL_FEEDBACK,FULFILMENT_REQUESTED,LINKED_QID | ADDRESS_NOT_VALID,FULFILMENT_REQUESTED,QUESTIONNAIRE_LINKED | CANCEL |

      | SPG | Hard Refusal | Contact Made | Phone - Hard Refusal | 6-20-04 | F | F |  |  |  |
      | SPG | Hard Refusal | Contact Made | Phone - Hard Refusal | 6-20-04 | T | F |  |  |  |
      | SPG | Hard Refusal | Contact Made | Phone - Hard Refusal | 6-20-04 | F | T |  |  |  |
      | SPG | Hard Refusal | Contact Made | Phone - Hard Refusal | 6-20-04 | T | T |  |  |  |

      | SPG | Extraordinary Refusal | Contact Made | Phone - Extraordinary Refusal | 6-20-05 | F | F | EXTRAORDINARY_REFUSAL_RECEIVED,CANCEL_FEEDBACK,FULFILMENT_REQUESTED,LINKED_QID | REFUSAL_RECEIVED | CANCEL |
      | SPG | Extraordinary Refusal | Contact Made | Phone - Extraordinary Refusal | 6-20-05 | T | F | EXTRAORDINARY_REFUSAL_RECEIVED,CANCEL_FEEDBACK,FULFILMENT_REQUESTED,LINKED_QID | REFUSAL_RECEIVED,QUESTIONNAIRE_LINKED | CANCEL |
      | SPG | Extraordinary Refusal | Contact Made | Phone - Extraordinary Refusal | 6-20-05 | F | T | EXTRAORDINARY_REFUSAL_RECEIVED,CANCEL_FEEDBACK,FULFILMENT_REQUESTED,LINKED_QID | REFUSAL_RECEIVED,FULFILMENT_REQUESTED | CANCEL |
      | SPG | Extraordinary Refusal | Contact Made | Phone - Extraordinary Refusal | 6-20-05 | T | T | EXTRAORDINARY_REFUSAL_RECEIVED,CANCEL_FEEDBACK,FULFILMENT_REQUESTED,LINKED_QID | REFUSAL_RECEIVED,FULFILMENT_REQUESTED,QUESTIONNAIRE_LINKED | CANCEL |

      | SPG | Cancel Feedback | Contact Made | HICL or Paper H Questionnaire delivered | 22-20-05 | F | F | CANCEL_FEEDBACK,FULFILMENT_REQUESTED,LINKED_QID |  | CANCEL |
      | SPG | Cancel Feedback | Contact Made | HICL or Paper H Questionnaire delivered | 22-20-05 | T | F | CANCEL_FEEDBACK,FULFILMENT_REQUESTED,LINKED_QID | QUESTIONNAIRE_LINKED | CANCEL |
      | SPG | Cancel Feedback | Contact Made | HICL or Paper H Questionnaire delivered | 22-20-05 | F | T | CANCEL_FEEDBACK,FULFILMENT_REQUESTED,LINKED_QID | FULFILMENT_REQUESTED | CANCEL |
      | SPG | Cancel Feedback | Contact Made | HICL or Paper H Questionnaire delivered | 22-20-05 | T | T | CANCEL_FEEDBACK,FULFILMENT_REQUESTED,LINKED_QID | FULFILMENT_REQUESTED,QUESTIONNAIRE_LINKED | CANCEL |

      | SPG | Delivered Feedback | Contact Made | HUAC required by text | 7-20-04 | F | F | DELIVERED_FEEDBACK,FULFILMENT_REQUESTED,LINKED_QID |  | UPDATE |
      | SPG | Delivered Feedback | Contact Made | HUAC required by text | 7-20-04 | T | F | DELIVERED_FEEDBACK,FULFILMENT_REQUESTED,LINKED_QID | QUESTIONNAIRE_LINKED | UPDATE |
      | SPG | Delivered Feedback | Contact Made | HUAC required by text | 7-20-04 | F | T | DELIVERED_FEEDBACK,FULFILMENT_REQUESTED,LINKED_QID | FULFILMENT_REQUESTED | UPDATE |
      | SPG | Delivered Feedback | Contact Made | HUAC required by text | 7-20-04 | T | T | DELIVERED_FEEDBACK,FULFILMENT_REQUESTED,LINKED_QID | FULFILMENT_REQUESTED,QUESTIONNAIRE_LINKED | UPDATE |

      | SPG | No Action | Irrelavant | Irrelavant | 6-20-02 | F | F | NO_ACTION,FULFILMENT_REQUESTED,LINKED_QID |  |  |
      | SPG | No Action | Irrelavant | Irrelavant | 6-20-02 | T | F | NO_ACTION,FULFILMENT_REQUESTED,LINKED_QID | QUESTIONNAIRE_LINKED |  |
      | SPG | No Action | Irrelavant | Irrelavant | 6-20-02 | F | T | NO_ACTION,FULFILMENT_REQUESTED,LINKED_QID | FULFILMENT_REQUESTED |  |
      | SPG | No Action | Irrelavant | Irrelavant | 6-20-02 | T | T | NO_ACTION,FULFILMENT_REQUESTED,LINKED_QID | FULFILMENT_REQUESTED,QUESTIONNAIRE_LINKED |  |

      | CE | Not Valid Address | Not Valid | Visit - Unoccupied Site | 20-10-05 | F | F | ADDRESS_NOT_VALID,FULFILMENT_REQUESTED,LINKED_QID | ADDRESS_NOT_VALID |  |
      | CE | Not Valid Address | Not Valid | Visit - Unoccupied Site | 20-10-05 | T | F | ADDRESS_NOT_VALID,FULFILMENT_REQUESTED,LINKED_QID | ADDRESS_NOT_VALID,QUESTIONNAIRE_LINKED |  |
      | CE | Not Valid Address | Not Valid | Visit - Unoccupied Site | 20-10-05 | F | T | ADDRESS_NOT_VALID,FULFILMENT_REQUESTED,LINKED_QID | ADDRESS_NOT_VALID,FULFILMENT_REQUESTED |  |
      | CE | Not Valid Address | Not Valid | Visit - Unoccupied Site | 20-10-05 | T | T | ADDRESS_NOT_VALID,FULFILMENT_REQUESTED,LINKED_QID | ADDRESS_NOT_VALID,FULFILMENT_REQUESTED,QUESTIONNAIRE_LINKED |  |

      | CE | Hard Refusal | Contact Made | Phone - Hard Refusal | 20-20-04 | F | F |  |  |  |
      | CE | Hard Refusal | Contact Made | Phone - Hard Refusal | 20-20-04 | T | F |  |  |  |
      | CE | Hard Refusal | Contact Made | Phone - Hard Refusal | 20-20-04 | F | T |  |  |  |
      | CE | Hard Refusal | Contact Made | Phone - Hard Refusal | 20-20-04 | T | T |  |  |  |

      | CE | Extraordinary Refusal | Contact Made | Phone - Extraordinary Refusal | 20-20-03 | F | F | EXTRAORDINARY_REFUSAL_RECEIVED,CANCEL_FEEDBACK,FULFILMENT_REQUESTED,LINKED_QID | REFUSAL_RECEIVED | CANCEL |
      | CE | Extraordinary Refusal | Contact Made | Phone - Extraordinary Refusal | 20-20-03 | T | F | EXTRAORDINARY_REFUSAL_RECEIVED,CANCEL_FEEDBACK,FULFILMENT_REQUESTED,LINKED_QID | REFUSAL_RECEIVED,QUESTIONNAIRE_LINKED | CANCEL |
      | CE | Extraordinary Refusal | Contact Made | Phone - Extraordinary Refusal | 20-20-03 | F | T | EXTRAORDINARY_REFUSAL_RECEIVED,CANCEL_FEEDBACK,FULFILMENT_REQUESTED,LINKED_QID | REFUSAL_RECEIVED,FULFILMENT_REQUESTED | CANCEL |
      | CE | Extraordinary Refusal | Contact Made | Phone - Extraordinary Refusal | 20-20-03 | T | T | EXTRAORDINARY_REFUSAL_RECEIVED,CANCEL_FEEDBACK,FULFILMENT_REQUESTED,LINKED_QID | REFUSAL_RECEIVED,FULFILMENT_REQUESTED,QUESTIONNAIRE_LINKED | CANCEL |

      | CE | Cancel Feedback | Contact Made | HICL or Paper H Questionnaire delivered | 22-20-05 | F | F | CANCEL_FEEDBACK,FULFILMENT_REQUESTED,LINKED_QID |  | CANCEL |
      | CE | Cancel Feedback | Contact Made | HICL or Paper H Questionnaire delivered | 22-20-05 | T | F | CANCEL_FEEDBACK,FULFILMENT_REQUESTED,LINKED_QID | QUESTIONNAIRE_LINKED | CANCEL |
      | CE | Cancel Feedback | Contact Made | HICL or Paper H Questionnaire delivered | 22-20-05 | F | T | CANCEL_FEEDBACK,FULFILMENT_REQUESTED,LINKED_QID | FULFILMENT_REQUESTED | CANCEL |
      | CE | Cancel Feedback | Contact Made | HICL or Paper H Questionnaire delivered | 22-20-05 | T | T | CANCEL_FEEDBACK,FULFILMENT_REQUESTED,LINKED_QID | FULFILMENT_REQUESTED,QUESTIONNAIRE_LINKED | CANCEL |

      | CE | Delivered Feedback | Contact Made | HUAC required by text | 7-20-04 | F | F | DELIVERED_FEEDBACK,FULFILMENT_REQUESTED,LINKED_QID |  | UPDATE |
      | CE | Delivered Feedback | Contact Made | HUAC required by text | 7-20-04 | T | F | DELIVERED_FEEDBACK,FULFILMENT_REQUESTED,LINKED_QID | QUESTIONNAIRE_LINKED | UPDATE |
      | CE | Delivered Feedback | Contact Made | HUAC required by text | 7-20-04 | F | T | DELIVERED_FEEDBACK,FULFILMENT_REQUESTED,LINKED_QID | FULFILMENT_REQUESTED | UPDATE |
      | CE | Delivered Feedback | Contact Made | HUAC required by text | 7-20-04 | T | T | DELIVERED_FEEDBACK,FULFILMENT_REQUESTED,LINKED_QID | FULFILMENT_REQUESTED,QUESTIONNAIRE_LINKED | UPDATE |

      | CE | Update Resident Count | Contact Made | Visit agreed to deliver | 20-20-01 | F | F | UPDATE_RESIDENT_COUNT,FULFILMENT_REQUESTED,LINKED_QID | FIELD_CASE_UPDATED |  |
      | CE | Update Resident Count | Contact Made | Visit agreed to deliver | 20-20-01 | T | F | UPDATE_RESIDENT_COUNT,FULFILMENT_REQUESTED,LINKED_QID | FIELD_CASE_UPDATED,QUESTIONNAIRE_LINKED |  |
      | CE | Update Resident Count | Contact Made | Visit agreed to deliver | 20-20-01 | F | T | UPDATE_RESIDENT_COUNT,FULFILMENT_REQUESTED,LINKED_QID | FIELD_CASE_UPDATED,FULFILMENT_REQUESTED |  |
      | CE | Update Resident Count | Contact Made | Visit agreed to deliver | 20-20-01 | T | T | UPDATE_RESIDENT_COUNT,FULFILMENT_REQUESTED,LINKED_QID | FIELD_CASE_UPDATED,FULFILMENT_REQUESTED,QUESTIONNAIRE_LINKED |  |

      | CE | No Action | Irrelavant | Irrelavant | 25-30-02 | F | F | NO_ACTION,FULFILMENT_REQUESTED,LINKED_QID |  |  |
      | CE | No Action | Irrelavant | Irrelavant | 25-30-02 | T | F | NO_ACTION,FULFILMENT_REQUESTED,LINKED_QID | QUESTIONNAIRE_LINKED |  |
      | CE | No Action | Irrelavant | Irrelavant | 25-30-02 | F | T | NO_ACTION,FULFILMENT_REQUESTED,LINKED_QID | FULFILMENT_REQUESTED |  |
      | CE | No Action | Irrelavant | Irrelavant | 25-30-02 | T | T | NO_ACTION,FULFILMENT_REQUESTED,LINKED_QID | FULFILMENT_REQUESTED,QUESTIONNAIRE_LINKED |  |

      | HH | Hard Refusal | Contact Made | Phone - Hard Refusal | 01-02-07 | F | F |  |  |  |
      | HH | Extraordinary Refusal | Contact Made | Phone - Extraordinary Refusal | 01-02-08 | F | F | EXTRAORDINARY_REFUSAL_RECEIVED,FEEDBACK_LONG_PAUSE | REFUSAL_RECEIVED | CANCEL |

      | HH | No Action | Irrelavant | Irrelavant | 01-01-04 | F | F | NO_ACTION,LINKED_QID |  |  |
      | HH | No Action | Irrelavant | Irrelavant | 01-01-04 | T | F | NO_ACTION,LINKED_QID | QUESTIONNAIRE_LINKED |  |

      | HH | No Action | Irrelavant | Irrelavant | 01-01-05 | F | F | NO_ACTION,FULFILMENT_REQUESTED |  |  |
      | HH | No Action | Irrelavant | Irrelavant | 01-01-05 | F | T | NO_ACTION,FULFILMENT_REQUESTED | FULFILMENT_REQUESTED |  |

      | HH | Not Valid Address | Not Valid | Visit - Unoccupied Site | 01-02-06 | F | F | ADDRESS_NOT_VALID,FEEDBACK_LONG_PAUSE | ADDRESS_NOT_VALID | CANCEL |
