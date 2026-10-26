package hyperframes.chrome

/**
 * HF-DEMO-QUALITY US-4 — configures the studio chrome rendered on top of every
 * scene of a HyperFrames composition.
 *
 * The chrome is **opt-in** ([enabled] defaults to `false`) so existing
 * compositions keep their historical rendering byte-for-byte. When enabled it
 * adds the demo-video furniture: corner marks, a top bar carrying a scene
 * timecode and a scene counter, and a global progress bar driven by the root
 * timeline.
 *
 * @property enabled       master switch (default `false`, backward compatible)
 * @property timecode      render the per-scene `HH:MM:SS:FF` timecode
 * @property cornerMarks   render the four corner registration marks
 * @property progressBar   render the global bottom progress bar
 * @property label         optional programme label shown before the scene counter
 */
data class ChromeConfig(
    val enabled: Boolean = false,
    val timecode: Boolean = true,
    val cornerMarks: Boolean = true,
    val progressBar: Boolean = true,
    val label: String = "",
) {
    /** True when chrome is enabled and at least one element will be rendered. */
    val isRenderable: Boolean
        get() = enabled && (timecode || cornerMarks || progressBar || label.isNotBlank())
}
