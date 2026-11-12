package hyperframes

import video.kernel.music.MusicLibrary
import video.kernel.music.MusicLicence
import video.kernel.music.MusicTrack
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * HF-MUSIC-LIBRARY US-3 — the processor consumes the sound blocks and emits the
 * document-ordered `<audio>` layer at the scene offsets.
 *
 * Pure processor, no I/O: the audio copy (sha256 skip) belongs to
 * `resolveHyperframesBgm` (US-4).
 */
class HyperframesHtmlProcessorSoundTest {
    private val library =
        MusicLibrary(
            tracks =
                listOf(
                    MusicTrack(id = "calm-piano", title = "Calm piano", file = "calm/piano.mp3", licence = MusicLicence.OWN, mood = "calm"),
                    MusicTrack(id = "epic-drums", title = "Epic drums", file = "epic/drums.mp3", licence = MusicLicence.CC0, mood = "epic"),
                ),
        )

    private val processor = HyperframesHtmlProcessor(1920, 1080, 30, "sound-demo").withMusicLibrary(library)

    private fun document(sections: String): String =
        """
        <!DOCTYPE html>
        <html>
        <body class="article">
        <div id="content">
        $sections
        </div>
        </body>
        </html>
        """.trimIndent()

    private fun musicBlock(listing: String): String =
        """<div class="listingblock hyperframes-music"><div class="content"><pre>${listing.trimIndent()}</pre></div></div>"""

    private fun openMusicBlock(lines: String): String =
        """<div class="openblock hyperframes-music"><div class="content"><div class="paragraph"><p>${lines.trimIndent()}</p></div></div></div>"""

    private fun sfxBlock(listing: String): String =
        """<div class="listingblock hyperframes-sfx"><div class="content"><pre>${listing.trimIndent()}</pre></div></div>"""

    private fun section(
        id: String,
        duration: Int,
        extra: String = "",
    ): String = """<div class="sect1" data-duration="$duration"><h2 id="$id">$id</h2><p>body</p>$extra</div>"""

    private fun audioCount(html: String): Int = Regex("""<audio class="hyperframes-(?:music|sfx)"""").findAll(html).count()

    @Test
    fun `a music block is consumed and emitted at its scene start`() {
        val result =
            processor.enhance(
                document(
                    section("one", 4) + section("two", 6, musicBlock("mood: calm\nvolume: 0.3")),
                ),
            )

        assertFalse(result.contains("mood: calm"), "the DSL block must never render as content")
        assertContains(result, """id="hf-music-0"""")
        assertContains(result, """data-start="4"""")
        assertContains(result, """data-duration="6"""")
        assertContains(result, """src="assets/audio/calm-piano.mp3"""")
        assertContains(result, """data-volume="0.30"""")
    }

    @Test
    fun `an open block collapsed into one paragraph parses the same way`() {
        val result =
            processor.enhance(
                document(
                    section("one", 5, openMusicBlock("mood: epic volume: 0.2")),
                ),
            )

        assertContains(result, """src="assets/audio/epic-drums.mp3"""")
        assertEquals(1, audioCount(result))
    }

    @Test
    fun `a scene re-declaring the same music keeps one continuous cue`() {
        val result =
            processor.enhance(
                document(
                    section("one", 4, musicBlock("mood: calm")) +
                        section("two", 4, musicBlock("mood: calm")) +
                        section("three", 4),
                ),
            )

        assertEquals(1, audioCount(result))
        assertContains(result, """data-duration="12"""")
    }

    @Test
    fun `track none stops the running music at that scene`() {
        val result =
            processor.enhance(
                document(
                    section("one", 4, musicBlock("mood: calm")) +
                        section("two", 4, musicBlock("track: none")) +
                        section("three", 4),
                ),
            )

        assertEquals(1, audioCount(result))
        assertContains(result, """data-duration="4"""")
    }

    @Test
    fun `an explicit local file needs no library`() {
        val noLibrary = HyperframesHtmlProcessor(1920, 1080, 30, "sound-demo")
        val result =
            noLibrary.enhance(
                document(section("one", 5, musicBlock("file: assets/audio/mine.mp3"))),
            )

        assertContains(result, """src="assets/audio/mine.mp3"""")
    }

    @Test
    fun `an sfx is anchored to the scene start plus its offset`() {
        val result =
            processor.enhance(
                document(
                    section("one", 4) + section("two", 4, sfxBlock("file: assets/audio/impact.mp3\noffset: 1.5")),
                ),
            )

        assertContains(result, """class="hyperframes-sfx"""")
        assertContains(result, """data-start="5.50"""")
    }

    @Test
    fun `an unresolvable cue fails before the render with an actionable message`() {
        val noLibrary = HyperframesHtmlProcessor(1920, 1080, 30, "sound-demo")
        val doc = document(section("one", 5, musicBlock("mood: calm")))

        val failure = assertFailsWith<IllegalStateException> { noLibrary.enhance(doc) }

        assertContains(failure.message.orEmpty(), "unresolved sound cue")
        assertContains(failure.message.orEmpty(), "music.libraryFile")
    }

    @Test
    fun `a composition without sound blocks keeps the historical output`() {
        val plain = document(section("one", 4) + section("two", 4))
        val result = processor.enhance(plain)

        assertEquals(0, audioCount(result))
        assertFalse(result.contains("hyperframes-music\""))
        assertFalse(result.contains("data-fade-in"))
    }

    @Test
    fun `the sound declaration never renders as visible content`() {
        val result =
            processor.enhance(
                document(section("one", 5, musicBlock("mood: calm"))),
            )

        assertFalse(result.contains("mood: calm"))
    }
}
