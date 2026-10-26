package hyperframes

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertFalse

/**
 * HF-DEMO-QUALITY US-4 — TestKit acceptance for the DSL chrome + typography.
 */
class HyperframesChromeFunctionalTest {

    @TempDir
    lateinit var tempDir: Path

    private fun project(chromeEnabled: Boolean): java.io.File {
        val projectDir = tempDir.toFile()
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories { mavenLocal(); gradlePluginPortal(); mavenCentral() }
            }
            rootProject.name = "chrome-functional-test"
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
                outputName.set("chrome-video")
                chromeEnabled.set($chromeEnabled)
                chromeLabel.set("LAB")
                displayFont.set("\"League Gothic\", Impact, sans-serif")
                monoFont.set("\"IBM Plex Mono\", monospace")
            }
            """.trimIndent()
        )
        val srcDir = projectDir.resolve("src/docs")
        srcDir.mkdirs()
        srcDir.resolve("index.adoc").writeText(
            """
            == Introduction

            First scene body.

            == Conclusion

            Second scene body.
            """.trimIndent()
        )
        return projectDir
    }

    private fun generate(projectDir: java.io.File): String =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("generateHyperframesHtml")
            .build()
            .let { projectDir.resolve("build/hyperframes/index.html").readText() }

    @Test
    fun `chrome DSL renders timecode marks and progress bar`() {
        val html = generate(project(chromeEnabled = true))

        assertContains(html, """class="hf-chrome"""")
        assertContains(html, """class="hf-chrome-timecode">00:00:00:00<""")
        assertContains(html, """class="hf-chrome-timecode">00:00:05:00<""")
        assertContains(html, """id="hf-chrome-progress"""")
        assertContains(html, "LAB · 01 / 02")
        assertContains(html, """--hf-display-font:"League Gothic", Impact, sans-serif""")
        assertContains(html, """--hf-mono-font:"IBM Plex Mono", monospace""")
        assertContains(html, "@font-face{font-family:'League Gothic';src:local('League Gothic')}")
        assertContains(html, "@font-face{font-family:'IBM Plex Mono';src:local('IBM Plex Mono')}")
    }

    @Test
    fun `chrome is opt-in and stays absent by default`() {
        val html = generate(project(chromeEnabled = false))

        assertFalse(html.contains("hf-chrome"))
    }
}
