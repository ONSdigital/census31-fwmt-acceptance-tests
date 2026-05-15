@Census @Acceptance @Inbound @Resilience
Feature: Resilience Tests

    Scenario Outline: As gateway I can process messages and store them based on their order
    Given that gateway has a message stored of type "<Database Stored Message>" with case ID "bd6345af-d706-43d3-a13b-8c549e081a76"
    And RM sends a "<Instruction>" with the same case ID that is "<Message Age>" than whats in cache
    Then the gateway will "<Gateway Action>" the message
    Then this message will have the "<Action Gateway Cache>" in the message cache and "<Action Message Cache>" in the gateway cache
    Examples:
      | Database Stored Message | Instruction  | Action Gateway Cache | Action Message Cache | Message Age | Gateway Action |
      | Empty                   | Create       | Create               |                      | Newer       | Process        |
      | Empty                   | Update       | Update(held)         | Update(held)         | Newer       | No Action      |
      | Empty                   | Cancel       | Cancel(held)         | Cancel(held)         | Newer       | No Action      |
      | Empty                   | CE Switch    | Update(held)         | Update(held)         | Newer       | No Action      |
      | Empty                   | CE Switch    | Update(held)         | Update(held)         | Older       | No Action      |
      | Create                  | CE Switch    | Create               |                      | Newer       | Process        |
      | Create                  | CE Switch    | Create               |                      | Older       | Process        |
      | Create                  | Update       | Update               |                      | Newer       | Process        |
      | Create                  | Update       | Create               |                      | Older       | Reject         |
      | Create                  | Cancel       | Cancel               |                      | Newer       | Process        |
      | Create                  | Cancel       | Create               |                      | Older       | Reject         |
      | Update                  | Update       | Update               |                      | Newer       | Process        |
      | Update                  | Update       | Update               |                      | Older       | Reject         |
      | Update                  | Cancel       | Cancel               |                      | Newer       | Process        |
      | Update                  | Cancel       | Update               |                      | Older       | No Action      |
      | Update                  | CE Switch    | Update               |                      | Newer       | Process        |
      | Update                  | CE Switch    | Update               |                      | Older       | Process        |
      | Update(Held)            | Create       | Update(held)         |                      | Newer       | Reject         |
      | Update(Held)            | Create       | Update               |                      | Older       | Merge          |
      | Update(Held)            | Update       | Update(held)         | Update(held)         | Newer       | No Action      |
      | Update(Held)            | Update       | Update(held)         | Update(held)         | Older       | No Action      |
      | Update(Held)            | Cancel       | Cancel(held)         | Cancel(held)         | Newer       | No Action      |
      | Update(Held)            | Cancel       | Update(held)         | Update(held)         | Older       | No Action      |
      | Update(Held)            | CE Switch    | Update(held)         | Update(held)         | Newer       | Reject         |
      | Update(Held)            | CE Switch    | Update(held)         | Update(held)         | Older       | Reject         |
      | Cancel                  | Update       | Update               |                      | Newer       | Process        |
      | Cancel                  | Update       | Update               |                      | Older       | Process        |
      | Cancel                  | Cancel       | Cancel               |                      | Newer       | Process        |
      | Cancel                  | Cancel       | Cancel               |                      | Older       | No Action      |
      | Cancel                  | CE Switch    | Cancel               |                      | Newer       | No Action      |
      | Cancel                  | CE Switch    | Cancel               |                      | Older       | No Action      |
      | Cancel(Held)            | Create       | Cancel(held)         |                      | Newer       | No Action      |
      | Cancel(Held)            | Create       | Cancel(held)         |                      | Older       | No Action      |
      | Cancel(Held)            | Update       | Update(held)         | Update(held)         | Newer       | No Action      |
      | Cancel(Held)            | Update       | Cancel(held)         | Cancel(held)         | Older       | No Action      |
      | Cancel(Held)            | Cancel       | Cancel(held)         | Cancel(held)         | Newer       | Reject         |
      | Cancel(Held)            | Cancel       | Cancel(held)         | Cancel(held)         | Older       | No Action      |
      | Cancel(Held)            | CE Switch    | Cancel(held)         | Cancel(held)         | Newer       | Reject         |
      | Cancel(Held)            | CE Switch    | Cancel(held)         | Cancel(held)         | Older       | Reject         |

  Scenario: As gateway I cannot process create messages once they are already present in the cache
    Given that gateway has a message stored of type "Create" with case ID "bd6345af-d706-43d3-a13b-8c549e081a76"
    And RM sends a "Create" with the same case ID that is "Newer" than whats in cache
    Then the gateway will not process the stored message the message
