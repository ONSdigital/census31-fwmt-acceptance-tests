@Census @Acceptance @Inbound @Create
Feature: Create Tests

  Scenario Outline: As Gateway I can receive a create job requests from RM
    Given a TM doesnt have a job with case ID "bd6345af-d706-43d3-a13b-8c549e081a76" in TM
    And RM sends a create job request with "<CaseRef>" "<Survey>" "<Type>" "<IsSecure>" "<HandDeliver>"
    When the Gateway sends a Create Job message to TM
    Then a new case is created of the right "<SurveyType>"
    And the right caseRef "<TmCaseRef>"
    And a new case with id of "bd6345af-d706-43d3-a13b-8c549e081a76" is created in TM
    Examples:
      | MessageTypeLabel            | Survey | Type    | IsSecure | CaseRef  | HandDeliver | SurveyType | TmCaseRef      |
      | SPG Site                    | SPG CE | Estab   | F        | 12345678 | F           | SPG Site   | 12345678       |
      | SPG Site (Secure)           | SPG CE | Estab   | T        | 12345678 | F           | SPG Site   | SECSS_12345678 |
      | SPG Unit Deliver            | SPG CE | Unit    | F        | 12345678 | T           | SPG Unit-D | 12345678       |
      | SPG Unit Follow-up          | SPG CE | Unit    | F        | 12345678 | F           | SPG Unit-F | 12345678       |
      | SPG Unit Follow-up (Secure) | SPG CE | Unit    | T        | 12345678 | F           | SPG Unit-F | SECSU_12345678 |
      | CE Est Deliver     	        | CE     | CE Est  | F        | 12345678 | T           | CE Est-D   | 12345678       |
      | CE Est Deliver (Secure)     | CE     | CE Est  | T        | 12345678 | T           | CE Est-D   | SECCE_12345678 |
      | CE Est Follow-up            | CE     | CE Est  | F        | 12345678 | F           | CE Est-F   | 12345678       |
      | CE Est Follow-up (Secure)   | CE     | CE Est  | T        | 12345678 | F           | CE Est-F   | SECCE_12345678 |
      | CE Unit Deliver             | CE     | CE Unit | F        | 12345678 | T           | CE Unit-D  | 12345678       |
      | CE Unit Deliver (Secure)    | CE     | CE Unit | T        | 12345678 | T           | CE Unit-D  | SECCU_12345678 |
      | CE Unit Follow-up           | CE     | CE Unit | F        | 12345678 | F           | CE Unit-F  | 12345678       |
      | CE Unit Follow-up (Secure)  | CE     | CE Unit | T        | 12345678 | F           | CE Unit-F  | SECCU_12345678 |
      | Household England and Wales | HH     | E&W     | F        | 12345678 | F           | HH         | 12345678       |
      | Household Nisra             | HH     | NISRA   | F        | 12345678 | F           | HH         | 12345678       |

  Scenario Outline: As Gateway I can receive a create CE Site job request from RM when a matching CE Unit exists in cache
    Given a TM doesnt have a job with case ID "f78607a6-bab4-11ea-b3de-0242ac130004" in TM
    And a CE Unit with estabUprn "6123456" exists in cache
    And RM sends a create CE Site job request with "<CaseRef>" "<Survey>" "<Type>" "<IsSecure>" "<HandDeliver>"
    When the Gateway sends a Create Job message to TM
    Then a new case is created of the right "<SurveyType>"
    And the right caseRef "<TmCaseRef>"
    And a new case with id of "f78607a6-bab4-11ea-b3de-0242ac130004" is created in TM
    Examples:
      | Survey | Type    | IsSecure | CaseRef  | HandDeliver | SurveyType | TmCaseRef      |
      | CE     | CE Site | F        | 12345678 | F           | CE Site    | 12345678       |
      | CE     | CE Site | T        | 12345678 | F           | CE Site    | SECCS_12345678 |

  Scenario Outline: As Gateway I can switch a CE survey type that has a matching estabUprn and address type
    Given a TM doesnt have a job with case ID "bd6345af-d706-43d3-a13b-8c549e081a76" in TM
    And RM sends a create CE Est job request with uprn matching estabUPRN with "12345678" "CE" "CE Est" "F" "T"
    And RM sends a create CE Unit with the same estabUPRN as the above CE Est request with "<CaseRef>" "<Survey>" "<Type>" "<IsSecure>" "<HandDeliver>"
    Then the existing case is updated to a switch and put back on the queue with caseId "bd6345af-d706-43d3-a13b-8c549e081a76"
    Then the related case will be closed with case ID "bd6345af-d706-43d3-a13b-8c549e081a76"
    And then reopened with the new SurveyType "<SurveyType>" and case ID "bd6345af-d706-43d3-a13b-8c549e081a76"
    Examples:
      | Survey | Type    | IsSecure | HandDeliver | CaseRef  | SurveyType |
      | CE     | CE Unit | F        | T           | 12345678 | CE Site    |
      | CE     | CE Unit | F        | F           | 12345678 | CE Site    |
