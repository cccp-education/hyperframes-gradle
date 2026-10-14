package hyperframes.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat

/**
 * HF-KERNEL-ADAPTER US-3 — Cucumber steps: `renderHyperframes` delegates the CLI
 * render to the shared `video.kernel.VideoEngine` adapter (`HyperFramesEngine`).
 *
 * All steps are English (BDD Gherkin convention).
 */
class HfKernelAdapterUs3Steps(private val world: HyperframesWorld) {

    @Given("a stub HyperFrames CLI that renders the MP4")
    fun stubCliOk() {
        world.createProject()
        world.writeStubCli(exitCode = 0)
    }

    @Given("a stub HyperFrames CLI failing with exit code {int}")
    fun stubCliFailing(exitCode: Int) {
        world.createProject()
        world.writeStubCli(exitCode = exitCode)
    }

    @When("I run the renderHyperframes task")
    fun runRender() {
        world.runTask("renderHyperframes")
    }

    @When("I run the renderHyperframes task allowing failure")
    fun runRenderAllowingFailure() {
        world.runTaskExpectingFailure("renderHyperframes")
    }

    @Then("the renderHyperframes task succeeds")
    fun renderSucceeds() {
        val result = world.buildResult
        assertThat(result).`as`("a build result must be captured").isNotNull
        assertThat(result!!.task(":renderHyperframes")?.outcome?.toString()).contains("SUCCESS")
    }

    @Then("the renderHyperframes task fails")
    fun renderFails() {
        val result = world.buildResult
        assertThat(result).`as`("a build result must be captured").isNotNull
        assertThat(result!!.output).contains("HyperFrames CLI failed with exit code")
    }

    @Then("the rendered MP4 file exists in the build directory")
    fun mp4Exists() {
        val mp4 = world.buildDir.resolve("test-video.mp4")
        assertThat(mp4).exists()
        assertThat(mp4.readText()).contains("MOCK-MP4")
    }
}
