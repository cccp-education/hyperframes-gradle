package hyperframes.notes

import video.kernel.notes.SpeakerNoteBlockParser

/**
 * Extracts the authored speaker notes of an AsciiDoc source and maps each one
 * onto the scene it belongs to (HF-SPEAKER-NOTES US-2).
 *
 * The **grammar** is the shared N1 contract [SpeakerNoteBlockParser] (the second
 * consumer after capsule): a note is a `[NOTE.speaker]` marker followed by a
 * `--`-delimited block, attached to the **preceding** level-based section.
 *
 * This object only adds the engine-local mapping the kernel cannot know: the
 * **scene ordinal**. Scenes are the level-based sections of the document, in
 * document order; the document title is not a scene. A note authored before
 * any section, under a deeper heading, or without a body is not a note (the
 * kernel already drops those).
 *
 * Pure transform — no I/O, no Gradle, no renderer. Deterministic.
 */
object SpeakerNotesExtractor {
    /**
     * Extracts every speaker note of [source], in document order.
     *
     * @param source       the AsciiDoc source to read.
     * @param headingLevel level that starts a scene (2 = `== `).
     * @param noteMarker   marker line that opens a note block.
     * @return the speaker-notes document (empty when nothing is authored).
     */
    fun extract(
        source: String,
        headingLevel: Int = SpeakerNoteBlockParser.DEFAULT_HEADING_LEVEL,
        noteMarker: String = SpeakerNoteBlockParser.DEFAULT_NOTE_MARKER,
    ): SpeakerNotesDocument {
        val headings = sectionTitles(source, headingLevel)
        val blocks = SpeakerNoteBlockParser.parse(source, headingLevel, noteMarker)

        var cursor = 0
        val notes = mutableListOf<SpeakerNote>()
        for (block in blocks) {
            // Both scans walk the same source with the same heading rule, so a
            // block always matches a heading. The guard is defensive: an
            // unmatched block is dropped rather than mis-attached to a scene.
            while (cursor < headings.size && headings[cursor] != block.title) cursor++
            if (cursor >= headings.size) continue
            notes.add(
                SpeakerNote(
                    sceneIndex = cursor + 1,
                    title = block.title,
                    text = block.text,
                ),
            )
            cursor++
        }
        return SpeakerNotesDocument(notes)
    }

    /** Titles of the level-[headingLevel] sections, in document order. */
    private fun sectionTitles(
        source: String,
        headingLevel: Int,
    ): List<String> {
        require(headingLevel >= 1) { "headingLevel must be >= 1 (was $headingLevel)" }
        val prefix = "=".repeat(headingLevel) + " "
        return source
            .lines()
            .filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix).trim() }
    }
}
