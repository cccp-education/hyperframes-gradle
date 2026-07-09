package hyperframes.contract

/**
 * N3 contract — context exported by hyperframes-gradle for runner-gradle.
 *
 * Single source of truth for the `metadata.json` format exchanged between
 * the N2 plugin (Watts) and the N3 orchestrator (New Jersey).
 *
 * @property plugin id of the emitting plugin (always "hyperframes-gradle")
 * @property version version of the emitting plugin
 * @property output descriptor of the produced MP4 video
 * @property source descriptor of the original AsciiDoc document
 * @property pronunciation optional descriptor of the TTS pronunciation dictionary
 * @property renderedAt ISO-8601 render timestamp
 * @property renderDurationMs render duration in milliseconds
 */
data class HyperframesParseContext(
    val plugin: String = "hyperframes-gradle",
    val version: String,
    val output: HyperframesVideoOutput,
    val source: HyperframesAsciidocSource,
    val pronunciation: HyperframesPronunciationContext? = null,
    val renderedAt: String,
    val renderDurationMs: Long
) {
    companion object {
        const val PLUGIN_ID = "hyperframes-gradle"
    }
}

/**
 * Descriptor of the produced MP4 video rendered by HyperFrames.
 *
 * @property video MP4 file name (relative to outputDir)
 * @property path absolute path of the MP4 file
 * @property sizeBytes file size in bytes
 * @property durationSeconds duration in seconds (0.0 if unknown)
 * @property width width in pixels
 * @property height height in pixels
 * @property fps frames per second
 * @property codec video codec (default "h264")
 */
data class HyperframesVideoOutput(
    val video: String,
    val path: String,
    val sizeBytes: Long,
    val durationSeconds: Double,
    val width: Int,
    val height: Int,
    val fps: Int,
    val codec: String = "h264"
)

/**
 * Descriptor of the original AsciiDoc source document.
 *
 * @property asciidoc name of the source AsciiDoc file
 * @property compositions list of detected composition ids
 * @property tracks number of detected tracks
 */
data class HyperframesAsciidocSource(
    val asciidoc: String,
    val compositions: List<String>,
    val tracks: Int
)

/**
 * HF-7f — Descriptor of the TTS pronunciation dictionary embedded in the N3
 * metadata.json for traceability and audit.
 *
 * @property domain the domain dictionary name if any (e.g. "fpa-fr"), or null
 * @property hintsCount number of hints in the dictionary
 * @property hints list of pronunciation hints
 */
data class HyperframesPronunciationContext(
    val domain: String? = null,
    val hintsCount: Int,
    val hints: List<HyperframesPronunciationEntry>
)

/**
 * One pronunciation hint entry in the N3 metadata.json.
 *
 * @property word the word to correct (normalized lowercase)
 * @property phonetic the phonetic approximation the TTS should speak
 * @property language optional language tag for disambiguation
 */
data class HyperframesPronunciationEntry(
    val word: String,
    val phonetic: String,
    val language: String? = null
)