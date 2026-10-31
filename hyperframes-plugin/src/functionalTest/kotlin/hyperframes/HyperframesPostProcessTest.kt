package hyperframes

import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.test.assertContains
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assumptions.assumeTrue
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
        // The render dir must exist (input-directory validation); it is empty.
        projectDir.resolve("build/hyperframes").mkdirs()

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

    @Test
    fun `generateHyperframesNarration warns when an existing take is silent`() {
        assumeTrue(ffmpegOnPath()) { "ffmpeg is required to build a real silent narration take" }
        val html = projectDir.resolve("build/hyperframes/index.html")
        html.parentFile.mkdirs()
        html.writeText(
            """<div id="stage"><div class="hyperframes-track"><p>Bonjour</p></div></div>""",
        )
        val narration = projectDir.resolve("build/hyperframes/narration/narration-1.wav")
        narration.parentFile.mkdirs()
        generateSilentWav(narration)

        val result = runner("generateHyperframesNarration")

        assertContains(result.output, "narration is silent")
    }

    private fun ffmpegOnPath(): Boolean =
        System.getenv("PATH")?.split(File.pathSeparator).orEmpty().any { entry ->
            File(entry, "ffmpeg").let { it.isFile && it.canExecute() }
        }

    private fun generateSilentWav(output: File) {
        val process = ProcessBuilder(
            "ffmpeg", "-v", "error", "-y",
            "-f", "lavfi", "-i", "anullsrc=r=16000:cl=mono",
            "-t", "1",
            output.absolutePath,
        ).redirectErrorStream(true).start()
        val finished = process.waitFor(60, TimeUnit.SECONDS)
        assumeTrue(finished && process.exitValue() == 0) {
            "ffmpeg could not produce a silent wav — narration silence check not exercised here"
        }
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
