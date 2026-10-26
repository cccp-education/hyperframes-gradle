package hyperframes.scenarios

import hyperframes.HyperframesHtmlProcessor
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When

/**
 * HF-DEMO-QUALITY US-2 — acceptance for DSL scene transitions (v1 cover
 * transitions). Pure processor, no Gradle/CLI/network.
 *
 * Steps are prefixed (`quality transition …`) to avoid glue collisions.
 */
class HfDemoQualityTransitionSteps {

    private var source: String = ""
    private var enhanced: String = ""

    @Given("a quality transition document whose first scene declares {string}")
    fun documentWithTransition(kind: String) {
        source = document(
            """
            <div class="sect1 hyperframes-composition hf-transition-$kind" id="one"><h2 id="one">One</h2></div>
            <div class="sect1"><h2 id="two">Two</h2></div>
            """.trimIndent(),
        )
    }

    @Given("a quality transition document without any transition")
    fun documentWithoutTransition() {
        source = document(
            """
            <div class="sect1"><h2 id="one">One</h2></div>
            <div class="sect1"><h2 id="two">Two</h2></div>
            """.trimIndent(),
        )
    }

    @When("I enhance the quality transition document with the HyperFrames processor")
    fun enhanceDocument() {
        enhanced = HyperframesHtmlProcessor(
            width = 1920,
            height = 1080,
            fps = 30,
            outputName = "quality-transition-demo",
        ).enhance(source)
    }

    @Then("the quality transition overlay is {string}")
    fun overlayIs(kind: String) {
        check(enhanced.contains("""class="hf-transition hf-transition-$kind" id="hf-transition-1"""")) {
            "expected a '$kind' cover overlay in the composition"
        }
    }

    @Then("the quality transition grid has {int} cells")
    fun gridHasCells(count: Int) {
        val actual = Regex("""class="hf-transition-cell"""").findAll(enhanced).count()
        check(actual == count) { "expected $count grid cells, was $actual" }
    }

    @Then("the quality transition root timeline is registered")
    fun rootTimelineRegistered() {
        check(enhanced.contains("""rootTl""")) { "the root timeline must drive the transition" }
        check(enhanced.contains("""window.__timelines["root"] = rootTl;""")) {
            "the root timeline must be registered under the root composition"
        }
    }

    @Then("the quality transition overlay is absent")
    fun overlayAbsent() {
        check(!enhanced.contains("""class="hf-transition hf-transition-""")) {
            "no cover overlay must be emitted without a transition role"
        }
    }

    @Then("the quality transition keeps the historical root timeline")
    fun historicalRootTimeline() {
        check(enhanced.contains("""window.__timelines["root"] = gsap.timeline({ paused: true });""")) {
            "a transition-free composition must keep the historical empty root timeline"
        }
    }

    @Then("the quality transitioned scene keeps its content until the boundary")
    fun sceneDoesNotFadeBeforeBoundary() {
        check(!enhanced.contains("""t.to("#one", { opacity: 0""")) {
            "the outgoing scene must stay visible until the cover overlay hands off"
        }
    }

    @Then("the quality overlap starts at {string} seconds")
    fun overlapStartsAt(seconds: String) {
        check(enhanced.contains("""id="two" data-composition-id="two" data-start="$seconds"""")) {
            "the incoming scene must start at $seconds, before the outgoing boundary"
        }
    }

    @Then("the quality transition blur is declared")
    fun blurDeclared() {
        check(enhanced.contains("""gsap.set("#two", { opacity: 0, filter: "blur(18px)" });""")) {
            "a blur-crossfade must initialise the incoming scene blurred and transparent"
        }
    }

    @Then("the quality transition push is declared")
    fun pushDeclared() {
        check(enhanced.contains("""gsap.set("#two", { yPercent: 100 });""")) {
            "a vertical-push must initialise the incoming scene below the frame"
        }
    }

    private fun document(sections: String): String = """
        <!DOCTYPE html>
        <html>
        <head><title>Quality transition</title></head>
        <body class="article">
        <div id="header"><h1>Demo</h1></div>
        <div id="content">
        $sections
        </div>
        </body>
        </html>
    """.trimIndent()
}
