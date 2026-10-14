Feature: HF-KERNEL-ADAPTER US-5 — end-to-end pipeline acceptance
  As a plugin maintainer
  I want the full AsciiDoc -> HTML -> MP4 -> N3 pipeline to run via hyperframesPipeline
  So that the integrated plugin is covered end-to-end (external CLI and prober stubbed)

  Scenario: The full pipeline produces HTML, MP4 and the N3 contract
    Given a stub HyperFrames CLI that renders the MP4
    And a stub ffprobe reporting "30.0" seconds
    And the source directory contains a plain AsciiDoc document
    When I run the hyperframesPipeline task
    Then the generated HyperFrames HTML exists
    And the rendered MP4 file exists in the build directory
    And the composite-context.json file exists
    And the JSON output field "output" contains "durationSeconds" with value "30.0"
    And the JSON contains the field "engine" with value "hyperframes"
