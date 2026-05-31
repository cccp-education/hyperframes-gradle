package hyperframes

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertTrue

class HyperframesPluginTest {

    @TempDir
    lateinit var tempDir: Path
    private lateinit var projectDir: File

    @BeforeEach
    fun setUp() {
        projectDir = tempDir.toFile()
    }

    @Test
    fun `plugin registers generateHyperframesHtml and renderHyperframes tasks`() {
        // Arrange
        writeBuildFiles(projectDir)

        // Act
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("tasks", "--group=hyperframes")
            .build()

        // Assert
        val output = result.output
        assertContains(output, "generateHyperframesHtml")
        assertContains(output, "renderHyperframes")
    }

    @Test
    fun `generateHyperframesHtml produces output with valid AsciiDoc`() {
        // Arrange
        writeBuildFiles(projectDir)

        val srcDir = projectDir.resolve("src/docs")
        srcDir.mkdirs()
        srcDir.resolve("index.adoc").writeText("""
            = Test Document HyperFrames
            :hyperframes-width: 1920
            :hyperframes-height: 1080

            == Introduction

            Ceci est un document de test pour le pipeline HyperFrames.

            == Contenu

            Le parse AsciidoctorJ convertit cet AsciiDoc en HTML.
        """.trimIndent())

        // Act
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("generateHyperframesHtml", "--info")
            .build()

        // Assert
        assertContains(result.output, "generateHyperframesHtml")
        assertTrue(result.task(":generateHyperframesHtml")?.outcome?.toString()?.contains("SUCCESS") == true,
            "Task generateHyperframesHtml should succeed")

        // Vérifie que le fichier HTML a été généré
        val htmlFile = projectDir.resolve("build/hyperframes/index.html")
        assertTrue(htmlFile.exists(), "Generated index.html should exist")
        assertContains(htmlFile.readText(), "Test Document HyperFrames")
    }

    @Test
    fun `renderHyperframes fails without prior generation`() {
        // Arrange
        writeBuildFiles(projectDir)

        // Act
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("renderHyperframes", "--info")
            .buildAndFail()

        // Assert
        assertContains(result.output, "No HyperFrames HTML found")
    }

    // ──────────────────────────────────────────────────
    // HF-2 Tests : Bridge CLI via ProcessBuilder
    // ──────────────────────────────────────────────────

    @Test
    fun `renderHyperframes invokes mock CLI and produces MP4`() {
        // Arrange
        val mockCli = createMockCliScript(projectDir, exitCode = 0)
        writeBuildFiles(projectDir, cliScript = mockCli.absolutePath)
        createFakeHtml(projectDir)

        // Act
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("renderHyperframes", "--info")
            .build()

        // Assert
        assertContains(result.output, "HyperFrames CLI completed")
        val mp4File = projectDir.resolve("build/hyperframes/test-video.mp4")
        assertTrue(mp4File.exists(), "MP4 output should exist after successful render")
        assertContains(mp4File.readText(), "MOCK-MP4")
    }

    @Test
    fun `renderHyperframes fails when CLI returns non-zero exit code`() {
        // Arrange
        val mockCli = createMockCliScript(projectDir, exitCode = 1)
        writeBuildFiles(projectDir, cliScript = mockCli.absolutePath)
        createFakeHtml(projectDir)

        // Act
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("renderHyperframes", "--info")
            .buildAndFail()

        // Assert
        assertContains(result.output, "HyperFrames CLI failed")
        assertContains(result.output, "exit code 1")
    }

    @Test
    fun `renderHyperframes fails when CLI script does not exist`() {
        // Arrange
        writeBuildFiles(projectDir, cliScript = "/nonexistent/hyperframes-cli.js")
        createFakeHtml(projectDir)

        // Act
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("renderHyperframes", "--info")
            .buildAndFail()

        // Assert
        assertContains(result.output, "HyperFrames CLI script not found")
    }

    @Test
    fun `renderHyperframes respects timeout and fails on timeout`() {
        // Arrange
        val mockCli = createSlowMockCliScript(projectDir, delayMs = 5000)
        writeBuildFiles(
            projectDir,
            cliScript = mockCli.absolutePath,
            renderTimeoutMs = "500"
        )
        createFakeHtml(projectDir)

        // Act
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("renderHyperframes", "--info")
            .buildAndFail()

        // Assert
        assertContains(result.output, "timed out")
    }

