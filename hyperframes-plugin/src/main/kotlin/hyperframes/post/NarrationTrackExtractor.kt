package hyperframes.post

/**
 * Pure extractor of the narration texts from the enriched HyperFrames HTML
 * (VK-4 US-4d).
 *
 * The [hyperframes.HyperframesHtmlProcessor] turns each `[hyperframes-track]`
 * paragraph into `<div class="… hyperframes-track …"><p>…</p></div>`. The
 * narration text is the paragraph content. Media-only tracks (no `<p>`) are
 * ignored, and inline tags are stripped.
 */
object NarrationTrackExtractor {
    private val trackRegex =
        Regex(
            """<div[^>]*class="[^"]*hyperframes-track[^"]*"[^>]*>(.*?)</div>""",
            RegexOption.DOT_MATCHES_ALL,
        )
    private val speakerNoteRegex =
        Regex(
            """<div[^>]*class="[^"]*hyperframes-narration[^"]*"[^>]*>(.*?)</div>""",
            RegexOption.DOT_MATCHES_ALL,
        )
    private val paragraphRegex = Regex("""<p>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)
    private val tagRegex = Regex("<[^>]+>")

    /** Narration texts in document order (blank tracks omitted). */
    fun extract(html: String): List<String> = extractWith(trackRegex, html)

    /**
     * HF-SPEAKER-NOTES US-4 — narration texts of the **speaker-note** markers
     * only, in scene order. A document that authors notes narrates its scenes
     * (`n` matches the scene order); a historical document (no note) keeps
     * narrating its `[hyperframes-track]` paragraphs.
     */
    fun extractNarration(html: String): List<String> = extractSpeakerNotes(html).ifEmpty { extract(html) }

    /** Narration texts of the speaker-note markers only, in scene order. */
    fun extractSpeakerNotes(html: String): List<String> = extractWith(speakerNoteRegex, html)

    private fun extractWith(
        regex: Regex,
        html: String,
    ): List<String> =
        regex
            .findAll(html)
            .mapNotNull { match ->
                val paragraph = paragraphRegex.find(match.groupValues[1]) ?: return@mapNotNull null
                tagRegex.replace(paragraph.groupValues[1], "").trim().takeIf { it.isNotEmpty() }
            }.toList()
}
