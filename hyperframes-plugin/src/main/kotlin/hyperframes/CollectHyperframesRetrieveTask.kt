package hyperframes

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import hyperframes.contract.HyperframesContextExtractor
import org.gradle.api.DefaultTask
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

    @TaskAction
    fun execute() {
        val output = outputFile.asFile.get()
        output.parentFile.mkdirs()

        val outDir = output.parentFile
        val mp4 = outDir.listFiles()?.firstOrNull { it.extension.equals("mp4", ignoreCase = true) }
        val html = outDir.listFiles()?.firstOrNull { it.name.equals("index.html", ignoreCase = true) }

        val mapper = jacksonObjectMapper()

        if (mp4 == null || html == null) {
            logger.lifecycle(
                "[hyperframes] collectHyperframesRetrieve — no MP4/HTML artifacts in {} (mp4={}, html={})",
                outDir.absolutePath, mp4 != null, html != null
            )
            return
        }

        val htmlContent = html.readText()
        val source = asciidocName.orNull ?: inferAsciidocName(outDir)
        val startedAt = System.currentTimeMillis()
        val extractor = HyperframesContextExtractor(pluginVersion.get())
        val context = extractor.extract(
            html = htmlContent,
            mp4 = mp4,
            asciidocName = source,
            renderedAt = Instant.now().toString(),
            renderDurationMs = System.currentTimeMillis() - startedAt
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