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
 * FPA training vocabulary (AFNOR, REAC) and technical English (Docker,
 * Kubernetes, CI/CD). Loaded lazily from classpath resources. Author
 * hints override domain hints on merge.
 */
class PronunciationDomainLoaderTest {

    @Test
    fun `loadDomain fpa-fr returns a non-empty dictionary`() {
        val domain = PronunciationDomainLoader.load("fpa-fr")

        assertTrue(domain.size() > 0, "fpa-fr domain should contain hints")
    }

    @Test
    fun `loadDomain tech-en returns a non-empty dictionary`() {
        val domain = PronunciationDomainLoader.load("tech-en")

        assertTrue(domain.size() > 0, "tech-en domain should contain hints")
    }

    @Test
    fun `loadDomain fpa-fr contains a hint for a known FPA word`() {
        val domain = PronunciationDomainLoader.load("fpa-fr")

        assertNotNull(domain.find("afnor"), "fpa-fr should contain the word 'afnor'")
    }

    @Test
    fun `loadDomain tech-en contains a hint for docker`() {
        val domain = PronunciationDomainLoader.load("tech-en")

        assertNotNull(domain.find("docker"), "tech-en should contain the word 'docker'")
    }

    @Test
    fun `loadDomain throws on an unknown domain name`() {
        assertThrows<IllegalArgumentException> {
            PronunciationDomainLoader.load("nonexistent-domain")
        }
    }

    @Test
    fun `author dictionary merged with domain keeps author hint on conflict`() {
        val domain = PronunciationDomainLoader.load("fpa-fr")
        val author = PronunciationDictionary()
        author.add(PronunciationHint.of("afnor", "ah-eff-en-oh-er", language = "fr"))

        val merged = author.merge(domain)

        assertEquals("ah-eff-en-oh-er", merged.find("afnor", language = "fr")?.phonetic)
    }

    @Test
    fun `domain-only word is kept after merge with an author dictionary`() {
        val domain = PronunciationDomainLoader.load("tech-en")
        val author = PronunciationDictionary()

        val merged = author.merge(domain)

        assertNotNull(merged.find("docker"))
    }
}