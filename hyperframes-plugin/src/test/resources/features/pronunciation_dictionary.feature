Feature: HF-7b — Pronunciation dictionary aggregate
  As a trainer correcting TTS pronunciation
  I want the pronunciation dictionary to collect, deduplicate and render hints
  So that the HyperFrames CLI can speak each word with the right phonetic

  Scenario: Dictionary renders a single hint as a JSON island
    Given a pronunciation dictionary with a hint for word "dos" speaking "do"
    When the dictionary renders its JSON island
    Then the rendered island contains the word "dos"
    And the rendered island contains the phonetic "do"
    And the rendered island is wrapped in a script tag with id "hf-pronunciation"

  Scenario: Dictionary deduplicates hints on word plus language
    Given a pronunciation dictionary with a hint for word "dos" speaking "do" in language "fr"
    When I add another hint for word "DOS" speaking "doh" in language "fr"
    Then the dictionary contains exactly 1 hint

  Scenario: Author hints override domain dictionary entries
    Given a domain dictionary with a hint for word "dos" speaking "doss"
    And an author dictionary with a hint for word "dos" speaking "do"
    When the author dictionary is merged with the domain dictionary
    Then the merged dictionary speaks "do" for word "dos"
    And the merged dictionary contains exactly 1 hint

  Scenario: Empty dictionary renders an empty JSON array
    Given an empty pronunciation dictionary
    When the dictionary renders its JSON island
    Then the rendered island contains an empty JSON array

  Scenario: AsciiDoc with a pronunciation block produces a JSON island in the rendered HTML
    Given the source directory contains an AsciiDoc document with a pronunciation block
    When I run the generateHyperframesHtml task
    Then the generated HTML contains a JSON pronunciation island
    And the pronunciation island contains the word "dos"
    And the pronunciation island contains the phonetic "do"

  Scenario: AsciiDoc without a pronunciation block does not produce a JSON island
    Given the source directory contains an AsciiDoc document without a pronunciation block
    When I run the generateHyperframesHtml task
    Then the generated HTML does not contain a JSON pronunciation island