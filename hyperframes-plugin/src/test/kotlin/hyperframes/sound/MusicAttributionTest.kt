package hyperframes.sound

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * HF-MUSIC-LIBRARY US-7 — music credits of the publication description.
 */
class MusicAttributionTest {
    private val library =
        MusicLibrary(
            tracks =
                listOf(
                    MusicTrack(
                        id = "cc-by-piano",
                        title = "Calm piano",
                        artist = "cheroliv",
                        file = "calm.mp3",
                        licence = MusicLicence.CC_BY,
                        attribution = "Music: Calm piano — cheroliv (CC BY 4.0)",
                    ),
                    MusicTrack(
                        id = "cc-by-drums",
                        title = "Epic drums",
                        artist = "cheroliv",
                        file = "drums.mp3",
                        licence = MusicLicence.CC_BY,
                    ),
                    MusicTrack(
                        id = "own-bed",
                        title = "Own bed",
                        artist = "cheroliv",
                        file = "own.mp3",
                        licence = MusicLicence.OWN,
                    ),
                    MusicTrack(
                        id = "unused-cc-by",
                        title = "Unused",
                        artist = "someone",
                        file = "unused.mp3",
                        licence = MusicLicence.CC_BY,
                    ),
                ),
        )

    private fun html(vararg trackIds: String): String =
        trackIds.joinToString("\n") { id ->
            """<audio class="hyperframes-music" data-track-id="$id" src="assets/audio/$id.mp3"></audio>"""
        }

    @Test
    fun `a declared attribution is used verbatim`() {
        val lines = MusicAttribution.lines(html("cc-by-piano"), library)

        assertEquals(listOf("Music: Calm piano — cheroliv (CC BY 4.0)"), lines)
    }

    @Test
    fun `a cc-by track without a declared line falls back to title and artist`() {
        val lines = MusicAttribution.lines(html("cc-by-drums"), library)

        assertEquals(listOf("Music: Epic drums — cheroliv (CC BY)"), lines)
    }

    @Test
    fun `a track under a licence without attribution obligation is not credited`() {
        assertTrue(MusicAttribution.lines(html("own-bed"), library).isEmpty())
    }

    @Test
    fun `unused library tracks are never credited`() {
        assertTrue(MusicAttribution.lines(html("own-bed"), library).none { it.contains("Unused") })
    }

    @Test
    fun `a track used twice is credited once, in document order`() {
        val lines = MusicAttribution.lines(html("cc-by-piano", "own-bed", "cc-by-piano", "cc-by-drums"), library)

        assertEquals(
            listOf(
                "Music: Calm piano — cheroliv (CC BY 4.0)",
                "Music: Epic drums — cheroliv (CC BY)",
            ),
            lines,
        )
    }

    @Test
    fun `appending no credit leaves the description byte-identical`() {
        assertEquals("Hello", MusicAttribution.append("Hello", emptyList()))
        assertEquals("Hello \n", MusicAttribution.append("Hello \n", emptyList()))
    }

    @Test
    fun `credits are appended as a final block`() {
        val description = MusicAttribution.append("Chapter list\n", listOf("Music: A — B (CC BY)"))

        assertEquals("Chapter list\n\nMusic: A — B (CC BY)", description)
    }

    @Test
    fun `an empty description still receives the credits`() {
        assertEquals("Music: A — B (CC BY)", MusicAttribution.append("", listOf("Music: A — B (CC BY)")))
    }
}
