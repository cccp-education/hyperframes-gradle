package hyperframes.scenarios

import hyperframes.HyperframesHtmlProcessor
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

/**
 * HF-DEMO-QUALITY US-6 — acceptance for the per-scene background image.
 *
 * Pure processor, no I/O (the asset copy is covered by the TestKit functional
 * test). Steps are prefixed (`quality background …`) to avoid glue collisions.
 */
class HfDemoQualityBackgroundSteps {

    private var source: String = ""
    private var enhanced: String = ""

    @Given("a quality background document with an image")
    fun documentWithImage() {
        source = document(block("file: assets/img/fiber.jpg"))
    }

    @Given("a quality background document with an image and custom options")
    fun documentWithCustomOptions() {
        source = document(
            block(
                """
                file: assets/img/datacenter.jpg
                opacity: 0.35
                scrim: false
                grid: true
                grain: true
                """.trimIndent()
            )
        )
    }

    @Given("a quality background document with an image and parallax")
    fun documentWithParallax() {
        source = document(block("file: assets/img/fiber.jpg\nparallax: 0.1"))
    }

    @Given("a quality background document without a background block")
    fun documentWithoutBackground() {
        source = document("")
    }

    @Given("a quality background document with an invalid background block")
    fun documentWithInvalidBackground() {
        source = document(block("opacity: 0.4"))
    }

    @When("I enhance the quality background document with the HyperFrames processor")
    fun enhanceDocument() {
        enhanced = HyperframesHtmlProcessor(
            width = 1920,
            height = 1080,
            fps = 30,
            outputName = "quality-background-demo",
        ).enhance(source)
    }

    @Then("the quality background composition has a media layer at opacity {double}")
    fun mediaLayerOpacity(opacity: Double) {
        check(enhanced.contains("""<div class="hf-bg" aria-hidden="true">""")) {
            "expected the hf-bg media layer"
        }
        check(enhanced.contains("""class="hf-bg-media"""")) {
            "expected the hf-bg-media image"
        }
        val expected = """style="opacity:$opacity""""
        check(enhanced.contains(expected)) { "expected $expected in the media layer" }
    }

    @Then("the quality background layer carries the scrim")
    fun carriesScrim() {
        check(enhanced.contains("""<div class="hf-bg-scrim"></div>""")) { "expected the scrim" }
    }

    @Then("the quality background layer has no scrim")
    fun hasNoScrim() {
        check(!enhanced.contains("""<div class="hf-bg-scrim"></div>""")) { "the scrim must be absent" }
    }

    @Then("the quality background layer has no grid and no grain")
    fun hasNoGridAndNoGrain() {
        check(!enhanced.contains("""<div class="hf-bg-grid"></div>""")) { "the grid must be absent" }
        check(!enhanced.contains("""<div class="hf-bg-grain"></div>""")) { "the grain must be absent" }
    }

    @Then("the quality background layer carries the grid and the grain")
    fun carriesGridAndGrain() {
        check(enhanced.contains("""<div class="hf-bg-grid"></div>""")) { "expected the grid" }
        check(enhanced.contains("""<div class="hf-bg-grain"></div>""")) { "expected the grain" }
    }

    @Then("the quality background scene keeps the static background")
    fun keepsStaticBackground() {
        check(enhanced.contains("""<div class="scene-bg"></div>""")) {
            "expected the historical static background"
        }
        check(!enhanced.contains("hf-bg")) { "no media layer must be rendered" }
    }

    @Then("the quality background media layer pans across the scene")
    fun mediaLayerPans() {
        check(enhanced.contains("""t.fromTo("#hook .hf-bg-media", { scale: 1.20, yPercent: -5 }""")) {
            "expected the parallax tween on the media layer"
        }
        check(enhanced.contains("""{ yPercent: 5, duration: 5, ease: "none" }, 0);""")) {
            "expected the parallax pan to span the scene"
        }
    }

    @Then("the quality background listing block is consumed")
    fun blockConsumed() {
        check(!enhanced.contains("hyperframes-background")) {
            "the [.hyperframes-background] block must not remain in the scene content"
        }
    }

    private fun block(listing: String): String =
        """
        <div class="listingblock hyperframes-background"><div class="content"><pre>$listing</pre></div></div>
        """.trimIndent()

    private fun document(background: String): String = """
        <!DOCTYPE html>
        <html>
        <head><title>Quality background</title></head>
        <body class="article">
        <div id="content">
        <div class="sect1"><h2 id="hook">Hook</h2>$background
        <p>The document becomes signal.</p></div>
        </div>
        </body>
        </html>
    """.trimIndent()
}
