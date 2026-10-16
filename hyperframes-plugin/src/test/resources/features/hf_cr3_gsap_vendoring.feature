Feature: HF-CR3-1 — vendored GSAP runtime
  As a plugin maintainer
  I want the generated HyperFrames HTML to embed the GSAP runtime
  So that rendering no longer depends on the external GSAP CDN

  @cr3 @gsap
  Scenario: The generated HTML embeds the vendored GSAP runtime
    Given a plain AsciiDoc source document
    When I generate the HyperFrames HTML
    Then the generated HTML embeds the vendored GSAP runtime
    And the vendored GSAP runtime file exists next to the composition
    And the generated HTML does not reference the GSAP CDN
