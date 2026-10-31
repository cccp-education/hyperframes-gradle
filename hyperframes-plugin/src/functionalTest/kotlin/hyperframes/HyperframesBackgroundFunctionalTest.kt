package hyperframes

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * HF-DEMO-QUALITY US-6 — TestKit acceptance for the per-scene background image.
 *
 * Proves the end-to-end DSL path: AsciiDoc conversion, processor wiring and the
 * `ReferencedAssets` copy (the synthesized `<img>` is copied next to the
 * composition, so the CLI resolves a real file).
 */
class HyperframesBackgroundFunctionalTest {

    @TempDir
    lateinit var tempDir: Path

    private val imageBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00)

    private fun project(background: String?): File {
        val projectDir = tempDir.toFile()
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories { mavenLocal(); gradlePluginPortal(); mavenCentral() }
            }
            rootProject.name = "background-functional-test"
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
                outputName.set("background-video")
            }
            """.trimIndent()
        )
        val srcDir = projectDir.resolve("src/docs")
        srcDir.resolve("assets/img").mkdirs()
        srcDir.resolve("assets/img/bg.png").writeBytes(imageBytes)
        srcDir.resolve("index.adoc").writeText(
            buildString {
                appendLine("= Background demo")
                appendLine()
                appendLine("== Hook")
                appendLine()
                if (background != null) {
                    appendLine("[.hyperframes-background]")
                    appendLine("----")
                    appendLine(background)
                    appendLine("----")
                    appendLine()
                }
                appendLine("The document becomes signal.")
            }
        )
        return projectDir
    }

    private fun generate(projectDir: File): String {
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("generateHyperframesHtml")
            .build()
        return projectDir.resolve("build/hyperframes/index.html").readText()
    }

    @Test
    fun `a declared background becomes a full-bleed layer and its asset is copied`() {
        val projectDir = project("file: assets/img/bg.png\nopacity: 0.4\ngrid: true")
        val html = generate(projectDir)

        assertContains(html, """class="hf-bg"""")
        assertContains(html, """src="assets/img/bg.png"""")
        assertContains(html, """style="opacity:0.40"""")
        assertContains(html, """<div class="hf-bg-grid"></div>""")
        assertFalse(html.contains("hyperframes-background"))

        val copied = projectDir.resolve("build/hyperframes/assets/img/bg.png")
        assertEquals(true, copied.isFile, "the background image must be copied next to the composition")
        assertEquals(imageBytes.toList(), copied.readBytes().toList())
    }

    @Test
    fun `a scene without a background keeps the historical static background`() {
        val html = generate(project(background = null))

        assertContains(html, """<div class="scene-bg"></div>""")
        assertFalse(html.contains("hf-bg"))
        assertFalse(File(tempDir.toFile(), "build/hyperframes/assets/img/bg.png").exists())
    }
}
