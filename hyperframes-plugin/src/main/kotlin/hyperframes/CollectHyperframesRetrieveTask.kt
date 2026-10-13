package hyperframes

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import hyperframes.contract.FfprobeDurationProbe
import hyperframes.contract.HyperframesContextExtractor
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.time.Instant

@DisableCachingByDefault(because = "Filesystem-bound: reads rendered MP4 + HTML and exports N3 contract")
abstract class CollectHyperframesRetrieveTask : DefaultTask() {

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @get:Input
    @get:Optional
    abstract val asciidocName: Property<String>

    @get:Input
    abstract val pluginVersion: Property<String>

    /**
     * D-E (S-017) — configured pronunciation domain name echoed into the N3
     * `pronunciation.domain` field (was always `null`).
     */
    @get:Input
    @get:Optional
    abstract val pronunciationDomain: Property<String>

    /**
     * D-C (S-017) — optional `ffprobe` executable used to measure the MP4 duration.
     * Absent → `ffprobe` on the PATH; unavailable → degraded `durationSeconds = 0.0`.
     */
    @get:Input
    @get:Optional
    abstract val ffprobeExecutable: Property<String>

    /**
     * D-F (S-017) — when `true`, missing rendered artifacts fail the task instead
     * of being silently skipped (degraded default `false`).
     */
    @get:Input
    abstract val strict: Property<Boolean>

    @TaskAction
    fun execute() {
        val output = outputFile.asFile.get()
        output.parentFile.mkdirs()

        val outDir = output.parentFile
        val mp4 = outDir.listFiles()?.firstOrNull { it.extension.equals("mp4", ignoreCase = true) }
        val html = outDir.listFiles()?.firstOrNull { it.name.equals("index.html", ignoreCase = true) }

        val mapper = jacksonObjectMapper()

        if (mp4 == null || html == null) {
            val message =
                "[hyperframes] collectHyperframesRetrieve — no MP4/HTML artifacts in ${outDir.absolutePath} " +
                    "(mp4=${mp4 != null}, html=${html != null})"
            if (strict.getOrElse(false)) {
                throw GradleException("$message — strict mode is enabled")
            }
            logger.lifecycle(message)
            return
        }

        val htmlContent = html.readText()
        val source = asciidocName.orNull ?: inferAsciidocName(outDir)
        val startedAt = System.currentTimeMillis()
        val durationSeconds = FfprobeDurationProbe(ffprobeExecutable.orNull ?: "ffprobe").probeSeconds(mp4)
        val extractor = HyperframesContextExtractor(pluginVersion.get(), pronunciationDomain.orNull)
        val context = extractor.extract(
            html = htmlContent,
            mp4 = mp4,
            asciidocName = source,
            renderedAt = Instant.now().toString(),
            renderDurationMs = System.currentTimeMillis() - startedAt,
            durationSeconds = durationSeconds
        )

        mapper.writerWithDefaultPrettyPrinter().writeValue(output, context)

        Metadata.writeTo(
            outDir,
            Metadata.forWatts(type = "retrieve", sessions = 1, dependencies = listOf("brooklyn"))
        )

        logger.lifecycle(
            "[hyperframes] collectHyperframesRetrieve — {}x{}, {} compositions, {} tracks → {}",
            context.output.width, context.output.height,
            context.source.compositions.size, context.source.tracks,
            output.absolutePath
        )
    }

    private fun inferAsciidocName(outDir: File): String {
        return outDir.listFiles()?.firstOrNull { it.extension.equals("adoc", ignoreCase = true) }?.name
            ?: "unknown.adoc"
    }
}