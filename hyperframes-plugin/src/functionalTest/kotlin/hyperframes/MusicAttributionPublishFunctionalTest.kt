package hyperframes

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertTrue

/**
 * HF-MUSIC-LIBRARY US-7 — TestKit acceptance of the music credits in the
 * publication description.
 *
 * Hermetic: the render is a stub CLI, the publication degrades on a missing
 * credentials file (no network). The proof is the composed description written
 * to `publication.json`: a `cc-by` track used by the composition **must** be
 * credited, and an `own` track must not.
 */
class MusicAttributionPublishFunctionalTest {
    @TempDir
    lateinit var tempDir: Path

    private fun project(): File {
        val projectDir = tempDir.toFile()
        val stub = FunctionalStubs.writeStubHyperframesCli(projectDir)
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories { mavenLocal(); gradlePluginPortal(); mavenCentral() }
            }
            rootProject.name = "music-credits-functional-test"
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
                outputName.set("credits")
                cliScript.set("${stub.absolutePath}")
                nodeExecutable.set("${stub.absolutePath}")
                music {
                    libraryFile.set(layout.projectDirectory.file("music-library.json"))
                    audioDir.set(layout.projectDirectory.dir("audio"))
                }
                publishEnabled.set(true)
                publishTitle.set("Credits demo")
                publishCredentialsFile.set("absent-youtube.yaml")
            }
            """.trimIndent(),
        )
        val srcDir = projectDir.resolve("src/docs")
        srcDir.mkdirs()
        srcDir.resolve("index.adoc").writeText(
            buildString {
                appendLine("= Credits demo")
                appendLine()
                appendLine("== Calm")
                appendLine()
                appendLine("[.hyperframes-music]")
                appendLine("----")
                appendLine("mood: calm")
                appendLine("----")
                appendLine()
                appendLine("Body.")
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
                  "sha256": "",
                  "durationSeconds": 92.5,
                  "licence": "cc-by",
                  "attribution": "Music: Calm piano — cheroliv (CC BY 4.0)",
                  "mood": "calm"
                }
              ]
            }
            """.trimIndent(),
        )
        projectDir.resolve("audio").mkdirs()
        projectDir.resolve("audio/calm.mp3").writeBytes(byteArrayOf(0x49, 0x44, 0x33))
        return projectDir
    }

    private fun run(projectDir: File): String =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("hyperframesPipeline", "publishHyperframesToYouTube")
            .build()
            .output

    @Test
    fun `a cc-by track used by the composition is credited in the description`() {
        val projectDir = project()

        val output = run(projectDir)

        assertTrue(output.contains("music credit — Music: Calm piano — cheroliv (CC BY 4.0)"), output)

        val report = projectDir.resolve("build/hyperframes/publish/publication.json")
        assertTrue(report.isFile, "the publication report must be written")
        val json = report.readText()
        assertContains(json, "Music: Calm piano — cheroliv (CC BY 4.0)")
    }

    @Test
    fun `a composition without library music keeps the composed description unchanged`() {
        val projectDir = project()
        // Remove the sound declaration: no library track is used, so no credit.
        projectDir.resolve("src/docs/index.adoc").writeText(
            """
            = Credits demo

            == Calm

            Body.
            """.trimIndent(),
        )

        run(projectDir)

        val json = projectDir.resolve("build/hyperframes/publish/publication.json").readText()
        assertTrue(!json.contains("Music:"), json)
    }
}
