Feature: HF-KERNEL-ADAPTER US-1 — N3 contract hardening (D-C, D-E, D-F)
  As a runner-gradle N3 orchestrator
  I want the hyperframes N3 contract to carry a measured duration, the configured
  pronunciation domain, and an explicit strict failure mode
  So that rendered metadata is trustworthy and degradations are explicit

  Scenario: N3 output carries the measured video duration (D-C)
    Given a stub ffprobe reporting "12.5" seconds
    And the build directory contains a rendered MP4 file
    And the build directory contains an enriched HyperFrames index.html
    When I run the collectHyperframesRetrieve task
    Then the task succeeds
    And the JSON output field "output" contains "durationSeconds" with value "12.5"

  Scenario: N3 pronunciation descriptor echoes the configured domain (D-E)
    Given the project uses the pronunciation domain "video-fr"
    And the build directory contains a rendered MP4 file
    And the build directory contains an enriched HyperFrames index.html with a pronunciation island
    When I run the collectHyperframesRetrieve task
    Then the task succeeds
    And the JSON output field "pronunciation" contains "domain" with value "video-fr"

  Scenario: Strict mode fails when nothing has been rendered (D-F)
    Given the plugin is configured in strict mode
    And the build directory contains no MP4 file
    When I run the collectHyperframesRetrieve task allowing failure
    Then the collectHyperframesRetrieve task fails
