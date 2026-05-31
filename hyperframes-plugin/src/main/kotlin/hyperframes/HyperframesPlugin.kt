package hyperframes

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Plugin Gradle N2 pour transformer un document AsciiDoc annoté
 * en vidéo MP4 via le moteur HyperFrames (HeyGen, Apache 2.0).
 *
 * Pipeline :
 *   AsciiDoc ──→ AsciidoctorJ (parse + extract) ──→ HTML HyperFrames ──→ CLI Node.js ──→ MP4
 *
 * Usage :
 * ```kotlin
 * plugins {
 *     id("education.cccp.hyperframes")
 * }
 * hyperframes {
 *     inputDir.set(layout.projectDirectory.dir("src/docs"))
 *     outputDir.set(layout.buildDirectory.dir("hyperframes"))
 * }
 * ```
 */
class HyperframesPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val extension = target.extensions.create("hyperframes", HyperframesExtension::class.java)

        // Tâche : génération HTML HyperFrames depuis AsciiDoc
        target.tasks.register("generateHyperframesHtml", GenerateHyperframesHtmlTask::class.java) { task ->
            task.description = "Generate HyperFrames HTML from AsciiDoc source"
            task.group = "hyperframes"
            task.inputDir.set(extension.inputDir)
            task.outputDir.set(extension.outputDir)
            task.width.set(extension.width)
            task.height.set(extension.height)
            task.fps.set(extension.fps)
            task.outputName.set(extension.outputName)
        }

        // Tâche : rendu MP4 via HyperFrames CLI (ProcessBuilder → node hyperframes render)
        target.tasks.register("renderHyperframes", RenderHyperframesTask::class.java) { task ->
            task.description = "Render HyperFrames HTML to MP4 via CLI (npx hyperframes render)"
            task.group = "hyperframes"
            task.outputDir.set(extension.outputDir)
            task.outputName.set(extension.outputName)
            task.renderTimeoutMs.set(extension.renderTimeoutMs)
            extension.cliScript.finalizeValueOnRead()
            task.cliScript.set(extension.cliScript)
            extension.nodeExecutable.finalizeValueOnRead()
            task.nodeExecutable.set(extension.nodeExecutable)
        }
    }
}
