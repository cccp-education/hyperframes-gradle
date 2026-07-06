Feature: HF-5b — Code-diff template expansion
  As a formateur writing AsciiDoc
  I want the [.hyperframes-code-diff#id] block to expand into a HyperFrames code-diff composition
  So that I get before/after code blocks with syntax highlighting and a fade animation

  Background:
    Given a hyperframes-gradle plugin is applied to a project

  Scenario: Expand code-diff block into a composition with data-composition-id
    Given the source directory contains an AsciiDoc document using the code-diff template
    When I run the generateHyperframesHtml task
    Then the generated HTML contains a hyperframes-composition div
    And the composition div has data-composition-id "refactor-demo"
    And the HTML contains a GSAP timeline
    And the HTML contains window.__timelines

  Scenario: Before and after code are rendered inside distinct pre blocks
    Given the source directory contains an AsciiDoc document using the code-diff template
    When I run the generateHyperframesHtml task
    Then the generated HTML contains a hf-code-diff-before pre block
    And the generated HTML contains a hf-code-diff-after pre block
    And the generated HTML contains the before code "oldFunc"
    And the generated HTML contains the after code "newFunc"

  Scenario: Code-diff track has default duration of three seconds
    Given the source directory contains an AsciiDoc document using the code-diff template
    When I run the generateHyperframesHtml task
    Then the generated HTML contains a hyperframes-track block
    And the track has data-start "0"
    And the track has data-duration "3"

  Scenario: Kotlin keywords are highlighted in both before and after blocks
    Given the source directory contains an AsciiDoc document using the code-diff template
    When I run the generateHyperframesHtml task
    Then the generated HTML contains a keyword span "val"
    And the before and after pre blocks both carry the lang class "kotlin"