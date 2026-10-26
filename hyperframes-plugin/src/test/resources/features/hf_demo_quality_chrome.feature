@quality-chrome
Feature: HF-DEMO-QUALITY US-4 — studio chrome and typography

  As a video author writing AsciiDoc
  I want a configurable studio chrome and display typography
  So that the generated composition reaches the reference demo quality

  Scenario: Enabled chrome carries a per-scene timecode, marks and a progress bar
    Given a quality chrome document with two scenes
    When I enhance the quality chrome document with a label "LAB" and chrome enabled
    Then the quality chrome has one chrome block per scene
    And the quality chrome first scene timecode is "00:00:00:00"
    And the quality chrome second scene timecode is "00:00:05:00"
    And the quality chrome renders four corner marks per scene
    And the quality chrome renders a global progress bar
    And the quality chrome root timeline drives the progress bar

  Scenario: Chrome stays opt-in
    Given a quality chrome document with two scenes
    When I enhance the quality chrome document without chrome
    Then the quality chrome is absent
    And the quality chrome keeps the historical root timeline

  Scenario: Typography is configurable
    Given a quality chrome document with two scenes
    When I enhance the quality chrome document with display font "League Gothic, Impact, sans-serif" and mono font "IBM Plex Mono, monospace"
    Then the quality chrome display font variable is "League Gothic, Impact, sans-serif"
    And the quality chrome mono font variable is "IBM Plex Mono, monospace"
    And the quality chrome headings use the display font

  Scenario: Typography declares local font faces for lint
    Given a quality chrome document with two scenes
    When I enhance the quality chrome document with display font "League Gothic, Impact, sans-serif" and mono font "IBM Plex Mono, monospace"
    Then the quality chrome declares a local font face for "League Gothic"
    And the quality chrome declares a local font face for "Impact"
    And the quality chrome declares a local font face for "IBM Plex Mono"
    And the quality chrome does not declare a font face for "sans-serif"

  Scenario: The progress bar can be disabled
    Given a quality chrome document with two scenes
    When I enhance the quality chrome document with chrome enabled and the progress bar disabled
    Then the quality chrome renders no progress bar
    And the quality chrome keeps the historical root timeline
