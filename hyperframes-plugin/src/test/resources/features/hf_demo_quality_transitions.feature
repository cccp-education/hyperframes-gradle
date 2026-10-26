@quality-transition
Feature: HF-DEMO-QUALITY US-2 — DSL scene transitions

  As a video author writing AsciiDoc
  I want to declare a scene transition with a role
  So that the composition hands off between scenes with a designed motion

  Scenario: A grid transition declared on the outgoing scene emits a cover overlay
    Given a quality transition document whose first scene declares "grid"
    When I enhance the quality transition document with the HyperFrames processor
    Then the quality transition overlay is "grid"
    And the quality transition grid has 12 cells
    And the quality transition root timeline is registered

  Scenario: A shutter transition emits its two halves
    Given a quality transition document whose first scene declares "shutter"
    When I enhance the quality transition document with the HyperFrames processor
    Then the quality transition overlay is "shutter"
    And the quality transition root timeline is registered

  Scenario: A fade transition emits a colour dip
    Given a quality transition document whose first scene declares "fade"
    When I enhance the quality transition document with the HyperFrames processor
    Then the quality transition overlay is "fade"
    And the quality transition root timeline is registered

  Scenario: A transitioned scene does not fade before its boundary
    Given a quality transition document whose first scene declares "grid"
    When I enhance the quality transition document with the HyperFrames processor
    Then the quality transitioned scene keeps its content until the boundary

  Scenario: No transition keeps the historical composition
    Given a quality transition document without any transition
    When I enhance the quality transition document with the HyperFrames processor
    Then the quality transition overlay is absent
    And the quality transition keeps the historical root timeline

  Scenario: A blur-crossfade overlaps the two scenes
    Given a quality transition document whose first scene declares "blur-crossfade"
    When I enhance the quality transition document with the HyperFrames processor
    Then the quality transition overlay is absent
    And the quality overlap starts at "9.40" seconds
    And the quality transition blur is declared
    And the quality transition root timeline is registered

  Scenario: A vertical-push overlaps the two scenes
    Given a quality transition document whose first scene declares "vertical-push"
    When I enhance the quality transition document with the HyperFrames processor
    Then the quality transition overlay is absent
    And the quality overlap starts at "9.40" seconds
    And the quality transition push is declared
    And the quality transition root timeline is registered
