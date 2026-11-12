@music-assets
Feature: HF-MUSIC-LIBRARY US-4 — resolved music assets

  As a video author using a shared music library
  I want the tracks referenced by the composition to be copied next to it
  So that the renderer loads real files, and an unchanged asset is never rewritten

  Scenario: Only the library-resolved clips are requested
    Given a music assets composition referencing one library track
    When I plan the music assets with the library
    Then the music assets plan has 1 copy
    And the music assets plan copy source is the library file

  Scenario: An identical sha256 makes the copy unnecessary
    Given a music assets composition referencing one library track
    When I plan the music assets with an identical existing asset
    Then the music assets plan has no copy
    And the music assets plan reports the asset as up to date

  Scenario: An unknown track is reported and never invented
    Given a music assets composition referencing an unknown track
    When I plan the music assets with the library
    Then the music assets plan has no copy
    And the music assets plan reports the unknown track
