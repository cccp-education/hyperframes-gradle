package hyperframes.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat

/**
 * HF-CR3-1 — Cucumber steps for the vendored GSAP runtime.
 *
 * Drives the real `generateHyperframesHtml` task and asserts that the produced
 * page embeds the runtime inline and never references the external CDN.
 *
 * Step names are prefixed ("cr3 gsap") to avoid glue collisions (pattern S-088).
 * All steps are English (BDD Gherkin convention).
 */
class Cr3GsapVendoringSteps(private val world: HyperframesWorld) {

    @Given("a plain AsciiDoc source document")
    fun plainSourceDocument() {
        world.createProject()
        world.writePlainAdoc()
    }

    @When("I generate the HyperFrames HTML")
    fun generateHtml() {
        world.runTask("generateHyperframesHtml")
    }

    @Then("the generated HTML embeds the vendored GSAP runtime")
    fun htmlEmbedsVendoredGsap() {
        val html = world.generatedHtml()
        // HF-CR4 — the runtime is written next to the composition and loaded as an
        // offline external sibling (an inlined runtime is linted as non-deterministic).
        assertThat(html).contains("<script src=\"gsap-3.12.5.min.js\"></script>")
    }

    @Then("the vendored GSAP runtime file exists next to the composition")
    fun gsapRuntimeFileExists() {
        val gsap = world.projectDir.resolve("build/hyperframes/gsap-3.12.5.min.js")
        assertThat(gsap).exists()
        assertThat(gsap.readText()).contains("GSAP 3.12.5")
        assertThat(gsap.readText()).contains("GreenSock")
    }

    @Then("the generated HTML does not reference the GSAP CDN")
    fun htmlDoesNotReferenceCdn() {
        val html = world.generatedHtml()
        assertThat(html).doesNotContain("cdnjs.cloudflare.com")
        assertThat(html).doesNotContain("src=\"http")
    }
}
