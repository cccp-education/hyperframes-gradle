@quality-media
Feature: HF-DEMO-QUALITY US-3 — DSL audio cues

  As a video author writing AsciiDoc
  I want to declare background music and sound effects in a block
  So that the rendered composition carries the intended sound design

  Scenario: A BGM and an SFX cue become direct audio children
    Given a quality media document with a bgm and an sfx cue
    When I enhance the quality media document with the HyperFrames processor
    Then the quality media composition has 2 audio elements
    And the quality media bgm starts at 0 with volume 0.3
    And the quality media sfx starts at 6
    And the quality media audio block is removed from the scene

  Scenario: Invalid cue lines are skipped
    Given a quality media document with only invalid audio lines
    When I enhance the quality media document with the HyperFrames processor
    Then the quality media composition has 0 audio elements

  Scenario: A document without an audio block emits no audio
    Given a quality media document without an audio block
    When I enhance the quality media document with the HyperFrames processor
    Then the quality media composition has 0 audio elements
