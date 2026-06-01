package hyperframes

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

@DisableCachingByDefault(because = "Filesystem-bound: reads rendered MP4 and exports metadata")
abstract class CollectHyperframesRetrieveTask : DefaultTask() {

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun execute() {
        val mapper = jacksonObjectMapper()

        // Create minimal context entries for demonstration
        val entries = emptyList<Map<String, Any>>()
        val result = mapOf(
            "source" to "watts",
            "entries" to entries,
            "count" to 0,
            "timestamp" to System.currentTimeMillis()
        )

        // Write composite context
        val output = outputFile.asFile.get()
        output.parentFile.mkdirs()
        mapper.writerWithDefaultPrettyPrinter().writeValue(output, result)

        // Write metadata file
        val metadata = Metadata.forWatts(
            type = "retrieve",
            sessions = 0,
            dependencies = listOf("brooklyn")
        )
        Metadata.writeTo(output.parentFile, metadata)

        logger.lifecycle("[hyperframes] collectHyperframesRetrieve — 0 entries → {}",
            output.absolutePath)
    }
}