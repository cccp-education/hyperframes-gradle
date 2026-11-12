package hyperframes.sound

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * HF-MUSIC-LIBRARY US-3 — document-ordered placement of the sound cues.
 */
class SoundSequencePlannerTest {
    private fun scene(
        id: String,
        start: Double,
        music: MusicDeclaration? = null,
        sfx: List<SfxDeclaration> = emptyList(),
    ) = SceneSoundInput(id, start, music, sfx)

    @Test
    fun `a scene without cue produces no sound layer`() {
        val plan = SoundSequencePlanner.plan(listOf(scene("a", 0.0), scene("b", 5.0)), 10.0)

        assertTrue(plan.isEmpty())
    }

    @Test
    fun `a cue spans until the next change`() {
        val plan =
            SoundSequencePlanner.plan(
                listOf(
                    scene("a", 0.0, MusicDeclaration(mood = "calm")),
                    scene("b", 5.0),
                    scene("c", 10.0, MusicDeclaration(mood = "epic")),
                    scene("d", 15.0),
                ),
                20.0,
            )

        assertEquals(2, plan.music.size)
        assertEquals("a", plan.music[0].sceneId)
        assertEquals(0.0, plan.music[0].startSeconds)
        assertEquals(10.0, plan.music[0].durationSeconds)
        assertEquals("c", plan.music[1].sceneId)
        assertEquals(10.0, plan.music[1].durationSeconds)
    }

    @Test
    fun `re-declaring the same music is a continuity no-op`() {
        val plan =
            SoundSequencePlanner.plan(
                listOf(
                    scene("a", 0.0, MusicDeclaration(mood = "calm")),
                    scene("b", 5.0, MusicDeclaration(mood = "calm", volume = 0.4)),
                    scene("c", 10.0, MusicDeclaration(trackId = "calm-piano")),
                ),
                15.0,
            )

        // one continuous cue for the calm bed, then the explicit track change
        assertEquals(2, plan.music.size)
        assertEquals("a", plan.music[0].sceneId)
        assertEquals(10.0, plan.music[0].durationSeconds)
        assertEquals("c", plan.music[1].sceneId)
        assertEquals(5.0, plan.music[1].durationSeconds)
    }

    @Test
    fun `stop closes the running cue and later declares a new one`() {
        val plan =
            SoundSequencePlanner.plan(
                listOf(
                    scene("a", 0.0, MusicDeclaration(mood = "calm")),
                    scene("b", 5.0, MusicDeclaration(stop = true)),
                    scene("c", 10.0, MusicDeclaration(mood = "epic")),
                ),
                15.0,
            )

        assertEquals(3, plan.music.size)
        assertTrue(plan.music[1].declaration.stop)
        assertEquals(5.0, plan.music[1].startSeconds)
        assertEquals(15.0 - 10.0, plan.music[2].durationSeconds)
    }

    @Test
    fun `a stop without running music emits nothing`() {
        val plan =
            SoundSequencePlanner.plan(
                listOf(scene("a", 0.0, MusicDeclaration(stop = true))),
                5.0,
            )

        assertTrue(plan.music.isEmpty())
    }

    @Test
    fun `the last cue never outlives the composition`() {
        val plan =
            SoundSequencePlanner.plan(
                listOf(scene("a", 2.0, MusicDeclaration(mood = "calm"))),
                8.0,
            )

        assertEquals(1, plan.music.size)
        assertEquals(2.0, plan.music[0].startSeconds)
        assertEquals(6.0, plan.music[0].durationSeconds)
    }

    @Test
    fun `a cue declaring no fade-out inherits the next fade-in for the handoff`() {
        val plan =
            SoundSequencePlanner.plan(
                listOf(
                    scene("a", 0.0, MusicDeclaration(mood = "calm")),
                    scene("b", 5.0, MusicDeclaration(mood = "epic", fadeInSeconds = 1.5)),
                ),
                10.0,
            )

        assertEquals(1.5, plan.music[0].declaration.fadeOutSeconds)
        assertEquals(1.5, plan.music[1].declaration.fadeInSeconds)
    }

    @Test
    fun `an explicit fade-out is preserved and never overridden`() {
        val plan =
            SoundSequencePlanner.plan(
                listOf(
                    scene("a", 0.0, MusicDeclaration(mood = "calm", fadeOutSeconds = 0.5)),
                    scene("b", 5.0, MusicDeclaration(mood = "epic", fadeInSeconds = 2.0)),
                ),
                10.0,
            )

        assertEquals(0.5, plan.music[0].declaration.fadeOutSeconds)
    }

    @Test
    fun `sfx are anchored to their scene start plus the offset`() {
        val plan =
            SoundSequencePlanner.plan(
                listOf(
                    scene("a", 0.0),
                    scene("b", 5.0, sfx = listOf(SfxDeclaration("impact", 1.5, 0.5))),
                ),
                10.0,
            )

        assertEquals(1, plan.sfx.size)
        assertEquals(6.5, plan.sfx[0].startSeconds)
        assertEquals("b", plan.sfx[0].sceneId)
        assertEquals(0.5, plan.sfx[0].declaration.volume)
    }
}
