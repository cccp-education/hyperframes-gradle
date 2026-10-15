package hyperframes.scenarios

import hyperframes.post.CommandRunner
import hyperframes.post.CommandResult
import hyperframes.post.EspeakTextToSpeech
import hyperframes.post.HyperframesAudioPostCommand
import hyperframes.post.HyperframesSubtitleCommand
import hyperframes.post.NarrationTrackExtractor
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import video.kernel.AudioPostSettings

/**
 * Steps for the shared post-production / narration ports (VK-4 US-4b→d).
 *
 * Prefixed (`post production ...`) to avoid glue collisions (pattern S-088).
 * Pure-domain: the fake runner never spawns a process.
 */
class PostProductionSteps {

    private var settings: AudioPostSettings = AudioPostSettings()
    private var hasBed: Boolean = false
    private var filterGraph: String = ""
    private var subtitleArgv: List<String> = emptyList()
    private var runner: CommandRunner = CommandRunner { _, _, _ -> CommandResult(exitCode = 1) }
    private var synthesized: Boolean = false
    private var html: String = ""
    private var narrations: List<String> = emptyList()

    @Given("the post production audio settings with no bed")
    fun noBed() {
        settings = AudioPostSettings()
        hasBed = false
    }

    @Given("the post production audio settings with a bed at {string} dB")
    fun bedAt(level: String) {
        settings = AudioPostSettings(bgmEnabled = true, bgmFile = "music.mp3", bgmLevelDb = level.toDouble())
        hasBed = true
    }

    @Given("the post production audio settings with a bed and ducking")
    fun bedAndDucking() {
        settings = AudioPostSettings(bgmEnabled = true, bgmFile = "music.mp3", duckingEnabled = true)
        hasBed = true
    }

    @When("I build the hyperframes audio post filter graph")
    fun buildGraph() {
        filterGraph = HyperframesAudioPostCommand.buildFilterGraph(settings, hasBgmInput = hasBed)
    }

    @Then("the audio post filter graph contains {string}")
    fun graphContains(fragment: String) {
        assertContains(filterGraph, fragment)
    }

    @When("I build the hyperframes subtitle command for {string}")
    fun buildSubtitle(subtitles: String) {
        subtitleArgv = HyperframesSubtitleCommand.buildArgv(
            File("/v/in.mp4"), File(subtitles), File("/v/out.mp4"), "ffmpeg",
        )
    }

    @Then("the subtitle argv contains {string}")
    fun subtitleArgvContains(fragment: String) {
        assertTrue(subtitleArgv.any { it.contains(fragment) }, "argv does not contain '$fragment': $subtitleArgv")
    }

    @Given("a post production fake command runner that writes the output")
    fun fakeRunner() {
        runner = CommandRunner { argv, _, _ ->
            File(argv.last()).apply { parentFile?.mkdirs() }.writeText("produced")
            CommandResult(exitCode = 0)
        }
    }

    @When("I synthesize narration {string} into {string}")
    fun synthesize(text: String, fileName: String) {
        val output = File.createTempFile("hf-narration-", "-$fileName").let { it.delete(); File(it.parentFile, fileName) }
        synthesized = EspeakTextToSpeech(runner = runner).synthesize(text, output)
        output.delete()
    }

    @Then("the narration synthesis succeeds")
    fun synthesisSucceeds() {
        assertTrue(synthesized)
    }

    @Given("the post production enriched HTML with the narration {string}")
    fun enrichedHtml(narration: String) {
        html = """<div class="paragraph hyperframes-track" data-track-index="0"><p>$narration</p></div>"""
    }

    @Given("the post production enriched HTML without narration")
    fun mediaOnlyHtml() {
        html = """<div class="paragraph hyperframes-track" data-track-index="0"><audio src="m.mp3"></audio></div>"""
    }

    @When("I extract the hyperframes narration tracks")
    fun extract() {
        narrations = NarrationTrackExtractor.extract(html)
    }

    @Then("the extracted narration list is {string}")
    fun listIs(value: String) {
        assertEquals(listOf(value), narrations)
    }

    @Then("the extracted narration list is empty")
    fun listIsEmpty() {
        assertEquals(emptyList(), narrations)
    }
}
