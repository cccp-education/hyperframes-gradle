package hyperframes

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TDD functional tests for HF-7c — pronunciation DSL processor.
 *
 * Verifies that [HyperframesHtmlProcessor] expands AsciiDoc
 * `[hyperframes-pronunciation]` listingblocks into a JSON island
 * consumed by the HyperFrames CLI to correct TTS phonetics.
 */
class HyperframesPronunciationProcessorTest {

    private val processor = HyperframesHtmlProcessor(
        width = 1920,
        height = 1080,
        fps = 30,
        outputName = "test-video"
    )

    @Test
    fun `pronunciation block is expanded into a JSON island script tag`() {
        val html = """<!DOCTYPE html>
<html>
<head><title>Test</title></head>
<body>
<div class="listingblock hyperframes-pronunciation">
<div class="content">
<pre>dos: do
kubernetes: koobernetayz</pre>
</div>
</div>
</body>
</html>"""

        val result = processor.enhance(html)

        assertContains(result, """<script type="application/json" id="hf-pronunciation">""")
        assertContains(result, "</script>")
        assertFalse(
            result.contains("class=\"listingblock hyperframes-pronunciation\""),
            "pronunciation block wrapper should be replaced by the JSON island"
        )
    }

    @Test
    fun `pronunciation block with two hints renders both words in the JSON array`() {
        val html = """<!DOCTYPE html>
<html>
<head><title>Test</title></head>
<body>
<div class="listingblock hyperframes-pronunciation">
<div class="content">
<pre>dos: do
kubernetes: koobernetayz</pre>
</div>
</div>
</body>
</html>"""

        val result = processor.enhance(html)

        assertContains(result, """"word":"dos"""")
        assertContains(result, """"phonetic":"do"""")
        assertContains(result, """"word":"kubernetes"""")
        assertContains(result, """"phonetic":"koobernetayz"""")
    }

    @Test
    fun `pronunciation block without lang attribute produces language-agnostic hints`() {
        val html = """<!DOCTYPE html>
<html>
<head><title>Test</title></head>
<body>
<div class="listingblock hyperframes-pronunciation">
<div class="content">
<pre>dos: do</pre>
</div>
</div>
</body>
</html>"""

        val result = processor.enhance(html)

        assertContains(result, """"word":"dos"""")
        assertFalse(result.contains(""""language":"""), "no language field when block has no lang attribute")
    }

    @Test
    fun `html without pronunciation block does not contain the JSON island`() {
        val html = """<!DOCTYPE html>
<html>
<head><title>Test</title></head>
<body>
<div class="paragraph"><p>Hello</p></div>
</body>
</html>"""

        val result = processor.enhance(html)

        assertFalse(result.contains("id=\"hf-pronunciation\""), "no JSON island without pronunciation block")
    }

    @Test
    fun `pronunciation block with a malformed line is skipped without crashing`() {
        val html = """<!DOCTYPE html>
<html>
<head><title>Test</title></head>
<body>
<div class="listingblock hyperframes-pronunciation">
<div class="content">
<pre>dos: do
this line has no colon
kubernetes: koobernetayz</pre>
</div>
</div>
</body>
</html>"""

        val result = processor.enhance(html)

        assertContains(result, """"word":"dos"""")
        assertContains(result, """"word":"kubernetes"""")
        assertFalse(result.contains(""""word":"this line has no colon""""))
    }

    @Test
    fun `pronunciation block with empty content produces no JSON island`() {
        val html = """<!DOCTYPE html>
<html>
<head><title>Test</title></head>
<body>
<div class="listingblock hyperframes-pronunciation">
<div class="content">
<pre></pre>
</div>
</div>
</body>
</html>"""

        val result = processor.enhance(html)

        assertFalse(
            result.contains("id=\"hf-pronunciation\""),
            "an empty pronunciation block collects no hints — no JSON island should be injected"
        )
    }

    @Test
    fun `pronunciation block preserves the rest of the HTML body`() {
        val html = """<!DOCTYPE html>
<html>
<head><title>Test</title></head>
<body>
<div class="listingblock hyperframes-pronunciation">
<div class="content">
<pre>dos: do</pre>
</div>
</div>
<div class="paragraph"><p>Le conteneur isole l'application.</p></div>
</body>
</html>"""

        val result = processor.enhance(html)

        assertContains(result, """<p>Le conteneur isole l'application.</p>""")
        assertContains(result, """"word":"dos"""")
    }
}