Feature: HF-7g — Pronunciation end-to-end integration
  As a trainer producing training videos
  I want pronunciation hints to flow through the full pipeline
  So that the TTS engine speaks every word correctly in the final MP4

  Background:
    Given a hyperframes-gradle plugin is applied to a project

  Scenario: Full pipeline with a pronunciation dictionary block
    Given the source directory contains an AsciiDoc document with a pronunciation block having two entries
    When I run the generateHyperframesHtml task
    Then the generated HTML contains a JSON pronunciation island
    And the pronunciation island contains the word "dos"
    And the pronunciation island contains the phonetic "do"
    And the pronunciation island contains the word "kubernetes"
    And the pronunciation island contains the phonetic "koobernetayz"

  Scenario: Full pipeline with inline hints in narration tracks
    Given the source directory contains an AsciiDoc document with an inline pronunciation hint in a track
    When I run the generateHyperframesHtml task
    Then the generated HTML contains a JSON pronunciation island
    And the pronunciation island contains the word "dos"
    And the pronunciation island contains the phonetic "do"
    And the rendered track text contains the word "dos"
    And the rendered track text does not contain the inline hint syntax

  Scenario: Full pipeline without pronunciation produces no JSON island
    Given the source directory contains an AsciiDoc document without a pronunciation block
    When I run the generateHyperframesHtml task
    Then the generated HTML does not contain a JSON pronunciation island