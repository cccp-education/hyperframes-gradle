Feature: HF-7 evolution — Pronunciation domain wiring
  As a trainer producing training videos
  I want a pre-built pronunciation domain to be injected as the baseline dictionary
  So that the TTS engine speaks common video words correctly without me repeating them

  Scenario: Domain dictionary is injected as baseline when no author block is present
    Given the project uses the pronunciation domain "video-fr"
    And the source directory contains an AsciiDoc document without a pronunciation block
    When I run the generateHyperframesHtml task
    Then the generated HTML contains a JSON pronunciation island
    And the pronunciation island contains the word "narration"
    And the pronunciation island contains the phonetic "na-ra-syon"

  Scenario: Author block overrides a domain hint on conflict
    Given the project uses the pronunciation domain "video-fr"
    And the source directory contains an AsciiDoc document with a pronunciation block overriding narration
    When I run the generateHyperframesHtml task
    Then the generated HTML contains a JSON pronunciation island
    And the pronunciation island contains the word "narration"
    And the pronunciation island contains the phonetic "my-override"
    And the pronunciation island does not contain the phonetic "na-ra-syon"
    And the pronunciation island contains the word "transition"
    And the pronunciation island contains the phonetic "tran-zi-syon"

  Scenario: Inline hints merge with the domain dictionary
    Given the project uses the pronunciation domain "video-fr"
    And the source directory contains an AsciiDoc document with an inline pronunciation hint in a track
    When I run the generateHyperframesHtml task
    Then the generated HTML contains a JSON pronunciation island
    And the pronunciation island contains the word "dos"
    And the pronunciation island contains the phonetic "do"
    And the pronunciation island contains the word "narration"
    And the pronunciation island contains the phonetic "na-ra-syon"

  Scenario: No domain and no author block produces no JSON island
    Given a hyperframes-gradle plugin is applied to a project
    And the source directory contains an AsciiDoc document without a pronunciation block
    When I run the generateHyperframesHtml task
    Then the generated HTML does not contain a JSON pronunciation island