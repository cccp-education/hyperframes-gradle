Feature: Narration silence check
  As a HyperFrames producer
  I want to flag mute narration takes through the shared N1 detector
  So that a silent TTS output is visible even without an ASR

  Scenario: A silent narration is flagged
    Given a narration silence file "narration-1.wav" with an envelope of "silence"
    When the narration silence check runs
    Then the narration silence flagged files are "narration-1.wav"

  Scenario: An audible narration is not flagged
    Given a narration silence file "narration-1.wav" with an envelope of "audible"
    When the narration silence check runs
    Then the narration silence flagged files are ""

  Scenario: An unavailable probe is not flagged
    Given a narration silence file "narration-1.wav" with an unavailable envelope
    When the narration silence check runs
    Then the narration silence flagged files are ""

  Scenario: A mixed batch reports only the silent files in order
    Given a narration silence file "narration-1.wav" with an envelope of "silence"
    And a narration silence file "narration-2.wav" with an envelope of "audible"
    And a narration silence file "narration-3.wav" with an envelope of "silence"
    When the narration silence check runs
    Then the narration silence flagged files are "narration-1.wav,narration-3.wav"
