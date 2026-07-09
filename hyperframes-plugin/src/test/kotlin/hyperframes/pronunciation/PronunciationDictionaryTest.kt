package hyperframes.pronunciation

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TDD unit tests for [PronunciationDictionary] — HF-7b.
 *
 * Baby-step (DDD red -> green): the aggregate root collects pronunciation
 * hints, deduplicates them on (word, language), and renders a JSON island
 * that the HyperFrames CLI consumes to correct TTS phonetics.
 */
class PronunciationDictionaryTest {

    @Test
    fun `empty dictionary renders an empty JSON array island`() {
        val dict = PronunciationDictionary()

        val html = dict.render()

        assertEquals(
            """<script type="application/json" id="hf-pronunciation">[]</script>""",
            html
        )
    }

    @Test
    fun `dictionary with a single hint renders a one-element JSON array`() {
        val dict = PronunciationDictionary()
        dict.add(PronunciationHint.of("dos", "do"))

        val html = dict.render()

        assertTrue(html.startsWith("""<script type="application/json" id="hf-pronunciation">["""), "island should open with array")
        assertTrue(html.contains(""""word":"dos""""), "island should contain the hint")
        assertTrue(html.endsWith("]</script>"), "island should close array and script")
    }

    @Test
    fun `dictionary with two hints renders a two-element JSON array`() {
        val dict = PronunciationDictionary()
        dict.add(PronunciationHint.of("dos", "do"))
        dict.add(PronunciationHint.of("kubernetes", "koobernetayz"))

        val html = dict.render()

        val arrayContent = html.substringAfter("[").substringBefore("]</script>")
        val hintCount = arrayContent.split("}").size - 1
        assertEquals(2, hintCount)
    }

    @Test
    fun `add deduplicates hints on word plus language`() {
        val dict = PronunciationDictionary()
        dict.add(PronunciationHint.of("dos", "do", language = "fr"))
        dict.add(PronunciationHint.of("DOS", "doh", language = "fr"))

        assertEquals(1, dict.size())
    }

    @Test
    fun `same word with different languages are kept as separate hints`() {
        val dict = PronunciationDictionary()
        dict.add(PronunciationHint.of("dos", "do", language = "fr"))
        dict.add(PronunciationHint.of("dos", "doss", language = "es"))

        assertEquals(2, dict.size())
    }

    @Test
    fun `same word with no language and with language are kept separate`() {
        val dict = PronunciationDictionary()
        dict.add(PronunciationHint.of("dos", "do"))
        dict.add(PronunciationHint.of("dos", "doh", language = "fr"))

        assertEquals(2, dict.size())
    }

    @Test
    fun `find returns the hint for a word case-insensitively when no language is set`() {
        val dict = PronunciationDictionary()
        dict.add(PronunciationHint.of("dos", "do"))

        val found = dict.find("DOS")
        assertEquals("do", found?.phonetic)
    }

    @Test
    fun `find returns null when the word is not in the dictionary`() {
        val dict = PronunciationDictionary()

        assertNull(dict.find("missing"))
    }

    @Test
    fun `find prefers the hint matching the requested language`() {
        val dict = PronunciationDictionary()
        dict.add(PronunciationHint.of("dos", "do", language = "fr"))
        dict.add(PronunciationHint.of("dos", "doss", language = "es"))

        assertEquals("do", dict.find("dos", language = "fr")?.phonetic)
        assertEquals("doss", dict.find("dos", language = "es")?.phonetic)
    }

    @Test
    fun `find falls back to the language-agnostic hint when no language match exists`() {
        val dict = PronunciationDictionary()
        dict.add(PronunciationHint.of("dos", "do"))

        assertEquals("do", dict.find("dos", language = "en")?.phonetic)
    }

    @Test
    fun `merge combines two dictionaries keeping the argument hints on conflict`() {
        val domain = PronunciationDictionary()
        domain.add(PronunciationHint.of("dos", "doss"))
        domain.add(PronunciationHint.of("kubernetes", "koobernetayz"))

        val author = PronunciationDictionary()
        author.add(PronunciationHint.of("dos", "do"))

        val merged = author.merge(domain)

        assertEquals("do", merged.find("dos")?.phonetic, "author hint should override domain")
        assertEquals("koobernetayz", merged.find("kubernetes")?.phonetic, "domain-only hint should be kept")
    }

    @Test
    fun `merge does not mutate the receiver or the argument`() {
        val a = PronunciationDictionary()
        a.add(PronunciationHint.of("dos", "do"))

        val b = PronunciationDictionary()
        b.add(PronunciationHint.of("kubernetes", "koobernetayz"))

        val merged = a.merge(b)

        assertEquals(1, a.size())
        assertEquals(1, b.size())
        assertEquals(2, merged.size())
    }
}