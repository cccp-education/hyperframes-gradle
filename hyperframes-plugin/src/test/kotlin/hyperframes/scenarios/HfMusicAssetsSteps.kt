package hyperframes.scenarios

import hyperframes.sound.MusicAssetPlan
import hyperframes.sound.MusicAssetPlanner
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import video.kernel.music.MusicLibrary
import video.kernel.music.MusicLicence
import video.kernel.music.MusicTrack

/**
 * HF-MUSIC-LIBRARY US-4 — acceptance for the resolved music-asset plan.
 *
 * Pure planner, no I/O (the copy itself is covered by the TestKit functional
 * test). Steps are prefixed (`music assets …`) to avoid glue collisions.
 */
class HfMusicAssetsSteps {
    private val library =
        MusicLibrary(
            tracks =
                listOf(
                    MusicTrack(
                        id = "calm-piano",
                        title = "Calm piano",
                        file = "calm/piano.mp3",
                        licence = MusicLicence.OWN,
                        sha256 = "abc123",
                        mood = "calm",
                    ),
                ),
        )

    private var html: String = ""
    private var existingSha256: String? = null
    private lateinit var plan: MusicAssetPlan

    private fun composition(trackId: String): String =
        """
        <div id="stage">
        <audio id="hf-music-0" class="hyperframes-music" data-track-id="$trackId" src="assets/audio/calm-piano.mp3" data-start="0"></audio>
        </div>
        """.trimIndent()

    @Given("a music assets composition referencing one library track")
    fun compositionWithLibraryTrack() {
        html = composition("calm-piano")
    }

    @Given("a music assets composition referencing an unknown track")
    fun compositionWithUnknownTrack() {
        html = composition("ghost")
    }

    @When("I plan the music assets with the library")
    fun planWithLibrary() {
        existingSha256 = null
        plan()
    }

    @When("I plan the music assets with an identical existing asset")
    fun planWithExistingAsset() {
        existingSha256 = "abc123"
        plan()
    }

    private fun plan() {
        plan =
            MusicAssetPlanner.plan(
                requests = MusicAssetPlanner.requests(html),
                library = library,
                audioRoot = "/library/audio",
                existsSha256 = { existingSha256 },
            )
    }

    @Then("the music assets plan has {int} copy")
    fun planHasOneCopy(count: Int) {
        check(plan.copies.size == count) { "expected $count copy, got ${plan.copies.size}" }
    }

    @Then("the music assets plan has {int} copies")
    fun planHasCopies(count: Int) {
        check(plan.copies.size == count) { "expected $count copies, got ${plan.copies.size}" }
    }

    @Then("the music assets plan has no copy")
    fun planHasNoCopy() {
        check(plan.copies.isEmpty()) { "expected no copy, got ${plan.copies.size}" }
    }

    @Then("the music assets plan copy source is the library file")
    fun copySourceIsTheLibraryFile() {
        check(plan.copies.single().source == "/library/audio/calm/piano.mp3") {
            "unexpected source ${plan.copies.single().source}"
        }
    }

    @Then("the music assets plan reports the asset as up to date")
    fun reportsUpToDate() {
        check(plan.upToDate.size == 1) { "expected 1 up-to-date asset, got ${plan.upToDate.size}" }
    }

    @Then("the music assets plan reports the unknown track")
    fun reportsUnknownTrack() {
        check(plan.missing.any { it.contains("ghost") }) { "expected a reason naming the unknown track, got ${plan.missing}" }
    }
}
