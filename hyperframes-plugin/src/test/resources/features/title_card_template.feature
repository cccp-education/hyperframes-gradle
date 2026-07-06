Feature: HF-5a — Title-card template expansion
  As a formateur writing AsciiDoc
  I want the [.hyperframes-title-card#id] block to expand into a HyperFrames title-card composition
  So that I get fade-in animation, title and subtitle without writing HTML

  Background:
    Given a hyperframes-gradle plugin is applied to a project

  Scenario: Expand title-card block into a composition with data-composition-id
    Given the source directory contains an AsciiDoc document using the title-card template
    When I run the generateHyperframesHtml task
    Then the generated HTML contains a hyperframes-composition div
    And the composition div has data-composition-id "intro"
    And the HTML contains a GSAP timeline
    And the HTML contains window.__timelines

  Scenario: Title and subtitle are rendered inside the title-card composition
    Given the source directory contains an AsciiDoc document using the title-card template
    When I run the generateHyperframesHtml task
    Then the generated HTML contains the title "My Formation"
    And the generated HTML contains the subtitle "Module 01"

  Scenario: Title-card track has default duration of two seconds
    Given the source directory contains an AsciiDoc document using the title-card template
    When I run the generateHyperframesHtml task
    Then the generated HTML contains a hyperframes-track block
    And the track has data-start "0"
    And the track has data-duration "2"

  Scenario: Title-card without subtitle renders only the title
    Given the source directory contains an AsciiDoc document with a title-card template but no subtitle
    When I run the generateHyperframesHtml task
    Then the generated HTML contains the title "Solo Title"
    And the generated HTML does not contain a subtitle span