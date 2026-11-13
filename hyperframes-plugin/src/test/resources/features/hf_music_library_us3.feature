@music
Feature: HF-MUSIC-LIBRARY US-3 — document sound sequence

  As a video author writing AsciiDoc
  I want to declare the music ambience and the sound effects scene by scene
  So that the produced video carries a document-ordered sound layer

  Scenario: A music block becomes a cue resolved from the library
    Given a music document whose second scene asks for the calm ambience
    When I enhance the music document with the HyperFrames processor
    Then the music layer has one cue starting at 4 seconds for 6 seconds
    And the music cue points to the resolved library asset
    And the music declaration is not rendered as visible content

  Scenario: A scene re-declaring the same music keeps one continuous cue
    Given a music document repeating the calm ambience on two scenes
    When I enhance the music document with the HyperFrames processor
    Then the music layer has exactly 1 cue
    And the music cue spans the whole composition

  Scenario: An explicit stop ends the running music
    Given a music document that stops the music on the second scene
    When I enhance the music document with the HyperFrames processor
    Then the music layer has exactly 1 cue
    And the music cue lasts 4 seconds

  Scenario: An open block collapsed into one paragraph parses the same way
    Given a music document declaring the ambience in a single collapsed line
    When I enhance the music document with the HyperFrames processor
    Then the music cue points to the resolved library asset

  Scenario: A sound effect is anchored to the scene offset
    Given a music document declaring an effect with an offset
    When I enhance the music document with the HyperFrames processor
    Then the sound layer starts the effect at 5.5 seconds

  Scenario: An incoming crossfade overlaps the outgoing cue
    Given a music document that crossfades into a second ambience
    When I enhance the music document with the HyperFrames processor
    Then the music layer has exactly 2 cues
    And the first music cue is extended to 7.50 seconds
    And the crossfade fades out and in over 1.50 seconds

  Scenario: An unresolvable cue fails before the render
    Given a music document with no music library configured
    When I enhance the music document expecting a failure
    Then the failure names the missing music library configuration
