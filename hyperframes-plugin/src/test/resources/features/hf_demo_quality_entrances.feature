@quality-entrances
Feature: HF-DEMO-QUALITY US-5 — choreographed scene entrances

  As a video author writing AsciiDoc
  I want each scene's content atoms to enter in sequence
  So that the composition avoids grouped appearances and dead stretches

  Scenario: Entrances are opt-in
    Given a quality entrance document with two scenes
    When I enhance the quality entrance document without entrances
    Then the quality entrance keeps the grouped scene animation
    And the quality entrance declares no load-time setup

  Scenario: Enabled entrances setup at load and reveal with a stagger
    Given a quality entrance document with two scenes
    When I enhance the quality entrance document with entrances enabled
    Then the quality entrance hides the atoms at load
    And the quality entrance staggers the atoms by "0.12" seconds
    And the quality entrance scopes the atoms to each scene
    And the quality entrance replaces the grouped scene animation

  Scenario: The first atom starts after the configured offset
    Given a quality entrance document with two scenes
    When I enhance the quality entrance document with a "0.25" second offset
    Then the quality entrance starts the first atom at "0.25" seconds
