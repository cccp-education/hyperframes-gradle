package hyperframes.sound

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * HF-MUSIC-LIBRARY US-3 — `<audio>` emission of the sound layer.
 */
class SoundHtmlRendererTest {
    private fun music(
        start: Double = 0.0,
        duration: Double = 5.0,
        declaration: MusicDeclaration = MusicDeclaration(mood = "calm"),
    ) = ResolvedMusic(SoundPlacement("s1", start, duration, declaration), "assets/audio/calm.mp3")

    @Test
    fun `a music cue becomes a direct stage child with its timeline attributes`() {
        val html = SoundHtmlRenderer.renderMusic(listOf(music(start = 5.0, duration = 10.5)))

        assertContains(html, """<audio id="hf-music-0" class="hyperframes-music"""")
        assertContains(html, """src="assets/audio/calm.mp3"""")
        assertContains(html, """data-start="5"""")
        assertContains(html, """data-duration="10.50"""")
        assertContains(html, """data-track-index="${SoundHtmlRenderer.MUSIC_TRACK_INDEX}"""")
        assertContains(html, """data-volume="0.25"""")
    }

    @Test
    fun `fades are emitted only when declared`() {
        val plain = SoundHtmlRenderer.renderMusic(listOf(music()))
        assertFalse(plain.contains("data-fade-in"))
        assertFalse(plain.contains("data-fade-out"))

        val faded =
            SoundHtmlRenderer.renderMusic(
                listOf(music(declaration = MusicDeclaration(mood = "calm", fadeInSeconds = 1.5, fadeOutSeconds = 2.0))),
            )
        assertContains(faded, """data-fade-in="1.50"""")
        assertContains(faded, """data-fade-out="2"""")
    }

    @Test
    fun `each cue gets its own track index`() {
        val html = SoundHtmlRenderer.renderMusic(listOf(music(), music(start = 5.0)))

        assertContains(html, """data-track-index="${SoundHtmlRenderer.MUSIC_TRACK_INDEX}"""")
        assertContains(html, """data-track-index="${SoundHtmlRenderer.MUSIC_TRACK_INDEX + 1}"""")
    }

    @Test
    fun `an sfx becomes a one-shot clip on its own track range`() {
        val html =
            SoundHtmlRenderer.renderSfx(
                listOf(ResolvedSfx(SfxPlacement("s1", 6.5, SfxDeclaration("impact", 1.5, 0.4)), "assets/audio/impact.mp3")),
            )

        assertContains(html, """class="hyperframes-sfx"""")
        assertContains(html, """data-start="6.50"""")
        assertContains(html, """data-track-index="${SoundHtmlRenderer.SFX_TRACK_INDEX}"""")
        assertContains(html, """data-volume="0.40"""")
        assertFalse(html.contains("data-duration"))
    }

    @Test
    fun `an empty layer renders nothing`() {
        assertEquals("", SoundHtmlRenderer.renderMusic(emptyList()))
        assertEquals("", SoundHtmlRenderer.renderSfx(emptyList()))
    }
}
