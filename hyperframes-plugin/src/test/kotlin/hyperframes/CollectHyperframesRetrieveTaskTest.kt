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

    // ──────────────────────────────────────────────
    // HF-KERNEL-ADAPTER US-1 — D-C (duration), D-F (strict), pipeline ordering
    // ──────────────────────────────────────────────

    @Test
    fun `collectHyperframesRetrieve reports the measured video duration (D-C)`() {
        // Arrange — a stub ffprobe prints the duration the real one would.
        val stubFfprobe = createStubFfprobe(projectDir, seconds = "12.5")
        writeBuildFiles(projectDir, ffprobeScript = stubFfprobe.absolutePath)

        val buildDir = projectDir.resolve("build/hyperframes")
        buildDir.mkdirs()
        buildDir.resolve("test-video.mp4").writeBytes(ByteArray(1024))
        buildDir.resolve("index.html").writeText(stageHtml())

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
        val contextContent = projectDir.resolve("build/hyperframes/composite-context.json").readText()
        assertContains(contextContent, "\"durationSeconds\" : 12.5")
    }

    @Test
    fun `collectHyperframesRetrieve degrades the duration to zero without a prober (D-C)`() {
        // Arrange — no ffprobe on the PATH nor configured.
        writeBuildFiles(projectDir)

        val buildDir = projectDir.resolve("build/hyperframes")
        buildDir.mkdirs()
        buildDir.resolve("test-video.mp4").writeBytes(ByteArray(1024))
        buildDir.resolve("index.html").writeText(stageHtml())

        // Act
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("collectHyperframesRetrieve", "--info")
            .build()

        // Assert — degraded duration, never a build failure.
        val contextContent = projectDir.resolve("build/hyperframes/composite-context.json").readText()
        assertContains(contextContent, "\"durationSeconds\" : 0.0")
    }

    @Test
    fun `collectHyperframesRetrieve fails in strict mode when no artifacts exist (D-F)`() {
        // Arrange — strict mode + nothing rendered.
        writeBuildFiles(projectDir, strict = true)

        // Act
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("collectHyperframesRetrieve", "--info")
            .buildAndFail()

        // Assert
        assertContains(result.output, "strict mode is enabled")
    }

    @Test
    fun `hyperframesPipeline lifecycle task orders generate then render then collect (D-F)`() {
        // Arrange
        writeBuildFiles(projectDir)

        // Act — dry-run only: no render, no node required.
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("hyperframesPipeline", "--dry-run")
            .build()

        // Assert — generate < render < collect in the execution plan.
        val output = result.output
        val generate = output.indexOf(":generateHyperframesHtml")
        val render = output.indexOf(":renderHyperframes")
        val collect = output.indexOf(":collectHyperframesRetrieve")
        assertTrue(
            generate in 0 until render && render < collect,
            "expected pipeline order generate($generate) < render($render) < collect($collect) in:\n$output"
        )
    }

    private fun writeBuildFiles(
        projectDir: File,
        ffprobeScript: String = "",
        strict: Boolean = false
    ) {
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
                ${if (ffprobeScript.isNotEmpty()) """ffprobeExecutable.set("$ffprobeScript")""" else ""}
                strict.set($strict)
            }
        """.trimIndent())
    }

    /**
     * Creates a stub `ffprobe` that prints the given duration and exits 0,
     * emulating `-show_entries format=duration`.
     */
    private fun createStubFfprobe(projectDir: File, seconds: String): File {
        val script = projectDir.resolve("stub-ffprobe.sh")
        val dollar = "${'$'}"
        script.writeText("""
            #!/bin/bash
            echo "$seconds"
            exit 0
        """.trimIndent())
        script.setExecutable(true)
        return script
    }

    private fun stageHtml() = """
        <html><body>
        <div id="stage" data-width="1920" data-height="1080" data-fps="30" data-output="test-video.mp4">
            <div class="sect1 hyperframes-composition" data-composition-id="intro">
                <h2 id="intro">Introduction</h2>
            </div>
            <div class="paragraph hyperframes-track" data-track-index="0" data-start="0" data-duration="5"></div>
        </div>
        </body></html>
    """.trimIndent()
}