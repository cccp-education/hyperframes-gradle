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
        assertContains(result, """data-composition-id="intro"""")
        assertContains(result, """data-track-index=""")
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
    // 3. Sub-composition hosts + one timeline each
    // ──────────────────────────────────────────────

    @Test
    fun `every scene is a sub-composition host with its own registered timeline`() {
        val result = processor.enhance(asciidoctorDocument("""<div class="sect1"><h2 id="a">A</h2></div>"""))

        // HF-CR4-suite — a scene carrying data-composition-id is no longer a nested
        // timeline element (no `nested_structure_needs_subcomposition`) and the
        // runtime auto-nests its dedicated timeline.
        assertContains(result, """data-composition-id="a"""")
        assertContains(result, """var t = window.__timelines["a"] || gsap.timeline({ paused: true });""")
        assertContains(result, """window.__timelines["a"] = t;""")
        assertContains(result, "t.seek(0);")
    }

    @Test
    fun `the root composition also registers a paused timeline`() {
        val result = processor.enhance(asciidoctorDocument("""<div class="sect1"><h2 id="a">A</h2></div>"""))

        assertContains(result, """window.__timelines["root"] = gsap.timeline({ paused: true });""")
    }

    @Test
    fun `author animation code is collected and reused by the scene timeline`() {
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

        // Author motion registered first, then reused (not recreated) by the scene.
        assertContains(result, """tl.from("#anim"""")
        assertContains(result, """window.__timelines["anim"] = tl;""")
        assertContains(result, """var t = window.__timelines["anim"] || gsap.timeline({ paused: true });""")
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

    // ──────────────────────────────────────────────
    // 5. Scene transitions (HF-DEMO-QUALITY US-2 v1)
    // ──────────────────────────────────────────────

    @Test
    fun `a grid transition on the outgoing scene emits a cover overlay and root tweens`() {
        val result = processor.enhance(
            asciidoctorDocument(
                """
                <div class="sect1 hyperframes-composition hf-transition-grid" id="one"><h2 id="s1">One</h2></div>
                <div class="sect1"><h2 id="s2">Two</h2></div>
                """.trimIndent()
            )
        )

        assertContains(result, """class="hf-transition hf-transition-grid" id="hf-transition-1"""")
        assertEquals(12, Regex("""class="hf-transition-cell"""").findAll(result).count(),
            "the grid dissolve covers the frame with 12 cells")
        assertContains(result, """rootTl.to("#hf-transition-1 .hf-transition-cell"""")
        assertContains(result, """window.__timelines["root"] = rootTl;""")
        assertFalse(
            result.contains("""window.__timelines["root"] = gsap.timeline({ paused: true });"""),
            "a transition must replace the historical empty root timeline"
        )
    }

    @Test
    fun `shutter and fade roles emit their own overlays`() {
        val result = processor.enhance(
            asciidoctorDocument(
                """
                <div class="sect1 hyperframes-composition hf-transition-shutter" id="one"><h2 id="s1">One</h2></div>
                <div class="sect1 hyperframes-composition hf-transition-fade" id="two"><h2 id="s2">Two</h2></div>
                <div class="sect1"><h2 id="s3">Three</h2></div>
                """.trimIndent()
            )
        )

        assertContains(result, """class="hf-transition hf-transition-shutter" id="hf-transition-1"""")
        assertContains(result, """class="hf-shutter-top"""")
        assertContains(result, """class="hf-transition hf-transition-fade" id="hf-transition-2"""")
        assertContains(result, """gsap.set("#hf-transition-1 .hf-shutter-top", { yPercent: -100 });""")
        assertContains(result, """rootTl.to("#hf-transition-1 .hf-shutter-top"""")
        assertContains(result, """rootTl.fromTo("#hf-transition-2"""")
    }

    @Test
    fun `a transitioned scene does not fade out before its boundary`() {
        val result = processor.enhance(
            asciidoctorDocument(
                """
                <div class="sect1 hyperframes-composition hf-transition-grid" id="one"><h2 id="s1">One</h2></div>
                <div class="sect1"><h2 id="s2">Two</h2></div>
                """.trimIndent()
            )
        )

        assertFalse(
            result.contains("""t.to("#s1", { opacity: 0"""),
            "the cover overlay owns the handoff; the scene must stay visible until the boundary"
        )
    }

    @Test
    fun `no transition keeps the historical empty root timeline`() {
        val result = processor.enhance(
            asciidoctorDocument(
                """
                <div class="sect1"><h2 id="s1">One</h2></div>
                <div class="sect1"><h2 id="s2">Two</h2></div>
                """.trimIndent()
            )
        )

        assertContains(result, """window.__timelines["root"] = gsap.timeline({ paused: true });""")
        assertFalse(result.contains("""class="hf-transition hf-transition-"""), "no overlay without a role")
    }

    @Test
    fun `a transition on the last scene is ignored`() {
        val result = processor.enhance(
            asciidoctorDocument(
                """
                <div class="sect1 hyperframes-composition hf-transition-grid" id="one"><h2 id="s1">One</h2></div>
                """.trimIndent()
            )
        )

        assertFalse(result.contains("""class="hf-transition hf-transition-"""), "nothing to transition to")
        assertContains(result, """window.__timelines["root"] = gsap.timeline({ paused: true });""")
    }

    // ──────────────────────────────────────────────
    // 6. Audio cues (HF-DEMO-QUALITY US-3)
    // ──────────────────────────────────────────────

    @Test
    fun `an audio block emits direct audio children with start and volume`() {
        val result = processor.enhance(
            asciidoctorDocument(
                """
                <div class="sect1"><h2 id="a">A</h2>
                <div class="listingblock hyperframes-audio"><div class="content"><pre>bgm: assets/audio/bgm.mp3 volume=0.3
                sfx: 6.0 assets/audio/whoosh.mp3 volume=0.25</pre></div></div>
                </div>
                """.trimIndent()
            )
        )

        assertContains(result, """<audio id="hf-audio-0" src="assets/audio/bgm.mp3" """)
        assertContains(result, """data-start="0" data-duration="10"""")
        assertContains(result, """data-track-index="90" data-volume="0.30"""")
        assertContains(result, """<audio id="hf-audio-1" src="assets/audio/whoosh.mp3" """)
        assertContains(result, """data-start="6" """)
        assertContains(result, """data-volume="0.25"""")
        assertFalse(
            result.contains("listingblock hyperframes-audio"),
            "the audio block must be removed from the scene content"
        )
    }

    @Test
    fun `an invalid audio line is skipped and an empty block emits nothing`() {
        val result = processor.enhance(
            asciidoctorDocument(
                """
                <div class="sect1"><h2 id="a">A</h2>
                <div class="listingblock hyperframes-audio"><div class="content"><pre>bad: nope
                sfx: not-a-time file.mp3</pre></div></div>
                </div>
                """.trimIndent()
            )
        )

        assertFalse(result.contains("""<audio"""), "no cue must be emitted from invalid lines")
    }

    @Test
    fun `a document without an audio block emits no audio element`() {
        val result = processor.enhance(asciidoctorDocument("""<div class="sect1"><h2 id="a">A</h2></div>"""))

        assertFalse(result.contains("""<audio"""), "no audio by default")
    }

    private fun assertEquals(expected: Int, actual: Int, message: String) {
        kotlin.test.assertEquals(expected, actual, message)
    }
}
