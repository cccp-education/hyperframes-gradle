@music-credits
Feature: HF-MUSIC-LIBRARY US-7 — music credits in the publication description

  As a video author using cc-by music
  I want the tracks actually used by the composition to be credited
  So that the licence obligation is never silently omitted

  Scenario: A cc-by track used by the composition is credited
    Given a credits composition using the cc-by piano track
    When I collect the music credits with the library
    Then the music credits contain the declared attribution line

  Scenario: A cc-by track without a declared line falls back to title and artist
    Given a credits composition using the cc-by drums track
    When I collect the music credits with the library
    Then the music credits contain the fallback credit line

  Scenario: A track without attribution obligation is not credited
    Given a credits composition using the own track
    When I collect the music credits with the library
    Then the music credits are empty
    And appending the empty credits keeps the description unchanged
