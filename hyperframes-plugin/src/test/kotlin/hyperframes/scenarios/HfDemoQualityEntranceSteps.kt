package hyperframes.scenarios

import hyperframes.HyperframesHtmlProcessor
import hyperframes.entrance.EntranceConfig
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

/**
 * HF-DEMO-QUALITY US-5 — acceptance for the per-scene entrance choreography.
 * Pure processor, no Gradle, no CLI, no network.
 *
 * Steps are prefixed (`quality entrance …`) to avoid glue collisions (S-088).
 */
class HfDemoQualityEntranceSteps {

    private var source: String = ""
    private var enhanced: String = ""

    @Given("a quality entrance document with two scenes")
    fun documentWithTwoScenes() {
        source = document(
            """
            <div class="sect1"><h2 id="one">One</h2><div class="sectionbody"><div class="paragraph"><p>Body</p></div></div></div>
            <div class="sect1"><h2 id="two">Two</h2><div class="sectionbody"><div class="paragraph"><p>Body</p></div></div></div>
            """.trimIndent(),
        )
    }

    @When("I enhance the quality entrance document without entrances")
    fun enhanceWithoutEntrances() {
        enhanced = processor().enhance(source)
    }

    @When("I enhance the quality entrance document with entrances enabled")
    fun enhanceWithEntrances() {
        enhanced = processor().withEntrances(EntranceConfig(enabled = true)).enhance(source)
    }

    @When("I enhance the quality entrance document with a {string} second offset")
    fun enhanceWithOffset(offset: String) {
        enhanced = processor()
            .withEntrances(EntranceConfig(enabled = true, offsetSeconds = offset.toDouble()))
            .enhance(source)
    }

    @Then("the quality entrance keeps the grouped scene animation")
    fun groupedAnimationKept() {
        check(enhanced.contains("""t.fromTo("#one .scene-inner", { opacity: 0, y: 30 }""")) {
            "expected the historical grouped entrance by default"
        }
    }

    @Then("the quality entrance declares no load-time setup")
    fun noSetup() {
        check(!enhanced.contains("var items = gsap.utils.toArray")) {
            "no load-time atom setup must be emitted without opt-in"
        }
    }

    @Then("the quality entrance hides the atoms at load")
    fun hidesAtomsAtLoad() {
        check(enhanced.contains("gsap.set(items, { opacity: 0, y: 30 });")) {
            "expected the load-time hidden state"
        }
    }

    @Then("the quality entrance staggers the atoms by {string} seconds")
    fun staggersAtoms(stagger: String) {
        check(enhanced.contains("stagger: $stagger")) {
            "expected a $stagger second stagger"
        }
    }

    @Then("the quality entrance scopes the atoms to each scene")
    fun scopesAtomsPerScene() {
        check(enhanced.contains("""gsap.utils.toArray("#one .scene-inner""")) {
            "expected a scene-one scoped selector"
        }
        check(enhanced.contains("""gsap.utils.toArray("#two .scene-inner""")) {
            "expected a scene-two scoped selector"
        }
    }

    @Then("the quality entrance replaces the grouped scene animation")
    fun replacesGroupedAnimation() {
        check(!enhanced.contains("""t.fromTo("#one .scene-inner""")) {
            "the grouped entrance must be replaced when choreography is enabled"
        }
    }

    @Then("the quality entrance starts the first atom at {string} seconds")
    fun firstAtomOffset(offset: String) {
        check(enhanced.contains("}, $offset);")) {
            "expected the first atom to start at $offset seconds"
        }
    }

    private fun processor() = HyperframesHtmlProcessor(
        width = 1920,
        height = 1080,
        fps = 30,
        outputName = "quality-entrance-demo",
    )

    private fun document(sections: String): String = """
        <!DOCTYPE html>
        <html>
        <head><title>Quality entrances</title></head>
        <body class="article">
        <div id="content">
        $sections
        </div>
        </body>
        </html>
    """.trimIndent()
}
