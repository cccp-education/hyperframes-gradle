package hyperframes

import capsule.kernel.FileVisualSource
import capsule.kernel.VideoRenderRequest
import hyperframes.kernel.HyperFramesEngine
import hyperframes.kernel.HyperFramesEngineConfig
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Exception lancée par le pipeline HyperFrames.
 * Gradle traite RuntimeException comme échec de tâche.
 */
private class HyperframesException(message: String) : RuntimeException(message)

/**
 * Tâche Gradle qui rend le HTML HyperFrames en MP4 via le **noyau partagé**.
 *
 * HF-2 : bridge CLI `node <cliScript> render`.
 * US-3 (S-018) : la tâche **délègue** au port `capsule.kernel.VideoEngine`
 * (`HyperFramesEngine`) — plus de CLI dupliquée. La tâche porte les diagnostics
 * Gradle (timeout, code de sortie, sortie CLI, fichier manquant/vide).
 *
 * D-D (S-017) — the documented invocation is the *actual* one: the plugin runs
 * `node <cliScript> render` (the user-facing equivalent is `npx hyperframes render`).
 *
 * Le chemin Node.js est résolu depuis [nodeExecutable] si fourni (tests / mock CLI),
 * puis depuis un cache Node géré par Gradle, puis depuis le `PATH` système.
 */
@DisableCachingByDefault(because = "External process-bound: calls Node.js HyperFrames CLI")
abstract class RenderHyperframesTask : DefaultTask() {

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    /** Timout du rendu en ms (défaut : 300s) */
    @get:Input
    abstract val renderTimeoutMs: Property<Long>

    /** Chemin vers le script CLI HyperFrames (npm package) */
    @get:Input
    @get:Optional
    abstract val cliScript: Property<String>

    /** Chemin vers l'exécutable Node.js (override pour tests) */
    @get:Input
    @get:Optional
    abstract val nodeExecutable: Property<String>

    /** Nom du fichier de sortie (sans extension) */
    @get:Input
    abstract val outputName: Property<String>

    /** Largeur de la vidéo en pixels (descripteur d'issue du noyau) */
    @get:Input
    abstract val width: Property<Int>

    /** Hauteur de la vidéo en pixels (descripteur d'issue du noyau) */
    @get:Input
    abstract val height: Property<Int>

    @TaskAction
    fun render() {
        val outputDirFile = outputDir.asFile.get()
        val htmlFile = outputDirFile.resolve("index.html")
        val outputMp4 = outputDirFile.resolve("${outputName.get()}.mp4")

        // Vérifier que le HTML source existe
        require(htmlFile.exists()) {
            buildString {
                appendLine("No HyperFrames HTML found at ${htmlFile.absolutePath}.")
                append("Run generateHyperframesHtml first, or check outputDir configuration.")
            }
        }

        // Résoudre le chemin du script CLI HyperFrames
        val cliScriptFile = resolveCliScript()

        // Résoudre l'exécutable Node.js
        val nodeExe = resolveNodeExecutable()

        logger.lifecycle("renderHyperframes: rendering MP4 from ${htmlFile.name}")
        logger.lifecycle("   Node executable : ${nodeExe.absolutePath}")
        logger.lifecycle("   CLI script      : ${cliScriptFile.absolutePath}")
        logger.lifecycle("   Output MP4      : ${outputMp4.absolutePath}")
        logger.lifecycle("   Timeout         : ${renderTimeoutMs.get()}ms")

        // US-3 (S-018) — delegate the whole-document render to the shared-kernel
        // adapter (`HyperFramesEngine : capsule.kernel.VideoEngine`). The task owns
        // the Gradle-facing diagnostics; the engine owns the CLI invocation.
        val engine = HyperFramesEngine(
            HyperFramesEngineConfig(
                nodeExecutable = nodeExe.absolutePath,
                cliScript = cliScriptFile.absolutePath,
                timeoutMillis = renderTimeoutMs.get(),
                width = width.get(),
                height = height.get(),
            )
        )
        val request = VideoRenderRequest(
            name = outputName.get(),
            visual = FileVisualSource(htmlFile),
            outputDir = outputDirFile,
            timeoutMillis = renderTimeoutMs.get(),
        )
        val process = engine.renderDetailed(request).process

        if (process.timedOut) {
            throw HyperframesException(
                "HyperFrames CLI timed out after ${renderTimeoutMs.get()}ms. " +
                    "Increase renderTimeoutMs in the hyperframes extension."
            )
        }

        if (process.exitCode != 0) {
            throw HyperframesException(
                buildString {
                    appendLine("HyperFrames CLI failed with exit code ${process.exitCode}.")
                    appendLine("Command: ${nodeExe.absolutePath} ${cliScriptFile.name} render ...")
                    if (process.output.isNotBlank()) {
                        appendLine("Output:")
                        appendLine(process.output)
                    }
                }
            )
        }

        // Vérifier que le fichier MP4 a été produit
        if (!outputMp4.exists()) {
            throw HyperframesException(
                "HyperFrames CLI completed but output MP4 not found: ${outputMp4.absolutePath}"
            )
        }

        if (outputMp4.length() == 0L) {
            throw HyperframesException(
                "HyperFrames CLI completed but output MP4 is empty: ${outputMp4.absolutePath}"
            )
        }

        logger.lifecycle("HyperFrames CLI completed: ${outputMp4.absolutePath} (${outputMp4.length()} bytes)")
        if (process.output.isNotBlank()) {
            logger.info(process.output)
        }
    }

