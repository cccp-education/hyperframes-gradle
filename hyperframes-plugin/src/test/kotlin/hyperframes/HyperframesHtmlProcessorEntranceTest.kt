package hyperframes

import hyperframes.entrance.EntranceConfig
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

/**
 * HF-DEMO-QUALITY US-5 — processor wiring for choreographed entrances.
 */
class HyperframesHtmlProcessorEntranceTest {

    private fun document(sections: String): String = """
        <!DOCTYPE html>
        <html>
        <head><title>Entrances</title></head>
        <body class="article">
        <div id="content">
        $sections
        </div>
        </body>
        </html>
    """.trimIndent()

    private fun processor(entrances: EntranceConfig) = HyperframesHtmlProcessor(
        width = 1920,
        height = 1080,
        fps = 30,
        outputName = "entrance-demo",
    ).withEntrances(entrances)

    @Test
    fun `entrances are opt-in and keep the historical grouped animation`() {
        val result = processor(EntranceConfig()).enhance(
            document("""<div class="sect1"><h2 id="one">One</h2></div>""")
        )

        assertContains(result, """t.fromTo("#one .scene-inner", { opacity: 0, y: 30 }""")
        assertFalse(result.contains("var items = gsap.utils.toArray"))
    }

    @Test
    fun `enabled entrances hide the atoms at load and stagger them in`() {
        val result = processor(EntranceConfig(enabled = true)).enhance(
            document("""<div class="sect1"><h2 id="one">One</h2></div>""")
        )

        assertContains(result, """var items = gsap.utils.toArray("#one .scene-inner h1, #one .scene-inner h2""")
        assertContains(result, "gsap.set(items, { opacity: 0, y: 30 });")
        assertContains(result, "t.to(items, { opacity: 1, y: 0, duration: 0.55")
        assertContains(result, "stagger: 0.12")
        assertFalse(result.contains("""t.fromTo("#one .scene-inner""""))
    }

    @Test
    fun `each scene receives its own scoped choreography`() {
        val result = processor(EntranceConfig(enabled = true)).enhance(
            document(
                """
                <div class="sect1"><h2 id="one">One</h2></div>
                <div class="sect1"><h2 id="two">Two</h2></div>
                """.trimIndent()
            )
        )

        assertContains(result, """gsap.utils.toArray("#one .scene-inner""")
        assertContains(result, """gsap.utils.toArray("#two .scene-inner""")
    }
}
