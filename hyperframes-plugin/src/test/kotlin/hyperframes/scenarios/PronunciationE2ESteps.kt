package hyperframes.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Cucumber steps for HF-7g — pronunciation end-to-end integration.
 *
 * These scenarios drive the full AsciiDoc -> HTML pipeline via the
 * `generateHyperframesHtml` Gradle task and assert the pronunciation
 * JSON island is present (or absent) in the rendered output.
 *
 * Shared steps (run task, JSON island assertions) are inherited from
 * [PronunciationDictionarySteps] and [TitleCardTemplateSteps] — Cucumber
 * discovers all step definitions in the glue package.
 *
 * All steps are English (BDD Gherkin convention — no `# language: fr`).
 */
class PronunciationE2ESteps(private val world: HyperframesWorld) {

    @Given("the source directory contains an AsciiDoc document with a pronunciation block having two entries")
    fun sourceContainsPronunciationTwoEntries() {
        world.writePronunciationTwoEntriesAdoc()
    }

    @Given("the source directory contains an AsciiDoc document with an inline pronunciation hint in a track")
    fun sourceContainsInlineHint() {
        world.writeInlineHintAdoc()
    }

    @Then("the rendered track text contains the word {string}")
    fun renderedTrackContainsWord(word: String) {
        val html = world.generatedHtml()
        assertThat(html).contains(word)
    }

    @Then("the rendered track text does not contain the inline hint syntax")
    fun renderedTrackDoesNotContainHintSyntax() {
        val html = world.generatedHtml()
        assertFalse(
            html.contains("hf:pron["),
            "inline hint syntax should be stripped from rendered text"
        )
    }
}