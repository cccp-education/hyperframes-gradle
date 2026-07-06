package hyperframes.scenarios

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.gradle.testkit.runner.GradleRunner
import java.io.File
import java.nio.file.Files

/**
 * Shared world for Cucumber scenarios testing the N3 contract task.
 * Holds the temp project, its build files, and the rendered artifacts.
 */
class HyperframesWorld {

    lateinit var projectDir: File
    lateinit var buildDir: File
    var buildResult: org.gradle.testkit.runner.BuildResult? = null
    private val mapper = jacksonObjectMapper()

    fun createProject() {
        projectDir = Files.createTempDirectory("hyperframes-cucumber").toFile()
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories { mavenLocal(); gradlePluginPortal(); mavenCentral() }
            }
            rootProject.name = "cucumber-hyperframes"
            """.trimIndent()
        )
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins { id("education.cccp.hyperframes") }
            hyperframes {
                inputDir.set(layout.projectDirectory.dir("src/docs"))
                outputDir.set(layout.buildDirectory.dir("hyperframes"))
                width.set(1920)
                height.set(1080)
                fps.set(30)
                outputName.set("test-video")
            }
            """.trimIndent()
        )
        buildDir = projectDir.resolve("build/hyperframes")
        buildDir.mkdirs()
    }

    fun writeMp4(name: String, bytes: ByteArray = ByteArray(1024)) {
        buildDir.resolve(name).writeBytes(bytes)
    }

    fun writeHtml(width: Int = 1920, height: Int = 1080, fps: Int = 30,
                  compositions: List<String> = listOf("intro"), tracks: Int = 1) {
        val compDivs = compositions.joinToString("\n") { id ->
            """<div class="sect1 hyperframes-composition" data-composition-id="$id"><h2 id="$id">$id</h2></div>"""
        }
        val trackDivs = (0 until tracks).joinToString("\n") { idx ->
            """<div class="paragraph hyperframes-track" data-track-index="$idx" data-start="0" data-duration="5"></div>"""
        }
        buildDir.resolve("index.html").writeText(
            """
            <html><body>
            <div id="stage" data-width="$width" data-height="$height" data-fps="$fps" data-output="test-video.mp4">
            $compDivs
            $trackDivs
            </div>
            </body></html>
            """.trimIndent()
        )
    }

    fun writeAsciidoc(name: String) {
        projectDir.resolve("src/docs").mkdirs()
        projectDir.resolve("src/docs/$name").writeText("= Title\n")
        // Mirror into buildDir so the task's inferAsciidocName finds it
        buildDir.resolve(name).writeText("= Title\n")
    }

    fun writeTitleCardAdoc(
        id: String,
        title: String,
        subtitle: String? = null,
        logoPath: String? = null,
        duration: Int? = null
    ) {
        // Reset build artifacts so the generate task is not UP-TO-DATE.
        buildDir.deleteRecursively()
        buildDir.mkdirs()
        val srcDir = projectDir.resolve("src/docs")
        srcDir.deleteRecursively()
        srcDir.mkdirs()
        val doc = buildString {
            appendLine("= Title-card demo")
            appendLine(":hyperframes-width: 1920")
            appendLine(":hyperframes-height: 1080")
            appendLine(":hyperframes-fps: 30")
            appendLine()
            appendLine("[.hyperframes-title-card#$id${if (logoPath != null) ", logo=\"$logoPath\"" else ""}${if (duration != null) ", duration=$duration" else ""}]")
            appendLine("== $title")
            if (subtitle != null) {
                appendLine()
                appendLine(subtitle)
            }
        }
        srcDir.resolve("index.adoc").writeText(doc)
    }

    fun runTask(taskName: String) {
        buildResult = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(taskName, "--info", "--rerun-tasks")
            .build()
    }

    fun generatedHtml(): String {
        val file = buildDir.resolve("index.html")
        require(file.exists()) { "Generated HTML not found at ${file.absolutePath}" }
        return file.readText()
    }

    fun compositeContextFile(): File = buildDir.resolve("composite-context.json")

    fun compositeContextJson(): JsonNode {
        val file = compositeContextFile()
        require(file.exists()) { "composite-context.json not found at ${file.absolutePath}" }
        return mapper.readTree(file)
    }
}