package hyperframes.sound

/**
 * Outcome of resolving a placed music cue against the library
 * (HF-MUSIC-LIBRARY US-3).
 */
sealed interface SoundResolution {
    /**
     * The cue points to a real audio asset (relative to the composition).
     *
     * @property src composition-relative path.
     * @property trackId library id when the asset comes from the library (blank
     *   for an author-supplied `file:`, which needs no copy).
     */
    data class Resolved(
        val src: String,
        val trackId: String = "",
    ) : SoundResolution

    /** The cue cannot be resolved; [reason] is actionable and secret-free. */
    data class Unresolved(
        val reason: String,
    ) : SoundResolution

    /** The cue is an explicit stop marker: no audio element. */
    data object Silence : SoundResolution
}

/**
 * Resolves a placed cue to an audio asset (HF-MUSIC-LIBRARY US-3).
 *
 * The resolution is pure: it reads the shared N1 selector and the manifest, and
 * returns the path the resolve step must produce (`assets/audio/<track-id>.<ext>`).
 * The copy itself (sha256 skip, economy of ink) belongs to the Gradle task
 * (`resolveHyperframesBgm`, US-4) — the processor never touches the disk.
 *
 * Precedence:
 * 1. an explicit `file:` (author-supplied asset, no library needed);
 * 2. the library, through [MusicTrackSelector] (explicit id, then mood);
 * 3. otherwise unresolved — an authored cue is never silently dropped.
 */
object SoundCueResolver {
    /** Composition-relative directory holding the resolved audio assets. */
    const val AUDIO_ASSET_DIR: String = "assets/audio"

    private const val DEFAULT_EXTENSION = "mp3"

    fun resolve(
        placement: SoundPlacement,
        library: MusicLibrary?,
    ): SoundResolution {
        val declaration = placement.declaration
        if (declaration.stop) return SoundResolution.Silence
        if (declaration.file.isNotBlank()) return SoundResolution.Resolved(declaration.file)
        if (library == null) {
            return SoundResolution.Unresolved(
                "scene '${placement.sceneId}': no music library configured for " +
                    "${describe(declaration)} (declare a 'file:' or set music.libraryFile)",
            )
        }
        return when (val selection = MusicTrackSelector.select(library, declaration.trackId, declaration.mood)) {
            is MusicSelection.Selected ->
                SoundResolution.Resolved(
                    assetPath(selection.track.id, selection.track.file),
                    selection.track.id,
                )
            is MusicSelection.Rejected -> SoundResolution.Unresolved("scene '${placement.sceneId}': ${selection.reason}")
        }
    }

    /** `assets/audio/<id>.<ext>` — the path the resolve step must produce. */
    fun assetPath(
        trackId: String,
        trackFile: String,
    ): String {
        val extension =
            trackFile
                .substringAfterLast('/')
                .substringAfterLast('.', "")
                .trim()
                .lowercase()
                .ifBlank { DEFAULT_EXTENSION }
        return "$AUDIO_ASSET_DIR/${trackId.trim()}.$extension"
    }

    /** An sfx reference is used as declared (a file path or a resolved library id). */
    fun resolveSfx(placement: SfxPlacement): String? {
        val file = placement.declaration.file.trim()
        if (file.isEmpty()) return null
        return if (file.contains('/')) file else "$AUDIO_ASSET_DIR/$file"
    }

    private fun describe(declaration: MusicDeclaration): String =
        when {
            declaration.trackId.isNotBlank() -> "track '${declaration.trackId}'"
            declaration.mood.isNotBlank() -> "mood '${declaration.mood}'"
            else -> "music cue"
        }
}
