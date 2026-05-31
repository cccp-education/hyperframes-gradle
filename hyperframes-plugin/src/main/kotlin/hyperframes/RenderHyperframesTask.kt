package hyperframes

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Tâche Gradle qui appelle la CLI HyperFrames pour convertir le HTML
 * généré en vidéo MP4.
 *
 * HF-1 : Stub — vérifie seulement que le HTML existe.
 * HF-2 : Implémentation complète avec ProcessBuilder → npx hyperframes render.
 */
abstract class RenderHyperframesTask : DefaultTask() {

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun render() {
        val outputFile = outputDir.asFile.get()
        val htmlFile = outputFile.resolve("index.html")

        require(htmlFile.exists()) {
            """
            |No HyperFrames HTML found at ${htmlFile.absolutePath}.
            |Run generateHyperframesHtml first, or check outputDir configuration.
            """.trimMargin()
        }

        logger.lifecycle("renderHyperframes: stub active (HF-1)")
        logger.lifecycle("   HTML ready: ${htmlFile.absolutePath}")
        logger.lifecycle("   Full CLI integration coming in HF-2 (ProcessBuilder -> npx hyperframes render)")
    }
}
