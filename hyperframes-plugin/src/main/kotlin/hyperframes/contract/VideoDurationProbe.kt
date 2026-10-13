package hyperframes.contract

import java.io.File

/**
 * Port (D-C, S-017) — measures the duration of a rendered video, in seconds.
 *
 * The N3 outcome descriptor (`HyperframesVideoOutput.durationSeconds`) requires a
 * **measured** duration, not a constant. Measurement is delegated behind this
 * minimal port to stay testable and degrade cleanly when the external prober
 * (`ffprobe`) is absent.
 *
 * Contract: never throws. Returns `0.0` when the duration is unknown.
 */
fun interface VideoDurationProbe {
    fun probeSeconds(video: File): Double
}

/**
 * Pure parser (stateless object) of the `ffprobe` output.
 *
 * Target command:
 * `ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 <file>`
 * prints a single decimal line (e.g. `12.5`).
 *
 * @return the duration in seconds if the output is a finite decimal >= 0, else `null`.
 */
object FfprobeDurationParser {
    fun parse(stdout: String): Double? {
        val line = stdout.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
        val value = line.toDoubleOrNull() ?: return null
        return if (value.isFinite() && value >= 0.0) value else null
    }
}

/**
 * Seam (D-C, S-017) over the `ffprobe` process — test-injectable.
 * Returns the captured standard output, or an empty string on failure.
 */
fun interface ProbeRunner {
    fun run(argv: List<String>, timeoutMillis: Long): String
}
