Feature: HF-4c — N3 contract for runner-gradle integration
  As a runner-gradle N3 orchestrator
  I want hyperframes-gradle to export a typed composite-context.json
  So that I can ingest rendered MP4 metadata without parsing HTML

  Background:
    Given a hyperframes-gradle plugin is applied to a project
    And the build directory contains a rendered MP4 file
    And the build directory contains an enriched HyperFrames index.html

  Scenario: Export composite-context.json with N3 contract top-level fields
    When I run the collectHyperframesRetrieve task
    Then the composite-context.json file exists
    And the JSON contains the field "plugin" with value "hyperframes-gradle"
    And the JSON contains the field "version"
    And the JSON contains the field "output"
    And the JSON contains the field "source"
    And the JSON contains the field "renderedAt"
    And the JSON contains the field "renderDurationMs"

  Scenario: Export video descriptor with dimensions and codec
    When I run the collectHyperframesRetrieve task
    Then the JSON output field "output" contains "video" with the MP4 filename
    And the JSON output field "output" contains "width" with the stage width
    And the JSON output field "output" contains "height" with the stage height
    And the JSON output field "output" contains "fps" with the stage fps
    And the JSON output field "output" contains "sizeBytes" with the MP4 size
    And the JSON output field "output" contains "codec" with value "h264"

  Scenario: Export source descriptor with asciidoc name compositions and tracks
    When I run the collectHyperframesRetrieve task
    Then the JSON output field "source" contains "asciidoc" with the source filename
    And the JSON output field "source" contains "compositions" with the detected composition ids
    And the JSON output field "source" contains "tracks" with the number of distinct tracks

  Scenario: Succeed with no artifacts when nothing has been rendered yet
    Given the build directory contains no MP4 file
    When I run the collectHyperframesRetrieve task
    Then the task succeeds
    But the composite-context.json file does not exist