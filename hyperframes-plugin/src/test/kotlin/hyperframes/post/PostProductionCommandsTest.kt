package hyperframes.post

import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import video.kernel.AudioPostSettings

/**
 * Pure FFmpeg argv/filter builders of the hyperframes post-production adapters
 * (VK-4 US-4b→d). No I/O.
 */
class PostProductionCommandsTest {

    @Test
    fun `subtitle argv passes the file to the subtitles filter`() {
        val argv = HyperframesSubtitleCommand.buildArgv(
            File("/v/in.mp4"), File("/s/subs.vtt"), File("/v/out.mp4"), "ffmpeg",
        )

        assertThat(argv).containsExactly(
            "ffmpeg", "-y", "-i", "/v/in.mp4",
            "-vf", "subtitles=/s/subs.vtt",
            "-c:a", "copy", "/v/out.mp4",
        )
    }

    @Test
    fun `audio filter graph is loudnorm plus resample without a bed`() {
        val graph = HyperframesAudioPostCommand.buildFilterGraph(AudioPostSettings(), hasBgmInput = false)

        assertThat(graph).contains("loudnorm=I=-16.0:TP=-1.5:LRA=11", "aresample=48000")
    }

    @Test
    fun `audio filter graph mixes a bed when enabled`() {
        val graph = HyperframesAudioPostCommand.buildFilterGraph(
            AudioPostSettings(bgmEnabled = true, bgmLevelDb = -20.0), hasBgmInput = true,
        )

        assertThat(graph).contains("loudnorm=", "aresample=48000", "volume=-20.0dB", "amix=inputs=2")
        assertThat(graph).doesNotContain("sidechaincompress")
    }

    @Test
    fun `audio filter graph ducks when enabled and a bed is present`() {
        val graph = HyperframesAudioPostCommand.buildFilterGraph(
            AudioPostSettings(bgmEnabled = true, duckingEnabled = true), hasBgmInput = true,
        )

        // S-028 — the voice must be asplit before both sidechaincompress and amix.
        assertThat(graph).contains("sidechaincompress", "asplit=2")
    }

    @Test
    fun `audio filter graph ignores ducking without a bed`() {
        val graph = HyperframesAudioPostCommand.buildFilterGraph(
            AudioPostSettings(bgmEnabled = true, duckingEnabled = true), hasBgmInput = false,
        )

        assertThat(graph).contains("loudnorm=I=-16.0:TP=-1.5:LRA=11", "aresample=48000")
        assertThat(graph).doesNotContain("sidechaincompress", "asplit")
    }

    @Test
    fun `audio argv uses -af without a bed and filter_complex with one`() {
        val noBed = HyperframesAudioPostCommand.buildArgv(
            File("/v/in.mp4"), null, File("/v/out.mp4"), AudioPostSettings(), "ffmpeg",
        )
        assertThat(noBed).contains("-af")
        assertThat(noBed).doesNotContain("-filter_complex")

        val bed = HyperframesAudioPostCommand.buildArgv(
            File("/v/in.mp4"), File("/m/bgm.mp3"), File("/v/out.mp4"),
            AudioPostSettings(bgmEnabled = true, bgmFile = "/m/bgm.mp3"), "ffmpeg",
        )
        assertThat(bed).contains("-i", "/m/bgm.mp3", "-filter_complex", "[aout]", "-c:a", "aac")
    }

    @Test
    fun `tts argv omits a blank voice and includes a set one`() {
        val blank = HyperframesTtsCommand.buildArgv("espeak", "", File("/o/a.wav"), "hello")
        assertThat(blank).containsExactly("espeak", "-w", "/o/a.wav", "hello")

        val voiced = HyperframesTtsCommand.buildArgv("espeak", "fr", File("/o/a.wav"), "salut")
        assertThat(voiced).containsExactly("espeak", "-v", "fr", "-w", "/o/a.wav", "salut")
    }
}
