package hyperframes

import hyperframes.pronunciation.PronunciationDictionary
import hyperframes.pronunciation.PronunciationDomainLoader
import hyperframes.pronunciation.PronunciationHint
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TDD unit tests for HF-7 evolution — domain dictionary wiring.
 *
 * Verifies that [HyperframesHtmlProcessor] accepts an optional domain
 * dictionary and merges author hints (from pronunciation blocks and inline
 * hints) with the domain so that:
 *  - author hints override domain hints on conflict (word + language)
 *  - domain-only words are kept in the rendered JSON island
 *  - when no author block/inline is present, the domain is injected as the
 *    baseline dictionary (the whole point of the configuration)
 *
 * Baby-step DDD: the domain is a [PronunciationDictionary] aggregate passed
 * to the processor — the processor stays a pure function of (html, domain).
 */
class HyperframesDomainWiringTest {

    private val videoFr = PronunciationDomainLoader.load("video-fr")
    private val videoEn = PronunciationDomainLoader.load("video-en")

    private fun processor(domain: PronunciationDictionary? = null) =
        HyperframesHtmlProcessor(width = 1920, height = 1080, fps = 30, outputName = "test-video")
            .withDomainDictionary(domain)

    // ──────────────────────────────────────────────
    // 1. Author block + domain merge
    // ──────────────────────────────────────────────

    @Test
    fun `author block merged with domain keeps author hint on conflict`() {
        val html = """<!DOCTYPE html>
<html><head><title>Test</title></head><body>
<div class="listingblock hyperframes-pronunciation"><div class="content"><pre>narration: my-override</pre></div></div>
</body></html>"""

        val result = processor(videoFr).enhance(html)

        assertContains(result, """"word":"narration"""")
        assertContains(result, """"phonetic":"my-override"""")
        assertFalse(
            result.contains(""""phonetic":"na-ra-syon""""),
            "author hint should override the domain phonetic for 'narration'"
        )
    }

    @Test
    fun `author block merged with domain keeps domain-only words`() {
        val html = """<!DOCTYPE html>
<html><head><title>Test</title></head><body>
<div class="listingblock hyperframes-pronunciation"><div class="content"><pre>dos: do</pre></div></div>
</body></html>"""

        val result = processor(videoFr).enhance(html)

        assertContains(result, """"word":"dos"""")
        assertContains(result, """"word":"transition"""")
        assertContains(result, """"phonetic":"tran-zi-syon"""")
    }

    // ──────────────────────────────────────────────
    // 2. Baseline injection — no author block, domain only
    // ──────────────────────────────────────────────

    @Test
    fun `domain is injected as baseline when no author block and no inline hints`() {
        val html = """<!DOCTYPE html>
<html><head><title>Test</title></head><body>
<div class="paragraph"><p>Hello world</p></div>
</body></html>"""

        val result = processor(videoEn).enhance(html)

        assertContains(result, """<script type="application/json" id="hf-pronunciation">""")
        assertContains(result, """"word":"render"""")
        assertContains(result, """"phonetic":"ren-der"""")
    }

    @Test
    fun `no domain and no author block produces no JSON island`() {
        val html = """<!DOCTYPE html>
<html><head><title>Test</title></head><body>
<div class="paragraph"><p>Hello world</p></div>
</body></html>"""

        val result = processor(null).enhance(html)

        assertFalse(result.contains("id=\"hf-pronunciation\""), "no island when no domain and no author block")
    }

    @Test
    fun `no domain provided keeps backward-compatible behavior for author block`() {
        val html = """<!DOCTYPE html>
<html><head><title>Test</title></head><body>
<div class="listingblock hyperframes-pronunciation"><div class="content"><pre>dos: do</pre></div></div>
</body></html>"""

        val result = processor(null).enhance(html)

        assertContains(result, """"word":"dos"""")
        assertContains(result, """"phonetic":"do"""")
    }

    // ──────────────────────────────────────────────
    // 3. Inline hints + domain merge
    // ──────────────────────────────────────────────

    @Test
    fun `inline hints merged with domain keeps domain-only words`() {
        val html = """<!DOCTYPE html>
<html><head><title>Test</title></head><body>
<div class="paragraph hyperframes-track"><p>Le conteneur hf:pron[dos, do] isole l'application.</p></div>
</body></html>"""

        val result = processor(videoFr).enhance(html)

        assertContains(result, """"word":"dos"""")
        assertContains(result, """"phonetic":"do"""")
        assertContains(result, """"word":"narration"""")
        assertContains(result, """"phonetic":"na-ra-syon"""")
    }

    @Test
    fun `inline hint overrides domain hint for the same word`() {
        val html = """<!DOCTYPE html>
<html><head><title>Test</title></head><body>
<div class="paragraph hyperframes-track"><p>La hf:pron[narration, over-ride] est claire.</p></div>
</body></html>"""

        val result = processor(videoFr).enhance(html)

        assertContains(result, """"word":"narration"""")
        assertContains(result, """"phonetic":"over-ride"""")
        assertFalse(
            result.contains(""""phonetic":"na-ra-syon""""),
            "inline hint should override the domain phonetic for 'narration'"
        )
    }

    // ──────────────────────────────────────────────
    // 4. Domain + author block + inline all merge
    // ──────────────────────────────────────────────

    @Test
    fun `domain plus author block plus inline hints all merge with author precedence`() {
        val html = """<!DOCTYPE html>
<html><head><title>Test</title></head><body>
<div class="listingblock hyperframes-pronunciation"><div class="content"><pre>narration: block-override</pre></div></div>
<div class="paragraph hyperframes-track"><p>La hf:pron[rendu, inline-override] est net.</p></div>
</body></html>"""

        val result = processor(videoFr).enhance(html)

        // Author block overrides domain for 'narration'
        assertContains(result, """"phonetic":"block-override"""")
        // Inline overrides domain for 'rendu'
        assertContains(result, """"phonetic":"inline-override"""")
        // Domain-only word kept
        assertContains(result, """"word":"transition"""")
        assertContains(result, """"phonetic":"tran-zi-syon"""")
    }

    @Test
    fun `domain dictionary is not mutated by the processor`() {
        val sizeBefore = videoFr.size()
        val html = """<!DOCTYPE html>
<html><head><title>Test</title></head><body>
<div class="listingblock hyperframes-pronunciation"><div class="content"><pre>dos: do</pre></div></div>
</body></html>"""

        processor(videoFr).enhance(html)

        assertTrue(videoFr.size() == sizeBefore, "domain dictionary must not be mutated by the processor")
        // Author word 'dos' should not leak into the domain
        assertTrue(videoFr.find("dos") == null, "domain should not contain the author word 'dos' after processing")
    }
}