    /**
     * Résout le chemin du script CLI HyperFrames.
     * Priorité : extension `cliScript` > chemin npm par défaut.
     */
    private fun resolveCliScript(): File {
        if (cliScript.isPresent) {
            val script = File(cliScript.get())
            require(script.exists()) {
                "HyperFrames CLI script not found at configured path: ${script.absolutePath}"
            }
            return script
        }
        // Chemin par défaut : node_modules/.bin/hyperframes
        val defaultScript = outputDir.asFile.get()
            .parentFile
            .resolve("node_modules")
            .resolve(".bin")
            .resolve("hyperframes")
        require(defaultScript.exists()) {
            buildString {
                appendLine("HyperFrames CLI script not found at default path: ${defaultScript.absolutePath}")
                append("Install the hyperframes npm package or set cliScript in the hyperframes extension.")
            }
        }
        return defaultScript
    }

    /**
     * Résout l'exécutable Node.js.
     *
     * Priorité :
     * 1. `nodeExecutable` de l'extension (override pour tests / mock CLI)
     * 2. Plugin Gradle Node (`com.github.node-gradle.node`) — vérifie
     *    les répertoires de download habituels
     * 3. `node` dans le PATH système (fallback)
     */
    private fun resolveNodeExecutable(): File {
        // 1. Override explicite (tests / mock CLI)
        if (nodeExecutable.isPresent) {
            val exe = File(nodeExecutable.get())
            require(exe.exists()) {
                "Node executable not found at configured path: ${exe.absolutePath}"
            }
            return exe
        }

        // 2. Plugin Gradle Node — répertoire node downloadé
        val gradleNodeDir = project.layout.buildDirectory
            .map { it.asFile }
            .getOrElse(project.projectDir)
            .resolve("node")
            .resolve("bin")
        val gradleNodeExe = gradleNodeDir.resolve("node")
        if (gradleNodeExe.exists()) {
            return gradleNodeExe
        }

        // 3. Cache Gradle Node (sous ~/.gradle)
        val gradleCacheNodeDir = File(project.gradle.gradleUserHomeDir, "nodejs")
        if (gradleCacheNodeDir.exists()) {
            val cachedNode = gradleCacheNodeDir
                .listFiles()
                ?.firstOrNull { it.isDirectory && it.name.startsWith("node-v") }
                ?.resolve("bin")
                ?.resolve("node")
            if (cachedNode?.exists() == true) {
                return cachedNode
            }
        }

        // 4. Fallback : PATH système
        try {
            val process = ProcessBuilder("which", "node")
                .redirectErrorStream(true)
                .start()
            process.waitFor(2, TimeUnit.SECONDS)
            val nodePath = process.inputStream.bufferedReader().readText().trim()
            if (nodePath.isNotBlank()) {
                val systemNode = File(nodePath)
                if (systemNode.exists()) {
                    logger.warn("Using system Node.js at ${systemNode.absolutePath}. " +
                        "Set nodeExecutable in the hyperframes extension for reproducible builds.")
                    return systemNode
                }
            }
        } catch (e: Exception) {
            logger.debug("Could not resolve node from PATH: ${e.message}")
        }

        throw HyperframesException(
            "Node.js executable not found. " +
                "Install node on the PATH or set nodeExecutable " +
                "in the hyperframes extension."
        )
    }
}
