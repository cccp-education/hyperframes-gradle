package hyperframes.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.assertj.core.api.Assertions.assertThat

/**
 * Cucumber steps for HF-5b — code-diff template expansion.
 * Shared steps (composition div, GSAP timeline, window.__timelines, track,
 * data-start, data-duration, data-composition-id) are inherited from
 * [TitleCardTemplateSteps] — Cucumber discovers all step definitions in
 * the glue package.
 *
 * All steps are English (BDD Gherkin convention — no `# language: fr`).
 */
class CodeDiffTemplateSteps(private val world: HyperframesWorld) {

    @Given("the source directory contains an AsciiDoc document using the code-diff template")
    fun sourceContainsCodeDiff() {
        world.writeCodeDiffAdoc(
            id = "refactor-demo",
            beforeCode = "val x = oldFunc()",
            afterCode = "val x = newFunc()",
            lang = "kotlin"
        )
    }

    @Then("the generated HTML contains a hf-code-diff-before pre block")
    fun htmlContainsBeforeBlock() {
        val html = world.generatedHtml()
        assertThat(html).contains("hf-code-diff-before")
    }

    @Then("the generated HTML contains a hf-code-diff-after pre block")
    fun htmlContainsAfterBlock() {
        val html = world.generatedHtml()
        assertThat(html).contains("hf-code-diff-after")
    }

    @Then("the generated HTML contains the before code {string}")
    fun htmlContainsBeforeCode(code: String) {
        val html = world.generatedHtml()
        assertThat(html).contains(code)
    }

    @Then("the generated HTML contains the after code {string}")
    fun htmlContainsAfterCode(code: String) {
        val html = world.generatedHtml()
        assertThat(html).contains(code)
    }

    @Then("the generated HTML contains a keyword span {string}")
    fun htmlContainsKeywordSpan(keyword: String) {
        val html = world.generatedHtml()
        assertThat(html).contains("<span class=\"hf-kw\">$keyword</span>")
    }

    @Then("the before and after pre blocks both carry the lang class {string}")
    fun preBlocksCarryLangClass(lang: String) {
        val html = world.generatedHtml()
        val occurrences = html.split("hf-lang-$lang").size - 1
        assertThat(occurrences).isGreaterThanOrEqualTo(2)
    }
}