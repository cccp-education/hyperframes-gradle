Feature: HF-5c — Data-chart template expansion
  As a content creator writing AsciiDoc
  I want the [.hyperframes-data-chart#id] block to expand into a HyperFrames bar-chart composition
  So that I get animated bars that rise from zero with proportional heights

  Background:
    Given a hyperframes-gradle plugin is applied to a project

  Scenario: Expand data-chart block into a composition with data-composition-id
    Given the source directory contains an AsciiDoc document using the data-chart template
    When I run the generateHyperframesHtml task
    Then the generated HTML contains a hyperframes-composition div
    And the composition div has data-composition-id "sales-chart"
    And the HTML contains a GSAP timeline
    And the HTML contains window.__timelines

  Scenario: Each data point becomes a bar with label and value
    Given the source directory contains an AsciiDoc document using the data-chart template
    When I run the generateHyperframesHtml task
    Then the generated HTML contains a hf-data-chart-bar with label "Q1" and value "30.0"
    And the generated HTML contains a hf-data-chart-bar with label "Q2" and value "50.0"

  Scenario: Data-chart track has default duration of four seconds
    Given the source directory contains an AsciiDoc document using the data-chart template
    When I run the generateHyperframesHtml task
    Then the generated HTML contains a hyperframes-track block
    And the track has data-start "0"
    And the track has data-duration "4"

  Scenario: Bars are animated with a staggered GSAP timeline
    Given the source directory contains an AsciiDoc document using the data-chart template
    When I run the generateHyperframesHtml task
    Then the generated HTML contains a stagger animation
    And the generated HTML contains a scaleY zero start