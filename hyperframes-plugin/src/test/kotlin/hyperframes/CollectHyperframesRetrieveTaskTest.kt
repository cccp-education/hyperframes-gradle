package hyperframes

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertTrue

class CollectHyperframesRetrieveTaskTest {

    @TempDir
    lateinit var tempDir: Path
    private lateinit var projectDir: File

    @BeforeEach
    fun setUp() {
        projectDir = tempDir.toFile()
    }

    @Test
    fun `plugin registers collectHyperframesRetrieve task`() {
        // Arrange
        writeBuildFiles(projectDir)

        // Act
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("tasks", "--all")
            .build()

        // Assert
        val output = result.output
        assertContains(output, "collectHyperframesRetrieve")
        assertContains(output, "Collect HyperFrames rendered video metadata for N3 runner integration")
    }

    @Test
    fun `collectHyperframesRetrieve succeeds with no artifacts when nothing rendered yet`() {
        // Arrange
        writeBuildFiles(projectDir)

        // Act
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("collectHyperframesRetrieve", "--info")
            .build()

        // Assert — task succeeds but writes no composite-context.json
        // (nothing has been rendered yet; no MP4/HTML to extract from)
        assertTrue(result.task(":collectHyperframesRetrieve")?.outcome?.toString()?.contains("SUCCESS") == true,
            "Task should succeed even with no artifacts")
        assertContains(result.output, "no MP4/HTML artifacts")
    }

    @Test
    fun `collectHyperframesRetrieve produces N3 contract JSON when MP4 and HTML exist`() {
        // Arrange
        writeBuildFiles(projectDir)

        val buildDir = projectDir.resolve("build/hyperframes")
        buildDir.mkdirs()
        val mp4File = buildDir.resolve("test-video.mp4")
        mp4File.writeBytes(ByteArray(1024))

        val htmlFile = buildDir.resolve("index.html")
        htmlFile.writeText(
            """
            <html><body>
            <div id="stage" data-width="1920" data-height="1080" data-fps="30" data-output="test-video.mp4">
                <div class="sect1 hyperframes-composition" data-composition-id="intro">
                    <h2 id="intro">Introduction</h2>
                </div>
                <div class="paragraph hyperframes-track" data-track-index="0" data-start="0" data-duration="5"></div>
            </div>
            </body></html>
            """.trimIndent()
        )

        // Act
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("collectHyperframesRetrieve", "--info")
            .build()

        // Assert
        assertTrue(
            result.task(":collectHyperframesRetrieve")?.outcome?.toString()?.contains("SUCCESS") == true,
            "Task should succeed"
        )

        val contextFile = projectDir.resolve("build/hyperframes/composite-context.json")
        assertTrue(contextFile.exists(), "composite-context.json should exist")

        val contextContent = contextFile.readText()
        // N3 contract — top-level fields
        assertContains(contextContent, "\"plugin\"")
        assertContains(contextContent, "\"version\"")
        assertContains(contextContent, "\"output\"")
        assertContains(contextContent, "\"renderedAt\"")
        assertContains(contextContent, "\"renderDurationMs\"")
        // N3 contract — nested video descriptor
        assertContains(contextContent, "\"video\"")
        assertContains(contextContent, "test-video.mp4")
        assertContains(contextContent, "\"width\" : 1920")
        assertContains(contextContent, "\"height\" : 1080")
        assertContains(contextContent, "\"fps\" : 30")
        assertContains(contextContent, "\"codec\" : \"h264\"")
        // N3 contract — nested source descriptor
        assertContains(contextContent, "\"asciidoc\"")
        assertContains(contextContent, "\"compositions\"")
        assertContains(contextContent, "intro")
        assertContains(contextContent, "\"tracks\" : 1")
    }

    private fun writeBuildFiles(projectDir: File) {
        projectDir.resolve("settings.gradle.kts").writeText("""
            pluginManagement {
                repositories {
                    mavenLocal()
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            rootProject.name = "test-hyperframes-retrieve"
        """.trimIndent())

        projectDir.resolve("build.gradle.kts").writeText("""
            plugins {
                id("education.cccp.hyperframes")
            }

            hyperframes {
                inputDir.set(layout.projectDirectory.dir("src/docs"))
                outputDir.set(layout.buildDirectory.dir("hyperframes"))
                width.set(1920)
                height.set(1080)
                fps.set(30)
                outputName.set("test-video")
            }
        """.trimIndent())
    }
}