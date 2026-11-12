package hyperframes.scenarios

import hyperframes.sound.MusicAttribution
import hyperframes.sound.MusicLibrary
import hyperframes.sound.MusicLicence
import hyperframes.sound.MusicTrack
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

/**
 * HF-MUSIC-LIBRARY US-7 — acceptance for the publication music credits.
 *
 * Pure domain, no I/O. Steps are prefixed (`credits …` / `music credits …`) to
 * avoid glue collisions.
 */
class HfMusicAttributionSteps {
    private val library =
        MusicLibrary(
            tracks =
                listOf(
                    MusicTrack(
                        id = "cc-by-piano",
                        title = "Calm piano",
                        artist = "cheroliv",
                        file = "calm.mp3",
                        licence = MusicLicence.CC_BY,
                        attribution = "Music: Calm piano — cheroliv (CC BY 4.0)",
                    ),
                    MusicTrack(
                        id = "cc-by-drums",
                        title = "Epic drums",
                        artist = "cheroliv",
                        file = "drums.mp3",
                        licence = MusicLicence.CC_BY,
                    ),
                    MusicTrack(
                        id = "own-bed",
                        title = "Own bed",
                        artist = "cheroliv",
                        file = "own.mp3",
                        licence = MusicLicence.OWN,
                    ),
                ),
        )

    private var html: String = ""
    private var credits: List<String> = emptyList()

    private fun composition(trackId: String): String = """<audio class="hyperframes-music" data-track-id="$trackId" src="assets/audio/$trackId.mp3"></audio>"""

    @Given("a credits composition using the cc-by piano track")
    fun compositionWithPiano() {
        html = composition("cc-by-piano")
    }

    @Given("a credits composition using the cc-by drums track")
    fun compositionWithDrums() {
        html = composition("cc-by-drums")
    }

    @Given("a credits composition using the own track")
    fun compositionWithOwnTrack() {
        html = composition("own-bed")
    }

    @When("I collect the music credits with the library")
    fun collectCredits() {
        credits = MusicAttribution.lines(html, library)
    }

    @Then("the music credits contain the declared attribution line")
    fun containsDeclaredLine() {
        check(credits == listOf("Music: Calm piano — cheroliv (CC BY 4.0)")) {
            "unexpected credits: $credits"
        }
    }

    @Then("the music credits contain the fallback credit line")
    fun containsFallbackLine() {
        check(credits == listOf("Music: Epic drums — cheroliv (CC BY)")) { "unexpected credits: $credits" }
    }

    @Then("the music credits are empty")
    fun creditsAreEmpty() {
        check(credits.isEmpty()) { "expected no credit, got $credits" }
    }

    @Then("appending the empty credits keeps the description unchanged")
    fun appendKeepsDescription() {
        check(MusicAttribution.append("Description", credits) == "Description") {
            "appending no credit must keep the description unchanged"
        }
    }
}
