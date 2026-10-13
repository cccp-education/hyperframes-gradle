package hyperframes.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat

/**
 * HF-KERNEL-ADAPTER US-1 — Cucumber steps for the N3 contract hardening
 * (defects D-C measured duration, D-E pronunciation domain, D-F strict mode).
 *
 * All steps are English (BDD Gherkin convention).
 */
class HfKernelAdapterUs1Steps(private val world: HyperframesWorld) {

    @Given("a stub ffprobe reporting {string} seconds")
    fun aStubFfprobe(seconds: String) {
        world.createProject()
        world.writeStubFfprobe(seconds)
    }

    @Given("the build directory contains an enriched HyperFrames index.html with a pronunciation island")
    fun htmlWithPronunciationIsland() {
        world.createProject()
        world.writeHtmlWithPronunciationIsland()
    }

    @Given("the plugin is configured in strict mode")
    fun strictMode() {
        world.createProject()
        world.setStrictMode()
    }

    @When("I run the collectHyperframesRetrieve task allowing failure")
    fun runAllowingFailure() {
        world.runTaskExpectingFailure("collectHyperframesRetrieve")
    }

    @Then("the collectHyperframesRetrieve task fails")
    fun collectFails() {
        val result = world.buildResult
        assertThat(result).`as`("a build result must be captured").isNotNull
        assertThat(result!!.output).contains("strict mode is enabled")
    }
}
