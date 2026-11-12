package hyperframes

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
 * HF-MUSIC-LIBRARY US-4 — TestKit acceptance for `resolveHyperframesBgm`.
 *
 * Proves the data boundary end to end: a versioned `music-library.json` and a
 * (gitignored) audio store, no code dependency on the producer. The task reads
 * the *composition HTML* — the same contract the runtime loads.
 */
class ResolveHyperframesBgmFunctionalTest {
    @TempDir
    lateinit var tempDir: Path

    private val audioBytes = byteArrayOf(0x49, 0x44, 0x33, 0x04, 0x00, 0x00, 0x00, 0x00, 0x7F)

    private fun sha256Of(bytes: ByteArray): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    private fun project(
        trackFile: String = "calm.mp3",
        writeAudio: Boolean = true,
        strict: Boolean = false,
        libraryJson: String? = null,
    ): File {
        val projectDir = tempDir.toFile()
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories { mavenLocal(); gradlePluginPortal(); mavenCentral() }
            }
            rootProject.name = "music-assets-functional-test"
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
                outputName.set("music-assets")
                music {
                    libraryFile.set(layout.projectDirectory.file("music-library.json"))
                    audioDir.set(layout.projectDirectory.dir("audio"))
                    strict.set($strict)
                }
            }
            """.trimIndent(),
        )
        val srcDir = projectDir.resolve("src/docs")
        srcDir.mkdirs()
        srcDir.resolve("index.adoc").writeText(
            buildString {
                appendLine("= Music assets demo")
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
            libraryJson ?: defaultManifest(trackFile),
        )
        projectDir.resolve("audio").mkdirs()
        if (writeAudio) {
            projectDir.resolve("audio/$trackFile").writeBytes(audioBytes)
        }
        return projectDir
    }

    private fun run(
        projectDir: File,
        task: String,
        rerun: Boolean = false,
    ) = GradleRunner
        .create()
        .withProjectDir(projectDir)
        .withPluginClasspath()
        .withArguments(listOfNotNull(task, if (rerun) "--rerun-tasks" else null))
        .build()

    private fun target(projectDir: File): File = projectDir.resolve("build/hyperframes/assets/audio/calm-piano.mp3")

    private fun defaultManifest(trackFile: String): String =
        """
        {
          "schemaVersion": 1,
          "tracks": [
            {
              "id": "calm-piano",
              "title": "Calm piano",
              "artist": "cheroliv",
              "file": "$trackFile",
              "sha256": "${sha256Of(audioBytes)}",
              "durationSeconds": 92.5,
              "licence": "own",
              "mood": "calm"
            }
          ]
        }
        """.trimIndent()

    @Test
    fun `the resolved library track is copied next to the composition`() {
        val projectDir = project()

        val result = run(projectDir, "resolveHyperframesBgm")

        assertTrue(result.output.contains("Music asset copied"), result.output)
        val copied = target(projectDir)
        assertTrue(copied.isFile, "the music asset must be copied next to the composition")
        assertEquals(audioBytes.toList(), copied.readBytes().toList())

        val html = projectDir.resolve("build/hyperframes/index.html").readText()
        assertContains(html, """data-track-id="calm-piano"""")
        assertContains(html, """src="assets/audio/calm-piano.mp3"""")
    }

    @Test
    fun `an identical sha256 skips the copy (economy of ink)`() {
        val projectDir = project()
        run(projectDir, "resolveHyperframesBgm")
        val copied = target(projectDir)
        val marker = copied.lastModified()
        assertTrue(copied.setLastModified(marker - 60_000), "could not age the copied asset")

        val result = run(projectDir, "resolveHyperframesBgm", rerun = true)

        assertFalse(result.output.contains("Music asset copied"), result.output)
        assertEquals(marker - 60_000, copied.lastModified(), "the identical asset must not be rewritten")
    }

    @Test
    fun `a missing audio file degrades by default`() {
        val projectDir = project(writeAudio = false)

        val result = run(projectDir, "resolveHyperframesBgm")

        assertTrue(result.output.contains("Music asset source not found"), result.output)
        assertFalse(target(projectDir).exists())
    }

    @Test
    fun `strict turns a missing audio file into a failure`() {
        val projectDir = project(writeAudio = false, strict = true)

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withPluginClasspath()
                .withArguments("resolveHyperframesBgm")
                .buildAndFail()

        assertTrue(result.output.contains("could not be placed"), result.output)
    }
}
