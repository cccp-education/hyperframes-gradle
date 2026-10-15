package hyperframes.post

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Pure narration extraction from the enriched HyperFrames HTML (VK-4 US-4d).
 */
class NarrationTrackExtractorTest {

    @Test
    fun `extracts the paragraph of each narration track in document order`() {
        val html = """
            <div id="stage">
              <div class="paragraph hyperframes-track" data-track-index="0" data-start="0" data-duration="4">
                <p>Première narration.</p>
              </div>
              <div class="paragraph hyperframes-track" data-track-index="1" data-start="4" data-duration="4">
                <p>Seconde narration.</p>
              </div>
            </div>
        """.trimIndent()

        assertThat(NarrationTrackExtractor.extract(html))
            .containsExactly("Première narration.", "Seconde narration.")
    }

    @Test
    fun `ignores media-only tracks without a paragraph`() {
        val html = """
            <div class="paragraph hyperframes-track" data-track-index="0">
              <audio src="music.mp3"></audio>
            </div>
        """.trimIndent()

        assertThat(NarrationTrackExtractor.extract(html)).isEmpty()
    }

    @Test
    fun `strips inline tags and skips blank narrations`() {
        val html = """
            <div class="paragraph hyperframes-track">
              <p>Hello <strong>world</strong></p>
            </div>
            <div class="paragraph hyperframes-track">
              <p>   </p>
            </div>
        """.trimIndent()

        assertThat(NarrationTrackExtractor.extract(html)).containsExactly("Hello world")
    }

    @Test
    fun `returns empty when there is no track`() {
        assertThat(NarrationTrackExtractor.extract("<div id=\"stage\"></div>")).isEmpty()
    }
}
