package hyperframes.sound

import video.kernel.music.MusicLibrary
import video.kernel.music.MusicLicence
import video.kernel.music.MusicTrack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * HF-MUSIC-LIBRARY US-4 — the pure copy plan of the resolved library tracks.
 */
class MusicAssetPlannerTest {
    private val library =
        MusicLibrary(
            tracks =
                listOf(
                    MusicTrack(
                        id = "calm-piano",
                        title = "Calm piano",
                        file = "calm/piano.mp3",
                        licence = MusicLicence.OWN,
                        sha256 = "abc123",
                        mood = "calm",
                    ),
                    MusicTrack(
                        id = "no-file",
                        title = "No audio",
                        file = "",
                        licence = MusicLicence.OWN,
                    ),
                ),
        )

    private fun html(vararg tags: String): String =
        """
        <div id="stage">
        ${tags.joinToString("\n")}
        </div>
        """.trimIndent()

    private fun musicTag(
        trackId: String,
        src: String,
    ): String = """<audio id="hf-music-0" class="hyperframes-music" data-track-id="$trackId" src="$src" data-start="0"></audio>"""

    @Test
    fun `only the music clips carrying a library track id are requested`() {
        val requests =
            MusicAssetPlanner.requests(
                html(
                    musicTag("calm-piano", "assets/audio/calm-piano.mp3"),
                    """<audio id="hf-music-1" class="hyperframes-music" src="audio/mine.mp3" data-start="0"></audio>""",
                    """<audio id="hf-sfx-0" class="hyperframes-sfx" src="assets/audio/impact.mp3" data-start="4"></audio>""",
                    """<audio id="hf-audio-0" src="assets/audio/bgm.mp3" data-start="0"></audio>""",
                ),
            )

        assertEquals(1, requests.size)
        assertEquals("calm-piano", requests[0].trackId)
        assertEquals("assets/audio/calm-piano.mp3", requests[0].src)
    }

    @Test
    fun `requests are deduplicated and keep the document order`() {
        val requests =
            MusicAssetPlanner.requests(
                html(
                    musicTag("calm-piano", "assets/audio/calm-piano.mp3"),
                    musicTag("calm-piano", "assets/audio/calm-piano.mp3"),
                ),
            )

        assertEquals(1, requests.size)
    }

    @Test
    fun `an absent target is planned for copy`() {
        val plan =
            MusicAssetPlanner.plan(
                requests = MusicAssetPlanner.requests(html(musicTag("calm-piano", "assets/audio/calm-piano.mp3"))),
                library = library,
                audioRoot = "/library/audio",
                existsSha256 = { null },
            )

        assertEquals(1, plan.copies.size)
        assertEquals("/library/audio/calm/piano.mp3", plan.copies[0].source)
        assertTrue(plan.upToDate.isEmpty())
        assertTrue(plan.missing.isEmpty())
    }

    @Test
    fun `an identical sha256 makes the copy unnecessary`() {
        val plan =
            MusicAssetPlanner.plan(
                requests = MusicAssetPlanner.requests(html(musicTag("calm-piano", "assets/audio/calm-piano.mp3"))),
                library = library,
                audioRoot = "/library/audio",
                existsSha256 = { "ABC123" },
            )

        assertTrue(plan.copies.isEmpty())
        assertEquals(1, plan.upToDate.size)
    }

    @Test
    fun `a different sha256 plans a fresh copy`() {
        val plan =
            MusicAssetPlanner.plan(
                requests = MusicAssetPlanner.requests(html(musicTag("calm-piano", "assets/audio/calm-piano.mp3"))),
                library = library,
                audioRoot = "/library/audio",
                existsSha256 = { "deadbeef" },
            )

        assertEquals(1, plan.copies.size)
        assertTrue(plan.upToDate.isEmpty())
    }

    @Test
    fun `an unknown track is reported, never invented`() {
        val plan =
            MusicAssetPlanner.plan(
                requests = MusicAssetPlanner.requests(html(musicTag("ghost", "assets/audio/ghost.mp3"))),
                library = library,
                audioRoot = "/library/audio",
                existsSha256 = { null },
            )

        assertTrue(plan.copies.isEmpty())
        assertEquals(1, plan.missing.size)
        assertTrue(plan.missing[0].contains("ghost"))
    }

    @Test
    fun `a track without an audio file is reported`() {
        val plan =
            MusicAssetPlanner.plan(
                requests = MusicAssetPlanner.requests(html(musicTag("no-file", "assets/audio/no-file.mp3"))),
                library = library,
                audioRoot = "/library/audio",
                existsSha256 = { null },
            )

        assertEquals(1, plan.missing.size)
        assertTrue(plan.missing[0].contains("no-file"))
    }

    @Test
    fun `an empty audio root keeps the manifest path relative`() {
        val plan =
            MusicAssetPlanner.plan(
                requests = MusicAssetPlanner.requests(html(musicTag("calm-piano", "assets/audio/calm-piano.mp3"))),
                library = library,
                audioRoot = "",
                existsSha256 = { null },
            )

        assertEquals("calm/piano.mp3", plan.copies[0].source)
    }
}
