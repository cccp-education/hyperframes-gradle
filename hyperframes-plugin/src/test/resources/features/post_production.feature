Feature: Shared post-production and narration ports (VK-4 US-4b to d)
  As a plugin maintainer
  I want hyperframes to consume the engine-neutral post-production ports
  So that a second engine shares the same neutral contracts as capsule

  Scenario: Loudness normalization is always applied
    Given the post production audio settings with no bed
    When I build the hyperframes audio post filter graph
    Then the audio post filter graph contains "loudnorm="

  Scenario: A background bed is mixed when enabled
    Given the post production audio settings with a bed at "-20.0" dB
    When I build the hyperframes audio post filter graph
    Then the audio post filter graph contains "amix=inputs=2"
    And the audio post filter graph contains "volume=-20.0dB"

  Scenario: Ducking is applied only when a bed is present
    Given the post production audio settings with a bed and ducking
    When I build the hyperframes audio post filter graph
    Then the audio post filter graph contains "sidechaincompress"

  Scenario: The subtitle command burns the subtitle file
    When I build the hyperframes subtitle command for "/s/subs.vtt"
    Then the subtitle argv contains "subtitles=/s/subs.vtt"

  Scenario: Narration is synthesized through the shared TTS adapter
    Given a post production fake command runner that writes the output
    When I synthesize narration "bonjour" into "out.wav"
    Then the narration synthesis succeeds

  Scenario: Narration is extracted from the enriched HTML
    Given the post production enriched HTML with the narration "Bonjour le monde"
    When I extract the hyperframes narration tracks
    Then the extracted narration list is "Bonjour le monde"

  Scenario: Narration extraction ignores media-only tracks
    Given the post production enriched HTML without narration
    When I extract the hyperframes narration tracks
    Then the extracted narration list is empty
