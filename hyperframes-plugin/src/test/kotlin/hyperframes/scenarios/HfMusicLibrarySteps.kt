package hyperframes.scenarios

import hyperframes.HyperframesHtmlProcessor
import hyperframes.sound.MusicLibrary
import hyperframes.sound.MusicLicence
import hyperframes.sound.MusicTrack
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

/**
 * HF-MUSIC-LIBRARY US-3 — acceptance for the document sound sequence.
 *
 * Pure processor, no I/O (the audio copy belongs to `resolveHyperframesBgm`,
 * US-4). Steps are prefixed (`music …` / `sound …`) to avoid glue collisions.
 */
class HfMusicLibrarySteps {
    private var source: String = ""
    private var enhanced: String = ""
    private var failure: Throwable? = null

    private val library =
        MusicLibrary(
            tracks =
                listOf(
                    MusicTrack(
                        id = "calm-piano",
                        title = "Calm piano",
                        file = "calm/piano.mp3",
                        licence = MusicLicence.OWN,
                        mood = "calm",
                    ),
                ),
        )

    private fun processor(withLibrary: Boolean = true): HyperframesHtmlProcessor {
        val base = HyperframesHtmlProcessor(width = 1920, height = 1080, fps = 30, outputName = "music-demo")
        return if (withLibrary) base.withMusicLibrary(library) else base
    }

    private fun document(sections: String): String =
        """
        <!DOCTYPE html>
        <html>
        <body class="article">
        <div id="content">
        $sections
        </div>
        </body>
        </html>
        """.trimIndent()

    private fun section(
        id: String,
        duration: Int,
        extra: String = "",
    ): String = """<div class="sect1" data-duration="$duration"><h2 id="$id">$id</h2><p>body</p>$extra</div>"""

    private fun musicBlock(listing: String): String = """<div class="listingblock hyperframes-music"><div class="content"><pre>$listing</pre></div></div>"""

    @Given("a music document whose second scene asks for the calm ambience")
    fun secondSceneCalm() {
        source =
            document(
                section("one", 4) + section("two", 6, musicBlock("mood: calm\nvolume: 0.3")),
            )
    }

    @Given("a music document repeating the calm ambience on two scenes")
    fun repeatingCalm() {
        source =
            document(
                section("one", 6, musicBlock("mood: calm")) +
                    section("two", 6, musicBlock("mood: calm")),
            )
    }

    @Given("a music document that stops the music on the second scene")
    fun stopsOnSecondScene() {
        source =
            document(
                section("one", 4, musicBlock("mood: calm")) +
                    section("two", 4, musicBlock("track: none")),
            )
    }

    @Given("a music document declaring the ambience in a single collapsed line")
    fun collapsedLine() {
        source =
            document(
                """<div class="sect1" data-duration="5"><h2 id="one">one</h2>""" +
                    """<div class="openblock hyperframes-music"><div class="content">""" +
                    """<div class="paragraph"><p>mood: calm volume: 0.2</p></div></div></div></div>""",
            )
    }

    @Given("a music document declaring an effect with an offset")
    fun effectWithOffset() {
        source =
            document(
                section("one", 4) +
                    section(
                        "two",
                        4,
                        """<div class="listingblock hyperframes-sfx"><div class="content">""" +
                            """<pre>file: assets/audio/impact.mp3
offset: 1.5</pre></div></div>""",
                    ),
            )
    }

    @Given("a music document with no music library configured")
    fun noLibrary() {
        source = document(section("one", 5, musicBlock("mood: calm")))
    }

    @When("I enhance the music document with the HyperFrames processor")
    fun enhance() {
        enhanced = processor().enhance(source)
    }

    @When("I enhance the music document expecting a failure")
    fun enhanceExpectingFailure() {
        failure = runCatching { processor(withLibrary = false).enhance(source) }.exceptionOrNull()
        check(failure != null) { "expected the processor to reject the unresolved cue" }
    }

    @Then("the music layer has one cue starting at {double} seconds for {double} seconds")
    fun oneCueAt(
        start: Double,
        duration: Double,
    ) {
        check(musicCues() == 1) { "expected exactly one music cue, got ${musicCues()}" }
        check(enhanced.contains("""data-start="${number(start)}"""")) {
            "expected data-start=${number(start)} in the music cue"
        }
        check(enhanced.contains("""data-duration="${number(duration)}"""")) {
            "expected data-duration=${number(duration)} in the music cue"
        }
    }

    @Then("the music cue points to the resolved library asset")
    fun pointsToAsset() {
        check(enhanced.contains("""src="assets/audio/calm-piano.mp3"""")) {
            "expected the resolved library asset in the music cue"
        }
    }

    @Then("the music declaration is not rendered as visible content")
    fun notVisible() {
        check(!enhanced.contains("mood: calm")) { "the music declaration leaked into the visible content" }
    }

    @Then("the music layer has exactly {int} cue")
    fun exactlyCue(count: Int) = assertCueCount(count)

    @Then("the music layer has exactly {int} cues")
    fun exactlyCues(count: Int) = assertCueCount(count)

    private fun assertCueCount(count: Int) {
        check(musicCues() == count) { "expected $count music cue(s), got ${musicCues()}" }
    }

    @Then("the music cue spans the whole composition")
    fun spansWholeComposition() {
        check(enhanced.contains("""data-duration="12"""")) { "expected the cue to span 12 seconds" }
    }

    @Then("the music cue lasts {double} seconds")
    fun cueLasts(duration: Double) {
        check(enhanced.contains("""data-duration="${number(duration)}"""")) {
            "expected the cue to last ${number(duration)} seconds"
        }
    }

    @Then("the sound layer starts the effect at {double} seconds")
    fun effectAt(start: Double) {
        check(enhanced.contains("""class="hyperframes-sfx"""")) { "expected an sfx clip" }
        check(enhanced.contains("""data-start="${number(start)}"""")) {
            "expected the effect to start at ${number(start)} seconds"
        }
    }

    @Then("the failure names the missing music library configuration")
    fun failureNamesConfiguration() {
        val message = failure?.message.orEmpty()
        check(message.contains("unresolved sound cue")) { "expected an unresolved-cue failure, got: $message" }
        check(message.contains("music.libraryFile")) { "expected the music.libraryFile hint, got: $message" }
    }

    private fun musicCues(): Int = Regex("""<audio id="hf-music-""").findAll(enhanced).count()

    private fun number(value: Double): String =
        if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format(java.util.Locale.US, "%.2f", value)
        }
}
