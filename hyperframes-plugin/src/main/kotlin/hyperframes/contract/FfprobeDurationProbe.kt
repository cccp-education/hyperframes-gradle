package hyperframes.contract

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Default [ProbeRunner] — spawns `ffprobe` via [ProcessBuilder].
 *
 * Any failure (missing executable, non-zero exit, timeout) yields an empty
 * string so the caller degrades to `0.0` (D-C, S-017).
 */
class ProcessBuilderProbeRunner : ProbeRunner {

    override fun run(argv: List<String>, timeoutMillis: Long): String {
        return try {
            val process = ProcessBuilder(argv)
                .redirectErrorStream(true)
                .start()
            val completed = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
            if (!completed) {
                process.destroyForcibly()
                ""
            } else {
                process.inputStream.bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            ""
        }
    }
}

/**
 * `ffprobe`-backed [VideoDurationProbe] (D-C, S-017).
 *
 * Degrades to `0.0` when `ffprobe` is missing, fails, or returns an unparsable
 * value — a build must never fail because an optional prober is unavailable.
 *
 * @param ffprobeExecutable resolved `ffprobe` binary (default: `PATH` lookup).
 */
class FfprobeDurationProbe(
    private val ffprobeExecutable: String = "ffprobe",
    private val runner: ProbeRunner = ProcessBuilderProbeRunner(),
    private val timeoutMillis: Long = 15_000L,
) : VideoDurationProbe {

    override fun probeSeconds(video: File): Double {
        if (!video.exists() || video.length() == 0L) return 0.0
        val argv = listOf(
            ffprobeExecutable,
            "-v", "error",
            "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1",
            video.absolutePath,
        )
        return FfprobeDurationParser.parse(runner.run(argv, timeoutMillis)) ?: 0.0
    }
}
