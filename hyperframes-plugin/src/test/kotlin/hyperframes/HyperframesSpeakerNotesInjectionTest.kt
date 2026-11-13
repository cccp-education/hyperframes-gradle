package hyperframes

import hyperframes.notes.SpeakerNote
import hyperframes.notes.SpeakerNotesDocument
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * HF-SPEAKER-NOTES US-3 — the processor removes the authored note blocks from
 * the visible composition and emits one narration marker per scene, anchored to
 * the scene id and start time.
 */
class HyperframesSpeakerNotesInjectionTest {
    private fun processor(notes: SpeakerNotesDocument) =
        HyperframesHtmlProcessor(
            width = 1920,
            height = 1080,
            fps = 30,
            outputName = "speaker-notes-demo",
        ).withSpeakerNotes(notes)

    private fun admonition(text: String): String =
        """
        <div class="admonitionblock note speaker">
        <table>
        <tr>
        <td class="icon"><div class="title">Note</div></td>
        <td class="content">
        <div class="paragraph"><p>$text</p></div>
        </td>
        </tr>
        </table>
        </div>
        """.trimIndent()

    private fun section(
        id: String,
        title: String,
        note: String?,
    ): String =
        """
        <div class="sect1">
        <h2 id="$id">$title</h2>
        <div class="sectionbody">
        <div class="paragraph"><p>Visible content of $title.</p></div>
        ${if (note != null) admonition(note) else ""}
        </div>
        </div>
        """.trimIndent()

    private fun document(vararg sections: String): String =
        """
        <!DOCTYPE html>
        <html>
        <body class="article">
        <div id="content">
        ${sections.joinToString("\n")}
        </div>
        </body>
        </html>
        """.trimIndent()

    @Test
    fun `removes the note block and emits one narration marker per scene`() {
        val notes =
            SpeakerNotesDocument(
                listOf(
                    SpeakerNote(sceneIndex = 1, title = "First scene", text = "The problem is the manual copy."),
                    SpeakerNote(sceneIndex = 2, title = "Second scene", text = "The fix is the document pipeline."),
                ),
            )
        val html =
            document(
                section("intro", "First scene", "The problem is the manual copy."),
                section("demo", "Second scene", "The fix is the document pipeline."),
            )

        val result = processor(notes).enhance(html)

        assertFalse(result.contains("admonitionblock"), "the note block must not reach the visible content")
        assertContains(result, """class="hyperframes-track hyperframes-narration"""")
        assertContains(result, """data-narration-index="1"""")
        assertContains(result, """data-narration-title="First scene"""")
        assertContains(result, """data-narration-scene="intro"""")
        assertContains(result, """data-narration-scene="demo"""")
        assertContains(result, """data-narration-start="0"""")
        assertContains(result, """data-narration-start="5"""")
        assertContains(result, "<p>The problem is the manual copy.</p>")
        assertContains(result, "<p>The fix is the document pipeline.</p>")
        // one marker per narration scene, in document order
        val firstMarker = result.indexOf("data-narration-index=\"1\"")
        val secondMarker = result.indexOf("data-narration-index=\"2\"")
        assertTrue(firstMarker in 0 until secondMarker)
    }

    @Test
    fun `keeps the historical composition when the document authors no note`() {
        val html = document(section("intro", "First scene", "A visible aside."))

        val result = processor(SpeakerNotesDocument()).enhance(html)

        // D9 — no note authored: the admonition is untouched and no narration
        // marker is emitted (byte-identical historical behaviour).
        assertContains(result, "admonitionblock note speaker")
        assertFalse(result.contains("hyperframes-narration"))
        assertFalse(result.contains("data-narration-"))
    }

    @Test
    fun `registers the pronunciation hints of a note through the track path`() {
        val notes =
            SpeakerNotesDocument(
                listOf(SpeakerNote(sceneIndex = 1, title = "Scene", text = "The container hf:pron[dos, do] runs.")),
            )
        val html = document(section("intro", "Scene", "placeholder"))

        val result = processor(notes).enhance(html)

        assertFalse(result.contains("hf:pron["), "the inline hint must be stripped from the spoken text")
        assertContains(result, "<p>The container dos runs.</p>")
        assertContains(result, """"word":"dos","phonetic":"do"""")
    }

    @Test
    fun `emits no marker when the source note has no rendered block`() {
        val notes =
            SpeakerNotesDocument(
                listOf(SpeakerNote(sceneIndex = 1, title = "Scene", text = "Orphan note.")),
            )
        val html = document(section("intro", "Scene", null))

        val result = processor(notes).enhance(html)

        assertFalse(result.contains("hyperframes-narration"))
        assertEquals(0, Regex("data-narration-").findAll(result).count())
    }
}
