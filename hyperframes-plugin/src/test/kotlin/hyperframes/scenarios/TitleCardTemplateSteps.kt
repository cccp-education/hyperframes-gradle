package hyperframes.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Cucumber steps for HF-5a — title-card template expansion.
 * All steps are English (BDD Gherkin convention — no `# language: fr`).
 */
class TitleCardTemplateSteps(private val world: HyperframesWorld) {

    @Given("the source directory contains an AsciiDoc document using the title-card template")
    fun sourceContainsTitleCard() {
        world.writeTitleCardAdoc(
            id = "intro",
            title = "My Formation",
            subtitle = "Module 01"
        )
    }

    @Given("the source directory contains an AsciiDoc document with a title-card template but no subtitle")
    fun sourceContainsTitleCardNoSubtitle() {
        world.writeTitleCardAdoc(
            id = "solo",
            title = "Solo Title",
            subtitle = null
        )
    }

    @When("I run the generateHyperframesHtml task")
    fun runGenerateHtml() {
        world.runTask("generateHyperframesHtml")
    }

    @Then("the generated HTML contains a hyperframes-composition div")
    fun htmlContainsCompositionDiv() {
        val html = world.generatedHtml()
        assertTrue(html.contains("hyperframes-composition"), "HTML should contain a composition div")
    }

    @Then("the composition div has data-composition-id {string}")
    fun compositionIdIs(id: String) {
        val html = world.generatedHtml()
        assertThat(html).contains("data-composition-id=\"$id\"")
    }

    @Then("the HTML contains a GSAP timeline")
    fun htmlContainsGsapTimeline() {
        val html = world.generatedHtml()
        assertThat(html).contains("gsap.timeline")
    }

    @Then("the HTML contains window.__timelines")
    fun htmlContainsTimelinesVar() {
        val html = world.generatedHtml()
        assertThat(html).contains("window.__timelines")
    }

    @Then("the generated HTML contains the title {string}")
    fun htmlContainsTitle(title: String) {
        val html = world.generatedHtml()
        assertThat(html).contains(title)
    }

    @Then("the generated HTML contains the subtitle {string}")
    fun htmlContainsSubtitle(subtitle: String) {
        val html = world.generatedHtml()
        assertThat(html).contains(subtitle)
    }

    @Then("the generated HTML contains a hyperframes-track block")
    fun htmlContainsTrack() {
        val html = world.generatedHtml()
        assertThat(html).contains("hyperframes-track")
    }

    @Then("the track has data-start {string}")
    fun trackStartIs(start: String) {
        val html = world.generatedHtml()
        assertThat(html).contains("data-start=\"$start\"")
    }

    @Then("the track has data-duration {string}")
    fun trackDurationIs(duration: String) {
        val html = world.generatedHtml()
        assertThat(html).contains("data-duration=\"$duration\"")
    }

    @Then("the generated HTML does not contain a subtitle span")
    fun htmlDoesNotContainSubtitle() {
        val html = world.generatedHtml()
        assertFalse(html.contains("hf-title-card-subtitle"), "HTML should not contain a subtitle span")
    }
}