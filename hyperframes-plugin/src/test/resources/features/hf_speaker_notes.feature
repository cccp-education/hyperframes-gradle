Feature: Speaker notes narrated per scene
  As a HyperFrames producer
  I want the authored [NOTE.speaker] blocks to drive the narration per scene
  So that the rendered video speaks without any post-production

  Scenario: Each note becomes the narration of its scene
    Given a speaker notes document with a note under each scene
    When I build the speaker notes composition
    Then the composition exposes 2 speaker notes narration markers
    And the speaker notes blocks are removed from the visible content
    And the speaker notes spoken texts are "The problem is the manual copy.|The fix is the document pipeline."
    And the speaker notes marker of scene "intro" starts at "0"
    And the speaker notes marker of scene "demo" starts at "5"

  Scenario: A scene without a note stays silent
    Given a speaker notes document whose first scene has no note
    When I build the speaker notes composition
    Then the composition exposes 1 speaker notes narration marker
    And the speaker notes spoken texts are "Only the second scene speaks."
    And the speaker notes marker of scene "silent" is absent

  Scenario: The pronunciation hint of a note is spoken through the track path
    Given a speaker notes document with a pronunciation hint in the note
    When I build the speaker notes composition
    Then the speaker notes spoken texts are "The container dos runs."
    And the speaker notes composition registers the pronunciation hint "dos" -> "do"

  Scenario: The N3 context lists the speaking scenes
    Given a speaker notes document with a note under each scene
    When I build the speaker notes N3 context
    Then the speaker notes N3 context lists the scenes "intro|demo"

  Scenario: The narration takes are muxed on the scene timeline
    Given a speaker notes document with a note under each scene
    When I build the speaker notes composition
    And I plan the narration mux with takes "narration-1.wav|narration-2.wav"
    Then the narration mux filter is "[1:a]adelay=0|0[a0];[2:a]adelay=5000|5000[a1];[a0][a1]amix=inputs=2:normalize=0,apad[aout]"
