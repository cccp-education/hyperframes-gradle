package hyperframes.post

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Seam over an external command process — test-injectable (pattern
 * [hyperframes.contract.ProbeRunner]).
 *
 * Returns the exit code and the captured output; any failure (missing
 * executable, timeout) yields a non-zero exit code so the caller degrades
 * instead of throwing (the shared N1 ports never throw).
 */
fun interface CommandRunner {
    fun run(argv: List<String>, workingDir: File, timeoutMillis: Long): CommandResult
}

/** Outcome of an external command invocation. */
data class CommandResult(
    val exitCode: Int,
    val output: String = "",
    val timedOut: Boolean = false,
)

/** Default [CommandRunner] — spawns the process via [ProcessBuilder]. */
class ProcessBuilderCommandRunner : CommandRunner {

    override fun run(argv: List<String>, workingDir: File, timeoutMillis: Long): CommandResult {
        return try {
            val process = ProcessBuilder(argv)
                .directory(workingDir)
                .redirectErrorStream(true)
                .start()
            val completed = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
            if (!completed) {
                process.destroyForcibly()
                CommandResult(exitCode = -1, output = "", timedOut = true)
            } else {
                CommandResult(exitCode = process.exitValue(), output = process.inputStream.bufferedReader().use { it.readText() })
            }
        } catch (e: Exception) {
            CommandResult(exitCode = -1, output = e.message ?: "")
        }
    }
}
