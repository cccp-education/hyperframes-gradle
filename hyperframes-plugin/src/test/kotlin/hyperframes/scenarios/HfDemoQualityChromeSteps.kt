package hyperframes.scenarios

import hyperframes.HyperframesHtmlProcessor
import hyperframes.chrome.ChromeConfig
import hyperframes.chrome.TypographyConfig
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

/**
 * HF-DEMO-QUALITY US-4 — acceptance for the DSL studio chrome and typography.
 * Pure processor, no Gradle, no CLI, no network.
 *
 * Steps are prefixed (`quality chrome …`) to avoid glue collisions (S-088).
 */
class HfDemoQualityChromeSteps {

    private var source: String = ""
    private var enhanced: String = ""

    @Given("a quality chrome document with two scenes")
    fun documentWithTwoScenes() {
        source = document(
            """
            <div class="sect1"><h2 id="one">One</h2></div>
            <div class="sect1"><h2 id="two">Two</h2></div>
            """.trimIndent(),
        )
    }

    @When("I enhance the quality chrome document with a label {string} and chrome enabled")
    fun enhanceWithChrome(label: String) {
        enhanced = processor().withChrome(ChromeConfig(enabled = true, label = label)).enhance(source)
    }

    @When("I enhance the quality chrome document without chrome")
    fun enhanceWithoutChrome() {
        enhanced = processor().enhance(source)
    }

    @When("I enhance the quality chrome document with display font {string} and mono font {string}")
    fun enhanceWithTypography(displayFont: String, monoFont: String) {
        enhanced = processor()
            .withTypography(TypographyConfig(displayFont = displayFont, monoFont = monoFont))
            .enhance(source)
    }

    @When("I enhance the quality chrome document with chrome enabled and the progress bar disabled")
    fun enhanceWithoutProgressBar() {
        enhanced = processor()
            .withChrome(ChromeConfig(enabled = true, progressBar = false))
            .enhance(source)
    }

    @Then("the quality chrome has one chrome block per scene")
    fun oneChromeBlockPerScene() {
        val actual = Regex("""class="hf-chrome"""").findAll(enhanced).count()
        check(actual == 2) { "expected one chrome block per scene (2), was $actual" }
    }

    @Then("the quality chrome first scene timecode is {string}")
    fun firstTimecode(timecode: String) {
        check(enhanced.contains("""class="hf-chrome-timecode">$timecode<""")) {
            "expected the first scene timecode $timecode"
        }
    }

    @Then("the quality chrome second scene timecode is {string}")
    fun secondTimecode(timecode: String) {
        check(Regex("""class="hf-chrome-timecode">$timecode<""").containsMatchIn(enhanced)) {
            "expected a scene timecode $timecode"
        }
        check(enhanced.contains("LAB · 02 / 02")) {
            "expected the second scene counter (02 / 02)"
        }
    }

    @Then("the quality chrome renders four corner marks per scene")
    fun cornerMarksPerScene() {
        val blocks = Regex(
            """class="hf-chrome-marks"[^>]*>\s*(?:<span></span>\s*){4}</div>"""
        ).findAll(enhanced).count()
        check(blocks == 2) { "expected two scenes with four corner marks each, was $blocks" }
    }

    @Then("the quality chrome renders a global progress bar")
    fun progressBarRendered() {
        check(enhanced.contains("""id="hf-chrome-progress"""")) {
            "expected the global progress bar"
        }
    }

    @Then("the quality chrome root timeline drives the progress bar")
    fun progressBarDrivenByRootTimeline() {
        check(enhanced.contains("""rootTl.fromTo("#hf-chrome-progress span", { scaleX: 0 }""")) {
            "expected the root timeline to fill the progress bar"
        }
        check(enhanced.contains("""window.__timelines["root"] = rootTl;""")) {
            "expected the root timeline to be registered"
        }
    }

    @Then("the quality chrome is absent")
    fun chromeAbsent() {
        check(!enhanced.contains("hf-chrome")) { "chrome must stay opt-in (absent by default)" }
    }

    @Then("the quality chrome keeps the historical root timeline")
    fun historicalRootTimeline() {
        check(enhanced.contains("""window.__timelines["root"] = gsap.timeline({ paused: true });""")) {
            "a chrome-free composition must keep the historical empty root timeline"
        }
    }

    @Then("the quality chrome display font variable is {string}")
    fun displayFontVariable(font: String) {
        check(enhanced.contains("""--hf-display-font:$font""")) {
            "expected the display font variable to be $font"
        }
    }

    @Then("the quality chrome mono font variable is {string}")
    fun monoFontVariable(font: String) {
        check(enhanced.contains("""--hf-mono-font:$font""")) {
            "expected the mono font variable to be $font"
        }
    }

    @Then("the quality chrome headings use the display font")
    fun headingsUseDisplayFont() {
        check(enhanced.contains(""".scene-inner h2{font-family:var(--hf-display-font)""")) {
            "expected headings to consume the display font variable"
        }
    }

    @Then("the quality chrome renders no progress bar")
    fun progressBarAbsent() {
        check(!enhanced.contains("""id="hf-chrome-progress"""")) {
            "the progress bar must honour its own switch"
        }
    }

    private fun processor() = HyperframesHtmlProcessor(
        width = 1920,
        height = 1080,
        fps = 30,
        outputName = "quality-chrome-demo",
    )

    private fun document(sections: String): String = """
        <!DOCTYPE html>
        <html>
        <head><title>Quality chrome</title></head>
        <body class="article">
        <div id="content">
        $sections
        </div>
        </body>
        </html>
    """.trimIndent()
}
