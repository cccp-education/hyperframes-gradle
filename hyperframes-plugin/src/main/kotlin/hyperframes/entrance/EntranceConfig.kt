package hyperframes.entrance

/**
 * HF-DEMO-QUALITY US-5 — per-scene choreographed entrances.
 *
 * The historical renderer animated the whole `.scene-inner` wrapper as one
 * block: every heading, paragraph and code block appeared together, leaving
 * long dead stretches afterwards. When [enabled], the processor hides the
 * scene's content atoms at load (`gsap.set`) and tweens them in with a
 * deterministic stagger, scene by scene.
 *
 * Opt-in (`false` by default) so an existing composition keeps its historical
 * entrance unless the author asks for the choreography.
 *
 * @property enabled           master switch (default `false`)
 * @property staggerSeconds    delay between two atoms (default `0.12`)
 * @property durationSeconds   duration of one atom entrance (default `0.55`)
 * @property offsetSeconds     delay before the first atom (default `0.10`)
 * @property yOffset           initial vertical offset in pixels (default `30`)
 */
data class EntranceConfig(
    val enabled: Boolean = false,
    val staggerSeconds: Double = 0.12,
    val durationSeconds: Double = 0.55,
    val offsetSeconds: Double = 0.10,
    val yOffset: Double = 30.0,
) {
    init {
        require(staggerSeconds >= 0.0) { "staggerSeconds must be >= 0" }
        require(durationSeconds > 0.0) { "durationSeconds must be > 0" }
        require(offsetSeconds >= 0.0) { "offsetSeconds must be >= 0" }
    }
}
