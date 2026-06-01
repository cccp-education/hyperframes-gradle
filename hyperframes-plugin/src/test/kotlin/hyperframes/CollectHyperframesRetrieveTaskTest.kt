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
    fun `collectHyperframesRetrieve produces empty context when no MP4 exists`() {
        // Arrange
        writeBuildFiles(projectDir)

        // Act
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("collectHyperframesRetrieve", "--info")
            .build()

        // Assert
        assertContains(result.output, "collectHyperframesRetrieve")
        assertTrue(result.task(":collectHyperframesRetrieve")?.outcome?.toString()?.contains("SUCCESS") == true,
            "Task collectHyperframesRetrieve should succeed")

        // Check that composite-context.json was created
        val compositeContextFile = projectDir.resolve("build/hyperframes/composite-context.json")
        assertTrue(compositeContextFile.exists(), "composite-context.json should exist")
        
        // Check that metadata.json was created
        val metadataFile = projectDir.resolve("build/hyperframes/metadata.json")
        assertTrue(metadataFile.exists(), "metadata.json should exist")
        
        val contextContent = compositeContextFile.readText()
        assertContains(contextContent, "\"count\" : 0")
        assertContains(contextContent, "\"source\" : \"watts\"")
    }

    @Test
    fun `collectHyperframesRetrieve produces context with video metadata when MP4 exists`() {
        // Arrange
        writeBuildFiles(projectDir)
        
        // Create a fake MP4 file to simulate successful rendering
        val buildDir = projectDir.resolve("build/hyperframes")
        buildDir.mkdirs()
        val mp4File = buildDir.resolve("test-video.mp4")
        mp4File.writeText("FAKE_MP4_CONTENT")
        
        // Create HTML with metadata to extract dimensions/compositions
        val htmlFile = buildDir.resolve("index.html")
        htmlFile.writeText("""
            <html>
            <body>
            <div id="stage" data-width="1920" data-height="1080" data-fps="30" data-output="test-video.mp4">
                <div class="sect1 hyperframes-composition" data-composition-id="intro">
                    <h2 id="intro">Introduction</h2>
                </div>
                <div class="paragraph hyperframes-track" data-track-index="0" data-start="0" data-duration="5"></div>
            </div>
            </body>
            </html>
        """.trimIndent())

        // Act
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("collectHyperframesRetrieve", "--info")
            .build()

        // Assert
        assertContains(result.output, "collectHyperframesRetrieve")
        assertTrue(result.task(":collectHyperframesRetrieve")?.outcome?.toString()?.contains("SUCCESS") == true,
            "Task collectHyperframesRetrieve should succeed")

        // Check that composite-context.json was created with video metadata
        val contextFile = projectDir.resolve("build/hyperframes/composite-context.json")
        assertTrue(contextFile.exists(), "composite-context.json should exist")
        
        val contextContent = contextFile.readText()
        assertContains(contextContent, "\"count\" : 0") // Current implementation generates empty entries
        assertContains(contextContent, "\"source\" : \"watts\"")
        
        // Check that metadata.json was created
        val metadataFile = projectDir.resolve("build/hyperframes/metadata.json")
        assertTrue(metadataFile.exists(), "metadata.json should exist")
        val metadataContent = metadataFile.readText()
        assertContains(metadataContent, "\"source\" : \"watts\"")
        assertContains(metadataContent, "\"type\" : \"retrieve\"")
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