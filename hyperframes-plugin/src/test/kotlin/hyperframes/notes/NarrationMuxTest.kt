package hyperframes.notes

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * HF-SPEAKER-NOTES US-5 — the pure alignment of the takes and the FFmpeg
 * command that muxes them over the rendered video.
 */
class NarrationMuxTest {
    private val html =
        """
        <div id="stage">
          <div class="scene clip" data-composition-id="intro">
            <div class="hyperframes-track hyperframes-narration" data-narration-index="1" data-narration-start="0"><p>One.</p></div>
          </div>
          <div class="scene clip" data-composition-id="demo">
            <div class="hyperframes-track hyperframes-narration" data-narration-index="2" data-narration-start="5"><p>Two.</p></div>
          </div>
        </div>
        """.trimIndent()

    @Test
    fun `aligns each take with the scene start in document order`() {
        val plan = NarrationMuxPlanner.plan(html, listOf("/tmp/narration-1.wav", "/tmp/narration-2.wav"))

        assertThat(plan.takes)
            .containsExactly(
                NarrationTake(file = "/tmp/narration-1.wav", startSeconds = 0.0),
                NarrationTake(file = "/tmp/narration-2.wav", startSeconds = 5.0),
            )
    }

    @Test
    fun `returns an empty plan when no take is available`() {
        assertThat(NarrationMuxPlanner.plan(html, emptyList()).isEmpty()).isTrue()
    }

    @Test
    fun `returns an empty plan when the composition has no narration anchor`() {
        assertThat(NarrationMuxPlanner.plan("<div id=\"stage\"></div>", listOf("/tmp/narration-1.wav")).isEmpty())
            .isTrue()
    }

    @Test
    fun `builds a single-take delay graph`() {
        val plan = NarrationMuxPlan(listOf(NarrationTake("/tmp/narration-1.wav", 2.5)))

        assertThat(NarrationMuxCommand.filterGraph(plan))
            .isEqualTo("[1:a]adelay=2500|2500,apad[aout]")
    }

    @Test
    fun `mixes several delayed takes without normalizing the narration level`() {
        val plan =
            NarrationMuxPlan(
                listOf(
                    NarrationTake("/tmp/narration-1.wav", 0.0),
                    NarrationTake("/tmp/narration-2.wav", 5.0),
                ),
            )

        assertThat(NarrationMuxCommand.filterGraph(plan))
            .isEqualTo("[1:a]adelay=0|0[a0];[2:a]adelay=5000|5000[a1];[a0][a1]amix=inputs=2:normalize=0,apad[aout]")
    }

    @Test
    fun `copies the video stream and bounds the result on the video`() {
        val plan = NarrationMuxPlan(listOf(NarrationTake("/tmp/narration-1.wav", 0.0)))

        val argv = NarrationMuxCommand.buildArgv("/tmp/in.mp4", "/tmp/out.mp4", plan, "ffmpeg")

        assertThat(argv).containsSequence("-i", "/tmp/in.mp4", "-i", "/tmp/narration-1.wav")
        assertThat(argv).containsSequence("-map", "0:v:0", "-map", "[aout]")
        assertThat(argv).containsSequence("-c:v", "copy", "-shortest")
        assertThat(argv.last()).isEqualTo("/tmp/out.mp4")
    }
}
