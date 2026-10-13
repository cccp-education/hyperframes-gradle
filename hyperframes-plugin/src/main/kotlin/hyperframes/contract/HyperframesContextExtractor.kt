package hyperframes.contract

/**
 * Extracts a [HyperframesParseContext] from the artifacts produced by the
 * HyperFrames pipeline (enriched HTML + rendered MP4).
 *
 * Domain Service (DDD) — pure function, no side effects.
 * Reads the `data-*` attributes of `<div id="stage">` for dimensions,
 * the `data-composition-id` for compositions,
 * the `data-track-index` for tracks,
 * and the `<script id="hf-pronunciation">` island for the TTS dictionary.
 *
 * @param pluginVersion version of the emitting plugin (injected by the Gradle task)
 * @param pronunciationDomain optional configured pronunciation domain name (D-E, S-017);
 *        echoed into the N3 `pronunciation.domain` field. `null` when no domain is configured.
 */
class HyperframesContextExtractor(
    private val pluginVersion: String,
    private val pronunciationDomain: String? = null,
) {

    /**
     * Extracts the N3 context from the HyperFrames HTML and the rendered MP4.
     *
     * @param html the enriched HyperFrames HTML (post-processor)
     * @param mp4 the rendered MP4 file (must exist on disk)
     * @param asciidocName name of the source AsciiDoc file
     * @param renderedAt ISO-8601 render timestamp
     * @param renderDurationMs render duration in ms
     * @param durationSeconds measured video duration in seconds (D-C, S-017).
     *        `0.0` when unknown (prober absent/failed).
     */
    fun extract(
        html: String,
        mp4: java.io.File,
        asciidocName: String,
        renderedAt: String,
        renderDurationMs: Long,
        durationSeconds: Double = 0.0
    ): HyperframesParseContext {
        val stage = parseStage(html)
        val compositions = parseCompositions(html)
        val tracks = parseTracks(html)
        val pronunciation = parsePronunciation(html)

        return HyperframesParseContext(
            version = pluginVersion,
            output = HyperframesVideoOutput(
                video = mp4.name,
                path = mp4.absolutePath,
                sizeBytes = if (mp4.exists()) mp4.length() else 0L,
                durationSeconds = durationSeconds,
                width = stage.width,
                height = stage.height,
                fps = stage.fps
            ),
            source = HyperframesAsciidocSource(
                asciidoc = asciidocName,
                compositions = compositions,
                tracks = tracks
            ),
            // US-2 (S-019) — neutral-model segments: one addressable unit per
            // composition boundary (whole-document engine).
            segments = compositions.mapIndexed { index, id -> HyperframesSegment(index = index, id = id) },
            pronunciation = pronunciation,
            renderedAt = renderedAt,
            renderDurationMs = renderDurationMs
        )
    }

    private data class StageAttributes(val width: Int, val height: Int, val fps: Int)

    private fun parseStage(html: String): StageAttributes {
        val stageRegex = Regex("""<div[^>]*id="stage"[^>]*>""")
        val stageMatch = stageRegex.find(html) ?: return StageAttributes(1920, 1080, 30)
        val openTag = stageMatch.value
        val width = Regex("""data-width="(\d+)"""").find(openTag)?.groupValues?.get(1)?.toIntOrNull() ?: 1920
        val height = Regex("""data-height="(\d+)"""").find(openTag)?.groupValues?.get(1)?.toIntOrNull() ?: 1080
        val fps = Regex("""data-fps="(\d+)"""").find(openTag)?.groupValues?.get(1)?.toIntOrNull() ?: 30
        return StageAttributes(width, height, fps)
    }

    private fun parseCompositions(html: String): List<String> {
        val regex = Regex("""data-composition-id="([^"]*)"""")
        return regex.findAll(html).map { it.groupValues[1] }.distinct().toList()
    }

    private fun parseTracks(html: String): Int {
        val regex = Regex("""data-track-index="(\d+)"""")
        return regex.findAll(html).map { it.groupValues[1].toInt() }.distinct().count()
    }

    /**
     * HF-7f — Parses the `<script id="hf-pronunciation">` JSON island into a
     * [HyperframesPronunciationContext]. Returns null when no island is present.
     *
     * D-E (S-017) — the configured [pronunciationDomain] is echoed into the N3
     * `domain` field (it was previously always `null`).
     */
    private fun parsePronunciation(html: String): HyperframesPronunciationContext? {
        val islandRegex = Regex(
            """<script type="application/json" id="hf-pronunciation">\[(.*?)\]</script>""",
            RegexOption.DOT_MATCHES_ALL
        )
        val match = islandRegex.find(html) ?: return null
        val arrayContent = match.groupValues[1].trim()
        if (arrayContent.isEmpty()) {
            return HyperframesPronunciationContext(domain = pronunciationDomain, hintsCount = 0, hints = emptyList())
        }

        val entryRegex = Regex(
            """\{"word":"([^"]+)","phonetic":"([^"]+)"(?:,"language":"([^"]+)")?\}"""
        )
        val entries = entryRegex.findAll(arrayContent).map { m ->
            HyperframesPronunciationEntry(
                word = m.groupValues[1],
                phonetic = m.groupValues[2],
                language = m.groupValues[3].ifEmpty { null }
            )
        }.toList()

        return HyperframesPronunciationContext(
            domain = pronunciationDomain,
            hintsCount = entries.size,
            hints = entries
        )
    }
}