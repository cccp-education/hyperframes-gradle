package hyperframes

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests unitaires pour [HyperframesHtmlProcessor] (HF-CR4).
 *
 * Le processor ne se contente plus d'habiller le document Asciidoctor : il
 * produit une composition HyperFrames conforme — une composition racine
 * `#stage`, une scène plein cadre par section, un unique timeline racine, et
 * un design system embarqué. Ces tests pinnent ce contrat.
 */
class HyperframesHtmlProcessorTest {

    private val processor = HyperframesHtmlProcessor(
        width = 1920,
        height = 1080,
        fps = 30,
        outputName = "test-video"
    )

    /** Minimal Asciidoctor-shaped document with one plain section. */
    private fun asciidoctorDocument(section: String): String = """
        <!DOCTYPE html>
        <html>
        <head><title>Test</title></head>
        <body class="article">
        <div id="header"><h1>Doc title</h1></div>
        <div id="content">
        $section
        </div>
        <div id="footer"><div id="footer-text">last updated</div></div>
        </body>
        </html>
    """.trimIndent()

    // ──────────────────────────────────────────────
    // 1. Root composition
    // ──────────────────────────────────────────────

    @Test
    fun `root stage carries the composition id and the configured dimensions`() {
        val result = processor.enhance(asciidoctorDocument("""<div class="sect1"><h2 id="normal">Normal Section</h2></div>"""))

        assertContains(result, """id="stage"""")
        assertContains(result, """data-composition-id="root"""")
        assertContains(result, """data-width="1920"""")
        assertContains(result, """data-height="1080"""")
        assertContains(result, """data-fps="30"""")
        assertContains(result, """data-output="test-video.mp4"""")
    }

    @Test
    fun `enhance produces a single well-formed body`() {
        val result = processor.enhance("""<html><head></head><body><p>Hello</p></body></html>""")

        assertEquals(1, Regex("<body[^>]*>").findAll(result).count(), "exactly one <body> open tag")
        assertEquals(1, Regex("</body>").findAll(result).count(), "exactly one </body> close tag")
    }

    @Test
    fun `vendored GSAP is loaded as an offline external script, never the CDN`() {
        val result = processor.enhance(asciidoctorDocument("""<div class="sect1"><h2 id="a">A</h2></div>"""))

        assertContains(result, """<script src="gsap-3.12.5.min.js"></script>""")
        assertFalse(result.contains("cdnjs.cloudflare.com"), "the CDN must never be referenced")
        assertFalse(result.contains("""src="http"""), "no external network script source")
    }

    // ──────────────────────────────────────────────
    // 2. Sections become scenes
    // ──────────────────────────────────────────────

    @Test
    fun `each top-level section becomes a full-bleed scene clip`() {
        val result = processor.enhance(
            asciidoctorDocument(
                """
                <div class="sect1">
                <h2 id="intro">Introduction</h2>
                <div class="sectionbody"><div class="paragraph"><p>Content</p></div></div>
                </div>
                """.trimIndent()
            )
        )

        assertContains(result, """class="scene clip" id="intro"""")
        assertContains(result, """data-track-index="0"""")
        assertContains(result, """<h2 id="intro">Introduction</h2>""")
        assertContains(result, "<p>Content</p>")
    }

    @Test
    fun `the document header becomes a leading title scene`() {
        val result = processor.enhance(
            asciidoctorDocument("""<div class="sect1"><h2 id="a">A</h2></div>""")
        )

        assertContains(result, """class="scene clip" id="hf-title"""")
        assertContains(result, "Doc title")
    }

    @Test
    fun `an explicit composition id wins over the heading id`() {
        val result = processor.enhance(
            asciidoctorDocument(
                """<div class="sect1 hyperframes-composition" data-composition-id="intro"><h2 id="legacy">Intro</h2></div>"""
            )
        )

        assertContains(result, """class="scene clip" id="intro"""")
    }

    @Test
    fun `reserved clip-timing attributes are stripped from nested content`() {
        // A legacy .hyperframes-track wrapper must not keep data-start/duration:
        // the runtime would hide it outside its (absolute) window and blank the scene.
        val result = processor.enhance(
            asciidoctorDocument(
                """
                <div class="sect1">
                <h2 id="a">A</h2>
                <div class="sectionbody">
                <div class="paragraph hyperframes-track" data-track-index="0" data-start="0" data-duration="6"><p>Track content</p></div>
                </div>
                </div>
                """.trimIndent()
            )
        )

        assertContains(result, "Track content")
        assertFalse(
            result.contains("hyperframes-track\" data-track-index"),
            "nested track wrappers must drop the reserved timing attributes"
        )
    }

    // ──────────────────────────────────────────────
    // 3. Single root timeline
    // ──────────────────────────────────────────────

    @Test
    fun `exactly one root timeline is registered and seeks to zero`() {
        val result = processor.enhance(asciidoctorDocument("""<div class="sect1"><h2 id="a">A</h2></div>"""))

        assertContains(result, """window.__timelines["root"] = t;""")
        assertContains(result, "gsap.timeline")
        assertContains(result, "t.seek(0);")
    }

    @Test
    fun `the scene starts registry maps every scene id to its start`() {
        val result = processor.enhance(
            asciidoctorDocument(
                """
                <div class="sect1"><h2 id="a">A</h2></div>
                <div class="sect1"><h2 id="b">B</h2></div>
                """.trimIndent()
            )
        )

        assertContains(result, "window.__hfSceneStarts = {")
        assertContains(result, """"a":""")
        assertContains(result, """"b":""")
    }

    @Test
    fun `author animation code is collected and merged, never registered as a stray timeline`() {
        val result = processor.enhance(
            asciidoctorDocument(
                """
                <div class="sect1">
                <h2 id="anim">Anim</h2>
                <div class="listingblock hyperframes-animation"><div class="content"><pre>const tl = gsap.timeline({ paused: true });
                tl.from("#anim", { opacity: 0, duration: 0.8 }, 0);
                window.__timelines["anim"] = tl;</pre></div></div>
                </div>
                """.trimIndent()
            )
        )

        assertContains(result, "window.__hfSceneTimelines")
        assertContains(result, """gsap["timeline"]()""")
        assertContains(result, """tl.from("#anim"""")
    }

    // ──────────────────────────────────────────────
    // 4. DSL templates still expand
    // ──────────────────────────────────────────────

    @Test
    fun `title-card block expands into a designed title scene`() {
        val result = processor.enhance(
            asciidoctorDocument(
                """
                <div class="sect1 hyperframes-title-card">
                <h2 id="intro">My Presentation</h2>
                <div class="sectionbody"><div class="paragraph"><p>Module 01</p></div></div>
                </div>
                """.trimIndent()
            )
        )

        assertContains(result, "hf-title-card-title\">My Presentation</span>")
        assertContains(result, "hf-title-card-subtitle\">Module 01</span>")
    }

    @Test
    fun `code-diff block expands into a scene with before and after pre blocks`() {
        val result = processor.enhance(
            asciidoctorDocument(
                """
                <div class="sect1 hyperframes-code-diff">
                <h2 id="refactor-demo">Refactoring demo</h2>
                <div class="sectionbody">
                <div class="listingblock"><div class="content"><pre class="highlight"><code class="language-kotlin" data-lang="kotlin">val x = oldFunc()</code></pre></div></div>
                <div class="listingblock"><div class="content"><pre class="highlight"><code class="language-kotlin" data-lang="kotlin">val x = newFunc()</code></pre></div></div>
                </div>
                </div>
                """.trimIndent()
            )
        )

        assertContains(result, """class="scene clip" id="refactor-demo"""")
        assertContains(result, "hf-code-diff-before")
        assertContains(result, "hf-code-diff-after")
        assertContains(result, "oldFunc()")
        assertContains(result, "newFunc()")
    }

    @Test
    fun `data-chart block expands into a scene with proportional bars`() {
        val result = processor.enhance(
            asciidoctorDocument(
                """
                <div class="sect1 hyperframes-data-chart">
                <h2 id="sales">Sales</h2>
                <div class="sectionbody"><div class="listingblock"><div class="content"><pre>Q1: 30
                Q2: 60</pre></div></div></div>
                </div>
                """.trimIndent()
            )
        )

        assertContains(result, "hf-data-chart-bar")
        assertContains(result, """style="height:100%"""")
    }

    private fun assertEquals(expected: Int, actual: Int, message: String) {
        kotlin.test.assertEquals(expected, actual, message)
    }
}
