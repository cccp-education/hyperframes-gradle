Feature: HF-5d — Kinetic-captions template expansion
  As a content creator writing AsciiDoc
  I want the [.hyperframes-kinetic-captions#id] block to expand into a HyperFrames timed-caption composition
  So that I get subtitles that appear and disappear at specified times

  Background:
    Given a hyperframes-gradle plugin is applied to a project

  Scenario: Expand kinetic-captions block into a composition with data-composition-id
    Given the source directory contains an AsciiDoc document using the kinetic-captions template
    When I run the generateHyperframesHtml task
    Then the generated HTML contains a hyperframes-composition div
    And the composition div has data-composition-id "intro-captions"
    And the HTML contains a GSAP timeline
    And the HTML contains window.__timelines

  Scenario: Each caption line becomes a caption div with start and end times
    Given the source directory contains an AsciiDoc document using the kinetic-captions template
    When I run the generateHyperframesHtml task
    Then the generated HTML contains a hf-kinetic-caption with start "0.0" and end "2.0" and text "Hello world"
    And the generated HTML contains a hf-kinetic-caption with start "2.5" and end "4.5" and text "This is a kinetic caption"

  Scenario: Kinetic-captions track has default duration of five seconds
    Given the source directory contains an AsciiDoc document using the kinetic-captions template
    When I run the generateHyperframesHtml task
    Then the generated HTML contains a hyperframes-track block
    And the track has data-start "0"
    And the track has data-duration "5"

  Scenario: Captions are hidden by default and animated via opacity
    Given the source directory contains an AsciiDoc document using the kinetic-captions template
    When I run the generateHyperframesHtml task
    Then the generated HTML contains display none captions
    And the generated HTML contains opacity one for show
    And the generated HTML contains opacity zero for hide