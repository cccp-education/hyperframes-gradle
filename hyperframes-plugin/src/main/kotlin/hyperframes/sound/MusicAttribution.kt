package hyperframes.sound

/**
 * Music credits of a composition (HF-MUSIC-LIBRARY US-7 / D10).
 *
 * A `cc-by` track **must** be credited in the publication description; omitting
 * it silently would be a licence breach. The manifest carries the required
 * credit line (`attribution`); when it is blank the title/artist fallback is
 * used, so a credit is never lost.
 *
 * Only the tracks **actually referenced by the composition** are credited: the
 * `<audio data-track-id>` clips are the proof of use.
 */
object MusicAttribution {
    private const val PLACEHOLDER_LICENCE = "CC BY"

    /** Credits of the tracks referenced by [html], in document order, deduplicated. */
    fun lines(
        html: String,
        library: MusicLibrary,
    ): List<String> = lines(MusicAssetPlanner.requests(html), library)

    /** Credits of the tracks referenced by [requests] (composition order, dedup). */
    fun lines(
        requests: List<MusicAssetRequest>,
        library: MusicLibrary,
    ): List<String> =
        requests
            .mapNotNull { library.find(it.trackId) }
            .distinctBy { it.id }
            .filter { it.requiresAttribution() }
            .map(::creditLine)

    /**
     * Appends [lines] as a final block of [description].
     *
     * No credit → the description is returned **unchanged** (byte-identical);
     * the caller can therefore append unconditionally.
     */
    fun append(
        description: String,
        lines: List<String>,
    ): String {
        if (lines.isEmpty()) return description
        val body = description.trimEnd()
        val block = lines.joinToString("\n")
        return if (body.isEmpty()) block else "$body\n\n$block"
    }

    private fun creditLine(track: MusicTrack): String {
        val declared = track.attribution.trim()
        if (declared.isNotEmpty()) return declared
        val artist = track.artist.trim()
        val suffix = if (artist.isEmpty()) "($PLACEHOLDER_LICENCE)" else "— $artist ($PLACEHOLDER_LICENCE)"
        return "Music: ${track.title.trim()} $suffix"
    }
}
