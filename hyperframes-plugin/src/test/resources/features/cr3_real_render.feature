@integration @real-render
Feature: HF-CR3-3 — real HyperFrames CLI render
  As a plugin maintainer
  I want the full pipeline to run against the real HyperFrames CLI
  So that the rendered MP4 is validated end to end, not only the HTML strings

  Scenario: The real CLI renders the generated HTML into a playable MP4
    Given a real HyperFrames CLI render environment is available
    And the source directory contains a renderable AsciiDoc document
    When I run the hyperframesPipeline task with the real CLI
    Then the real-render HTML embeds the vendored GSAP runtime
    And the real-render HTML does not reference the GSAP CDN
    And the real-render HTML contains multiple compositions
    And the real-render HTML embeds an inlined GSAP timeline
    And the rendered MP4 file exists and is not empty
    And the rendered MP4 reports a positive duration
    And the rendered MP4 exposes a readable first video stream
