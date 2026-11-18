package hyperframes.sound

import video.kernel.music.MusicLibrary
import video.kernel.music.MusicLicence
import video.kernel.music.MusicTrack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * HF-MUSIC-LIBRARY US-3 — resolution of a placed cue against the shared library.
 */
class SoundCueResolverTest {
    private val library =
        MusicLibrary(
            tracks =
                listOf(
                    MusicTrack(
                        id = "calm-piano",
                        title = "Calm piano",
                        file = "calm/piano-01.mp3",
                        licence = MusicLicence.OWN,
                        mood = "calm",
                    ),
                    MusicTrack(
                        id = "epic-drums",
                        title = "Epic drums",
                        file = "epic/drums.flac",
                        licence = MusicLicence.CC0,
                        mood = "epic",
                    ),
                    MusicTrack(
                        id = "unlicensed",
                        title = "No licence",
                        file = "x/y.mp3",
                        licence = MusicLicence.UNKNOWN,
                        mood = "calm",
                    ),
                ),
        )

    private fun placement(
        declaration: MusicDeclaration,
        scene: String = "s1",
    ) = SoundPlacement(scene, 0.0, 5.0, declaration)

    @Test
    fun `an explicit file wins and needs no library`() {
        val resolution = SoundCueResolver.resolve(placement(MusicDeclaration(file = "audio/mine.mp3")), null)

        assertEquals("audio/mine.mp3", assertIs<SoundResolution.Resolved>(resolution).src)
    }

    @Test
    fun `an explicit track id resolves to the library asset path`() {
        val resolution = SoundCueResolver.resolve(placement(MusicDeclaration(trackId = "calm-piano")), library)

        assertEquals("assets/audio/calm-piano.mp3", assertIs<SoundResolution.Resolved>(resolution).src)
    }

    @Test
    fun `a mood selects the usable track of that mood`() {
        val resolution = SoundCueResolver.resolve(placement(MusicDeclaration(mood = "epic")), library)

        assertEquals("assets/audio/epic-drums.flac", assertIs<SoundResolution.Resolved>(resolution).src)
    }

    @Test
    fun `an unlicensed track is rejected before the render`() {
        val resolution = SoundCueResolver.resolve(placement(MusicDeclaration(trackId = "unlicensed")), library)

        val unresolved = assertIs<SoundResolution.Unresolved>(resolution)
        assertTrue(unresolved.reason.contains("not usable"), "expected an actionable reason, got ${unresolved.reason}")
    }

    @Test
    fun `an unknown track is never substituted`() {
        val resolution = SoundCueResolver.resolve(placement(MusicDeclaration(trackId = "nope")), library)

        assertTrue(assertIs<SoundResolution.Unresolved>(resolution).reason.contains("unknown track"))
    }

    @Test
    fun `a mood with no match is actionable`() {
        val resolution = SoundCueResolver.resolve(placement(MusicDeclaration(mood = "melancholic")), library)

        assertTrue(assertIs<SoundResolution.Unresolved>(resolution).reason.contains("no usable track for mood"))
    }

    @Test
    fun `mood without a library names the missing configuration`() {
        val resolution = SoundCueResolver.resolve(placement(MusicDeclaration(mood = "calm")), null)

        val reason = assertIs<SoundResolution.Unresolved>(resolution).reason
        assertTrue(reason.contains("music.libraryFile"), "expected the configuration hint, got $reason")
    }

    @Test
    fun `a stop cue is silence`() {
        val resolution = SoundCueResolver.resolve(placement(MusicDeclaration(stop = true)), library)

        assertEquals(SoundResolution.Silence, resolution)
    }

    @Test
    fun `the asset path keeps the track extension and falls back to mp3`() {
        assertEquals("assets/audio/a.ogg", SoundCueResolver.assetPath("a", "x/y/a.ogg"))
        assertEquals("assets/audio/a.mp3", SoundCueResolver.assetPath("a", ""))
    }

    @Test
    fun `an sfx keeps a file path and prefixes a bare library id`() {
        val path = SfxDeclaration("assets/audio/whoosh.mp3")

        assertEquals("assets/audio/whoosh.mp3", SoundCueResolver.resolveSfx(SfxPlacement("s1", 0.0, path)))
        assertEquals(
            "assets/audio/impact",
            SoundCueResolver.resolveSfx(SfxPlacement("s1", 0.0, SfxDeclaration("impact"))),
        )
    }
}
