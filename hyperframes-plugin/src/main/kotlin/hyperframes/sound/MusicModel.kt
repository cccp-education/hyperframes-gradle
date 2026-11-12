package hyperframes.sound

/**
 * Rights under which a music track may be used in a produced video
 * (HF-MUSIC-LIBRARY US-4).
 *
 * **Temporary N2 copy of the `video.kernel.music` contract.** The shared kernel
 * published `video.kernel.music` in `0.8.0`, which is only available on JitPack:
 * a *published plugin* cannot rely on it, because its POM dependencies must be
 * resolvable from the consumer's repositories (Maven Central / Plugin Portal).
 * Until `education.cccp:video-kernel:0.8.0` reaches Central, hyperframes keeps
 * this verbatim copy (the cadrage D4 fallback), and will re-point to the N1
 * contract at the channel reset.
 *
 * Fail-closed: anything that is not an explicitly allowed licence is [UNKNOWN]
 * and can never be selected for a publication.
 */
enum class MusicLicence {
    /** The producer owns the track (own composition, own recording). */
    OWN,

    /** Public-domain dedication (CC0). */
    CC0,

    /** Creative Commons Attribution: reuse allowed **with attribution**. */
    CC_BY,

    /** Royalty-free licence purchased/free-to-use under its terms. */
    ROYALTY_FREE,

    /** Explicit written licence for this use. */
    LICENSED,

    /** Unknown or undeclared: never usable. */
    UNKNOWN,
    ;

    /** `true` when the licence obliges the video description to credit the author. */
    val requiresAttribution: Boolean get() = this == CC_BY

    companion object {
        /**
         * Parses a declared licence (`own`, `cc0`, `cc-by`, `royalty-free`,
         * `licensed`), case/`_`/`-` insensitive. Anything else is [UNKNOWN].
         */
        fun parse(raw: String): MusicLicence =
            when (raw.trim().lowercase().replace('_', '-')) {
                "own" -> OWN
                "cc0" -> CC0
                "cc-by" -> CC_BY
                "royalty-free" -> ROYALTY_FREE
                "licensed" -> LICENSED
                else -> UNKNOWN
            }
    }
}

/**
 * One track of the shared music library (HF-MUSIC-LIBRARY US-4).
 *
 * @property id stable identifier used by the AsciiDoc sound cues.
 * @property title human title (diagnostic/attribution).
 * @property artist author/performer (attribution).
 * @property file relative path of the audio file (audio root).
 * @property sha256 content fingerprint (copy skip — economy of ink).
 * @property durationSeconds measured duration (0 when unprobed).
 * @property licence rights under which the track may be used.
 * @property sourceUrl provenance URL (never used at render time).
 * @property attribution credit line required by the licence (may be blank).
 * @property tags free-form aesthetic tags (`piano`, `strings`, `ambient`…).
 * @property mood primary aesthetic intent (`calm`, `epic`, `documentary`…).
 * @property energy intensity in `0..100` (selection hint).
 */
data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String = "",
    val file: String = "",
    val sha256: String = "",
    val durationSeconds: Double = 0.0,
    val licence: MusicLicence = MusicLicence.UNKNOWN,
    val sourceUrl: String = "",
    val attribution: String = "",
    val tags: List<String> = emptyList(),
    val mood: String = "",
    val energy: Int = 0,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(title.isNotBlank()) { "title must not be blank" }
        require(durationSeconds.isFinite() && durationSeconds >= 0.0) {
            "durationSeconds must be a finite non-negative value, got $durationSeconds"
        }
        require(energy in 0..100) { "energy must be in 0..100, got $energy" }
    }

    /**
     * `true` when the track may be selected: a declared, allowed licence **and**
     * an audio file reference.
     */
    fun isUsable(): Boolean = licence != MusicLicence.UNKNOWN && file.isNotBlank()

    /** `true` when the licence obliges an attribution line. */
    fun requiresAttribution(): Boolean = licence.requiresAttribution

    /** `true` when [mood] matches [candidate] (case-insensitive, blank never matches). */
    fun hasMood(candidate: String): Boolean = mood.isNotBlank() && mood.equals(candidate.trim(), ignoreCase = true)
}

/**
 * A set of music tracks with its manifest schema version
 * (HF-MUSIC-LIBRARY US-4).
 *
 * @property schemaVersion manifest schema version (`>= 1`).
 * @property tracks all declared tracks, usable or not.
 */
data class MusicLibrary(
    val schemaVersion: Int = 1,
    val tracks: List<MusicTrack> = emptyList(),
) {
    init {
        require(schemaVersion >= 1) { "schemaVersion must be >= 1, got $schemaVersion" }
    }

    /** The track with [id], or `null`. */
    fun find(id: String): MusicTrack? = tracks.firstOrNull { it.id == id.trim() }

    /** Tracks that may actually be selected (declared licence + audio file). */
    fun usable(): List<MusicTrack> = tracks.filter { it.isUsable() }

    /** Usable tracks whose mood matches [mood]. */
    fun byMood(mood: String): List<MusicTrack> = usable().filter { it.hasMood(mood) }
}

/**
 * Outcome of a music selection (HF-MUSIC-LIBRARY US-4).
 */
sealed interface MusicSelection {
    /** A usable track was found. */
    data class Selected(
        val track: MusicTrack,
    ) : MusicSelection

    /** Nothing can be selected; [reason] is actionable and secret-free. */
    data class Rejected(
        val reason: String,
    ) : MusicSelection
}

/**
 * Deterministic track selection (HF-MUSIC-LIBRARY US-4).
 *
 * Pure and LLM-free on purpose: the same document must resolve to the same
 * track. Precedence:
 * 1. an explicit [trackId] (unknown or unusable → rejected, never substituted);
 * 2. a [mood] filter over usable tracks;
 * 3. any usable track.
 *
 * Ties are broken by `id` (stable, locale-independent) so a build is
 * reproducible.
 */
object MusicTrackSelector {
    fun select(
        library: MusicLibrary,
        trackId: String = "",
        mood: String = "",
    ): MusicSelection {
        if (trackId.isNotBlank()) {
            val track =
                library.find(trackId)
                    ?: return MusicSelection.Rejected("unknown track id '${trackId.trim()}'")
            if (!track.isUsable()) {
                return MusicSelection.Rejected(
                    "track '${track.id}' is not usable (licence=${track.licence}, file=${track.file.ifBlank { "none" }})",
                )
            }
            return MusicSelection.Selected(track)
        }

        val candidates =
            if (mood.isNotBlank()) {
                library.byMood(mood).ifEmpty {
                    return MusicSelection.Rejected("no usable track for mood '${mood.trim()}'")
                }
            } else {
                library.usable().ifEmpty {
                    return MusicSelection.Rejected("music library has no usable track")
                }
            }
        return MusicSelection.Selected(candidates.sortedBy { it.id }.first())
    }
}
