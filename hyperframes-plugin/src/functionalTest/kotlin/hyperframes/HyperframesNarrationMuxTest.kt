package hyperframes

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertTrue

/**
 * HF-SPEAKER-NOTES US-5 — functional acceptance of the narration mux wiring.
 *
 * Hermetic: FFmpeg is replaced by a stub script, so neither espeak nor a real
 * encoder is required. The stub copies the input video to the output, which
 * proves the task builds the argv, runs the configured executable and writes
 * the muxed file; the degrade paths are covered without any tool.
 */
class HyperframesNarrationMuxTest {
    @TempDir
    lateinit var tempDir: Path
    private lateinit var projectDir: File

    @BeforeEach
    fun setUp() {
        projectDir = tempDir.toFile()
        buildDir().mkdirs()
    }

    private fun buildDir(): File = projectDir.resolve("build/hyperframes")

    private fun writeBuildFiles(
        ffmpegStub: String? = null,
        strict: Boolean = false,
    ) {
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    mavenLocal()
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            rootProject.name = "test-hyperframes-narration-mux"
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
                strict.set($strict)
                ${if (ffmpegStub != null) """ffmpegExecutable.set("$ffmpegStub")""" else ""}
            }
            """.trimIndent(),
        )
    }

    private fun writeComposition(withAnchors: Boolean) {
        val marker =
            if (withAnchors) {
                """<div class="hyperframes-track hyperframes-narration" data-narration-index="1" data-narration-title="Intro" data-narration-scene="intro" data-narration-start="2"><p>Hello.</p></div>"""
            } else {
                ""
            }
        buildDir().resolve("index.html").writeText(
            """
            <div id="stage">
              <div class="scene clip" data-composition-id="intro">$marker</div>
            </div>
            """.trimIndent(),
        )
    }

    private fun writeVideo() {
        buildDir().resolve("test-video.mp4").writeText("MOCK-MP4")
    }

    private fun writeTake(index: Int) {
        val dir = buildDir().resolve("narration")
        dir.mkdirs()
        dir.resolve("narration-$index.wav").writeText("MOCK-WAV")
    }

    private fun stubFfmpeg(exitCode: Int): File {
        val script = projectDir.resolve("stub-ffmpeg.sh")
        script.writeText(
            """
            #!/bin/bash
            PREV=""
            INPUT=""
            for arg in "${'$'}@"; do
              if [ "${'$'}PREV" = "-i" ]; then INPUT="${'$'}arg"; break; fi
              PREV="${'$'}arg"
            done
            for last; do :; done
            if [ $exitCode -eq 0 ]; then cp "${'$'}INPUT" "${'$'}last"; fi
            echo "stub ffmpeg done"
            exit $exitCode
            """.trimIndent(),
        )
        script.setExecutable(true)
        return script
    }

    private fun runner(vararg args: String) =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(*args)
            .build()

    @Test
    fun `plugin registers the narration mux task`() {
        writeBuildFiles()

        val result = runner("tasks", "--group=hyperframes")

        assertContains(result.output, "muxHyperframesNarration")
    }

    @Test
    fun `carries the rendered MP4 through when no note is authored`() {
        writeBuildFiles()
        writeVideo()
        writeComposition(withAnchors = false)

        val result = runner("muxHyperframesNarration")

        assertContains(result.output, "no narration take to mux")
        assertTrue(projectDir.resolve("build/hyperframes/narration-mux/test-video.mp4").exists())
    }

    @Test
    fun `muxes the anchored take through the configured ffmpeg`() {
        val stub = stubFfmpeg(exitCode = 0)
        writeBuildFiles(ffmpegStub = stub.absolutePath)
        writeVideo()
        writeComposition(withAnchors = true)
        writeTake(1)

        val result = runner("muxHyperframesNarration")

        assertContains(result.output, "muxed 1 narration take(s)")
        val output = projectDir.resolve("build/hyperframes/narration-mux/test-video.mp4")
        assertTrue(output.exists())
        assertContains(output.readText(), "MOCK-MP4")
    }

    @Test
    fun `falls back to the rendered MP4 when ffmpeg fails`() {
        val stub = stubFfmpeg(exitCode = 1)
        writeBuildFiles(ffmpegStub = stub.absolutePath)
        writeVideo()
        writeComposition(withAnchors = true)
        writeTake(1)

        val result = runner("muxHyperframesNarration")

        assertContains(result.output, "keeping the original MP4")
        assertTrue(projectDir.resolve("build/hyperframes/narration-mux/test-video.mp4").exists())
    }

    @Test
    fun `fails when strict mode is enabled and the mux degrades`() {
        val stub = stubFfmpeg(exitCode = 1)
        writeBuildFiles(ffmpegStub = stub.absolutePath, strict = true)
        writeVideo()
        writeComposition(withAnchors = true)
        writeTake(1)

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("muxHyperframesNarration")
                .buildAndFail()

        assertContains(result.output, "strict mode is enabled")
    }
}
