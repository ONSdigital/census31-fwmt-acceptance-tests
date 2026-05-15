@Census @Acceptance @Inbound @SPG
Feature: SPG Update Tests

 Scenario Outline: As Gateway I can receive an update to a job requests from RM for an existing job
   Given a TM doesnt have a job with case ID "bd6345af-d706-43d3-a13b-8c549e081a76" in TM
   And RM sends a create job request with "<CaseRef>" "<Survey>" "<Type>" "<IsSecure>" "<HandDeliver>"
   And RM sends an update case request for the case
   When Gateway receives an update message for the case
   Then it will update the job in TM
   And the updated job is acknowledged by TM
  Examples:
     | Survey | Type     | IsSecure  | CaseRef  | HandDeliver |
#      Not sure if SPG Estab is required as there are no specs on confluence. TBC
#     | SPG CE | Estab    |  F        | 12345678 | F           |
     | SPG CE | Unit     |  F        | 12345678 | F           |
     | SPG CE | Unit     |  F        | 12345678 | T           |
     | CE     | CE Est   |  F        | 12345678 | T           |
     | CE     | CE Est   |  F        | 12345678 | F           |
     | CE     | CE Unit  |  F        | 12345678 | T           |
     | CE     | CE Unit  |  F        | 12345678 | F           |
     | CE     | CE Unit  |  F        | 12345678 | F           |



 Scenario Outline: As Gateway I can receive a create CE Site job request from RM after a CE Estab has been processed
   Given a TM doesnt have a job with case ID "bd6345af-d706-43d3-a13b-8c549e081a76" in TM
   And RM sends a create job request with "12345678" "CE" "CE Est" "F" "T"
   And RM sends a create CE Site job request with "<CaseRef>" "<Survey>" "<Type>" "<IsSecure>" "<HandDeliver>"
   When the Gateway sends a Create Job message to TM
   And RM sends an update case request for the case
   When Gateway receives an update message for the case
   Then it will update the job in TM
   And the updated job is acknowledged by TM
   Examples:
     |Survey | Type     | IsSecure | CaseRef  | HandDeliver |
     |CE     | CE Site  | F        | 12345678 | F           |
     |CE     | CE Site  | T        | 12345678 | F           |


 Scenario: As Gateway I will hold a Unit Update request from RM for a job that does not exist when undeliveredAsAddress is false
   Given RM sends a unit update case request where undeliveredAsAddress is "false"
   Then the update job should be held


 Scenario: As Gateway I can receive an update job request for SPG Unit for an unexisting job and is set to undeliveredAsAddress. gateway will process as a Create message
   Given RM sends a unit update case request where undeliveredAsAddress is "true"
   When Gateway receives an update message for the case
   Then Gateway will reroute it as a create message
   And Gateway will send a create job to TM
   And the create job is acknowledged by tm

 Scenario Outline: As Gateway I can receive an update to a request from RM for an existing Household job
   Given a TM doesnt have a job with case ID "bd6345af-d706-43d3-a13b-8c549e081a76" in TM
   And RM sends a HH create job request with "<CaseRef>" "<Survey>" "<oa>"  
   And RM sends a HH update case request for the case "<isBlankFormReturned>" "<isUndeliveredAsAddress>"
   When Gateway receives an update message for the case
		And is Processed as "<ProcessedAs>" 
   Then it will update the job in TM
   And the updated job is acknowledged by TM
   And an associated a Pause is deleted "<IsPauseDeleted>"
  Examples:
     | Survey | oa          |  CaseRef  | isBlankFormReturned | isUndeliveredAsAddress | ProcessedAs | IsPauseDeleted |
     | HH     | E00167164   |  12345678 | F                   | F                      | HH E & W    | F              |
     | HH     | E00167164   |  12345678 | T                   | F                      | HH E & W    | T              |
     | HH     | E00167164   |  12345678 | F                   | T                      | HH E & W    | T              |
     | HH     | E00167164   |  12345678 | T                   | T                      | HH E & W    | T              |
     | HH     | N00167164   |  12345678 | F                   | F                      | NISRA       | F              |
     | HH     | N00167164   |  12345678 | T                   | F                      | NISRA       | T              |
     | HH     | N00167164   |  12345678 | F                   | T                      | NISRA       | T              |
     | HH     | N00167164   |  12345678 | T                   | T                      | NISRA       | T              |


  Scenario: As Gateway I can receive an Pause Case to a request from RM for an existing Household job
    Given a TM doesnt have a job with case ID "bd6345af-d706-43d3-a13b-8c549e081a76" in TM
    And RM sends a HH create job request 
    And RM sends a HH Pause Case request for the case
    When Gateway receives an HH Pause Case message for the case
		And is Processed as "HH Pause Case"
    Then it will Pause the job in TM
    And the Paused job is acknowledged by TM
  