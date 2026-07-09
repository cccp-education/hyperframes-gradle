package hyperframes.scenarios

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import org.assertj.core.api.Assertions.assertThat

/**
 * Cucumber steps for HF-7 evolution — pronunciation domain wiring.
 *
 * Verifies the full AsciiDoc -> HTML pipeline with an optional pre-built
 * domain dictionary loaded from classpath resources and merged with author
 * hints (block + inline). Author hints override domain hints on conflict;
 * domain-only words are kept as the baseline dictionary.
 *
 * All steps are English (BDD Gherkin convention — no `# language: fr`).
 */
class PronunciationDomainSteps(private val world: HyperframesWorld) {

    @Given("the project uses the pronunciation domain {string}")
    fun projectUsesPronunciationDomain(domain: String) {
        world.pronunciationDomain = domain
        world.createProject()
    }

    @Given("the source directory contains an AsciiDoc document with a pronunciation block overriding narration")
    fun sourceContainsPronunciationBlockOverridingNarration() {
        world.writePronunciationAdoc(entries = listOf("narration" to "my-override"))
    }

    @Then("the pronunciation island does not contain the phonetic {string}")
    fun pronunciationIslandDoesNotContainPhonetic(phonetic: String) {
        val html = world.generatedHtml()
        assertThat(html).doesNotContain(""""phonetic":"$phonetic"""")
    }
}