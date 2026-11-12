package hyperframes.sound

import video.kernel.music.MusicLicence
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * HF-MUSIC-LIBRARY US-3 — reading the `music-library.json` manifest produced by
 * the `newpipe` borough (data boundary, no code dependency).
 */
class MusicLibraryManifestLoaderTest {
    private val manifest =
        """
        {
          "schemaVersion": 1,
          "tracks": [
            {
              "id": "calm-piano-01",
              "title": "Calm piano",
              "artist": "cheroliv",
              "file": "cheroliv/calm-piano-01.mp3",
              "sha256": "abc123",
              "durationSeconds": 92.5,
              "licence": "own",
              "mood": "calm",
              "tags": ["piano", "ambient"],
              "energy": 20
            }
          ]
        }
        """.trimIndent()

    @Test
    fun `parses a produced manifest into the neutral model`() {
        val library = MusicLibraryManifestLoader.parse(manifest)

        assertEquals(1, library.schemaVersion)
        val track = library.find("calm-piano-01")!!
        assertEquals("Calm piano", track.title)
        assertEquals("cheroliv/calm-piano-01.mp3", track.file)
        assertEquals(MusicLicence.OWN, track.licence)
        assertEquals("calm", track.mood)
        assertEquals(20, track.energy)
        assertTrue(track.isUsable())
    }

    @Test
    fun `unknown keys stay tolerated`() {
        val library =
            MusicLibraryManifestLoader.parse(
                """{"schemaVersion":1,"tracks":[{"id":"a","title":"A","file":"a.mp3","licence":"cc0","producer":"newpipe"}],"generatedAt":"2026-11-12"}""",
            )

        assertEquals(1, library.tracks.size)
    }

    @Test
    fun `an unrecognised licence is fail-closed and not usable`() {
        val library =
            MusicLibraryManifestLoader.parse(
                """{"tracks":[{"id":"a","title":"A","file":"a.mp3","licence":"all-rights-reserved"}]}""",
            )

        assertEquals(MusicLicence.UNKNOWN, library.find("a")!!.licence)
        assertTrue(library.usable().isEmpty())
    }

    @Test
    fun `entries missing an id or a title are dropped`() {
        val library =
            MusicLibraryManifestLoader.parse(
                """{"tracks":[{"id":"","title":"A"},{"id":"b","title":""},{"id":"c","title":"C","file":"c.mp3","licence":"own"}]}""",
            )

        assertEquals(listOf("c"), library.tracks.map { it.id })
    }

    @Test
    fun `a missing manifest is an empty library`() {
        val missing = File(Files.createTempDirectory("music-library").toFile(), "absent.json")

        assertTrue(MusicLibraryManifestLoader.load(missing).tracks.isEmpty())
    }

    @Test
    fun `an invalid manifest fails loudly instead of silently muting the video`() {
        val file = Files.createTempDirectory("music-library").toFile().resolve("music-library.json")
        file.writeText("{ not json")

        val failure = assertFailsWith<IllegalArgumentException> { MusicLibraryManifestLoader.load(file) }
        assertTrue(failure.message!!.contains("music-library.json"))
    }
}
