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
        assertThat(html).contains("GSAP 3.12.5")
        assertThat(html).contains("GreenSock")
        assertThat(html).contains("<script>")
    }

    @Then("the generated HTML does not reference the GSAP CDN")
    fun htmlDoesNotReferenceCdn() {
        val html = world.generatedHtml()
        assertThat(html).doesNotContain("cdnjs.cloudflare.com")
        assertThat(html).doesNotContain("src=\"http")
    }
}
