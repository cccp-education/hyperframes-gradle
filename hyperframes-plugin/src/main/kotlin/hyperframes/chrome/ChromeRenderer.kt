package hyperframes.chrome

import java.util.Locale
import kotlin.math.roundToLong

/**
 * HF-DEMO-QUALITY US-4 — pure renderer for the studio chrome and typography.
 *
 * No I/O, no Gradle, no clock: every value is derived from the composition
 * configuration (scene start, fps, scene index, total scenes), so the generated
 * HTML is deterministic and seek-safe.
 */
object ChromeRenderer {

    /**
     * Renders the chrome of a single scene.
     *
     * The timecode is the **scene start** (like the reference demo, where each
     * scene's top bar shows the absolute time at which the scene begins), not a
     * live per-frame clock: a frozen frame must be reproducible from the
     * timeline alone.
     *
     * @return an empty string when chrome is disabled.
     */
    fun sceneChrome(
        config: ChromeConfig,
        sceneStartSeconds: Double,
        fps: Int,
        sceneNumber: Int,
        sceneTotal: Int,
    ): String {
        if (!config.enabled) return ""
        val counter = String.format(Locale.US, "%02d / %02d", sceneNumber, sceneTotal)
        val rightLabel = if (config.label.isBlank()) counter else "${escapeText(config.label)} · $counter"

        return buildString {
            appendLine("<div class=\"hf-chrome\" data-layout-allow-occlusion>")
            if (config.cornerMarks) {
                appendLine("  <div class=\"hf-chrome-marks\" data-layout-allow-occlusion>")
                repeat(CORNER_MARKS) {
                    appendLine("    <span></span>")
                }
                appendLine("  </div>")
            }
            appendLine("  <div class=\"hf-chrome-topbar\" data-layout-allow-occlusion>")
            if (config.timecode) {
                appendLine(
                    "    <span class=\"hf-chrome-timecode\">${formatTimecode(sceneStartSeconds, fps)}</span>"
                )
            } else {
                appendLine("    <span></span>")
            }
            appendLine("    <span class=\"hf-chrome-label\">$rightLabel</span>")
            appendLine("  </div>")
            append("</div>")
        }
    }

    /**
     * Renders the global progress bar as a direct child of `#stage`.
     *
     * @return an empty string when chrome or the progress bar is disabled.
     */
    fun progressBar(config: ChromeConfig): String {
        if (!config.enabled || !config.progressBar) return ""
        return "<div class=\"hf-chrome-progress\" id=\"$PROGRESS_ID\" data-layout-allow-occlusion>" +
            "<span></span></div>"
    }

    /**
     * Root-timeline statement filling the progress bar over the whole
     * composition.
     *
     * @return an empty string when chrome or the progress bar is disabled.
     */
    fun rootProgressTween(config: ChromeConfig, totalSeconds: Double): String {
        if (!config.enabled || !config.progressBar) return ""
        val duration = number(totalSeconds)
        return "    rootTl.fromTo(\"#$PROGRESS_ID span\", { scaleX: 0 }, " +
            "{ scaleX: 1, duration: $duration, ease: \"none\" }, 0);"
    }

    /**
     * Formats [seconds] as `HH:MM:SS:FF` at [fps] frames per second.
     *
     * Negative values clamp to zero; frame counts round to the nearest frame.
     */
    fun formatTimecode(seconds: Double, fps: Int): String {
        require(fps > 0) { "fps must be positive" }
        val totalFrames = (seconds * fps).roundToLong().coerceAtLeast(0L)
        val frames = totalFrames % fps
        val totalSeconds = totalFrames / fps
        val secs = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return String.format(Locale.US, "%02d:%02d:%02d:%02d", hours, minutes, secs, frames)
    }

    /** Formats a double with a dot decimal separator (never the default locale). */
    private fun number(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString()
        else String.format(Locale.US, "%.2f", value)

    private fun escapeText(value: String): String =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private const val CORNER_MARKS = 4
    private const val PROGRESS_ID = "hf-chrome-progress"
}
