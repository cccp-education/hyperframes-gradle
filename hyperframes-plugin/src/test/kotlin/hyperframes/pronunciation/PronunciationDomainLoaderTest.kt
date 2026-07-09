package hyperframes.pronunciation

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * TDD unit tests for [PronunciationDomainLoader] — HF-7d.
 *
 * Baby-step (DDD red -> green): pre-built domain dictionaries covering
 * generic video-content vocabulary (French and English). Loaded lazily
 * from classpath resources. Author hints override domain hints on merge.
 *
 * Bounded context note: the plugin is a generic AsciiDoc-to-video
 * transformer (public OSS). Domain dictionaries cover video production
 * vocabulary, not any private business domain. Consumer boroughs inject
 * their own domain dictionary via configuration.
 */
class PronunciationDomainLoaderTest {

    @Test
    fun `loadDomain video-fr returns a non-empty dictionary`() {
        val domain = PronunciationDomainLoader.load("video-fr")

        assertTrue(domain.size() > 0, "video-fr domain should contain hints")
    }

    @Test
    fun `loadDomain video-en returns a non-empty dictionary`() {
        val domain = PronunciationDomainLoader.load("video-en")

        assertTrue(domain.size() > 0, "video-en domain should contain hints")
    }

    @Test
    fun `loadDomain video-fr contains a hint for a known video word`() {
        val domain = PronunciationDomainLoader.load("video-fr")

        assertNotNull(domain.find("narration"), "video-fr should contain the word 'narration'")
    }

    @Test
    fun `loadDomain video-en contains a hint for render`() {
        val domain = PronunciationDomainLoader.load("video-en")

        assertNotNull(domain.find("render"), "video-en should contain the word 'render'")
    }

    @Test
    fun `loadDomain throws on an unknown domain name`() {
        assertThrows<IllegalArgumentException> {
            PronunciationDomainLoader.load("nonexistent-domain")
        }
    }

    @Test
    fun `author dictionary merged with domain keeps author hint on conflict`() {
        val domain = PronunciationDomainLoader.load("video-fr")
        val author = PronunciationDictionary()
        author.add(PronunciationHint.of("narration", "na-ra-syon-explicit", language = "fr"))

        val merged = author.merge(domain)

        assertEquals("na-ra-syon-explicit", merged.find("narration", language = "fr")?.phonetic)
    }

    @Test
    fun `domain-only word is kept after merge with an author dictionary`() {
        val domain = PronunciationDomainLoader.load("video-en")
        val author = PronunciationDictionary()

        val merged = author.merge(domain)

        assertNotNull(merged.find("render"))
    }
}