package hyperframes.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.File

/**
 * HF-CR3-3 — Cucumber steps for the real-render acceptance.
 *
 * Unlike the string-level unit tests, these steps drive the **real** HyperFrames
 * CLI over the generated HTML and validate the produced MP4 (existence, size,
 * duration and a readable first video stream). The whole scenario is
 * **skip-guarded**: if any prerequisite (Node.js / the `hyperframes` npm package
 * / `ffprobe`) is unavailable, it is skipped — never a false success.
 *
 * Step names are prefixed ("real-render") to avoid glue collisions (pattern S-088).
 * All steps are English (BDD Gherkin convention).
 */
class Cr3RealRenderSteps(private val world: HyperframesWorld) {

    private var cliPath: String = ""
    private var nodePath: String = ""
    private var ffprobePath: String = ""

    @Given("a real HyperFrames CLI render environment is available")
    fun realEnvironmentAvailable() {
        cliPath = envOrPath("HF_HYPERFRAMES_CLI", "hyperframes") ?: ""
        nodePath = envOrPath("HF_NODE", "node") ?: ""
        ffprobePath = envOrPath("HF_FFPROBE", "ffprobe") ?: ""
        assumeTrue(cliPath.isNotBlank() && nodePath.isNotBlank() && ffprobePath.isNotBlank()) {
            "real HyperFrames render prerequisites missing " +
                "(cli='$cliPath', node='$nodePath', ffprobe='$ffprobePath') — scenario skipped"
        }
    }

    @Given("the source directory contains a renderable AsciiDoc document")
    fun renderableDocument() {
        world.createProject()
        world.writeRenderableAdoc()
    }

    @When("I run the hyperframesPipeline task with the real CLI")
    fun runRealPipeline() {
        world.useRealCli(cliPath, nodePath, ffprobePath)
        world.runTask("hyperframesPipeline")
    }

    @Then("the real-render HTML embeds the vendored GSAP runtime")
    fun htmlEmbedsVendoredGsap() {
        val html = world.generatedHtml()
        assertThat(html).contains("GSAP 3.12.5")
        assertThat(html).contains("<script>")
    }

    @Then("the real-render HTML does not reference the GSAP CDN")
    fun htmlNoCdn() {
        assertThat(world.generatedHtml()).doesNotContain("cdnjs.cloudflare.com")
    }

    @Then("the rendered MP4 file exists and is not empty")
    fun mp4ExistsNotEmpty() {
        val mp4 = world.renderedMp4()
        assertThat(mp4).`as`("a rendered MP4 must exist").isNotNull
        assertThat(mp4!!.length()).`as`("the rendered MP4 must not be empty").isGreaterThan(0L)
    }

    @Then("the rendered MP4 reports a positive duration")
    fun mp4PositiveDuration() {
        val duration = world.probeDurationSeconds(ffprobePath)
        assertThat(duration).`as`("ffprobe must report a positive duration").isGreaterThan(0.0)

        // Publish the artefact for the CI duration-probe step (HF-CR3-2), when asked.
        (System.getenv("HF_REAL_RENDER_OUTPUT") ?: "")
            .takeIf { it.isNotBlank() }
            ?.let { world.publishRenderedMp4(File(it)) }
    }

    @Then("the rendered MP4 exposes a readable first video stream")
    fun mp4FirstVideoStream() {
        val codec = world.probeFirstVideoCodec(ffprobePath)
        assertThat(codec).`as`("the MP4 must expose a readable first video stream").isNotBlank()
    }

    private fun envOrPath(env: String, command: String): String? {
        System.getenv(env)?.takeIf { it.isNotBlank() }?.let { return it }
        return try {
            val process = ProcessBuilder("which", command).redirectErrorStream(true).start()
            process.waitFor()
            process.inputStream.bufferedReader().readText().trim().ifBlank { null }
        } catch (e: Exception) {
            null
        }
    }
}
