package hyperframes.sound

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * HF-MUSIC-LIBRARY US-3 — parsing of the pilot-document sound blocks.
 */
class SoundBlockParserTest {
    private fun music(text: String): MusicDeclaration =
        when (val result = SoundBlockParser.parseMusic(text)) {
            is SoundBlockParseResult.Parsed -> result.declaration
            is SoundBlockParseResult.Invalid -> error("expected a parsed music block, got $result")
        }

    private fun sfx(text: String): SfxDeclaration =
        when (val result = SoundBlockParser.parseSfx(text)) {
            is SoundBlockParseResult.Parsed -> result.declaration
            is SoundBlockParseResult.Invalid -> error("expected a parsed sfx block, got $result")
        }

    @Test
    fun `parses a full music listing`() {
        val declaration =
            music(
                """
                mood: documentary
                track: calm-piano-01
                volume: 0.3
                fade-in: 1.5
                fade-out: 2
                loop: false
                """.trimIndent(),
            )

        assertEquals("calm-piano-01", declaration.trackId)
        assertEquals("documentary", declaration.mood)
        assertEquals(0.3, declaration.volume)
        assertEquals(1.5, declaration.fadeInSeconds)
        assertEquals(2.0, declaration.fadeOutSeconds)
        assertFalse(declaration.loop)
        assertFalse(declaration.stop)
    }

    @Test
    fun `mood alone is a valid declaration with the documented defaults`() {
        val declaration = music("mood: calm")

        assertEquals("", declaration.trackId)
        assertEquals("calm", declaration.mood)
        assertEquals(MusicDeclaration.DEFAULT_VOLUME, declaration.volume)
        assertTrue(declaration.loop)
    }

    @Test
    fun `an open block collapsed into one line still parses every key`() {
        // Asciidoctor may join the lines of a `--` open block into one paragraph.
        val declaration = music("mood: calm volume: 0.4 fade-in: 1 track: intro-bed")

        assertEquals("calm", declaration.mood)
        assertEquals("intro-bed", declaration.trackId)
        assertEquals(0.4, declaration.volume)
        assertEquals(1.0, declaration.fadeInSeconds)
    }

    @Test
    fun `track none and music off both stop the running music`() {
        assertTrue(music("track: none").stop)
        assertTrue(music("music: off").stop)
        assertTrue(music("stop: true").stop)
    }

    @Test
    fun `comments and blank lines are ignored`() {
        val declaration =
            music(
                """
                # the bed of the intro

                mood: tech
                """.trimIndent(),
            )

        assertEquals("tech", declaration.mood)
    }

    @Test
    fun `an unsupported key is rejected with an actionable reason`() {
        val result = SoundBlockParser.parseMusic("mood: calm\nbpm: 120")

        assertIs<SoundBlockParseResult.Invalid>(result)
        assertTrue(result.reasons.any { it.contains("bpm") }, "expected a reason naming the key, got ${result.reasons}")
    }

    @Test
    fun `a non numeric volume is rejected`() {
        val result = SoundBlockParser.parseMusic("mood: calm\nvolume: loud")

        assertIs<SoundBlockParseResult.Invalid>(result)
        assertTrue(result.reasons.any { it.contains("volume") })
    }

    @Test
    fun `a volume above one is rejected by the contract`() {
        val result = SoundBlockParser.parseMusic("mood: calm\nvolume: 1.4")

        assertIs<SoundBlockParseResult.Invalid>(result)
    }

    @Test
    fun `an empty block declares nothing`() {
        assertIs<SoundBlockParseResult.Invalid>(SoundBlockParser.parseMusic(""))
        assertIs<SoundBlockParseResult.Invalid>(SoundBlockParser.parseSfx(""))
    }

    @Test
    fun `parses an sfx block with offset alias and volume`() {
        val declaration = sfx("file: whoosh-impact\nat: 1.25\nvolume: 0.4")

        assertEquals("whoosh-impact", declaration.file)
        assertEquals(1.25, declaration.offsetSeconds)
        assertEquals(0.4, declaration.volume)
    }

    @Test
    fun `an sfx block without file is rejected`() {
        val result = SoundBlockParser.parseSfx("offset: 1")

        assertIs<SoundBlockParseResult.Invalid>(result)
        assertTrue(result.reasons.any { it.contains("file") })
    }
}
