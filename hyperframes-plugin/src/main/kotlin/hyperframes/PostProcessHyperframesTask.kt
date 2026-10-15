package hyperframes

import hyperframes.post.FfmpegAudioPostProduction
import hyperframes.post.FfmpegSubtitleBurnIn
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
import video.kernel.AudioPostSettings

/**
 * Applies the shared N1 post-production ports to the MP4 produced by
 * [RenderHyperframesTask] (VK-4 US-4b→d).
 *
 * This is where hyperframes becomes the **second consumer** of the engine-neutral
 * ports [video.kernel.SubtitleBurnIn] and [video.kernel.AudioPostProduction],
 * alongside capsule:
 * 1. burn a subtitle file (SRT/VTT) into the rendered MP4 when `subtitleFile`
 *    is configured;
 * 2. apply audio post-production (EBU R128 loudness, optional background bed and
 *    ducking) when `audioPostEnabled` is set.
 *
 * Economy of ink (AGENT.adoc): each step **degrades silently** — when the tool
 * is unavailable or the input is missing, the previous file is carried through
 * and a warning is logged; the task always writes `<outputName>.mp4` into
 * [outputDir] (never fails the build because an optional tool is absent).
 */
@DisableCachingByDefault(because = "External process-bound: calls FFmpeg for burn-in / audio post")
abstract class PostProcessHyperframesTask : DefaultTask() {

    /** Render output directory (contains `<outputName>.mp4` and `index.html`). */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val renderDir: DirectoryProperty

    /** Post-production output directory (receives `<outputName>.mp4`). */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val outputName: Property<String>

    /** Optional subtitle file (SRT/VTT) to burn into the MP4. */
    @get:Input
    @get:Optional
    abstract val subtitleFile: Property<String>

    /** Enable audio post-production (loudness normalization, optional bed). */
    @get:Input
    abstract val audioPostEnabled: Property<Boolean>

    /** Optional background-music file for the audio bed. */
    @get:Input
    @get:Optional
    abstract val bgmFile: Property<String>

    @get:Input
    abstract val bgmLevelDb: Property<Double>

    @get:Input
    abstract val loudnessTargetLufs: Property<Double>

    @get:Input
    abstract val duckingEnabled: Property<Boolean>

    /** Optional `ffmpeg` executable (defaults to `ffmpeg` on the `PATH`). */
    @get:Input
    @get:Optional
    abstract val ffmpegExecutable: Property<String>

    @TaskAction
    fun postProcess() {
        val renderDirectory = renderDir.asFile.get()
        val outDir = outputDir.asFile.get().apply { mkdirs() }
        val name = outputName.get()
        val rendered = File(renderDirectory, "$name.mp4")
        require(rendered.exists() && rendered.length() > 0L) {
            "No rendered MP4 found at ${rendered.absolutePath}. Run renderHyperframes first."
        }
        val ffmpeg = ffmpegExecutable.orNull ?: "ffmpeg"
        val tmpDir = File(outDir, "tmp").apply { mkdirs() }

        var current = rendered

        // 1. Subtitle burn-in (shared port SubtitleBurnIn).
        val subtitles = subtitleFile.orNull?.takeIf { it.isNotBlank() }?.let(::File)
        if (subtitles != null && subtitles.exists()) {
            val burned = File(tmpDir, "$name.burned.mp4")
            if (FfmpegSubtitleBurnIn(ffmpeg).burnIn(current, subtitles, burned)) {
                current = burned
                logger.lifecycle("postProcessHyperframes: burned subtitles into $name.mp4")
            } else {
                logger.warn("postProcessHyperframes: subtitle burn-in unavailable/failed — keeping original")
            }
        }

        // 2. Audio post-production (shared port AudioPostProduction).
        if (audioPostEnabled.get()) {
            val settings = AudioPostSettings(
                bgmEnabled = bgmFile.orNull?.isNotBlank() == true,
                bgmFile = bgmFile.orNull ?: "",
                bgmLevelDb = bgmLevelDb.get(),
                loudnessTargetLufs = loudnessTargetLufs.get(),
                duckingEnabled = duckingEnabled.get(),
            )
            val processed = File(tmpDir, "$name.audio.mp4")
            if (FfmpegAudioPostProduction(ffmpeg).process(current, processed, settings)) {
                current = processed
                logger.lifecycle("postProcessHyperframes: applied audio post-production to $name.mp4")
            } else {
                logger.warn("postProcessHyperframes: audio post-production unavailable/failed — keeping original")
            }
        }

        val finalFile = File(outDir, "$name.mp4")
        if (current.absolutePath != finalFile.absolutePath) {
            current.copyTo(finalFile, overwrite = true)
        }
        tmpDir.deleteRecursively()

        logger.lifecycle("postProcessHyperframes: wrote ${finalFile.absolutePath} (${finalFile.length()} bytes)")
    }
}
