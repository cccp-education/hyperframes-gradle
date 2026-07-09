package hyperframes.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.assertTrue

/**
 * Cucumber steps for HF-5d — kinetic-captions template expansion.
 * Shared steps (composition div, GSAP timeline, window.__timelines, track,
 * data-start, data-duration, data-composition-id) are inherited from
 * [TitleCardTemplateSteps] — Cucumber discovers all step definitions in
 * the glue package.
 *
 * All steps are English (BDD Gherkin convention — no `# language: fr`).
 */
class KineticCaptionsTemplateSteps(private val world: HyperframesWorld) {

    @Given("the source directory contains an AsciiDoc document using the kinetic-captions template")
    fun sourceContainsKineticCaptions() {
        world.writeKineticCaptionsAdoc(
            id = "intro-captions",
            title = "Intro captions",
            captions = listOf(
                Triple(0.0, 2.0, "Hello world"),
                Triple(2.5, 4.5, "This is a kinetic caption")
            )
        )
    }

    @Then("the generated HTML contains a hf-kinetic-caption with start {string} and end {string} and text {string}")
    fun htmlContainsCaptionWithStartEndText(start: String, end: String, text: String) {
        val html = world.generatedHtml()
        assertThat(html).contains("class=\"hf-kinetic-caption\"")
        assertThat(html).contains("data-start=\"$start\"")
        assertThat(html).contains("data-end=\"$end\"")
        assertThat(html).contains(">$text<")
    }

    @Then("the generated HTML contains display none captions")
    fun htmlContainsDisplayNoneCaptions() {
        val html = world.generatedHtml()
        assertThat(html).contains("display:none")
    }

    @Then("the generated HTML contains opacity one for show")
    fun htmlContainsOpacityOne() {
        val html = world.generatedHtml()
        assertTrue(html.contains("opacity: 1"), "HTML should show captions with opacity one")
    }

    @Then("the generated HTML contains opacity zero for hide")
    fun htmlContainsOpacityZero() {
        val html = world.generatedHtml()
        assertTrue(html.contains("opacity: 0"), "HTML should hide captions with opacity zero")
    }
}