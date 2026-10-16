package hyperframes

import java.io.File
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Functional acceptance for `publishHyperframesToYouTube` (HF-YOUTUBE-PUBLISH
 * US-2). Hermetic: the task is exercised through Gradle TestKit with publishing
 * **disabled or unconfigured**, so it never reaches the network — the YouTube
 * flow itself is covered by the unit tests + the `@publish` BDD feature (fake
 * transport).
 */
class HyperframesPublishFunctionalTest {

    @TempDir
    lateinit var tempDir: Path
    private lateinit var projectDir: File

    @BeforeEach
    fun setUp() {
        projectDir = tempDir.toFile()
        writeBuildFiles(projectDir)
    }

    @Test
    fun `plugin registers the publish task under the deploy group`() {
        val result = runner("tasks", "--group=deploy")

        assertContains(result.output, "publishHyperframesToYouTube")
    }

    @Test
    fun `publishing is disabled by default and writes a skipped report`() {
        val result = runner("publishHyperframesToYouTube")

        assertContains(result.output, "disabled")
        val report = report()
        assertTrue(report.exists(), "publication.json should be written")
        val json = report.readText()
        assertTrue(SKIPPED_TRUE.containsMatchIn(json), "report should be skipped=true: $json")
        assertContains(json, "disabled")
    }

    @Test
    fun `an enabled publish without a credentials file degrades without network`() {
        // A rendered video + a title make the publication valid, so the resolver
        // is reached — it degrades (no credentials file) instead of failing.
        renderedVideo().writeText("MOCK-MP4")
        writeBuildFiles(projectDir, publishEnabled = true, title = "A title")

        val result = runner("publishHyperframesToYouTube")

        assertContains(result.output, "degraded")
        val json = report().readText()
        assertTrue(SKIPPED_TRUE.containsMatchIn(json), "report should be skipped=true: $json")
        assertContains(json, "credentials")
    }

    private companion object {
        /** Spacing-insensitive match for `"skipped": true` (Jackson pretty-print). */
        val SKIPPED_TRUE = Regex(""""skipped"\s*:\s*true""")
    }

    private fun renderedVideo(): File =
        projectDir.resolve("build/hyperframes/test-video.mp4").apply { parentFile.mkdirs() }

    private fun report(): File = projectDir.resolve("build/hyperframes/publish/publication.json")

    private fun runner(vararg args: String) = GradleRunner.create()
        .withProjectDir(projectDir)
        .withPluginClasspath()
        .withArguments(*args)
        .build()

    private fun writeBuildFiles(
        projectDir: File,
        publishEnabled: Boolean = false,
        title: String = "",
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
            rootProject.name = "test-hyperframes-publish"
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
                publishEnabled.set($publishEnabled)
                ${if (title.isNotBlank()) """publishTitle.set("$title")""" else ""}
                publishCategoryId.set("27")
            }
            """.trimIndent(),
        )
    }
}
