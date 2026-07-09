package hyperframes.scenarios

import hyperframes.pronunciation.PronunciationDictionary
import hyperframes.pronunciation.PronunciationHint
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.assertEquals

/**
 * Cucumber steps for HF-7b — pronunciation dictionary aggregate.
 *
 * The first four scenarios operate at the dictionary level (no Gradle task);
 * the last two are end-to-end (AsciiDoc -> HTML) and require a project.
 *
 * All steps are English (BDD Gherkin convention — no `# language: fr`).
 */
class PronunciationDictionarySteps(private val world: HyperframesWorld) {

    private var dictionary: PronunciationDictionary = PronunciationDictionary()
    private var domainDictionary: PronunciationDictionary = PronunciationDictionary()
    private var authorDictionary: PronunciationDictionary = PronunciationDictionary()
    private var mergedDictionary: PronunciationDictionary = PronunciationDictionary()
    private var rendered: String = ""

    @Given("a pronunciation dictionary with a hint for word {string} speaking {string}")
    fun dictionaryWithHint(word: String, phonetic: String) {
        dictionary = PronunciationDictionary()
        dictionary.add(PronunciationHint.of(word, phonetic))
    }

    @Given("a pronunciation dictionary with a hint for word {string} speaking {string} in language {string}")
    fun dictionaryWithHintInLanguage(word: String, phonetic: String, language: String) {
        dictionary = PronunciationDictionary()
        dictionary.add(PronunciationHint.of(word, phonetic, language = language))
    }

    @Given("a domain dictionary with a hint for word {string} speaking {string}")
    fun domainDictionaryWithHint(word: String, phonetic: String) {
        domainDictionary = PronunciationDictionary()
        domainDictionary.add(PronunciationHint.of(word, phonetic))
    }

    @Given("an author dictionary with a hint for word {string} speaking {string}")
    fun authorDictionaryWithHint(word: String, phonetic: String) {
        authorDictionary = PronunciationDictionary()
        authorDictionary.add(PronunciationHint.of(word, phonetic))
    }

    @Given("an empty pronunciation dictionary")
    fun emptyDictionary() {
        dictionary = PronunciationDictionary()
    }

    @When("the dictionary renders its JSON island")
    fun renderDictionary() {
        rendered = dictionary.render()
    }

    @When("I add another hint for word {string} speaking {string} in language {string}")
    fun addAnotherHint(word: String, phonetic: String, language: String) {
        dictionary.add(PronunciationHint.of(word, phonetic, language = language))
    }

    @When("the author dictionary is merged with the domain dictionary")
    fun mergeAuthorWithDomain() {
        mergedDictionary = authorDictionary.merge(domainDictionary)
    }

    @Then("the rendered island contains the word {string}")
    fun renderedContainsWord(word: String) {
        assertThat(rendered).contains(""""word":"$word"""")
    }

    @Then("the rendered island contains the phonetic {string}")
    fun renderedContainsPhonetic(phonetic: String) {
        assertThat(rendered).contains(""""phonetic":"$phonetic"""")
    }

    @Then("the rendered island is wrapped in a script tag with id {string}")
    fun renderedIsWrappedInScriptTag(id: String) {
        assertThat(rendered).contains("""<script type="application/json" id="$id">""")
        assertThat(rendered).contains("</script>")
    }

    @Then("the dictionary contains exactly {int} hint(s)")
    fun dictionaryContainsExactly(count: Int) {
        assertEquals(count, dictionary.size())
    }

    @Then("the merged dictionary speaks {string} for word {string}")
    fun mergedSpeaks(phonetic: String, word: String) {
        assertEquals(phonetic, mergedDictionary.find(word)?.phonetic)
    }

    @Then("the merged dictionary contains exactly {int} hint(s)")
    fun mergedContainsExactly(count: Int) {
        assertEquals(count, mergedDictionary.size())
    }

    @Then("the rendered island contains an empty JSON array")
    fun renderedContainsEmptyArray() {
        assertThat(rendered).contains("[]")
    }

    @Given("the source directory contains an AsciiDoc document with a pronunciation block")
    fun sourceContainsPronunciationBlock() {
        world.createProject()
        world.writePronunciationAdoc(entries = listOf("dos" to "do"))
    }

    @Given("the source directory contains an AsciiDoc document without a pronunciation block")
    fun sourceContainsNoPronunciationBlock() {
        world.createProject()
        world.writePlainAdoc()
    }

    @Then("the generated HTML contains a JSON pronunciation island")
    fun htmlContainsPronunciationIsland() {
        val html = world.generatedHtml()
        assertThat(html).contains("""<script type="application/json" id="hf-pronunciation">""")
        assertThat(html).contains("</script>")
    }

    @Then("the generated HTML does not contain a JSON pronunciation island")
    fun htmlDoesNotContainPronunciationIsland() {
        val html = world.generatedHtml()
        assertThat(html).doesNotContain("id=\"hf-pronunciation\"")
    }

    @Then("the pronunciation island contains the word {string}")
    fun pronunciationIslandContainsWord(word: String) {
        val html = world.generatedHtml()
        assertThat(html).contains(""""word":"$word"""")
    }

    @Then("the pronunciation island contains the phonetic {string}")
    fun pronunciationIslandContainsPhonetic(phonetic: String) {
        val html = world.generatedHtml()
        assertThat(html).contains(""""phonetic":"$phonetic"""")
    }
}