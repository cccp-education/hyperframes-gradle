package hyperframes.kernel

import video.kernel.VideoEngine
import video.kernel.VideoOutcome
import video.kernel.VideoRenderRequest
import video.kernel.VideoRenderResult
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Seam over the external HyperFrames CLI process (test-injectable).
 */
fun interface HyperFramesRenderRunner {
    fun run(argv: List<String>, workingDir: File, timeoutMillis: Long): HyperFramesProcessResult
}

/** Outcome of an external CLI invocation. */
data class HyperFramesProcessResult(
    val exitCode: Int,
    val output: String = "",
    val timedOut: Boolean = false,
)

/**
 * Adapter configuration (engine wiring, not part of the shared contract).
 *
 * @property nodeExecutable resolved `node` binary.
 * @property cliScript      the HyperFrames CLI script (npm package).
 * @property timeoutMillis  default CLI timeout.
 */
data class HyperFramesEngineConfig(
    val nodeExecutable: String,
    val cliScript: String,
    val timeoutMillis: Long = 300_000L,
    val width: Int = 0,
    val height: Int = 0,
    val codec: String = "h264",
)

/**
 * Detailed outcome of one engine render (adapter-level diagnostics).
 *
 * [result] is the neutral port result; [process] carries the raw CLI outcome
 * (exit code / timeout / output) so a Gradle task can turn it into an actionable
 * failure message while the shared port stays contract-pure.
 */
data class HyperFramesRenderOutcome(
    val result: VideoRenderResult,
    val process: HyperFramesProcessResult,
)

/**
 * SPIKE (video-kernel 0.0.2 falsification) — HyperFrames as the **2nd engine**
 * behind the shared [VideoEngine] port.
 *
 * This is deliberately a **whole-document** engine: it renders the enriched
 * `index.html` (the [VideoRenderRequest.visual]) into one MP4 via the HyperFrames
 * CLI. It has **no** notion of segments, durations, viewport or parallelism —
 * exactly the capabilities the thinned kernel no longer imposes.
 *
 * US-3 (S-018) — this adapter is now the **production** render path:
 * `RenderHyperframesTask` delegates to [renderDetailed] instead of running the
 * CLI itself (no duplicated CLI code).
 */
open class HyperFramesEngine(
    private val config: HyperFramesEngineConfig,
    private val runner: HyperFramesRenderRunner = ProcessBuilderHyperFramesRunner(),
) : VideoEngine {

    override fun name(): String = "hyperframes"

    override fun isAvailable(): Boolean =
        File(config.cliScript).exists() && File(config.nodeExecutable).exists()

    /**
     * US-3 (S-018) — the shared port entry point. Delegates to [renderDetailed]
     * and returns the neutral result.
     */
    override fun render(request: VideoRenderRequest): VideoRenderResult = renderDetailed(request).result

    /**
     * US-3 (S-018) — renders the whole document and keeps the raw CLI process
     * outcome for adapter-level diagnostics (the Gradle task maps it to
     * actionable messages: timeout, non-zero exit, output).
     */
    fun renderDetailed(request: VideoRenderRequest): HyperFramesRenderOutcome {
        val htmlFile = request.visual.file
        val outputDir = request.outputDir.apply { mkdirs() }
        val outputMp4 = outputDir.resolve("${request.name}.mp4")
        val timeout = if (request.timeoutMillis > 0) request.timeoutMillis else config.timeoutMillis

        val argv = listOf(
            config.nodeExecutable,
            config.cliScript,
            "render",
            "--input", htmlFile.absolutePath,
            "--output", outputMp4.absolutePath,
        )
        val process = runner.run(argv, outputDir, timeout)
        val produced = if (process.exitCode == 0 && outputMp4.exists() && outputMp4.length() > 0L) {
            outputMp4
        } else {
            null
        }
        val outcome = produced?.let {
            // duration is unknown here (the CLI does not report it); a consumer
            // that needs it probes the file or reads the N3 parse context.
            VideoOutcome(
                durationSeconds = 0.0,
                width = config.width,
                height = config.height,
                sizeBytes = it.length(),
                codec = config.codec,
            )
        }
        return HyperFramesRenderOutcome(
            result = VideoRenderResult(produced = produced, outcome = outcome),
            process = process,
        )
    }

    override fun close() {}
}

/**
 * Default [HyperFramesRenderRunner] — spawns the CLI via [ProcessBuilder],
 * bounded by [HyperFramesEngineConfig.timeoutMillis].
 */
class ProcessBuilderHyperFramesRunner : HyperFramesRenderRunner {

    override fun run(argv: List<String>, workingDir: File, timeoutMillis: Long): HyperFramesProcessResult {
        val process = ProcessBuilder(argv)
            .directory(workingDir)
            .redirectErrorStream(true)
            .start()
        val completed = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
        if (!completed) {
            process.destroyForcibly()
            return HyperFramesProcessResult(exitCode = -1, output = "", timedOut = true)
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        return HyperFramesProcessResult(exitCode = process.exitValue(), output = output)
    }
}
