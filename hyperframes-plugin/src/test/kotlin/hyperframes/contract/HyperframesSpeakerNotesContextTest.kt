package hyperframes.contract

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * HF-SPEAKER-NOTES US-6 — the speaking scenes exposed in the N3 metadata.json.
 */
class HyperframesSpeakerNotesContextTest {
    private fun extract(html: String): HyperframesParseContext {
        val mp4 = Files.createTempFile("hyperframes-speaker-notes", ".mp4").toFile().apply { deleteOnExit() }
        return HyperframesContextExtractor("0.0.16").extract(
            html = html,
            mp4 = mp4,
            asciidocName = "index.adoc",
            renderedAt = "2026-11-13T00:00:00Z",
            renderDurationMs = 1,
        )
    }

    @Test
    fun `exposes the speaking scenes in document order`() {
        val html =
            """
            <div id="stage" data-width="1920" data-height="1080" data-fps="30">
              <div class="scene clip" data-composition-id="root">
                <div class="hyperframes-track hyperframes-narration" data-narration-index="1" data-narration-title="First scene" data-narration-scene="intro" data-narration-start="0"><p>One.</p></div>
                <div class="hyperframes-track hyperframes-narration" data-narration-index="2" data-narration-title="Second scene" data-narration-scene="demo" data-narration-start="5"><p>Two.</p></div>
              </div>
            </div>
            """.trimIndent()

        val context = extract(html)

        assertThat(context.speakerNotes)
            .containsExactly(
                HyperframesSpeakerNoteContext(sceneId = "intro", title = "First scene"),
                HyperframesSpeakerNoteContext(sceneId = "demo", title = "Second scene"),
            )
    }

    @Test
    fun `returns an empty list when no note is authored`() {
        val html = """<div id="stage" data-width="1920" data-height="1080" data-fps="30"></div>"""

        assertThat(extract(html).speakerNotes).isEmpty()
    }

    @Test
    fun `decodes the escaped attribute entities of a title`() {
        val html =
            """
            <div id="stage">
              <div class="hyperframes-track hyperframes-narration" data-narration-title="A &amp; B &quot;quoted&quot;" data-narration-scene="intro" data-narration-start="0"><p>One.</p></div>
            </div>
            """.trimIndent()

        assertThat(extract(html).speakerNotes.single())
            .isEqualTo(HyperframesSpeakerNoteContext(sceneId = "intro", title = "A & B \"quoted\""))
    }
}
