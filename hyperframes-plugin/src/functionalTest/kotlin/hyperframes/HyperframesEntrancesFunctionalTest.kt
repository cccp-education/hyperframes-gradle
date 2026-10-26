package hyperframes

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertFalse

/**
 * HF-DEMO-QUALITY US-5 — TestKit acceptance for the DSL entrance choreography.
 */
class HyperframesEntrancesFunctionalTest {

    @TempDir
    lateinit var tempDir: Path

    private fun project(entrancesEnabled: Boolean): java.io.File {
        val projectDir = tempDir.toFile()
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories { mavenLocal(); gradlePluginPortal(); mavenCentral() }
            }
            rootProject.name = "entrances-functional-test"
            """.trimIndent()
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
                outputName.set("entrances-video")
                entrancesEnabled.set($entrancesEnabled)
            }
            """.trimIndent()
        )
        val srcDir = projectDir.resolve("src/docs")
        srcDir.mkdirs()
        srcDir.resolve("index.adoc").writeText(
            """
            [#introduction]
            == Introduction

            First atom.

            * one
            * two

            [#conclusion]
            == Conclusion

            Second scene body.
            """.trimIndent()
        )
        return projectDir
    }

    private fun generate(projectDir: java.io.File): String =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments("generateHyperframesHtml")
            .build()
            .let { projectDir.resolve("build/hyperframes/index.html").readText() }

    @Test
    fun `enabled entrances emit load-time setup and staggered tweens`() {
        val html = generate(project(entrancesEnabled = true))

        assertContains(html, """var items = gsap.utils.toArray("#introduction .scene-inner h1""")
        assertContains(html, "gsap.set(items, { opacity: 0, y: 30 });")
        assertContains(html, "t.to(items, { opacity: 1, y: 0, duration: 0.55")
        assertContains(html, "stagger: 0.12")
    }

    @Test
    fun `entrances are opt-in and keep the historical grouped animation by default`() {
        val html = generate(project(entrancesEnabled = false))

        assertContains(html, """t.fromTo("#introduction .scene-inner""")
        assertFalse(html.contains("var items = gsap.utils.toArray"))
    }
}
