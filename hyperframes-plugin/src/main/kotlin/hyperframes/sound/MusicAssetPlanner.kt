package hyperframes.sound

/**
 * One music asset the composition references
 * (HF-MUSIC-LIBRARY US-4).
 *
 * @property trackId library id carried by the `<audio data-track-id>` attribute.
 * @property src composition-relative path the runtime loads
 *   (`assets/audio/<track-id>.<ext>`).
 */
data class MusicAssetRequest(
    val trackId: String,
    val src: String,
)

/**
 * One asset to place next to the composition (HF-MUSIC-LIBRARY US-4).
 *
 * @property trackId library id (diagnostic).
 * @property src composition-relative target.
 * @property source library-relative source path (audio root + manifest file).
 * @property sha256 expected content hash from the manifest (blank = unknown).
 */
data class MusicAssetCopy(
    val trackId: String,
    val src: String,
    val source: String,
    val sha256: String = "",
)

/**
 * Verdict of the asset resolution (HF-MUSIC-LIBRARY US-4).
 *
 * @property copies assets to write.
 * @property upToDate assets already byte-identical (economy of ink).
 * @property missing actionable reasons (asset or track absent).
 */
data class MusicAssetPlan(
    val copies: List<MusicAssetCopy> = emptyList(),
    val upToDate: List<MusicAssetCopy> = emptyList(),
    val missing: List<String> = emptyList(),
) {
    fun isEmpty(): Boolean = copies.isEmpty() && upToDate.isEmpty() && missing.isEmpty()
}

/**
 * Plans the copy of the resolved library tracks next to the composition
 * (HF-MUSIC-LIBRARY US-4).
 *
 * The composition HTML is the contract: the processor emits
 * `<audio class="hyperframes-music" data-track-id="…" src="assets/audio/…">`, so
 * the planner never re-parses the pilot document and cannot drift from it.
 *
 * Pure: the file system is injected ([existsSha256]), so the skip rule
 * (`sha256` identical → no copy) is unit-tested without touching a disk.
 */
object MusicAssetPlanner {
    private val AUDIO_TAG = Regex("""<audio\b[^>]*>""", RegexOption.IGNORE_CASE)
    private val TRACK_ID_ATTR = Regex("""\bdata-track-id="([^"]*)"""")
    private val SRC_ATTR = Regex("""\bsrc="([^"]*)"""")

    /** Music assets referenced by [html], in document order, deduplicated. */
    fun requests(html: String): List<MusicAssetRequest> =
        AUDIO_TAG
            .findAll(html)
            .mapNotNull { tag ->
                val trackId = attribute(TRACK_ID_ATTR, tag.value) ?: return@mapNotNull null
                val src = attribute(SRC_ATTR, tag.value) ?: return@mapNotNull null
                MusicAssetRequest(trackId, src)
            }.filter { it.trackId.isNotBlank() && it.src.isNotBlank() }
            .distinct()
            .toList()

    /**
     * Builds the copy plan.
     *
     * @param requests music assets referenced by the composition.
     * @param library manifest the audio root belongs to.
     * @param audioRoot directory holding the library audio (manifest `file` paths
     *   are relative to it).
     * @param existsSha256 hash of the **existing target** for a `src`, or `null`
     *   when the target is absent.
     */
    fun plan(
        requests: List<MusicAssetRequest>,
        library: MusicLibrary,
        audioRoot: String,
        existsSha256: (String) -> String?,
    ): MusicAssetPlan {
        val copies = mutableListOf<MusicAssetCopy>()
        val upToDate = mutableListOf<MusicAssetCopy>()
        val missing = mutableListOf<String>()

        requests.forEach { request ->
            val track = library.find(request.trackId)
            if (track == null) {
                missing += "track '${request.trackId}' is referenced by the composition but absent from the music library"
                return@forEach
            }
            if (track.file.isBlank()) {
                missing += "track '${track.id}' has no audio file in the music library"
                return@forEach
            }
            val asset = MusicAssetCopy(track.id, request.src, join(audioRoot, track.file), track.sha256)
            val existing = existsSha256(request.src)
            if (existing != null && track.sha256.isNotBlank() && existing.equals(track.sha256, ignoreCase = true)) {
                upToDate += asset
            } else {
                copies += asset
            }
        }
        return MusicAssetPlan(copies = copies, upToDate = upToDate, missing = missing)
    }

    private fun attribute(
        pattern: Regex,
        tag: String,
    ): String? =
        pattern
            .find(tag)
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    private fun join(
        root: String,
        file: String,
    ): String {
        val cleanRoot = root.trim().trimEnd('/', '\\')
        val cleanFile = file.trim().trimStart('/', '\\')
        return if (cleanRoot.isEmpty()) cleanFile else "$cleanRoot/$cleanFile"
    }
}
