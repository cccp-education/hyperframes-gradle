package hyperframes

import hyperframes.post.EspeakTextToSpeech
import hyperframes.post.NarrationTrackExtractor
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import video.kernel.TextToSpeech

/**
 * Synthesizes narration audio from the enriched HyperFrames HTML through the
 * shared N1 port [video.kernel.TextToSpeech] (VK-4 US-4d).
 *
 * hyperframes becomes the **second consumer** of the engine-neutral narration
 * port: each `[hyperframes-track]` narration paragraph of the generated
 * `index.html` is spoken into `narration-<n>.wav` in [outputDir], so a consumer
 * can pre-render narration locally instead of relying solely on the CLI.
 *
 * Economy of ink (AGENT.adoc): an already-produced narration file is reused
 * (skip), and a synthesis that degrades (engine absent/failure) is logged,
 * never fatal — this task is opt-in and never blocks the pipeline.
 */
@DisableCachingByDefault(because = "External process-bound: calls a TTS binary (espeak)")
abstract class GenerateHyperframesNarrationTask : DefaultTask() {

    /** Generated HyperFrames HTML directory (contains `index.html`). */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val htmlDir: DirectoryProperty

    /** Narration output directory (receives `narration-<n>.wav`). */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    /** Optional TTS executable (defaults to `espeak`), resolved on the `PATH`. */
    @get:Input
    @get:Optional
    abstract val ttsExecutable: Property<String>

    /** Optional TTS voice (e.g. `fr`, `en`); blank = the engine's default. */
    @get:Input
    @get:Optional
    abstract val ttsVoice: Property<String>

    @TaskAction
    fun synthesizeNarration() {
        val htmlFile = File(htmlDir.asFile.get(), "index.html")
        require(htmlFile.exists()) {
            "No enriched HTML found at ${htmlFile.absolutePath}. Run generateHyperframesHtml first."
        }
        val outDir = outputDir.asFile.get().apply { mkdirs() }
        val narrations = NarrationTrackExtractor.extract(htmlFile.readText())
        if (narrations.isEmpty()) {
            logger.lifecycle("generateHyperframesNarration: no narration tracks found — nothing to synthesize")
            return
        }

        val tts: TextToSpeech = EspeakTextToSpeech(
            executable = ttsExecutable.orNull ?: "espeak",
            voice = ttsVoice.orNull ?: "",
        )

        var produced = 0
        narrations.forEachIndexed { index, text ->
            val output = File(outDir, "narration-${index + 1}.wav")
            if (output.exists() && output.length() > 0L) {
                produced++ // economy of ink
                return@forEachIndexed
            }
            if (tts.synthesize(text, output)) produced++
        }
        if (produced < narrations.size) {
            logger.warn("generateHyperframesNarration: {} of {} narration tracks synthesized (TTS unavailable/failed)",
                produced, narrations.size)
        }
        logger.lifecycle("generateHyperframesNarration: {} narration file(s) in {}", produced, outDir.absolutePath)
    }
}
