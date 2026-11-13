package hyperframes

import hyperframes.notes.NarrationMuxCommand
import hyperframes.notes.NarrationMuxPlan
import hyperframes.notes.NarrationMuxPlanner
import hyperframes.post.CommandRunner
import hyperframes.post.ProcessBuilderCommandRunner
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * HF-SPEAKER-NOTES US-5 — muxes the synthesized speaker-note takes over the
 * rendered MP4, aligned on the scene timeline.
 *
 * The composition anchors each narration marker with its scene start
 * (`data-narration-start`); the takes are produced in the same order, so the
 * n-th take is delayed to the n-th scene start (`adelay`), all takes are mixed
 * (`amix`, levels preserved) and the video stream is copied. `-shortest` bounds
 * the audio on the video so the timeline never drifts.
 *
 * Economy of ink and degradation (AGENT.adoc):
 * - no authored note, or no take, or no rendered MP4 → the previous file is
 *   carried through, a warning is logged, the build never fails;
 * - `strict = true` promotes the same conditions to a [GradleException];
 * - the task is **not** part of `hyperframesPipeline`: it is opt-in, so a
 *   historical document keeps its exact historical pipeline.
 */
@DisableCachingByDefault(because = "External process-bound: calls FFmpeg to mux narration")
abstract class MuxHyperframesNarrationTask : DefaultTask() {
    /** Render output directory (contains `<outputName>.mp4`). */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val renderDir: DirectoryProperty

    /** Composition directory (contains the anchored `index.html`). */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val htmlDir: DirectoryProperty

    /** Synthesized takes (`narration-<n>.wav`), produced by the narration task. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val narrationFiles: ConfigurableFileCollection

    /** Mux output directory (receives `<outputName>.mp4`). */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val outputName: Property<String>

    /** Optional `ffmpeg` executable (defaults to `ffmpeg` on the `PATH`). */
    @get:Input
    @get:Optional
    abstract val ffmpegExecutable: Property<String>

    /** Promote an absent artifact / failed mux to a build failure. */
    @get:Input
    abstract val strict: Property<Boolean>

    /** Command timeout (defaults to 300 s). */
    @get:Input
    abstract val timeoutMillis: Property<Long>

    /** Seam over the external process — test-injectable. */
    @get:Internal
    var commandRunner: CommandRunner = ProcessBuilderCommandRunner()

    @TaskAction
    fun muxNarration() {
        val name = outputName.get()
        val video = File(renderDir.asFile.get(), "$name.mp4")
        val outDir = outputDir.asFile.get().apply { mkdirs() }
        val output = File(outDir, "$name.mp4")

        if (!video.exists() || video.length() == 0L) {
            degrade(
                "No rendered MP4 found at ${video.absolutePath}. Run renderHyperframes first.",
                fallback = null,
                output = output,
            )
            return
        }

        val htmlFile = File(htmlDir.asFile.get(), "index.html")
        val takes = orderedTakes()
        val plan: NarrationMuxPlan =
            if (htmlFile.exists()) {
                NarrationMuxPlanner.plan(htmlFile.readText(), takes.map { it.absolutePath })
            } else {
                NarrationMuxPlan()
            }

        if (plan.isEmpty()) {
            video.copyTo(output, overwrite = true)
            logger.lifecycle("muxHyperframesNarration: no narration take to mux — MP4 carried through")
            return
        }

        val ffmpeg = ffmpegExecutable.orNull ?: "ffmpeg"
        val argv = NarrationMuxCommand.buildArgv(video.absolutePath, output.absolutePath, plan, ffmpeg)
        val result = commandRunner.run(argv, outDir, timeoutMillis.getOrElse(DEFAULT_TIMEOUT_MS))
        val muxed = output.exists() && output.length() > 0L
        if (result.exitCode != 0 || !muxed) {
            val detail = result.output.trim().take(400)
            degrade(
                "FFmpeg narration mux failed (exit ${result.exitCode}) $detail — keeping the original MP4",
                fallback = video,
                output = output,
            )
            return
        }

        logger.lifecycle(
            "muxHyperframesNarration: muxed {} narration take(s) into {}",
            plan.size,
            output.absolutePath,
        )
    }

    private fun degrade(
        message: String,
        fallback: File?,
        output: File,
    ) {
        if (fallback != null) fallback.copyTo(output, overwrite = true)
        if (strict.getOrElse(false)) {
            throw GradleException("muxHyperframesNarration: $message — strict mode is enabled")
        }
        logger.warn("muxHyperframesNarration: $message")
    }

    /** Takes in `narration-<n>.wav` order (blank/non-matching names last). */
    private fun orderedTakes(): List<File> =
        narrationFiles.files
            .filter { it.isFile && it.length() > 0L }
            .sortedWith(compareBy({ takeIndex(it.name) ?: Int.MAX_VALUE }, { it.name }))

    private fun takeIndex(name: String): Int? =
        TAKE_NAME
            .find(name)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 300_000L
        val TAKE_NAME = Regex("""narration-(\d+)\.wav""")
    }
}
