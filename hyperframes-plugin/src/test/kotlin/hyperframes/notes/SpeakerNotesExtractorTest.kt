package hyperframes.notes

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * HF-SPEAKER-NOTES US-2 — the extraction of the authored `[NOTE.speaker]`
 * blocks, mapped onto the surrounding scene (level-2 section) ordinal.
 */
class SpeakerNotesExtractorTest {
    @Test
    fun `extracts one note per section in document order`() {
        val source =
            """
            = Demo document

            [.hyperframes-composition#intro]
            == First scene

            Some visible content.

            [NOTE.speaker]
            --
            This scene explains the problem.
            --

            [.hyperframes-composition#demo]
            == Second scene

            More visible content.

            [NOTE.speaker]
            --
            This scene shows the fix.
            --
            """.trimIndent()

        val document = SpeakerNotesExtractor.extract(source)

        assertThat(document.notes)
            .containsExactly(
                SpeakerNote(sceneIndex = 1, title = "First scene", text = "This scene explains the problem."),
                SpeakerNote(sceneIndex = 2, title = "Second scene", text = "This scene shows the fix."),
            )
    }

    @Test
    fun `skips sections without a note but keeps the section ordinals`() {
        val source =
            """
            == Silent scene

            No note here.

            == Speaking scene

            [NOTE.speaker]
            --
            Only this one speaks.
            --
            """.trimIndent()

        val document = SpeakerNotesExtractor.extract(source)

        assertThat(document.notes).containsExactly(
            SpeakerNote(sceneIndex = 2, title = "Speaking scene", text = "Only this one speaks."),
        )
    }

    @Test
    fun `ignores a note authored before any section`() {
        val source =
            """
            = Doc

            [NOTE.speaker]
            --
            Orphan note.
            --

            == Scene

            [NOTE.speaker]
            --
            Attached note.
            --
            """.trimIndent()

        assertThat(SpeakerNotesExtractor.extract(source).notes)
            .containsExactly(SpeakerNote(sceneIndex = 1, title = "Scene", text = "Attached note."))
    }

    @Test
    fun `accepts a marker carrying attributes`() {
        val source =
            """
            == Scene

            [NOTE.speaker,role="aside"]
            --
            Attribute marker note.
            --
            """.trimIndent()

        assertThat(SpeakerNotesExtractor.extract(source).notes)
            .containsExactly(SpeakerNote(sceneIndex = 1, title = "Scene", text = "Attribute marker note."))
    }

    @Test
    fun `keeps multi-line note bodies`() {
        val source =
            """
            == Scene

            [NOTE.speaker]
            --
            First line.

            Second line.
            --
            """.trimIndent()

        assertThat(
            SpeakerNotesExtractor
                .extract(source)
                .notes
                .single()
                .text,
        ).isEqualTo("First line.\n\nSecond line.")
    }

    @Test
    fun `maps duplicate section titles to successive ordinals`() {
        val source =
            """
            == Repeated

            [NOTE.speaker]
            --
            First occurrence.
            --

            == Repeated

            [NOTE.speaker]
            --
            Second occurrence.
            --
            """.trimIndent()

        assertThat(SpeakerNotesExtractor.extract(source).notes)
            .containsExactly(
                SpeakerNote(sceneIndex = 1, title = "Repeated", text = "First occurrence."),
                SpeakerNote(sceneIndex = 2, title = "Repeated", text = "Second occurrence."),
            )
    }

    @Test
    fun `returns an empty document when nothing is authored`() {
        val document = SpeakerNotesExtractor.extract("== Scene\n\nOnly visible content.\n")

        assertThat(document.isEmpty()).isTrue()
        assertThat(document.size).isZero()
        assertThat(document.forScene(1)).isNull()
    }

    @Test
    fun `honours a custom heading level and marker`() {
        val source =
            """
            === Deep scene

            [NOTE.voice]
            --
            Custom grammar.
            --
            """.trimIndent()

        val document =
            SpeakerNotesExtractor.extract(
                source = source,
                headingLevel = 3,
                noteMarker = "[NOTE.voice]",
            )

        assertThat(document.notes)
            .containsExactly(SpeakerNote(sceneIndex = 1, title = "Deep scene", text = "Custom grammar."))
    }
}
