package hyperframes

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests unitaires pour [HyperframesHtmlProcessor].
 * Vérifie la transformation HTML : rôles AsciiDoc → data-* attributes HyperFrames.
 */
class HyperframesHtmlProcessorTest {

    private val processor = HyperframesHtmlProcessor(
        width = 1920,
        height = 1080,
        fps = 30,
        outputName = "test-video"
    )

    // ──────────────────────────────────────────────
    // 1. Stage wrapper
    // ──────────────────────────────────────────────

    @Test
    fun `wrap body content in stage div with data attributes`() {
        // Arrange
        val html = """<!DOCTYPE html>
<html>
<head><title>Test</title></head>
<body>
<div class="paragraph"><p>Hello</p></div>
</body>
</html>"""

        // Act
        val result = processor.enhance(html)

        // Assert
        assertContains(result, """data-width="1920"""")
        assertContains(result, """data-height="1080"""")
        assertContains(result, """data-fps="30"""")
        assertContains(result, """data-output="test-video.mp4"""")
        assertContains(result, """id="stage"""")
        assertContains(result, """<p>Hello</p>""")
    }

    // ──────────────────────────────────────────────
    // 2. Compositions
    // ──────────────────────────────────────────────

    @Test
    fun `add data-composition-id to hyperframes-composition blocks`() {
        // Arrange — le id="intro" est sur le <h2> enfant, pas sur le <div> parent
        val html = """<!DOCTYPE html>
<html>
<head><title>Test</title></head>
<body>
<div class="sect1 hyperframes-composition">
<h2 id="intro">Introduction</h2>
<div class="sectionbody">
<div class="paragraph"><p>Content</p></div>
</div>
</div>
</body>
</html>"""

        // Act
        val result = processor.enhance(html)

        // Assert
        assertContains(result, """data-composition-id="intro"""")
        // Vérifie que c'est sur le div composition, pas sur le h2
        val compDivRegex = Regex("""<div class="sect1 hyperframes-composition" data-composition-id="intro">""")
        assertTrue(compDivRegex.containsMatchIn(result), "data-composition-id should be on the composition div")
    }

    @Test
    fun `handle multiple compositions`() {
        // Arrange
        val html = """<!DOCTYPE html>
<html>
<head><title>Test</title></head>
<body>
<div class="sect1 hyperframes-composition">
<h2 id="intro">Intro</h2>
</div>
<div class="sect1 hyperframes-composition">
<h2 id="main">Main</h2>
</div>
</body>
</html>"""

        // Act
        val result = processor.enhance(html)

        // Assert
        assertContains(result, """data-composition-id="intro"""")
        assertContains(result, """data-composition-id="main"""")
    }

    @Test
    fun `composition without heading id gets no data-composition-id`() {
        // Arrange — pas d'id sur le heading
        val html = """<!DOCTYPE html>
<html>
<head><title>Test</title></head>
<body>
<div class="sect1 hyperframes-composition">
<h2>Untitled</h2>
</div>
</body>
</html>"""

        // Act
        val result = processor.enhance(html)

        // Assert — le div composition existe sans data-composition-id
        assertContains(result, """class="sect1 hyperframes-composition" """.trimEnd())
        assertFalse(result.contains("data-composition-id"), "Should not have data-composition-id without heading id")
    }

    // ──────────────────────────────────────────────
    // 3. Tracks
    // ──────────────────────────────────────────────

    @Test
    fun `add data-track-attributes to hyperframes-track blocks`() {
        // Arrange
        val html = """<!DOCTYPE html>
<html>
<head><title>Test</title></head>
<body>
<div class="paragraph hyperframes-track">
<p>Track content</p>
</div>
</body>
</html>"""

        // Act
        val result = processor.enhance(html)

        // Assert
        assertContains(result, """data-track-index="0"""")
        assertContains(result, """data-start="0"""")
        assertContains(result, """data-duration="6"""")
    }

    @Test
    fun `track blocks get sequential indexes`() {
        // Arrange
        val html = """<!DOCTYPE html>
<html>
<head><title>Test</title></head>
<body>
<div class="paragraph hyperframes-track"><p>Track 1</p></div>
<div class="paragraph hyperframes-track"><p>Track 2</p></div>
<div class="paragraph hyperframes-track"><p>Track 3</p></div>
</body>
</html>"""

        // Act
        val result = processor.enhance(html)

        // Assert
        assertContains(result, """data-track-index="0"""")
        assertContains(result, """data-track-index="1"""")
        assertContains(result, """data-track-index="2"""")
    }

    // ──────────────────────────────────────────────
    // 4. Animations
    // ──────────────────────────────────────────────

    @Test
    fun `wrap hyperframes-animation listingblock in script tag with GSAP`() {
        // Arrange — en AsciiDoc c'est un listingblock (---- fencé)
        val html = """<!DOCTYPE html>
<html>
<head><title>Test</title></head>
<body>
<div class="listingblock hyperframes-animation">
<div class="content">
<pre>const tl = gsap.timeline({ paused: true });
tl.from("#title", { opacity: 0, y: 40, duration: 0.8 }, 1);</pre>
</div>
</div>
</body>
</html>"""

        // Act
        val result = processor.enhance(html)

        // Assert
        assertContains(result, """<script type="text/javascript">""")
        assertContains(result, """window.__timelines = window.__timelines || {};""")
        assertContains(result, """gsap.timeline""")
        assertFalse(result.contains("class=\"listingblock hyperframes-animation\""),
            "Animation block wrapper should be replaced by script tag")
    }

    // ──────────────────────────────────────────────
    // 5. Edge cases
    // ──────────────────────────────────────────────

    @Test
    fun `preserve non-hyperframes blocks unchanged`() {
        // Arrange
        val html = """<!DOCTYPE html>
<html>
<head><title>Test</title></head>
<body>
<div class="sect1">
<h2 id="normal">Normal Section</h2>
<div class="sectionbody">
<div class="paragraph"><p>Normal paragraph</p></div>
</div>
</div>
</body>
</html>"""

        // Act
        val result = processor.enhance(html)

        // Assert — contenu normal préservé + stage ajouté
        assertContains(result, """<h2 id="normal">Normal Section</h2>""")
        assertContains(result, """<p>Normal paragraph</p>""")
        assertContains(result, """id="stage"""")
    }

    @Test
    fun `enhance produces valid HTML structure`() {
        // Arrange
        val html = """<html><head></head><body><p>Hello</p></body></html>"""

        // Act
        val result = processor.enhance(html)

        // Assert
        val bodyOpenCount = Regex("<body[^>]*>").findAll(result).count()
        val bodyCloseCount = Regex("</body>").findAll(result).count()
        assertTrue(bodyOpenCount == 1, "Should have exactly one <body> open tag")
        assertTrue(bodyCloseCount == 1, "Should have exactly one </body> close tag")
    }

    @Test
    fun `inject GSAP CDN script in head`() {
        // Arrange
        val html = """<!DOCTYPE html>
<html>
<head><title>Test</title></head>
<body><p>Hello</p></body>
</html>"""

        // Act
        val result = processor.enhance(html)

        // Assert
        assertContains(result, """gsap.min.js""")
        assertContains(result, """cdnjs.cloudflare.com""")
    }

    // ──────────────────────────────────────────────
    // 6. Code-diff template (HF-5b)
    // ──────────────────────────────────────────────

    @Test
    fun `expand code-diff block into composition with before and after pre blocks`() {
        // Arrange — HTML exactly as produced by AsciidoctorJ
        val html = """<!DOCTYPE html>
<html>
<head><title>Test</title></head>
<body>
<div class="sect1 hyperframes-code-diff">
<h2 id="refactor-demo">Refactoring demo</h2>
<div class="sectionbody">
<div class="listingblock">
<div class="content">
<pre class="highlight"><code class="language-kotlin" data-lang="kotlin">val x = oldFunc()</code></pre>
</div>
</div>
<div class="listingblock">
<div class="content">
<pre class="highlight"><code class="language-kotlin" data-lang="kotlin">val x = newFunc()</code></pre>
</div>
</div>
</div>
</div>
</body>
</html>"""

        // Act
        val result = processor.enhance(html)

        // Assert
        assertContains(result, "data-composition-id=\"refactor-demo\"")
        assertContains(result, "hf-code-diff-before")
        assertContains(result, "hf-code-diff-after")
        assertContains(result, "oldFunc()")
        assertContains(result, "newFunc()")
    }
}
