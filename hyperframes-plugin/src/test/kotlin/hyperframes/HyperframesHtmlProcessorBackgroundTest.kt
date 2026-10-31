package hyperframes

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * HF-DEMO-QUALITY US-6 — processor wiring for the per-scene background image.
 *
 * The pure domain is covered by [hyperframes.media.BackgroundImageTest]; this
 * class pins the composition contract: the `[.hyperframes-background]` block is
 * consumed (never rendered as visible content) and replaced by the full-bleed
 * `hf-bg` layer on the owning scene only.
 */
class HyperframesHtmlProcessorBackgroundTest {

    private val processor = HyperframesHtmlProcessor(
        width = 1920,
        height = 1080,
        fps = 30,
        outputName = "background-demo",
    )

    private fun document(sections: String): String = """
        <!DOCTYPE html>
        <html>
        <head><title>Background</title></head>
        <body class="article">
        <div id="content">
        $sections
        </div>
        </body>
        </html>
    """.trimIndent()

    private fun backgroundBlock(listing: String): String = """
        <div class="listingblock hyperframes-background"><div class="content"><pre>${listing.trimIndent()}</pre></div></div>
    """.trimIndent()

    @Test
    fun `a background block becomes a full-bleed layer on its scene`() {
        val result = processor.enhance(
            document(
                """
                <div class="sect1"><h2 id="one">One</h2>${backgroundBlock("file: assets/img/fiber.jpg")}</div>
                """.trimIndent()
            )
        )

        assertContains(result, """<div class="hf-bg" aria-hidden="true">""")
        assertContains(result, """<img class="hf-bg-media" src="assets/img/fiber.jpg" alt="" style="opacity:0.55">""")
        assertContains(result, """<div class="hf-bg-scrim"></div>""")
        assertFalse(result.contains("""<div class="hf-bg-grid"></div>"""))
        assertFalse(result.contains("""<div class="hf-bg-grain"></div>"""))
    }

    @Test
    fun `the background listing block is consumed and never rendered as content`() {
        val result = processor.enhance(
            document(
                """
                <div class="sect1"><h2 id="one">One</h2>${backgroundBlock("file: assets/img/fiber.jpg")}</div>
                """.trimIndent()
            )
        )

        assertFalse(result.contains("hyperframes-background"))
        assertFalse(result.contains("file: assets/img/fiber.jpg"))
    }

    @Test
    fun `the options drive the overlays and the media opacity`() {
        val result = processor.enhance(
            document(
                """
                <div class="sect1"><h2 id="one">One</h2>${backgroundBlock(
                    """
                    file: assets/img/datacenter.jpg
                    opacity: 0.35
                    scrim: false
                    grid: true
                    grain: true
                    """.trimIndent()
                )}</div>
                """.trimIndent()
            )
        )

        assertContains(result, """style="opacity:0.35"""")
        assertFalse(result.contains("""<div class="hf-bg-scrim"></div>"""))
        assertContains(result, """<div class="hf-bg-grid"></div>""")
        assertContains(result, """<div class="hf-bg-grain"></div>""")
    }

    @Test
    fun `the parallax option animates the media layer across the scene`() {
        val result = processor.enhance(
            document(
                """
                <div class="sect1"><h2 id="one">One</h2>${backgroundBlock(
                    """
                    file: assets/img/fiber.jpg
                    parallax: 0.1
                    """.trimIndent()
                )}</div>
                """.trimIndent()
            )
        )

        assertContains(result, """t.fromTo("#one .hf-bg-media", { scale: 1.20, yPercent: -5 }""")
        assertContains(result, """{ yPercent: 5, duration: 5, ease: "none" }, 0);""")
    }

    @Test
    fun `a media layer without parallax stays static`() {
        val result = processor.enhance(
            document(
                """
                <div class="sect1"><h2 id="one">One</h2>${backgroundBlock("file: assets/img/fiber.jpg")}</div>
                """.trimIndent()
            )
        )

        assertFalse(result.contains("""#one .hf-bg-media"""))
    }

    @Test
    fun `a scene without a background keeps the default scene background`() {
        val result = processor.enhance(
            document("""<div class="sect1"><h2 id="one">One</h2></div>""")
        )

        assertContains(result, """<div class="scene-bg"></div>""")
        assertFalse(result.contains("hf-bg"))
    }

    @Test
    fun `only the owning scene carries the media layer`() {
        val result = processor.enhance(
            document(
                """
                <div class="sect1"><h2 id="one">One</h2>${backgroundBlock("file: assets/img/fiber.jpg")}</div>
                <div class="sect1"><h2 id="two">Two</h2></div>
                """.trimIndent()
            )
        )

        assertEquals(1, Regex("""class="hf-bg"""").findAll(result).count())
        assertEquals(1, Regex("""class="scene-bg"""").findAll(result).count())
    }

    @Test
    fun `an invalid background block leaves the default scene background`() {
        val result = processor.enhance(
            document(
                """
                <div class="sect1"><h2 id="one">One</h2>${backgroundBlock("opacity: 0.4")}</div>
                """.trimIndent()
            )
        )

        assertContains(result, """<div class="scene-bg"></div>""")
        assertFalse(result.contains("hf-bg"))
        assertFalse(result.contains("hyperframes-background"))
    }
}
