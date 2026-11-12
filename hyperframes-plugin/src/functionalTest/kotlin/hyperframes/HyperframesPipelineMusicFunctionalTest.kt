package hyperframes

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * HF-MUSIC-LIBRARY US-5 — TestKit acceptance of the **whole pipeline** with a
 * music library: `generateHyperframesHtml` → `resolveHyperframesBgm` →
 * `renderHyperframes`.
 *
 * The renderer is a stub CLI (it writes a marker MP4 from `--output`), so the
 * test is hermetic: no Node.js, no Chromium, no ffmpeg. What is proved is the
 * graph (order), the data boundary (manifest + audio store) and the economy of
 * ink (an identical asset is never rewritten).
 */
class HyperframesPipelineMusicFunctionalTest {
    @TempDir
    lateinit var tempDir: Path

    private val audioBytes = byteArrayOf(0x49, 0x44, 0x33, 0x04, 0x00, 0x00, 0x00, 0x00, 0x7F)

    private fun sha256Of(bytes: ByteArray): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun writeStubCli(projectDir: File): File {
        val script = projectDir.resolve("stub-hyperframes-cli.sh")
        val dollar = "$"
        script.writeText(
            """
            #!/bin/bash
            OUTPUT_FILE=""
            while [[ $dollar# -gt 0 ]]; do
                case ${dollar}1 in
                    --output) OUTPUT_FILE="${dollar}2"; shift 2 ;;
                    *) shift ;;
                esac
            done
            if [[ -n "${dollar}OUTPUT_FILE" ]]; then
                echo "MOCK-MP4" > "${dollar}OUTPUT_FILE"
            fi
            echo "Mock HyperFrames CLI: render complete"
            exit 0
            """.trimIndent(),
        )
        script.setExecutable(true)
        return script
    }

    private fun project(): File {
        val projectDir = tempDir.toFile()
        val stub = writeStubCli(projectDir)
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories { mavenLocal(); gradlePluginPortal(); mavenCentral() }
            }
            rootProject.name = "pipeline-music-functional-test"
            """.trimIndent(),
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
                outputName.set("music-pipeline")
                cliScript.set("${stub.absolutePath}")
                nodeExecutable.set("${stub.absolutePath}")
                music {
                    libraryFile.set(layout.projectDirectory.file("music-library.json"))
                    audioDir.set(layout.projectDirectory.dir("audio"))
                }
            }
            """.trimIndent(),
        )
        val srcDir = projectDir.resolve("src/docs")
        srcDir.mkdirs()
        srcDir.resolve("index.adoc").writeText(
            buildString {
                appendLine("= Music pipeline demo")
                appendLine()
                appendLine("== Calm")
                appendLine()
                appendLine("[.hyperframes-music]")
                appendLine("----")
                appendLine("mood: calm")
                appendLine("----")
                appendLine()
                appendLine("The bed is resolved from the library.")
            },
        )
        projectDir.resolve("music-library.json").writeText(
            """
            {
              "schemaVersion": 1,
              "tracks": [
                {
                  "id": "calm-piano",
                  "title": "Calm piano",
                  "artist": "cheroliv",
                  "file": "calm.mp3",
                  "sha256": "${sha256Of(audioBytes)}",
                  "durationSeconds": 92.5,
                  "licence": "own",
                  "mood": "calm"
                }
              ]
            }
            """.trimIndent(),
        )
        projectDir.resolve("audio").mkdirs()
        projectDir.resolve("audio/calm.mp3").writeBytes(audioBytes)
        return projectDir
    }

    private fun run(
        projectDir: File,
        task: String,
        rerun: Boolean = false,
    ): BuildResult =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(listOfNotNull(task, if (rerun) "--rerun-tasks" else null))
            .build()

    private fun asset(projectDir: File): File = projectDir.resolve("build/hyperframes/assets/audio/calm-piano.mp3")

    @Test
    fun `the pipeline generates the HTML, resolves the asset then renders the MP4`() {
        val projectDir = project()

        val result = run(projectDir, "hyperframesPipeline")

        val generated = result.output.indexOf("> Task :generateHyperframesHtml")
        val resolved = result.output.indexOf("> Task :resolveHyperframesBgm")
        val rendered = result.output.indexOf("> Task :renderHyperframes")
        assertTrue(generated >= 0 && resolved > generated && rendered > resolved, result.output)

        assertTrue(asset(projectDir).isFile, "the music asset must be next to the composition")
        assertEquals(audioBytes.toList(), asset(projectDir).readBytes().toList())

        val html = projectDir.resolve("build/hyperframes/index.html").readText()
        assertContains(html, """data-track-id="calm-piano"""")
        assertContains(html, """src="assets/audio/calm-piano.mp3"""")

        val mp4 = projectDir.resolve("build/hyperframes/music-pipeline.mp4")
        assertTrue(mp4.isFile, "the stub CLI must have produced the MP4")
        assertContains(mp4.readText(), "MOCK-MP4")
    }

    @Test
    fun `a second pipeline run does not rewrite the identical asset`() {
        val projectDir = project()
        run(projectDir, "hyperframesPipeline")
        val target = asset(projectDir)
        val aged = target.lastModified() - 60_000
        assertTrue(target.setLastModified(aged), "could not age the copied asset")

        val result = run(projectDir, "hyperframesPipeline", rerun = true)

        assertFalse(result.output.contains("Music asset copied"), result.output)
        assertTrue(result.output.contains("Music assets already in place"), result.output)
        assertEquals(aged, target.lastModified(), "the identical asset must not be rewritten")
    }
}
