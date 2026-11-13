package hyperframes.notes

/**
 * One speaker note attached to a scene of the document (HF-SPEAKER-NOTES US-2).
 *
 * The note body is authored in the AsciiDoc source as a `[NOTE.speaker]` open
 * block placed under a scene heading; it is never displayed, it is *spoken*.
 *
 * @property sceneIndex 1-based ordinal of the enclosing level-based section
 *   (the scene), in document order. The document title is not a section.
 * @property title title of the enclosing section.
 * @property text note body, trimmed.
 */
data class SpeakerNote(
    val sceneIndex: Int,
    val title: String,
    val text: String,
) {
    init {
        require(sceneIndex >= 1) { "SpeakerNote.sceneIndex must be >= 1 (was $sceneIndex)" }
        require(title.isNotBlank()) { "SpeakerNote.title must not be blank" }
        require(text.isNotBlank()) { "SpeakerNote.text must not be blank" }
    }
}

/**
 * The speaker notes authored in one document, in document order.
 *
 * An empty document (no `[NOTE.speaker]` block) is the historical case: the
 * composition stays unchanged and no narration marker is emitted.
 */
data class SpeakerNotesDocument(
    val notes: List<SpeakerNote> = emptyList(),
) {
    /** `true` when the document authors no speaker note at all. */
    fun isEmpty(): Boolean = notes.isEmpty()

    /** Number of authored notes. */
    val size: Int
        get() = notes.size

    /**
     * The note attached to the scene at the 1-based [sceneIndex] ordinal, or
     * `null` when that scene authors no note (backward-compatible silence).
     */
    fun forScene(sceneIndex: Int): SpeakerNote? = notes.firstOrNull { it.sceneIndex == sceneIndex }
}
