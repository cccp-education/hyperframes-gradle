package hyperframes

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertTrue

class HyperframesPluginTest {

    @Test
    fun `plugin registers generateHyperframesHtml and renderHyperframes tasks`(@TempDir tempDir: Path) {
        // Arrange
        writeBuildFiles(tempDir.toFile())

        // Act
        val result = GradleRunner.create()
            .withProjectDir(tempDir.toFile())
            .withPluginClasspath()
            .withArguments("tasks", "--group=hyperframes")
            .build()

        // Assert
        val output = result.output
        assertContains(output, "generateHyperframesHtml")
        assertContains(output, "renderHyperframes")
    }

    @Test
    fun `generateHyperframesHtml produces output with valid AsciiDoc`(@TempDir tempDir: Path) {
        // Arrange
        val projectDir = tempDir.toFile()
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
    fun `renderHyperframes fails without prior generation`(@TempDir tempDir: Path) {
        // Arrange
        val projectDir = tempDir.toFile()
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

    private fun writeBuildFiles(projectDir: File) {
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
            }
        """.trimIndent())
    }
}
