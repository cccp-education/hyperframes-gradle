package hyperframes.media

/**
 * A per-scene background image declared by an `[.hyperframes-background]` block
 * (HF-DEMO-QUALITY US-6).
 *
 * The image is rendered full-bleed behind the scene content, with an optional
 * legibility scrim and technical overlays (grid, grain). Pure model — no I/O,
 * no Gradle.
 *
 * @property file    relative path of the image, resolved against the input dir
 *                   by [hyperframes.ReferencedAssets].
 * @property opacity media opacity in `[0.0, 1.0]` (default `0.55`).
 * @property scrim   render the left-to-right legibility scrim (default `true`).
 * @property grid    render the technical grid overlay (default `false`).
 * @property grain   render the film-grain overlay (default `false`).
 * @property parallax slow media pan across the scene, `0.0` (off) to `0.25`
 *                    (default `0.0` — opt-in, US-7).
 */
data class BackgroundImage(
    val file: String,
    val opacity: Double = DEFAULT_OPACITY,
    val scrim: Boolean = true,
    val grid: Boolean = false,
    val grain: Boolean = false,
    val parallax: Double = DEFAULT_PARALLAX,
) {
    init {
        require(file.isNotBlank()) { "BackgroundImage.file must not be blank" }
        require(opacity in MIN_OPACITY..MAX_OPACITY) {
            "BackgroundImage.opacity must be in [$MIN_OPACITY, $MAX_OPACITY], got $opacity"
        }
        require(parallax in MIN_PARALLAX..MAX_PARALLAX) {
            "BackgroundImage.parallax must be in [$MIN_PARALLAX, $MAX_PARALLAX], got $parallax"
        }
    }

    companion object {
        const val DEFAULT_OPACITY: Double = 0.55
        const val MIN_OPACITY: Double = 0.0
        const val MAX_OPACITY: Double = 1.0
        const val DEFAULT_PARALLAX: Double = 0.0
        const val MIN_PARALLAX: Double = 0.0
        const val MAX_PARALLAX: Double = 0.25
    }
}
