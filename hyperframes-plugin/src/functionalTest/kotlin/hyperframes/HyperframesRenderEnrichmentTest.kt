package hyperframes

import java.io.File
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * HF-CR3 suite (B) — hermetic coverage of a *rich* enriched HTML: several
 * compositions plus an inlined GSAP timeline. The heavy real-render acceptance
 * (`cr3_real_render.feature`, non-blocking workflow) exercises the same document
 * against the real CLI; this test pins the enrichment itself in the required CI.
 */
class HyperframesRenderEnrichmentTest {

    @TempDir
    lateinit var tempDir: Path
    private lateinit var projectDir: File

    @BeforeEach
    fun setUp() {
        projectDir = tempDir.toFile()
        writeBuildFiles(projectDir)
    }

    @Test
    fun `generateHyperframesHtml enriches multiple compositions and an inlined GSAP timeline`() {
        val srcDir = projectDir.resolve("src/docs").apply { mkdirs() }
        srcDir.resolve("index.adoc").writeText(renderableDoc())

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("generateHyperframesHtml")
            .build()

        assertTrue(
            result.task(":generateHyperframesHtml")?.outcome?.toString()?.contains("SUCCESS") == true,
        )

        val html = projectDir.resolve("build/hyperframes/index.html").readText()
        assertContains(html, """data-composition-id="intro"""")
        assertContains(html, """data-composition-id="outro"""")
        assertContains(html, "window.__timelines")
        assertContains(html, "gsap.timeline")
        assertContains(html, "GSAP 3.12.5")
        assertTrue(!html.contains("cdnjs.cloudflare.com"), "no external GSAP CDN reference")
    }

    private fun renderableDoc(): String = buildString {
        appendLine("= Enrichment demo")
        appendLine(":hyperframes-width: 1920")
        appendLine(":hyperframes-height: 1080")
        appendLine(":hyperframes-fps: 30")
        appendLine()
        appendLine("[.hyperframes-composition#intro]")
        appendLine("== Intro")
        appendLine()
        appendLine("Hello.")
        appendLine()
        appendLine("[.hyperframes-animation, type=gsap]")
        appendLine("----")
        appendLine("window.__timelines = window.__timelines || {};")
        appendLine("const tl = gsap.timeline({ paused: true });")
        appendLine("window.__timelines[\"intro\"] = tl;")
        appendLine("----")
        appendLine()
        appendLine("[.hyperframes-composition#outro]")
        appendLine("== Outro")
        appendLine()
        appendLine("Bye.")
    }

    private fun writeBuildFiles(projectDir: File) {
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories { mavenLocal(); gradlePluginPortal(); mavenCentral() }
            }
            rootProject.name = "test-hyperframes-enrich"
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
                outputName.set("test-video")
            }
            """.trimIndent(),
        )
    }
}
