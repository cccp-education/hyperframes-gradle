package hyperframes

import java.io.File
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Functional acceptance for the shared post-production / narration tasks
 * (VK-4 US-4b→d). Hermetic: no real FFmpeg / espeak is required — every step
 * degrades gracefully (pass-through / no-op) when the tool is absent.
 */
class HyperframesPostProcessTest {

    @TempDir
    lateinit var tempDir: Path
    private lateinit var projectDir: File

    @BeforeEach
    fun setUp() {
        projectDir = tempDir.toFile()
        writeBuildFiles(projectDir)
    }

    @Test
    fun `plugin registers the post-production and narration tasks`() {
        val result = runner("tasks", "--group=hyperframes")

        assertContains(result.output, "postProcessHyperframes")
        assertContains(result.output, "generateHyperframesNarration")
    }

    @Test
    fun `postProcessHyperframes passes the rendered MP4 through when nothing is configured`() {
        val rendered = projectDir.resolve("build/hyperframes/test-video.mp4")
        rendered.parentFile.mkdirs()
        rendered.writeText("MOCK-MP4")

        val result = runner("postProcessHyperframes")

        assertContains(result.output, "postProcessHyperframes: wrote")

        val output = projectDir.resolve("build/hyperframes/post/test-video.mp4")
        assertTrue(output.exists(), "post-processed MP4 should exist")
        assertContains(output.readText(), "MOCK-MP4")
    }

    @Test
    fun `postProcessHyperframes fails without a rendered MP4`() {
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("postProcessHyperframes")
            .buildAndFail()

        assertContains(result.output, "No rendered MP4 found")
    }

    @Test
    fun `generateHyperframesNarration is a no-op when there is no narration track`() {
        val html = projectDir.resolve("build/hyperframes/index.html")
        html.parentFile.mkdirs()
        html.writeText("""<div id="stage"><p>No tracks here</p></div>""")

        val result = runner("generateHyperframesNarration")

        assertContains(result.output, "no narration tracks found")
    }

    private fun runner(vararg args: String) = GradleRunner.create()
        .withProjectDir(projectDir)
        .withPluginClasspath()
        .withArguments(*args)
        .build()

    private fun writeBuildFiles(projectDir: File) {
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    mavenLocal()
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            rootProject.name = "test-hyperframes-post"
            """.trimIndent(),
        )
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.hyperframes")
            }

            hyperframes {
                inputDir.set(layout.projectDirectory.dir("src/docs"))
                outputDir.set(layout.buildDirectory.dir("hyperframes"))
                outputName.set("test-video")
            }
            """.trimIndent(),
        )
    }
}
