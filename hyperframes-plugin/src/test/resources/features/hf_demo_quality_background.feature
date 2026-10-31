@quality-background
Feature: HF-DEMO-QUALITY US-6 — per-scene background media

  As a video author writing AsciiDoc
  I want to declare a background image per scene
  So that the composition carries an ambient visual layer with legibility overlays

  Scenario: A background block becomes a full-bleed media layer with defaults
    Given a quality background document with an image
    When I enhance the quality background document with the HyperFrames processor
    Then the quality background composition has a media layer at opacity 0.55
    And the quality background layer carries the scrim
    And the quality background layer has no grid and no grain
    And the quality background listing block is consumed

  Scenario: The options drive the media opacity and the overlays
    Given a quality background document with an image and custom options
    When I enhance the quality background document with the HyperFrames processor
    Then the quality background composition has a media layer at opacity 0.35
    And the quality background layer has no scrim
    And the quality background layer carries the grid and the grain

  Scenario: A scene without a background keeps the static background
    Given a quality background document without a background block
    When I enhance the quality background document with the HyperFrames processor
    Then the quality background scene keeps the static background

  Scenario: An invalid background block is ignored
    Given a quality background document with an invalid background block
    When I enhance the quality background document with the HyperFrames processor
    Then the quality background scene keeps the static background
    And the quality background listing block is consumed
