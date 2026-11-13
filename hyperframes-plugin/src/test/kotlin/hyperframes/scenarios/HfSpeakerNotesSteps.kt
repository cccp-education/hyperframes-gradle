package hyperframes.scenarios

import hyperframes.HyperframesHtmlProcessor
import hyperframes.contract.HyperframesContextExtractor
import hyperframes.contract.HyperframesParseContext
import hyperframes.notes.NarrationMuxCommand
import hyperframes.notes.NarrationMuxPlan
import hyperframes.notes.NarrationMuxPlanner
import hyperframes.notes.SpeakerNotesDocument
import hyperframes.notes.SpeakerNotesExtractor
import hyperframes.post.NarrationTrackExtractor
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.assertj.core.api.Assertions.assertThat
import java.nio.file.Files

/**
 * HF-SPEAKER-NOTES — BDD acceptance for the authored notes becoming the
 * per-scene narration (US-2 → US-3) and for the N3 traceability (US-6).
 *
 * Pure domain path (no TestKit, no TTS, no FFmpeg): the steps build the source,
 * extract the notes through the shared N1 grammar, run the processor, then read
 * the emitted markers. Steps are prefixed ("speaker notes …") so the
 * classpath-wide Cucumber glue never collides (S-088).
 */
class HfSpeakerNotesSteps {
    private var source: String = ""
    private var html: String = ""
    private var notes: SpeakerNotesDocument = SpeakerNotesDocument()
    private var composition: String = ""
    private var context: HyperframesParseContext? = null
    private var muxPlan: NarrationMuxPlan = NarrationMuxPlan()

    @Given("a speaker notes document with a note under each scene")
    fun notesUnderEachScene() {
        val scenes =
            listOf(
                Scene("intro", "First scene", "The problem is the manual copy."),
                Scene("demo", "Second scene", "The fix is the document pipeline."),
            )
        source = document(scenes)
        html = rendered(scenes)
    }

    @Given("a speaker notes document whose first scene has no note")
    fun firstSceneWithoutNote() {
        val scenes =
            listOf(
                Scene("silent", "Silent scene", null),
                Scene("demo", "Speaking scene", "Only the second scene speaks."),
            )
        source = document(scenes)
        html = rendered(scenes)
    }

    @Given("a speaker notes document with a pronunciation hint in the note")
    fun noteWithPronunciationHint() {
        val scenes = listOf(Scene("intro", "Scene", "The container hf:pron[dos, do] runs."))
        source = document(scenes)
        html = rendered(scenes)
    }

    @When("I build the speaker notes composition")
    fun buildComposition() {
        notes = SpeakerNotesExtractor.extract(source)
        composition = processor().enhance(html)
    }

    @When("I build the speaker notes N3 context")
    fun buildN3Context() {
        notes = SpeakerNotesExtractor.extract(source)
        composition = processor().enhance(html)
        val mp4 = Files.createTempFile("hyperframes-speaker-notes", ".mp4").toFile().apply { deleteOnExit() }
        context =
            HyperframesContextExtractor("0.0.16").extract(
                html = composition,
                mp4 = mp4,
                asciidocName = "index.adoc",
                renderedAt = "2026-11-13T00:00:00Z",
                renderDurationMs = 1,
            )
    }

    @When("I plan the narration mux with takes {string}")
    fun planNarrationMux(takes: String) {
        muxPlan = NarrationMuxPlanner.plan(composition, takes.split("|"))
    }

    @Then("the narration mux filter is {string}")
    fun muxFilter(expected: String) {
        assertThat(NarrationMuxCommand.filterGraph(muxPlan)).isEqualTo(expected)
    }

    @Then("the composition exposes {int} speaker notes narration markers")
    fun markerCount(count: Int) {
        assertThat(markers()).hasSize(count)
    }

    @Then("the speaker notes blocks are removed from the visible content")
    fun blocksRemoved() {
        assertThat(composition).doesNotContain("admonitionblock")
    }

    @Then("the speaker notes spoken texts are {string}")
    fun spokenTexts(expected: String) {
        assertThat(NarrationTrackExtractor.extract(composition))
            .containsExactlyElementsOf(expected.split("|"))
    }

    @Then("the speaker notes marker of scene {string} starts at {string}")
    fun markerStart(
        sceneId: String,
        start: String,
    ) {
        assertThat(composition).contains("data-narration-scene=\"$sceneId\" data-narration-start=\"$start\"")
    }

    @Then("the speaker notes marker of scene {string} is absent")
    fun markerAbsent(sceneId: String) {
        assertThat(composition).doesNotContain("data-narration-scene=\"$sceneId\"")
    }

    @Then("the speaker notes composition registers the pronunciation hint {string} -> {string}")
    fun hintRegistered(
        word: String,
        phonetic: String,
    ) {
        assertThat(composition).contains("\"word\":\"$word\",\"phonetic\":\"$phonetic\"")
    }

    @Then("the speaker notes N3 context lists the scenes {string}")
    fun contextScenes(expected: String) {
        assertThat(context?.speakerNotes?.map { it.sceneId })
            .containsExactlyElementsOf(expected.split("|"))
        assertThat(context?.speakerNotes?.map { it.title })
            .containsExactlyElementsOf(listOf("First scene", "Second scene"))
    }

    private fun processor(): HyperframesHtmlProcessor =
        HyperframesHtmlProcessor(
            width = 1920,
            height = 1080,
            fps = 30,
            outputName = "speaker-notes-demo",
        ).withSpeakerNotes(notes)

    private fun markers(): List<String> =
        Regex("""<div class="hyperframes-track hyperframes-narration"[^>]*>""")
            .findAll(composition)
            .map { it.value }
            .toList()

    private data class Scene(
        val id: String,
        val title: String,
        val note: String?,
    )

    /** The AsciiDoc source: one level-2 section per scene, notes authored inline. */
    private fun document(scenes: List<Scene>): String =
        buildString {
            appendLine("= Speaker notes demo")
            appendLine()
            scenes.forEach { scene ->
                appendLine("[.hyperframes-composition#${scene.id}]")
                appendLine("== ${scene.title}")
                appendLine()
                appendLine("Visible content of ${scene.title}.")
                appendLine()
                if (scene.note != null) {
                    appendLine("[NOTE.speaker]")
                    appendLine("--")
                    appendLine(scene.note)
                    appendLine("--")
                    appendLine()
                }
            }
        }

    /** The Asciidoctor-rendered HTML the processor receives (notes are admonitions). */
    private fun rendered(scenes: List<Scene>): String {
        val sections =
            scenes.joinToString("\n") { scene ->
                """
                <div class="sect1">
                <h2 id="${scene.id}">${scene.title}</h2>
                <div class="sectionbody">
                <div class="paragraph"><p>Visible content of ${scene.title}.</p></div>
                ${
                    if (scene.note != null) {
                        """
                        <div class="admonitionblock note speaker">
                        <table><tr><td class="content"><div class="paragraph"><p>${scene.note}</p></div></td></tr></table>
                        </div>
                        """.trimIndent()
                    } else {
                        ""
                    }
                }
                </div>
                </div>
                """.trimIndent()
            }
        return """
            <!DOCTYPE html>
            <html>
            <body class="article">
            <div id="content">
            $sections
            </div>
            </body>
            </html>
            """.trimIndent()
    }
}
