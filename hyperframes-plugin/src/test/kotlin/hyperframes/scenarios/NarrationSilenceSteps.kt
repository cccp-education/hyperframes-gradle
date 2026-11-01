package hyperframes.scenarios

import hyperframes.post.NarrationSilenceCheck
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertEquals
import java.io.File

/**
 * BDD steps for `narration_silence.feature` (VK-4 US-4e).
 *
 * Pure domain — fake envelopes only, no ffmpeg, no TTS. Steps are prefixed with
 * "narration silence" (S-088 — the Cucumber glue is classpath-wide).
 */
class NarrationSilenceSteps {
    private val envelopes = linkedMapOf<String, DoubleArray?>()
    private var flagged: List<String> = emptyList()

    @Given("a narration silence file {string} with an envelope of {string}")
    fun aNarrationSilenceFile(
        name: String,
        kind: String,
    ) {
        envelopes[name] =
            when (kind) {
                "silence" -> DoubleArray(20) { 0.0 }
                "audible" -> DoubleArray(20) { 0.1 }
                else -> error("unknown envelope kind: $kind")
            }
    }

    @Given("a narration silence file {string} with an unavailable envelope")
    fun aNarrationSilenceFileWithUnavailableEnvelope(name: String) {
        envelopes[name] = null
    }

    @When("the narration silence check runs")
    fun theNarrationSilenceCheckRuns() {
        val files = envelopes.keys.map(::File)
        flagged = NarrationSilenceCheck.silentFiles(files) { envelopes[it.name] }.map { it.name }
    }

    @Then("the narration silence flagged files are {string}")
    fun theNarrationSilenceFlaggedFilesAre(expected: String) {
        val expectedFiles = expected.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        assertEquals(expectedFiles, flagged)
    }
}