    // ──────────────────────────────────────────────────
    // HF-3 Tests : DSL AsciiDoc → HTML enrichi
    // ──────────────────────────────────────────────────

    @Test
    fun `generateHyperframesHtml adds stage div with data attributes`() {
        // Arrange
        writeBuildFiles(projectDir)
        writeDocWithComposition(projectDir, compositionBody = """
            == Test
            Simple content.
        """.trimIndent())

        // Act
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("generateHyperframesHtml", "--info")
            .build()

        // Assert
        assertTrue(result.task(":generateHyperframesHtml")?.outcome?.toString()?.contains("SUCCESS") == true)
        val html = projectDir.resolve("build/hyperframes/index.html").readText()
        assertContains(html, """id="stage"""")
        assertContains(html, """data-width="1920"""")
        assertContains(html, """data-height="1080"""")
        assertContains(html, """data-fps="30"""")
    }

    @Test
    fun `generateHyperframesHtml adds data-composition-id from heading id`() {
        // Arrange
        writeBuildFiles(projectDir)
        writeDocWithComposition(projectDir, compositionBody = buildString {
            // Syntaxe AsciiDoc : [role#id] avant le heading
            appendLine("[.hyperframes-composition#intro]")
            appendLine("== Introduction")
            append("Content for intro.")
        })

        // Act
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("generateHyperframesHtml", "--info")
            .build()

        // Assert
        assertTrue(result.task(":generateHyperframesHtml")?.outcome?.toString()?.contains("SUCCESS") == true)
        val html = projectDir.resolve("build/hyperframes/index.html").readText()
        assertContains(html, """data-composition-id="intro"""")
    }

    @Test
    fun `generateHyperframesHtml adds data-track-attributes to track blocks`() {
        // Arrange
        writeBuildFiles(projectDir)
        writeDocWithComposition(projectDir, compositionBody = buildString {
            appendLine("[.hyperframes-track]")
            append("Track content here.")
        })

        // Act
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("generateHyperframesHtml", "--info")
            .build()

        // Assert
        assertTrue(result.task(":generateHyperframesHtml")?.outcome?.toString()?.contains("SUCCESS") == true)
        val html = projectDir.resolve("build/hyperframes/index.html").readText()
        assertContains(html, """data-track-index""")
        assertContains(html, """data-start""")
        assertContains(html, """data-duration""")
    }

    @Test
    fun `generateHyperframesHtml injects GSAP CDN in head`() {
        // Arrange
        writeBuildFiles(projectDir)
        writeDocWithComposition(projectDir, compositionBody = buildString {
            appendLine("== Test")
            append("Simple content.")
        })

        // Act
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("generateHyperframesHtml", "--info")
            .build()

        // Assert
        assertTrue(result.task(":generateHyperframesHtml")?.outcome?.toString()?.contains("SUCCESS") == true)
        val html = projectDir.resolve("build/hyperframes/index.html").readText()
        assertContains(html, """gsap.min.js""")
        assertContains(html, """cdnjs.cloudflare.com""")
    }

    @Test
    fun `generateHyperframesHtml with full DSL produces valid HyperFrames HTML`() {
        // Arrange — DSL complet avec composition + tracks + animation
        writeBuildFiles(projectDir)
        writeFullDslDoc(projectDir)

        // Act
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("generateHyperframesHtml", "--info")
            .build()

        // Assert
        assertTrue(result.task(":generateHyperframesHtml")?.outcome?.toString()?.contains("SUCCESS") == true)
        val html = projectDir.resolve("build/hyperframes/index.html").readText()

        // Stage
        assertContains(html, """id="stage"""")
        assertContains(html, """data-output="test-video.mp4"""")

        // Composition
        assertContains(html, """data-composition-id="intro"""")

        // Tracks
        assertContains(html, """data-track-index""")
        assertContains(html, """data-duration""")

        // GSAP
        assertContains(html, """gsap.min.js""")

        // Animation script
        assertContains(html, """__timelines""")
    }

    // ──────────────────────────────────────────────────
    // Helpers — HF-2
    // ──────────────────────────────────────────────────

    /**
     * Creates a mock CLI script that simulates HyperFrames render.
     * Writes a fake MP4 and exits with given code.
     */
    private fun createMockCliScript(projectDir: File, exitCode: Int): File {
        val script = projectDir.resolve("mock-hyperframes-cli.sh")
        val dollar = "${'$'}"
        script.writeText("""
            #!/bin/bash
            # Mock HyperFrames CLI pour tests HF-2
            OUTPUT_FILE=""
            while [[ ${dollar}# -gt 0 ]]; do
                case ${dollar}1 in
                    --output) OUTPUT_FILE="${dollar}2"; shift 2 ;;
                    *) shift ;;
                esac
            done
            if [[ -n "${dollar}OUTPUT_FILE" ]]; then
                echo "MOCK-MP4" > "${dollar}OUTPUT_FILE"
            fi
            echo "Mock HyperFrames CLI: render complete"
            exit $exitCode
        """.trimIndent())
        script.setExecutable(true)
        return script
    }

    /**
     * Creates a slow mock CLI that delays before exiting.
     */
    private fun createSlowMockCliScript(projectDir: File, delayMs: Long): File {
        val script = projectDir.resolve("mock-hyperframes-slow.sh")
        val dollar = "${'$'}"
        script.writeText("""
            #!/bin/bash
            # Mock slow CLI pour test timeout
            sleep ${dollar}(( $delayMs / 1000 + 1 ))
            echo "Mock slow CLI: done (too late)"
            exit 0
        """.trimIndent())
        script.setExecutable(true)
        return script
    }

    /**
     * Creates a fake index.html in the output directory.
     */
    private fun createFakeHtml(projectDir: File) {
        val htmlFile = projectDir.resolve("build/hyperframes/index.html")
        htmlFile.parentFile.mkdirs()
        htmlFile.writeText("<html><body>Test HyperFrames</body></html>")
    }

    // ──────────────────────────────────────────────────
    // Helpers — HF-3
    // ──────────────────────────────────────────────────

    /**
     * Écrit un document AsciiDoc avec contenu additionnel dans src/docs/index.adoc.
     */
    private fun writeDocWithComposition(projectDir: File, compositionBody: String) {
        val srcDir = projectDir.resolve("src/docs")
        srcDir.mkdirs()
        // Construit l'AsciiDoc sans trimIndent pour éviter les problèmes d'indentation
        val doc = buildString {
            appendLine("= Test HyperFrames DSL")
            appendLine(":hyperframes-width: 1920")
            appendLine(":hyperframes-height: 1080")
            appendLine(":hyperframes-fps: 30")
            appendLine()
            appendLine(compositionBody.trimStart())
        }
        srcDir.resolve("index.adoc").writeText(doc)
    }

    /**
     * Écrit un document AsciiDoc avec le DSL HyperFrames complet.
     */
    private fun writeFullDslDoc(projectDir: File) {
        val srcDir = projectDir.resolve("src/docs")
        srcDir.mkdirs()
        val doc = buildString {
            appendLine("= Test HyperFrames DSL Complet")
            appendLine(":hyperframes-width: 1920")
            appendLine(":hyperframes-height: 1080")
            appendLine(":hyperframes-fps: 30")
            appendLine()
            appendLine("[.hyperframes-composition#intro]")
            appendLine("== Introduction")
            appendLine()
            appendLine("[.hyperframes-track]")
            appendLine("Ceci est un track de test.")
            appendLine()
            appendLine("[.hyperframes-animation]")
            appendLine("----")
            appendLine("const tl = gsap.timeline({ paused: true });")
            appendLine("tl.from(\"#title\", { opacity: 0, y: 40, duration: 0.8 }, 1);")
            appendLine("----")
        }
        srcDir.resolve("index.adoc").writeText(doc)
    }

    private fun writeBuildFiles(
        projectDir: File,
        cliScript: String = "",
        renderTimeoutMs: String = "300000"
    ) {
        projectDir.resolve("settings.gradle.kts").writeText("""
            pluginManagement {
                repositories {
                    mavenLocal()
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            rootProject.name = "test-hyperframes"
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
                ${if (cliScript.isNotEmpty()) """cliScript.set("$cliScript")""" else ""}
                ${if (cliScript.isNotEmpty()) """nodeExecutable.set("$cliScript")""" else ""}
                renderTimeoutMs.set($renderTimeoutMs)
            }
        """.trimIndent())
    }
}
