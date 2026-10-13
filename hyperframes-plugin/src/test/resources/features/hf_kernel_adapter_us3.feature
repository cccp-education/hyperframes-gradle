Feature: HF-KERNEL-ADAPTER US-3 — render task delegates to the shared VideoEngine
  As a plugin maintainer
  I want renderHyperframes to delegate the CLI render to HyperFramesEngine
  So that the shared-kernel adapter is the single render path (no duplicated CLI code)

  Scenario: Render delegates to the engine and produces the MP4
    Given a stub HyperFrames CLI that renders the MP4
    And the build directory contains an enriched HyperFrames index.html
    When I run the renderHyperframes task
    Then the renderHyperframes task succeeds
    And the rendered MP4 file exists in the build directory

  Scenario: A failing CLI surfaces the engine diagnostics
    Given a stub HyperFrames CLI failing with exit code 1
    And the build directory contains an enriched HyperFrames index.html
    When I run the renderHyperframes task allowing failure
    Then the renderHyperframes task fails
