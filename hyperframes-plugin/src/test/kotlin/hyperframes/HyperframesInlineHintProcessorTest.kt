package hyperframes

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TDD functional tests for HF-7e — inline pronunciation hints in narration.
 *
 * Verifies that [HyperframesHtmlProcessor] extracts inline `hf:pron[word, phonetic]`
 * hints from track text, injects them into the pronunciation JSON island, and
 * keeps the word visible in the rendered text (the phonetic is consumed by the
 * TTS, not displayed on screen).
 */
class HyperframesInlineHintProcessorTest {

    private val processor = HyperframesHtmlProcessor(
        width = 1920,
        height = 1080,
        fps = 30,
        outputName = "test-video"
    )

    @Test
    fun `inline hint in a track paragraph is extracted into the pronunciation island`() {
        val html = """<!DOCTYPE html>
<html>
<head><title>Test</title></head>
<body>
<div class="paragraph hyperframes-track">
<p>Le conteneur hf:pron[dos, do] isole l'application.</p>
</div>
</body>
</html>"""

        val result = processor.enhance(html)

        assertContains(result, """<script type="application/json" id="hf-pronunciation">""")
        assertContains(result, """"word":"dos"""")
        assertContains(result, """"phonetic":"do"""")
    }

    @Test
    fun `inline hint keeps the word visible in the rendered text`() {
        val html = """<!DOCTYPE html>
<html>
<head><title>Test</title></head>
<body>
<div class="paragraph hyperframes-track">
<p>Le conteneur hf:pron[dos, do] isole l'application.</p>
</div>
</body>
</html>"""

        val result = processor.enhance(html)

        assertContains(result, "dos")
        assertFalse(result.contains("hf:pron[dos, do]"), "inline hint syntax should be stripped from rendered text")
    }

    @Test
    fun `multiple inline hints in the same track are all extracted`() {
        val html = """<!DOCTYPE html>
<html>
<head><title>Test</title></head>
<body>
<div class="paragraph hyperframes-track">
<p>hf:pron[dos, do] and hf:pron[kubernetes, koobernetayz] are tricky.</p>
</div>
</body>
</html>"""

        val result = processor.enhance(html)

        assertContains(result, """"word":"dos"""")
        assertContains(result, """"word":"kubernetes"""")
    }

    @Test
    fun `track without inline hints does not produce a pronunciation island`() {
        val html = """<!DOCTYPE html>
<html>
<head><title>Test</title></head>
<body>
<div class="paragraph hyperframes-track">
<p>No hints here.</p>
</div>
</body>
</html>"""

        val result = processor.enhance(html)

        assertFalse(result.contains("id=\"hf-pronunciation\""), "no island when track has no inline hints")
    }

    @Test
    fun `inline hint outside a track is not extracted`() {
        val html = """<!DOCTYPE html>
<html>
<head><title>Test</title></head>
<body>
<div class="paragraph">
<p>hf:pron[dos, do] outside a track.</p>
</div>
</body>
</html>"""

        val result = processor.enhance(html)

        assertFalse(result.contains("id=\"hf-pronunciation\""), "inline hint outside a track should be ignored")
    }
}