Feature: HF-5d — Kinetic-captions template expansion
  As a content creator writing AsciiDoc
  I want the [.hyperframes-kinetic-captions#id] block to expand into a HyperFrames timed-caption composition
  So that I get subtitles that appear and disappear at specified times

  Background:
    Given a hyperframes-gradle plugin is applied to a project

  Scenario: Expand kinetic-captions block into a scene clip
    Given the source directory contains an AsciiDoc document using the kinetic-captions template
    When I run the generateHyperframesHtml task
    Then the generated HTML contains a scene clip
    And the scene clip has id "intro-captions"
    And the HTML contains a GSAP timeline
    And the HTML contains window.__timelines

  Scenario: Each caption line becomes a caption div with start and end times
    Given the source directory contains an AsciiDoc document using the kinetic-captions template
    When I run the generateHyperframesHtml task
    Then the generated HTML contains a hf-kinetic-caption at "0.0" until "2.0" and text "Hello world"
    And the generated HTML contains a hf-kinetic-caption at "2.5" until "4.5" and text "This is a kinetic caption"

  Scenario: Kinetic-captions track has default duration of five seconds
    Given the source directory contains an AsciiDoc document using the kinetic-captions template
    When I run the generateHyperframesHtml task
    Then the generated HTML contains a hyperframes-track block
    And the track has data-start "0"
    And the track has data-duration "5"

  Scenario: Captions are hidden by default and animated via opacity
    Given the source directory contains an AsciiDoc document using the kinetic-captions template
    When I run the generateHyperframesHtml task
    Then the generated HTML contains captions hidden by inline opacity
    And the generated HTML contains opacity one for show
    And the generated HTML contains opacity zero for hide