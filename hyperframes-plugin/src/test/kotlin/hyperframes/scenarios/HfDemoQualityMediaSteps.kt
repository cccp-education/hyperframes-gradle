package hyperframes.scenarios

import hyperframes.HyperframesHtmlProcessor
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

/**
 * HF-DEMO-QUALITY US-3 — acceptance for DSL audio cues. Pure processor, no I/O
 * (asset copying is covered by [hyperframes.ReferencedAssetsTest] and the
 * TestKit functional test).
 *
 * Steps are prefixed (`quality media …`) to avoid glue collisions.
 */
class HfDemoQualityMediaSteps {

    private var source: String = ""
    private var enhanced: String = ""

    @Given("a quality media document with a bgm and an sfx cue")
    fun documentWithTwoCues() {
        source = document(
            """
            <div class="sect1"><h2 id="one">One</h2>
            <div class="listingblock hyperframes-audio"><div class="content"><pre>bgm: assets/audio/bgm.mp3 volume=0.3
            sfx: 6.0 assets/audio/whoosh.mp3 volume=0.25</pre></div></div>
            </div>
            """.trimIndent(),
        )
    }

    @Given("a quality media document with only invalid audio lines")
    fun documentWithInvalidCues() {
        source = document(
            """
            <div class="sect1"><h2 id="one">One</h2>
            <div class="listingblock hyperframes-audio"><div class="content"><pre>bad: nope
            sfx: not-a-time file.mp3</pre></div></div>
            </div>
            """.trimIndent(),
        )
    }

    @Given("a quality media document without an audio block")
    fun documentWithoutAudio() {
        source = document("""<div class="sect1"><h2 id="one">One</h2></div>""")
    }

    @When("I enhance the quality media document with the HyperFrames processor")
    fun enhanceDocument() {
        enhanced = HyperframesHtmlProcessor(
            width = 1920,
            height = 1080,
            fps = 30,
            outputName = "quality-media-demo",
        ).enhance(source)
    }

    @Then("the quality media composition has {int} audio elements")
    fun audioCount(expected: Int) {
        val actual = Regex("""<audio\b""").findAll(enhanced).count()
        check(actual == expected) { "expected $expected audio element(s), was $actual" }
    }

    @Then("the quality media bgm starts at 0 with volume 0.3")
    fun bgmCue() {
        check(enhanced.contains("""<audio id="hf-audio-0" src="assets/audio/bgm.mp3" """)) {
            "expected the BGM cue as the first direct audio child"
        }
        check(enhanced.contains("""data-start="0""")) { "the BGM must start at 0" }
        check(enhanced.contains("""data-volume="0.30"""")) { "the BGM volume must be 0.3" }
    }

    @Then("the quality media sfx starts at 6")
    fun sfxCue() {
        check(enhanced.contains("""<audio id="hf-audio-1" src="assets/audio/whoosh.mp3" """)) {
            "expected the SFX cue as the second audio child"
        }
        check(enhanced.contains("""data-start="6" """)) { "the SFX must start at 6s" }
    }

    @Then("the quality media audio block is removed from the scene")
    fun audioBlockRemoved() {
        check(!enhanced.contains("hyperframes-audio")) {
            "the [.hyperframes-audio] block must not remain in the scene content"
        }
    }

    private fun document(section: String): String = """
        <!DOCTYPE html>
        <html>
        <head><title>Quality media</title></head>
        <body class="article">
        <div id="content">
        $section
        </div>
        </body>
        </html>
    """.trimIndent()
}
