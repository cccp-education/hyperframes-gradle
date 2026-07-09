package hyperframes.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.assertTrue

/**
 * Cucumber steps for HF-5c — data-chart template expansion.
 * Shared steps (composition div, GSAP timeline, window.__timelines, track,
 * data-start, data-duration, data-composition-id) are inherited from
 * [TitleCardTemplateSteps] — Cucumber discovers all step definitions in
 * the glue package.
 *
 * All steps are English (BDD Gherkin convention — no `# language: fr`).
 */
class DataChartTemplateSteps(private val world: HyperframesWorld) {

    @Given("the source directory contains an AsciiDoc document using the data-chart template")
    fun sourceContainsDataChart() {
        world.writeDataChartAdoc(
            id = "sales-chart",
            title = "Quarterly sales",
            dataPoints = listOf("Q1" to 30.0, "Q2" to 50.0)
        )
    }

    @Then("the generated HTML contains a hf-data-chart-bar with label {string} and value {string}")
    fun htmlContainsBarWithLabelAndValue(label: String, value: String) {
        val html = world.generatedHtml()
        assertThat(html).contains("data-label=\"$label\"")
        assertThat(html).contains("data-value=\"$value\"")
    }

    @Then("the generated HTML contains a stagger animation")
    fun htmlContainsStagger() {
        val html = world.generatedHtml()
        assertThat(html).contains("stagger")
    }

    @Then("the generated HTML contains a scaleY zero start")
    fun htmlContainsScaleYZero() {
        val html = world.generatedHtml()
        assertTrue(html.contains("scaleY: 0"), "HTML should animate bars from scaleY zero")
    }
}