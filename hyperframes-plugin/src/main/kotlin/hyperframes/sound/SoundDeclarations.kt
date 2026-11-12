package hyperframes.sound

/**
 * A parsed `[.hyperframes-music]` declaration attached to a scene
 * (HF-MUSIC-LIBRARY US-3 / D12).
 *
 * The declaration is the **intent** written in the pilot AsciiDoc; it is never a
 * resolved file. Resolution against the music library happens in
 * [SoundCueResolver] so the same document always produces the same track.
 *
 * A scene with no declaration inherits the running music (the sequence is
 * document-ordered): silence is never implicit. `track: none` (or
 * `music: off`) is an explicit stop.
 *
 * @property trackId explicit track id (`blank` = select by [mood] or [file]).
 * @property mood aesthetic intent resolved by the library selector.
 * @property file explicit local audio file (highest precedence, no library).
 * @property volume perceived level in `0..1`.
 * @property fadeInSeconds fade-in length (`>= 0`).
 * @property fadeOutSeconds fade-out length (`>= 0`).
 * @property loop loop the track across its cue span.
 * @property stop explicitly stop the running music at this scene.
 */
data class MusicDeclaration(
    val trackId: String = "",
    val mood: String = "",
    val file: String = "",
    val volume: Double = DEFAULT_VOLUME,
    val fadeInSeconds: Double = 0.0,
    val fadeOutSeconds: Double = 0.0,
    val loop: Boolean = true,
    val stop: Boolean = false,
) {
    init {
        require(stop || trackId.isNotBlank() || mood.isNotBlank() || file.isNotBlank()) {
            "a music declaration must name a track, a file, a mood, or stop"
        }
        require(!stop || (trackId.isBlank() && mood.isBlank() && file.isBlank())) {
            "a stop declaration must not also name a track, a file, or a mood"
        }
        require(volume.isFinite() && volume in 0.0..1.0) { "volume must be in 0..1, got $volume" }
        require(fadeInSeconds.isFinite() && fadeInSeconds >= 0.0) {
            "fade-in must be a finite non-negative value, got $fadeInSeconds"
        }
        require(fadeOutSeconds.isFinite() && fadeOutSeconds >= 0.0) {
            "fade-out must be a finite non-negative value, got $fadeOutSeconds"
        }
    }

    /**
     * `true` when [other] asks for the **same music** (identity only: track, mood,
     * file). Volume/fades are per-cue and never restart a running track, so
     * re-declaring the ambience on every scene keeps one continuous cue.
     */
    fun sameMusicAs(other: MusicDeclaration): Boolean =
        trackId == other.trackId &&
            mood.equals(other.mood, ignoreCase = true) &&
            file == other.file

    companion object {
        /** Default perceived level of a music cue (quiet bed under narration). */
        const val DEFAULT_VOLUME: Double = 0.25
    }
}

/**
 * A parsed `[.hyperframes-sfx]` declaration attached to a scene
 * (HF-MUSIC-LIBRARY US-3 / D12).
 *
 * A sound effect is a one-shot cue anchored to its scene: it starts at
 * `scene start + offset` and keeps its intrinsic length.
 *
 * @property file audio file reference (library id or local path).
 * @property offsetSeconds delay from the scene start (`>= 0`).
 * @property volume perceived level in `0..1`.
 */
data class SfxDeclaration(
    val file: String,
    val offsetSeconds: Double = 0.0,
    val volume: Double = DEFAULT_VOLUME,
) {
    init {
        require(file.isNotBlank()) { "an sfx declaration must name a file" }
        require(offsetSeconds.isFinite() && offsetSeconds >= 0.0) {
            "sfx offset must be a finite non-negative value, got $offsetSeconds"
        }
        require(volume.isFinite() && volume in 0.0..1.0) { "volume must be in 0..1, got $volume" }
    }

    companion object {
        /** Default perceived level of a one-shot sound effect. */
        const val DEFAULT_VOLUME: Double = 0.25
    }
}
