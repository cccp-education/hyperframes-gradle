package hyperframes

import hyperframes.chrome.ChromeConfig
import hyperframes.chrome.TypographyConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * HF-DEMO-QUALITY US-4 — processor wiring for the studio chrome and the
 * configurable typography.
 */
class HyperframesHtmlProcessorChromeTest {

    private fun document(sections: String): String = """
        <!DOCTYPE html>
        <html>
        <head><title>Chrome</title></head>
        <body class="article">
        <div id="content">
        $sections
        </div>
        </body>
        </html>
    """.trimIndent()

    private fun processor(
        chrome: ChromeConfig = ChromeConfig(),
        typography: TypographyConfig = TypographyConfig(),
    ) = HyperframesHtmlProcessor(
        width = 1920,
        height = 1080,
        fps = 30,
        outputName = "chrome-demo",
    ).withChrome(chrome).withTypography(typography)

    @Test
    fun `chrome is disabled by default and leaves the composition unchanged`() {
        val result = processor().enhance(
            document("""<div class="sect1"><h2 id="one">One</h2></div>""")
        )

        assertFalse(result.contains("hf-chrome"))
        assertContains(result, """window.__timelines["root"] = gsap.timeline({ paused: true });""")
    }

    @Test
    fun `enabled chrome is rendered per scene with its absolute start timecode`() {
        val result = processor(ChromeConfig(enabled = true, label = "LAB")).enhance(
            document(
                """
                <div class="sect1" data-duration="12"><h2 id="one">One</h2></div>
                <div class="sect1"><h2 id="two">Two</h2></div>
                """.trimIndent()
            )
        )

        assertEquals(2, Regex("""class="hf-chrome"""").findAll(result).count())
        assertContains(result, """class="hf-chrome-timecode">00:00:00:00<""")
        assertContains(result, """class="hf-chrome-timecode">00:00:12:00<""")
        assertContains(result, "LAB · 01 / 02")
        assertContains(result, "LAB · 02 / 02")
    }

    @Test
    fun `enabled chrome also emits the global progress bar and its root tween`() {
        val result = processor(ChromeConfig(enabled = true)).enhance(
            document(
                """
                <div class="sect1"><h2 id="one">One</h2></div>
                <div class="sect1"><h2 id="two">Two</h2></div>
                """.trimIndent()
            )
        )

        assertContains(result, """<div class="hf-chrome-progress" id="hf-chrome-progress"""")
        assertContains(
            result,
            """rootTl.fromTo("#hf-chrome-progress span", { scaleX: 0 }, { scaleX: 1, duration: 10, ease: "none" }, 0);"""
        )
        assertContains(result, """window.__timelines["root"] = rootTl;""")
    }

    @Test
    fun `typography config drives the css variables and the display elements`() {
        val result = processor(
            typography = TypographyConfig(
                displayFont = """"League Gothic", Impact, sans-serif""",
                monoFont = """"IBM Plex Mono", monospace""",
            )
        ).enhance(document("""<div class="sect1"><h2 id="one">One</h2></div>"""))

        assertContains(result, """--hf-display-font:"League Gothic", Impact, sans-serif""")
        assertContains(result, """--hf-mono-font:"IBM Plex Mono", monospace""")
        assertContains(result, "@font-face{font-family:'League Gothic';src:local('League Gothic')}")
        assertContains(result, "@font-face{font-family:'IBM Plex Mono';src:local('IBM Plex Mono')}")
        assertContains(result, ".scene-inner h2{font-family:var(--hf-display-font)")
        assertContains(result, ".scene-inner pre{font-family:var(--hf-mono-font)")
    }
}
