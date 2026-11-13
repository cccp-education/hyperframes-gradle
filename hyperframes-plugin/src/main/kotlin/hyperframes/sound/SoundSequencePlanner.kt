package hyperframes.sound

/**
 * One scene of the document, as seen by the sound planner
 * (HF-MUSIC-LIBRARY US-3).
 *
 * @property sceneId stable scene id (composition id or heading id).
 * @property startSeconds absolute start of the scene on the timeline.
 * @property music music declaration attached to the scene, or `null`.
 * @property sfx one-shot effects anchored to the scene.
 */
data class SceneSoundInput(
    val sceneId: String,
    val startSeconds: Double,
    val music: MusicDeclaration? = null,
    val sfx: List<SfxDeclaration> = emptyList(),
) {
    init {
        require(sceneId.isNotBlank()) { "sceneId must not be blank" }
        require(startSeconds.isFinite() && startSeconds >= 0.0) {
            "startSeconds must be a finite non-negative value, got $startSeconds"
        }
    }
}

/**
 * A music cue **placed** on the timeline (HF-MUSIC-LIBRARY US-3).
 *
 * @property sceneId the scene that opened the cue.
 * @property startSeconds absolute start on the timeline.
 * @property durationSeconds span of the cue (bounded by the next change or the
 *   composition end; `0.0` for a stop marker).
 * @property declaration the authored intent.
 */
data class SoundPlacement(
    val sceneId: String,
    val startSeconds: Double,
    val durationSeconds: Double,
    val declaration: MusicDeclaration,
)

/**
 * A one-shot effect placed on the timeline (HF-MUSIC-LIBRARY US-3).
 *
 * @property sceneId the scene the effect is anchored to.
 * @property startSeconds absolute start (scene start + declared offset).
 * @property declaration the authored intent.
 */
data class SfxPlacement(
    val sceneId: String,
    val startSeconds: Double,
    val declaration: SfxDeclaration,
)

/**
 * The placed sound layer of a composition (HF-MUSIC-LIBRARY US-3).
 */
data class SoundPlan(
    val music: List<SoundPlacement> = emptyList(),
    val sfx: List<SfxPlacement> = emptyList(),
) {
    fun isEmpty(): Boolean = music.isEmpty() && sfx.isEmpty()
}

/**
 * Builds the document-ordered sound layer (HF-MUSIC-LIBRARY US-3 / D12).
 *
 * Semantics, in document order:
 * 1. a scene declaring music **opens a cue**; a scene declaring the same music
 *    again is a continuity no-op (one continuous cue, no gap);
 * 2. a cue ends when another music declaration (or a stop) opens, else at the
 *    composition end — so the bed never outlives the video;
 * 3. `track: none` / `music: off` **stops** the running cue at that scene and
 *    emits a stop marker (no audio element);
 * 4. fades carry the handoff: a cue declaring no fade-out inherits the next
 *    cue's fade-in (bounded by its own span), so a crossfade is never silent;
 * 5. **US-8** — an incoming cue declaring `crossfadeSeconds > 0` extends the
 *    outgoing cue past the boundary (the two cues overlap on separate tracks),
 *    bounded by the composition end; the outgoing fade-out and incoming fade-in
 *    default to the overlap when the author declared none.
 *
 * Pure and deterministic — no I/O, no library, no LLM.
 */
object SoundSequencePlanner {
    fun plan(
        scenes: List<SceneSoundInput>,
        totalSeconds: Double,
    ): SoundPlan {
        val music = mutableListOf<SoundPlacement>()
        val sfx = mutableListOf<SfxPlacement>()
        var open: SoundPlacement? = null

        fun close(
            atSeconds: Double,
            fadeOutOverride: Double? = null,
        ) {
            val cue = open ?: return
            val span = (atSeconds - cue.startSeconds).coerceAtLeast(0.0)
            if (span > 0.0) {
                val declaration =
                    if (fadeOutOverride != null && cue.declaration.fadeOutSeconds == 0.0) {
                        cue.declaration.copy(fadeOutSeconds = fadeOutOverride)
                    } else {
                        cue.declaration
                    }
                music += cue.copy(durationSeconds = span, declaration = declaration)
            }
            open = null
        }

        scenes.forEach { scene ->
            scene.sfx.forEach { declaration ->
                sfx += SfxPlacement(scene.sceneId, scene.startSeconds + declaration.offsetSeconds, declaration)
            }
            val declaration = scene.music ?: return@forEach
            if (declaration.stop) {
                if (open != null) {
                    close(scene.startSeconds)
                    music += SoundPlacement(scene.sceneId, scene.startSeconds, 0.0, declaration)
                }
                return@forEach
            }
            if (open?.declaration?.sameMusicAs(declaration) == true) return@forEach
            val overlap = if (open != null) crossfadeSeconds(declaration, scene.startSeconds, totalSeconds) else 0.0
            close(scene.startSeconds + overlap, fadeOutOverride = overlap.takeIf { it > 0.0 })
            val incoming =
                if (overlap > 0.0 && declaration.fadeInSeconds == 0.0) {
                    declaration.copy(fadeInSeconds = overlap)
                } else {
                    declaration
                }
            open = SoundPlacement(scene.sceneId, scene.startSeconds, 0.0, incoming)
        }
        close(totalSeconds)

        return SoundPlan(music = handoffFades(music), sfx = sfx)
    }

    /** Temporal overlap with the previous cue, bounded by the composition end. */
    private fun crossfadeSeconds(
        declaration: MusicDeclaration,
        boundarySeconds: Double,
        totalSeconds: Double,
    ): Double {
        if (declaration.crossfadeSeconds <= 0.0) return 0.0
        val available = (totalSeconds - boundarySeconds).coerceAtLeast(0.0)
        return minOf(declaration.crossfadeSeconds, available)
    }

    /**
     * Carries the handoff: when a cue declares no fade-out and the next one fades
     * in, the outgoing cue fades out over the same amount (never longer than its
     * own span). A stop marker never fades — it also inherits the incoming
     * fade-in of a later restart, which is what an author hears.
     */
    private fun handoffFades(placements: List<SoundPlacement>): List<SoundPlacement> {
        if (placements.size < 2) return placements
        return placements.mapIndexed { index, placement ->
            val next = placements.getOrNull(index + 1) ?: return@mapIndexed placement
            if (placement.declaration.stop || next.declaration.stop) return@mapIndexed placement
            val incomingFade = next.declaration.fadeInSeconds
            if (placement.declaration.fadeOutSeconds > 0.0 || incomingFade <= 0.0) {
                return@mapIndexed placement
            }
            placement.copy(
                declaration =
                    placement.declaration.copy(
                        fadeOutSeconds = minOf(incomingFade, placement.durationSeconds),
                    ),
            )
        }
    }
}
