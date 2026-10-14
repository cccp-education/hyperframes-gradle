package hyperframes.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat

/**
 * HF-KERNEL-ADAPTER US-5 (S-021) — end-to-end acceptance steps.
 *
 * Drives the whole plugin pipeline (`hyperframesPipeline` = generate → render →
 * collect) with the external CLI and the duration prober stubbed, and asserts the
 * produced artifacts and the N3 contract.
 *
 * All steps are English (BDD Gherkin convention).
 */
class HfKernelAdapterUs5Steps(private val world: HyperframesWorld) {

    @Given("the source directory contains a plain AsciiDoc document")
    fun plainDocument() {
        world.createProject()
        world.writePlainAdoc()
    }

    @When("I run the hyperframesPipeline task")
    fun runPipeline() {
        world.runTask("hyperframesPipeline")
    }

    @Then("the generated HyperFrames HTML exists")
    fun generatedHtmlExists() {
        assertThat(world.buildDir.resolve("index.html")).exists()
    }
}
