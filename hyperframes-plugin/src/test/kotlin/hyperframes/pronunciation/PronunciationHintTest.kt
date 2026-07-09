package hyperframes.pronunciation

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TDD unit tests for [PronunciationHint] — HF-7a.
 *
 * Baby-step (DDD red -> green): a value object representing one entry of the
 * TTS pronunciation dictionary. It carries the word to correct, the phonetic
 * approximation the TTS should speak, and an optional language tag for
 * disambiguation (e.g. "dos" means "do" in French but "doss" in Spanish).
 *
 * The hint renders as a JSON object that the HyperFrames CLI consumes from the
 * `<script type="application/json" id="hf-pronunciation">` island injected by
 * [PronunciationDictionary] (HF-7b).
 */
class PronunciationHintTest {

    @Test
    fun `hint renders a valid JSON object with word and phonetic`() {
        val hint = PronunciationHint.of(word = "dos", phonetic = "do")

        assertEquals("""{"word":"dos","phonetic":"do"}""", hint.render())
    }

    @Test
    fun `hint with language adds the language field`() {
        val hint = PronunciationHint.of(word = "dos", phonetic = "do", language = "fr")

        assertEquals("""{"word":"dos","phonetic":"do","language":"fr"}""", hint.render())
    }

    @Test
    fun `hint rejects an empty word`() {
        assertThrows<IllegalArgumentException> {
            PronunciationHint.of(word = "", phonetic = "do")
        }
    }

    @Test
    fun `hint rejects a blank word`() {
        assertThrows<IllegalArgumentException> {
            PronunciationHint.of(word = "   ", phonetic = "do")
        }
    }

    @Test
    fun `hint rejects an empty phonetic`() {
        assertThrows<IllegalArgumentException> {
            PronunciationHint.of(word = "dos", phonetic = "")
        }
    }

    @Test
    fun `hint rejects a blank phonetic`() {
        assertThrows<IllegalArgumentException> {
            PronunciationHint.of(word = "dos", phonetic = "   ")
        }
    }

    @Test
    fun `word is normalized to lowercase so lookups are case-insensitive`() {
        val hint = PronunciationHint.of(word = "DOS", phonetic = "do")

        assertEquals("dos", hint.word)
    }

    @Test
    fun `phonetic is trimmed of surrounding whitespace`() {
        val hint = PronunciationHint.of(word = "dos", phonetic = "  do  ")

        assertEquals("do", hint.phonetic)
    }

    @Test
    fun `word is trimmed of surrounding whitespace before normalization`() {
        val hint = PronunciationHint.of(word = "  Kubernetes  ", phonetic = "koobernetayz")

        assertEquals("kubernetes", hint.word)
    }

    @Test
    fun `two hints with the same word and language are equal`() {
        val a = PronunciationHint.of(word = "dos", phonetic = "do", language = "fr")
        val b = PronunciationHint.of(word = "DOS", phonetic = "do", language = "fr")

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `two hints with the same word but different language are not equal`() {
        val fr = PronunciationHint.of(word = "dos", phonetic = "do", language = "fr")
        val es = PronunciationHint.of(word = "dos", phonetic = "doss", language = "es")

        assertTrue(fr != es, "hints differing only by language should not be equal")
    }
}