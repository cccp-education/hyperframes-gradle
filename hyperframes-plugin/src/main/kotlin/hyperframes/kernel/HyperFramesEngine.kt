package hyperframes.kernel

import capsule.kernel.VideoEngine
import capsule.kernel.VideoRenderRequest
import capsule.kernel.VideoRenderResult
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
 * Purpose of the spike: prove (or falsify) that the neutral port is sufficient
 * for a second, non-reveal engine.
 */
open class HyperFramesEngine(
    private val config: HyperFramesEngineConfig,
    private val runner: HyperFramesRenderRunner = ProcessBuilderHyperFramesRunner(),
) : VideoEngine {

    override fun name(): String = "hyperframes"

    override fun isAvailable(): Boolean =
        File(config.cliScript).exists() && File(config.nodeExecutable).exists()

    override fun render(request: VideoRenderRequest): VideoRenderResult {
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
        val result = runner.run(argv, outputDir, timeout)
        val produced = if (result.exitCode == 0 && outputMp4.exists() && outputMp4.length() > 0L) {
            outputMp4
        } else {
            null
        }
        return VideoRenderResult(produced = produced)
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
