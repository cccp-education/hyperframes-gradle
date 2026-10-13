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
            task.pronunciationDomain.set(extension.pronunciationDomain)
        }

        // Tâche : rendu MP4 via HyperFrames CLI (ProcessBuilder → node <cliScript> render)
        target.tasks.register("renderHyperframes", RenderHyperframesTask::class.java) { task ->
            task.description = "Render HyperFrames HTML to MP4 via the HyperFrames CLI (node <cliScript> render)"
            task.group = "hyperframes"
            task.outputDir.set(extension.outputDir)
            task.outputName.set(extension.outputName)
            task.renderTimeoutMs.set(extension.renderTimeoutMs)
            extension.cliScript.finalizeValueOnRead()
            task.cliScript.set(extension.cliScript)
            extension.nodeExecutable.finalizeValueOnRead()
            task.nodeExecutable.set(extension.nodeExecutable)
        }

        // Tâche : collect retrieve context for N3 runner integration
        target.tasks.register("collectHyperframesRetrieve", CollectHyperframesRetrieveTask::class.java) { task ->
            task.description = "Collect HyperFrames rendered video metadata for N3 runner integration"
            task.group = "collect"
            task.outputFile.set(target.layout.buildDirectory.file("hyperframes/composite-context.json"))
            task.pluginVersion.set(target.version.toString())
            task.pronunciationDomain.set(extension.pronunciationDomain)
            task.ffprobeExecutable.set(extension.ffprobeExecutable)
            task.strict.set(extension.strict)
        }

        // D-F (S-017) — pipeline ordering: generate → render → collect.
        // `mustRunAfter` (not `dependsOn`) orders the tasks when they are all in
        // the graph without forcing the expensive render on a plain collect.
        target.tasks.named("renderHyperframes") { it.mustRunAfter("generateHyperframesHtml") }
        target.tasks.named("collectHyperframesRetrieve") { it.mustRunAfter("renderHyperframes") }

        // D-F (S-017) — lifecycle task running the whole pipeline in order.
        target.tasks.register("hyperframesPipeline") { task ->
            task.description = "Run the full HyperFrames pipeline: AsciiDoc → HTML → MP4 → N3 metadata"
            task.group = "hyperframes"
            task.dependsOn("generateHyperframesHtml", "renderHyperframes", "collectHyperframesRetrieve")
        }
    }
}
